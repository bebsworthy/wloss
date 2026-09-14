package app.wlo.app.ui.settings

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.wlo.app.notification.WeighInReminder
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloSwitchRow
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.vault.AppLockController
import app.wlo.core.vault.LockTimeout
import app.wlo.core.vault.canPromptBiometric
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Settings (IA §1): ONE screen deep — AI Studio and Data Vault live one tap
 * behind it, the app lock (F13 §3: biometric + configurable timeout) renders
 * inline because it is the discretion switch every sensitive surface defers
 * to. No drawer, no settings search (IA §7).
 */
@Composable
public fun SettingsScreen(
    onOpenAiStudio: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canPrompt = (context as? FragmentActivity)?.canPromptBiometric() == true

    // Android 13+ gates reminders behind POST_NOTIFICATIONS; a denial keeps
    // the toggle off — no re-prompt loop, no settings lecture.
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.setReminder(granted, state.reminderMinuteOfDay)
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("settings"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        WloScreenTitle(title = "Settings", modifier = Modifier.testTag("settings-title"))
        Text(
            text = "One screen, then the two deep surfaces. Everything here lives on this device.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        WloListRow(
            label = "AI Studio",
            secondary = "Consent, receipts, models, kill switch",
            chevron = true,
            onClick = onOpenAiStudio,
            modifier = Modifier.testTag("settings-open-ai"),
        )
        WloListRow(
            label = "Data Vault",
            secondary = "Backups, restore, export, storage",
            chevron = true,
            onClick = onOpenVault,
            modifier = Modifier.testTag("settings-open-vault"),
        )
        WloListRow(
            label = "Goals",
            secondary = "Goal weight, pace, budget — every save is a new version",
            chevron = true,
            onClick = onOpenGoals,
            modifier = Modifier.testTag("settings-open-goals"),
        )
        WloListRow(
            label = "Profile",
            secondary = "Sex, birth year, height, activity — the facts the math reads",
            chevron = true,
            onClick = onOpenProfile,
            modifier = Modifier.testTag("settings-open-profile"),
        )

        // --- App lock (F13 §3) ------------------------------------------------
        WloCard(
            modifier = Modifier.testTag("settings-applock"),
            header = { WloCardHeader(title = "App lock") },
        ) {
            if (canPrompt) {
                WloSwitchRow(
                    label = "Ask for your screen lock (biometric or PIN) when you come back",
                    checked = state.appLockEnabled,
                    onCheckedChange = viewModel::setAppLock,
                    modifier = Modifier.testTag("settings-applock-toggle"),
                )
            } else {
                Text(
                    text =
                        "This device has no screen lock configured, so there is nothing to " +
                            "verify against. Set one in Android's security settings first.",
                    style = wloType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("settings-applock-availability"),
                )
            }
            if (state.appLockEnabled) {
                Text(text = "Lock when I've been away for…", style = wloType.statS)
                Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                    for (timeout in LockTimeout.entries) {
                        SelectChip(
                            label = timeoutLabel(timeout),
                            selected = state.lockTimeout == timeout,
                            onClick = { viewModel.setLockTimeout(timeout) },
                            modifier = Modifier.testTag("settings-lock-timeout-${timeout.wireName}"),
                        )
                    }
                }
                Text(
                    text =
                        "The lock is a convenience screen — your data is already encrypted at rest, " +
                            "and a weigh-in glance-away shouldn't relock everything. One minute is the default.",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
            if (!canPrompt) {
                Text(
                    text = "Open Android → Security to set a screen lock, then come back.",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                    modifier = Modifier.testTag("settings-applock-no-lock"),
                )
            }
        }

        // --- Weigh-in reminder (F06 §4, WLO-0040) ---------------------------
        WloCard(
            modifier = Modifier.testTag("settings-reminder"),
            header = { WloCardHeader(title = "Weigh-in reminder") },
        ) {
            WloSwitchRow(
                label = "One soft daily nudge — an invitation, never a streak",
                checked = state.reminderEnabled,
                onCheckedChange = { wanted ->
                    if (wanted && Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setReminder(wanted, state.reminderMinuteOfDay)
                    }
                },
                modifier = Modifier.testTag("settings-reminder-toggle"),
            )
            if (state.reminderEnabled) {
                Text(text = "Nudge me around…", style = wloType.statS)
                Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                    for (minute in listOf(360, 420, 450, 510, 720, 1200)) {
                        SelectChip(
                            label = minuteLabel(minute),
                            selected = state.reminderMinuteOfDay == minute,
                            onClick = { viewModel.setReminder(true, minute) },
                            modifier = Modifier.testTag("settings-reminder-$minute"),
                        )
                    }
                }
            }
        }

        Text(
            text =
                "WLO is free, offline-first, and account-less. No telemetry exists in this app — " +
                    "the egress ledger in AI Studio is the whole story.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
        )
    }
}

private fun minuteLabel(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

private fun timeoutLabel(timeout: LockTimeout): String =
    when (timeout) {
        LockTimeout.IMMEDIATE -> "Immediately"
        LockTimeout.ONE_MINUTE -> "1 minute"
        LockTimeout.FIVE_MINUTES -> "5 minutes"
    }

/** Settings state: the app-lock posture (F13 §3) + the reminder (F06 §4). */
public data class SettingsUiState(
    public val appLockEnabled: Boolean = false,
    public val lockTimeout: LockTimeout = LockTimeout.ONE_MINUTE,
    public val reminderEnabled: Boolean = false,
    public val reminderMinuteOfDay: Int = 450,
)

public class SettingsViewModel(
    private val settings: SettingsStore,
    private val appLock: AppLockController,
    private val appContext: Context,
) : ViewModel() {
    public val state: StateFlow<SettingsUiState> =
        combine(
            combine(settings.appLockEnabled, settings.lockTimeout) { enabled, timeout ->
                SettingsUiState(appLockEnabled = enabled, lockTimeout = LockTimeout.fromWire(timeout))
            },
            settings.weighInReminderEnabled,
            settings.weighInReminderMinuteOfDay,
        ) { base, reminderEnabled, minute ->
            base.copy(reminderEnabled = reminderEnabled, reminderMinuteOfDay = minute)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /**
     * Persists the reminder AND reschedules the WorkManager work — one door,
     * so the on-device schedule can never drift from the stored choice.
     */
    public fun setReminder(
        enabled: Boolean,
        minuteOfDay: Int,
    ) {
        viewModelScope.launch {
            settings.setWeighInReminder(enabled, minuteOfDay)
            WeighInReminder.reschedule(appContext, enabled, minuteOfDay)
        }
    }

    public fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { settings.setAppLockEnabled(enabled) }
    }

    public fun setLockTimeout(timeout: LockTimeout) {
        viewModelScope.launch { settings.setLockTimeout(timeout.wireName) }
    }

    /** The gate's honest escape hatch on credential-less devices. */
    public fun turnOffAppLockAndUnlock() {
        viewModelScope.launch {
            settings.setAppLockEnabled(false)
            appLock.onUnlock()
        }
    }
}
