package app.wlo.core.vault

import android.app.Activity
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import app.wlo.core.ports.BiometricGate
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The BiometricGate port's platform impl (F13 §3: "biometric app lock
 * (BiometricPrompt, PIN fallback) with a configurable timeout").
 *
 * FLAGGED choice (for ADR-008 / PART B): the gate is a CONVENIENCE lock, not
 * an encryption gate — it authenticates presence, it does not key any
 * cipher. Reasons: (1) everything sensitive is already encrypted at rest
 * (Keystore partition keys; the passphrase-derived backup), (2) crypto-bound
 * prompts (setUserAuthenticationRequired keys) brick data on vendor keystore
 * lockouts — the failure mode is worse than the threat here, (3) v1 carries
 * zero extra dependencies: the PLATFORM android.hardware.biometrics
 * .BiometricPrompt (API 28+, ADR-002 minSdk 29) with device-credential
 * fallback; the androidx.biometric library would drag a fragment/dialog
 * stack we don't otherwise use. MainActivity extends FragmentActivity
 * (a ComponentActivity since fragment 1.3), so compose keeps working.
 */
public class PromptBiometricGate(
    private val activity: FragmentActivity,
) : BiometricGate {
    override suspend fun unlock(reason: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val executor =
                androidx.core.content.ContextCompat
                    .getMainExecutor(activity)
            val prompt =
                android.hardware.biometrics.BiometricPrompt
                    .Builder(activity)
                    .setTitle("Unlock WLO")
                    .setSubtitle(reason)
                    .apply {
                        if (android.os.Build.VERSION.SDK_INT >= 30) {
                            setAllowedAuthenticators(
                                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                            )
                        } else {
                            setDeviceCredentialAllowed(true)
                        }
                    }.build()
            val cancellation = android.os.CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            prompt.authenticate(
                cancellation,
                executor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) {
                        continuation.resume(Result.success(Unit))
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        // A user "cancel" is not a failure of the lock — but the
                        // caller treats any non-success as locked; the reason is
                        // the dialog's own words (D8: values, not magic codes).
                        continuation.resume(Result.failure(BiometricLockedException(errString.toString())))
                    }
                },
            )
        }
}

/** The gate's typed failure (dialog error text rides along). */
public class BiometricLockedException(
    public val detail: String,
) : Exception(detail)

/**
 * Whether the device can show a biometric/credential prompt at all — PART B
 * uses this to render the "app lock" row as unavailable instead of failing.
 * Platform manager (no androidx.biometric dependency — see the class FLAG).
 */
public fun FragmentActivity.canPromptBiometric(): Boolean =
    runCatching {
        val manager = getSystemService(android.hardware.biometrics.BiometricManager::class.java) ?: return false
        val result =
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                manager.canAuthenticate(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            } else {
                manager.canAuthenticate()
            }
        result == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

/** Applies or clears FLAG_SECURE on [activity]'s window for [route]. */
public fun applyFlagSecure(
    activity: Activity,
    route: String?,
) {
    if (SecureSurfaces.isSecure(route)) {
        activity.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * Binds lock lifecycle plumbing to any [LifecycleOwner] (MainActivity):
 * background timestamps for [AppLockController]. :app wires the timeout from
 * the settings flow at each ON_START.
 */
public fun LifecycleOwner.bindAppLock(
    controller: AppLockController,
    timeoutAt: () -> LockTimeout,
    clock: () -> Long,
) {
    lifecycle.addObserver(
        LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> controller.onBackground(clock())
                Lifecycle.Event.ON_START -> controller.onForeground(clock(), timeoutAt())
                else -> Unit
            }
        },
    )
}
