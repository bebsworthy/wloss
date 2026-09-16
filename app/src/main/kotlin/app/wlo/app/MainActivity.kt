package app.wlo.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.wlo.app.navigation.WloApp
import app.wlo.app.ui.lock.LockGateScreen
import app.wlo.core.designsystem.WloTheme
import app.wlo.core.vault.AppLockController
import app.wlo.core.vault.AppLockPosture
import app.wlo.core.vault.LockTimeout
import app.wlo.core.vault.applyFlagSecure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

/**
 * The single activity (R-D2 shell). Dark is forced as the base scheme (R-D1 —
 * the 6 a.m. weigh-in happens in a dark bathroom); `wlo://` links arrive via
 * the manifest filter, first launch is handled by NavHost itself, re-delivery
 * (singleTask) by [onNewIntent].
 *
 * M6 (WLO-0028 PART A): the activity is a [FragmentActivity] (a
 * ComponentActivity since fragment 1.3 — compose untouched) because the
 * PLATFORM BiometricPrompt constructor binds one (PromptBiometricGate, F13
 * §3). Discretion hooks per IA §6 / F13 §3:
 *  - FLAG_SECURE follows the nav route through [SecureSurfaces] (Archive tab,
 *    vault surfaces, F09 photo contexts) — and holds on the lock gate route;
 *  - the app lock's background timestamps feed [AppLockController], whose
 *    `locked` state swaps the whole shell for the lock gate (PART B): the
 *    timeout re-reads the settings flow on every ON_START so a change in
 *    Settings applies to the very next background blip.
 */
public class MainActivity : FragmentActivity() {
    /**
     * Test hook: the route reported by the last destination-change firing.
     * Instrumented deep-link tests poll this instead of scraping the view tree.
     */
    public var currentDestinationForVerification: String? = null
        private set

    /**
     * Test hook: WHAT the hub route currently renders — "onboarding" while the
     * shell gate is fresh, "hub" once a plan exists. Lets deep-link tests
     * verify the cold-start rule without scraping the compose tree.
     */
    public var currentSurfaceForVerification: String? = null
        private set

    private val deepLinkIntent = mutableStateOf<Intent?>(null)

    /**
     * The configured lock timeout, kept current by the settings collector —
     * the lifecycle observer's `timeoutAt` lambda reads this non-suspend.
     */
    private var lockTimeout: LockTimeout = LockTimeout.ONE_MINUTE

    /**
     * The F13 §3 backup-folder pick (PART B renders the entry point; the
     * plumbing lands here so the E2E drives the REAL user flow). A successful
     * pick takes the persistable SAF grant and stores the tree uri — the
     * [app.wlo.core.ports.BackupScheduler] destination thereafter.
     */
    private val backupFolderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                val settings = GlobalContext.get().get<app.wlo.core.datastore.SettingsStore>()
                lifecycleScope.launch { settings.setBackupFolderUri(uri.toString()) }
            }
        }

    /** Opens the SAF folder picker seeded at the emulator's Documents folder. */
    public fun pickBackupFolder(initialUri: android.net.Uri? = null) {
        backupFolderPicker.launch(initialUri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fail closed before Compose (or even the first destination callback)
        // can expose a sensitive route in a screenshot/recents thumbnail.
        applyFlagSecure(this, "applock/gate")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val koin = GlobalContext.get()
        val appLock = koin.get<AppLockController>()
        val settings = koin.get<app.wlo.core.datastore.SettingsStore>()
        val (appLockEnabled, startupTimeout) =
            runBlocking {
                settings.appLockEnabled.first() to LockTimeout.fromWire(settings.lockTimeout.first())
            }
        lockTimeout = startupTimeout
        // A newly constructed controller is UNRESOLVED. Resolve it before
        // setContent: enabled always means LOCKED after cold start/process
        // death because an earlier background timestamp cannot be trusted.
        appLock.resolveStartup(appLockEnabled)

        // App lock: background timestamps + the CONFIGURED timeout. The timeout
        // is re-read on every ON_START (PART B: a Settings change applies to
        // the very next background blip, not the next launch).
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> appLock.onBackground(System.currentTimeMillis())
                    Lifecycle.Event.ON_START -> {
                        lifecycleScope.launch { lockTimeout = LockTimeout.fromWire(settings.lockTimeout.first()) }
                        appLock.onForeground(System.currentTimeMillis(), lockTimeout)
                    }

                    else -> Unit
                }
            },
        )
        lifecycleScope.launch {
            settings.lockTimeout.collect { wire -> lockTimeout = LockTimeout.fromWire(wire) }
        }

        setContent {
            // R-D1: dark is canonical; the light scheme is future work.
            WloTheme(darkTheme = true) {
                val lockPosture by appLock.posture.collectAsStateWithLifecycle()
                val persistedLockEnabled by
                    settings.appLockEnabled.collectAsStateWithLifecycle(initialValue = appLockEnabled)
                // UNRESOLVED is also gate posture. It should normally last
                // only until the synchronous startup read above, but keeping
                // this branch fail-closed prevents future async regressions.
                val gateShowing = persistedLockEnabled && lockPosture != AppLockPosture.UNLOCKED
                // Discreet mode: while the gate shows, the window is FLAG_SECURE
                // too (the gate route is in SecureSurfaces) — no recents leak.
                val secureRoute = if (gateShowing) "applock/gate" else currentDestinationForVerification
                applyFlagSecure(this@MainActivity, secureRoute)
                if (gateShowing) {
                    LockGateScreen(onUnlocked = { appLock.onUnlock() })
                } else {
                    WloApp(
                        newIntent = deepLinkIntent.value,
                        onDestinationChanged = { route ->
                            currentDestinationForVerification = route
                            // Discreet mode: FLAG_SECURE on Archive/vault/photo
                            // surfaces, cleared everywhere else (IA §6).
                            applyFlagSecure(this@MainActivity, route)
                        },
                        onSurfaceChanged = { surface -> currentSurfaceForVerification = surface },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // No setIntent() here: it would rewrite the activity's launch intent,
        // which (a) nothing in the app reads — the link rides deepLinkIntent —
        // and (b) makes ActivityScenario treat every later lifecycle event as
        // "intent does not match", freezing its state machine (teardown hangs
        // waiting for DESTROYED that it already refuses to observe).
        deepLinkIntent.value = intent
    }

    /**
     * Test hook: [onNewIntent] is protected, so the instrumented singleTask
     * re-delivery test goes through this public forwarder.
     */
    public fun deliverNewIntentForVerification(intent: Intent) {
        onNewIntent(intent)
    }
}
