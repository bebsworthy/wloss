package app.wlo.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
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
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canPrompt = (context as? FragmentActivity)?.canPromptBiometric() == true

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("settings"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(
            text = "Settings",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("settings-title"),
        )
        Text(
            text = "One screen, then the two deep surfaces. Everything here lives on this device.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsRow(
            tag = "settings-open-ai",
            label = "AI Studio",
            sub = "Consent, receipts, models, kill switch",
            onClick = onOpenAiStudio,
        )
        SettingsRow(
            tag = "settings-open-vault",
            label = "Data Vault",
            sub = "Backups, restore, export, storage",
            onClick = onOpenVault,
        )

        // --- App lock (F13 §3) ------------------------------------------------
        Surface(
            shape = WloShape.Card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().testTag("settings-applock"),
        ) {
            Column(Modifier.padding(WloSpacing.PAD_CARD), verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text = "App lock", style = wloType.title)
                        Text(
                            text =
                                if (canPrompt) {
                                    "Ask for your screen lock (biometric or PIN) when you come back."
                                } else {
                                    "This device has no screen lock configured, so there is nothing to " +
                                        "verify against. Set one in Android's security settings first."
                                },
                            style = wloType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("settings-applock-availability"),
                        )
                    }
                    Switch(
                        checked = state.appLockEnabled,
                        onCheckedChange = viewModel::setAppLock,
                        enabled = canPrompt,
                        colors =
                            SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedThumbColor = wloExtendedColors.surfaceSunken,
                            ),
                        modifier = Modifier.testTag("settings-applock-toggle"),
                    )
                }
                if (state.appLockEnabled) {
                    Text(text = "Lock when I've been away for…", style = wloType.statS)
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        for (timeout in LockTimeout.entries) {
                            app.wlo.core.designsystem.SelectChip(
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

@Composable
private fun SettingsRow(
    tag: String,
    label: String,
    sub: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(tag),
        onClick = onClick,
    ) {
        Column(Modifier.padding(WloSpacing.PAD_CARD)) {
            Text(text = label, style = wloType.title)
            Text(text = sub, style = wloType.receipt, color = wloExtendedColors.textTertiary)
        }
    }
}

private fun timeoutLabel(timeout: LockTimeout): String =
    when (timeout) {
        LockTimeout.IMMEDIATE -> "Immediately"
        LockTimeout.ONE_MINUTE -> "1 minute"
        LockTimeout.FIVE_MINUTES -> "5 minutes"
    }

/** Settings state: the app-lock posture (F13 §3). */
public data class SettingsUiState(
    public val appLockEnabled: Boolean = false,
    public val lockTimeout: LockTimeout = LockTimeout.ONE_MINUTE,
)

public class SettingsViewModel(
    private val settings: SettingsStore,
    private val appLock: AppLockController,
) : ViewModel() {
    public val state: StateFlow<SettingsUiState> =
        combine(settings.appLockEnabled, settings.lockTimeout) { enabled, timeout ->
            SettingsUiState(appLockEnabled = enabled, lockTimeout = LockTimeout.fromWire(timeout))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

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
