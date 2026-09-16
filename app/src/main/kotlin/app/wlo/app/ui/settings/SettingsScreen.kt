package app.wlo.app.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.wlo.app.notification.ReminderAvailability
import app.wlo.app.notification.WeighInReminder
import app.wlo.app.notification.WeighInReminderWorker
import app.wlo.core.common.MassUnit
import app.wlo.core.common.getOrNull
import app.wlo.core.data.ProfileRepository
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloSwitchRow
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.UnitSystem
import app.wlo.core.vault.AppLockController
import app.wlo.core.vault.LockTimeout
import app.wlo.core.vault.canPromptBiometric
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** Grouped app preferences with focused detail surfaces. */
@Composable
public fun SettingsScreen(
    onOpenAiStudio: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenDetail: (String) -> Unit,
    section: String = "overview",
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val canPrompt = (context as? FragmentActivity)?.canPromptBiometric() == true
    var showTimePicker by remember { mutableStateOf(false) }
    var showUnits by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshReminderAvailability()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+ gates reminders behind POST_NOTIFICATIONS; a denial keeps
    // the toggle off — no re-prompt loop, no settings lecture.
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.setReminder(true, state.reminderMinuteOfDay)
            viewModel.refreshReminderAvailability()
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
        if (section == "overview") {
            app.wlo.core.designsystem.WloSettingsGroup("Everyday use") {
                WloListRow(
                    label = "Weight unit",
                    secondary = state.massUnit.settingsLabel(),
                    chevron = true,
                    onClick = { showUnits = true },
                    modifier = Modifier.testTag("settings-weight-unit"),
                )
                WloListRow(
                    label = "Weigh-in reminder",
                    secondary =
                        when {
                            state.reminderEnabled -> minuteLabel(state.reminderMinuteOfDay)
                            state.reminderRequested -> "Blocked by Android"
                            else -> "Off"
                        },
                    chevron = true,
                    onClick = { onOpenDetail("reminder") },
                )
            }
            app.wlo.core.designsystem.WloSettingsGroup("Privacy") {
                WloListRow(
                    label = "App lock",
                    secondary =
                        when {
                            !canPrompt -> "Device screen lock required"
                            state.appLockEnabled -> timeoutLabel(state.lockTimeout)
                            else -> "Off"
                        },
                    chevron = true,
                    onClick = { onOpenDetail("lock") },
                )
                WloListRow(
                    label = "AI",
                    secondary = "On-device models and cloud access",
                    chevron = true,
                    onClick = onOpenAiStudio,
                    modifier = Modifier.testTag("settings-open-ai"),
                )
                WloListRow(
                    label = "Diagnostics",
                    secondary = "Optional crash reporting",
                    chevron = true,
                    onClick = { onOpenDetail("diagnostics") },
                )
            }
            app.wlo.core.designsystem.WloSettingsGroup("Data") {
                WloListRow(
                    label = "Data & backup",
                    secondary = "Backups, import, export and storage",
                    chevron = true,
                    onClick = onOpenVault,
                    modifier = Modifier.testTag("settings-open-vault"),
                )
                WloListRow(
                    label = "Health Connect",
                    secondary = "Weight and body-fat access",
                    chevron = true,
                    onClick = onOpenHealth,
                    modifier = Modifier.testTag("settings-open-health"),
                )
            }
        }
        if (showUnits || section == "units") {
            AlertDialog(
                onDismissRequest = { showUnits = false },
                title = { Text("Weight unit") },
                text = {
                    Column {
                        MassUnit.entries.forEach { unit ->
                            androidx.compose.material3.ListItem(
                                headlineContent = { Text(unit.settingsLabel()) },
                                leadingContent = {
                                    androidx.compose.material3.RadioButton(
                                        selected = state.massUnit == unit,
                                        onClick = null,
                                    )
                                },
                                modifier =
                                    Modifier
                                        .selectable(
                                            selected = state.massUnit == unit,
                                            role = androidx.compose.ui.semantics.Role.RadioButton,
                                            onClick = {
                                                viewModel.setMassUnit(unit)
                                                showUnits = false
                                            },
                                        ).testTag("settings-unit-${unit.symbol}"),
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showUnits = false }) { Text("Done") } },
            )
        }
        // --- App lock (F13 §3) ------------------------------------------------
        if (section == "lock") {
            if (canPrompt) {
                WloSwitchRow(
                    label = "Lock WLO",
                    checked = state.appLockEnabled,
                    onCheckedChange = viewModel::setAppLock,
                    modifier = Modifier.testTag("settings-applock-toggle"),
                )
            } else {
                Text(
                    text =
                        "Set up an Android screen lock before enabling app lock.",
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
                        "Choose how soon WLO locks after you leave the app.",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
            if (!canPrompt) {
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) {
                    Text("Open Android security settings")
                }
                Text(
                    text = "Uses your device PIN or biometrics.",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                    modifier = Modifier.testTag("settings-applock-no-lock"),
                )
            }
        }

        // --- Weigh-in reminder (F06 §4, WLO-0040) ---------------------------
        if (section == "reminder") {
            WloSwitchRow(
                label = "Weigh-in reminder",
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
            if (state.reminderRequested) {
                WloListRow(
                    label = "Approximate local time",
                    secondary = minuteLabel(state.reminderMinuteOfDay),
                    chevron = true,
                    onClick = { showTimePicker = true },
                    modifier = Modifier.testTag("settings-reminder-time"),
                )
                Text(
                    if (state.reminderEnabled) {
                        "Android schedules this approximately; it is not an exact alarm."
                    } else {
                        reminderAvailabilityCopy(state.reminderAvailability)
                    },
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
                if (!state.reminderEnabled) {
                    app.wlo.core.designsystem.WloSecondaryButton(
                        label = "Open notification settings",
                        onClick = {
                            val intent =
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("settings-reminder-repair"),
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        ReminderTimePicker(
            minuteOfDay = state.reminderMinuteOfDay,
            is24Hour = DateFormat.is24HourFormat(context),
            onDismiss = { showTimePicker = false },
            onConfirm = { minute ->
                showTimePicker = false
                viewModel.setReminder(true, minute)
            },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReminderTimePicker(
    minuteOfDay: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val pickerState =
        rememberTimePickerState(
            initialHour = minuteOfDay / 60,
            initialMinute = minuteOfDay % 60,
            is24Hour = is24Hour,
        )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weigh-in reminder time") },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            Button(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) { Text("Set time") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun minuteLabel(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

private fun reminderAvailabilityCopy(availability: ReminderAvailability): String =
    when (availability) {
        ReminderAvailability.AVAILABLE -> "Android schedules this approximately; it is not an exact alarm."
        ReminderAvailability.PERMISSION_REQUIRED -> "Notification permission is off. Your reminder time is still saved."
        ReminderAvailability.APP_BLOCKED -> "Notifications are off for WLO. Your reminder time is still saved."
        ReminderAvailability.CHANNEL_BLOCKED ->
            "Weigh-in reminders are off in Android settings. Your time is still saved."
    }

private fun timeoutLabel(timeout: LockTimeout): String =
    when (timeout) {
        LockTimeout.IMMEDIATE -> "Immediately"
        LockTimeout.ONE_MINUTE -> "1 minute"
        LockTimeout.FIVE_MINUTES -> "5 minutes"
    }

/** Settings state: the app-lock posture (F13 §3) + the reminder (F06 §4). */
public data class SettingsUiState(
    public val massUnit: MassUnit = MassUnit.KILOGRAM,
    public val appLockEnabled: Boolean = false,
    public val lockTimeout: LockTimeout = LockTimeout.ONE_MINUTE,
    public val reminderEnabled: Boolean = false,
    public val reminderRequested: Boolean = false,
    public val reminderAvailability: ReminderAvailability = ReminderAvailability.AVAILABLE,
    public val reminderMinuteOfDay: Int = 450,
)

public class SettingsViewModel(
    private val settings: SettingsStore,
    private val profiles: ProfileRepository,
    private val appLock: AppLockController,
    private val appContext: Context,
) : ViewModel() {
    private val reminderAvailability = MutableStateFlow(WeighInReminderWorker.availability(appContext))

    public val state: StateFlow<SettingsUiState> =
        combine(
            combine(settings.massUnit, settings.appLockEnabled, settings.lockTimeout) { massUnit, enabled, timeout ->
                SettingsUiState(
                    massUnit = massUnit,
                    appLockEnabled = enabled,
                    lockTimeout = LockTimeout.fromWire(timeout),
                )
            },
            settings.weighInReminderEnabled,
            settings.weighInReminderMinuteOfDay,
            reminderAvailability,
        ) { base, reminderRequested, minute, availability ->
            base.copy(
                reminderEnabled = reminderRequested && availability == ReminderAvailability.AVAILABLE,
                reminderRequested = reminderRequested,
                reminderAvailability = availability,
                reminderMinuteOfDay = minute,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    public fun refreshReminderAvailability() {
        WeighInReminderWorker.ensureChannel(appContext)
        reminderAvailability.value = WeighInReminderWorker.availability(appContext)
    }

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

    /**
     * DataStore is the global render authority. The active profile retains a
     * compatibility mirror for existing vault documents and restores.
     */
    public fun setMassUnit(unit: MassUnit) {
        viewModelScope.launch {
            settings.setMassUnit(unit)
            profiles.active().getOrNull()?.let { profile ->
                profiles.setUnitPreference(profile.id, unit.toUnitSystem())
            }
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

private fun MassUnit.settingsLabel(): String =
    when (this) {
        MassUnit.KILOGRAM -> "Kilograms (kg)"
        MassUnit.POUND -> "Pounds (lb)"
    }

private fun MassUnit.toUnitSystem(): UnitSystem =
    when (this) {
        MassUnit.KILOGRAM -> UnitSystem.METRIC
        MassUnit.POUND -> UnitSystem.IMPERIAL
    }
