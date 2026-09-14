package app.wlo.app.ui.lock

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.fragment.app.FragmentActivity
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.vault.PromptBiometricGate
import app.wlo.core.vault.canPromptBiometric
import kotlinx.coroutines.launch

/**
 * The app-lock gate (F13 §3): a CONVENIENCE lock — it authenticates presence,
 * it never keys a cipher (everything sensitive is already encrypted at rest,
 * see PromptBiometricGate's FLAG). Renders in three honest states:
 *
 *  1. device CAN prompt → "Unlock" runs the platform BiometricPrompt with
 *     device-credential fallback (PIN/pattern where no biometrics enrolled);
 *  2. device has NO screen lock → the gate cannot verify anyone, so it says
 *     so, links to the system security settings, and offers the one-tap way
 *     OUT (turn app lock off) rather than dead-locking the user;
 *  3. cancel/error → still locked, the platform dialog's words ride along.
 *
 * The 200 ms fade and no splash theater per F13 §4; FLAG_SECURE applies on
 * this route (registered in SecureSurfaces) so the gate never appears in
 * recents thumbnails either.
 */
@Composable
public fun LockGateScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val canPrompt = activity != null && activity.canPromptBiometric()
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(WloSpacing.SCREEN)
                .testTag("applock-gate"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WloScreenTitle(title = "WLO is locked")
        Text(
            text = "Your data is on this device, encrypted. Confirm it's you to open it.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = WloSpacing.TIGHT, bottom = WloSpacing.CARD),
        )
        if (canPrompt) {
            WloButton(
                label = "Unlock",
                onClick = {
                    if (activity != null) {
                        scope.launch {
                            val gate = PromptBiometricGate(activity)
                            val result =
                                gate.unlock(reason = "Open WLO")
                            result.onSuccess { onUnlocked() }
                            // Failure keeps the gate up; the platform dialog
                            // already said why (cancelled, locked out, …).
                        }
                    }
                },
                modifier = Modifier.testTag("applock-unlock"),
            )
        } else {
            Text(
                text =
                    "This device has no screen lock, so there is nothing to verify against. Set one " +
                        "in Android's security settings — or turn the app lock off here.",
                style = wloType.body,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.testTag("applock-no-credential"),
            )
            Spacer(Modifier.height(WloSpacing.CARD))
            WloSecondaryButton(
                label = "Open security settings",
                onClick = {
                    val intent = Intent("android.settings.SECURITY_SETTINGS")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                modifier = Modifier.testTag("applock-open-security"),
            )
            WloSecondaryButton(
                label = "Turn app lock off",
                onClick = {
                    val koin =
                        org.koin.core.context.GlobalContext
                            .get()
                    val settings = koin.get<app.wlo.core.datastore.SettingsStore>()
                    val controller = koin.get<app.wlo.core.vault.AppLockController>()
                    scope.launch {
                        settings.setAppLockEnabled(false)
                        controller.onUnlock()
                        onUnlocked()
                    }
                },
                modifier = Modifier.testTag("applock-turn-off"),
            )
        }
    }
}
