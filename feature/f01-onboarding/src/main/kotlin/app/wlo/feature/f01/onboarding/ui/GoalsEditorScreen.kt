package app.wlo.feature.f01.onboarding.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloForecastBands
import app.wlo.core.designsystem.WloForecastCard
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.engines.ForecastBands
import app.wlo.core.engines.GoalForecastResult
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Provenance
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalMode
import app.wlo.feature.f01.onboarding.state.GoalFormState
import app.wlo.feature.f01.onboarding.state.GoalsEditorEvent
import app.wlo.feature.f01.onboarding.state.GoalsEditorViewModel
import kotlinx.datetime.LocalDate

/**
 * The goals editor (WLO-0035 W4): goal weight, pace, target date, daily
 * budget — written as a new Targets version through the STUDIO_F01 door with
 * the diff ribbon shown, plus the versions ledger with revert. The wizard is
 * this editor's first run, never its replacement.
 */
@Composable
public fun GoalsEditorScreen(
    viewModel: GoalsEditorViewModel,
    onBack: () -> Unit = {},
    registerUpHandler: ((() -> Unit)?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestBack: () -> Unit = {
        when {
            state.formState == GoalFormState.SAVING -> Unit
            state.dirty -> confirmDiscard = true
            else -> onBack()
        }
    }
    BackHandler(onBack = requestBack)
    DisposableEffect(state.dirty, state.formState) {
        registerUpHandler(requestBack)
        onDispose { registerUpHandler(null) }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Keep editing?") },
            text = { Text("Your unsaved goal draft is still available on this device.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        viewModel.onEvent(GoalsEditorEvent.DiscardDraft)
                        onBack()
                    },
                ) { Text("Discard") }
            },
        )
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        when {
            state.loading -> Unit

            else -> {
                if (!state.hasTargets) {
                    WloBanner(
                        text = "Set a weight goal here. A diet plan and calorie budget are optional.",
                        tone = WloBannerTone.Info,
                        modifier = Modifier.testTag("f01-goals-empty"),
                    )
                }
                state.diff.forEach { line ->
                    WloBanner(
                        text = line,
                        tone = WloBannerTone.Info,
                        modifier = Modifier.testTag("f01-goals-diff"),
                    )
                }
                state.notice?.let {
                    WloBanner(
                        text = it,
                        tone = WloBannerTone.Info,
                        modifier = Modifier.testTag("f01-goals-notice"),
                    )
                }

                WloCard(modifier = Modifier.testTag("f01-goals-card")) {
                    WloCardHeader(title = "Goal")
                    WloListRow(
                        label = if (state.currentWeightIsStarting) "Starting weight" else "Current trend",
                        value = {
                            Text(
                                state.currentWeightKg?.let(state.massUnit::format) ?: "Not available",
                                style = wloType.body,
                            )
                        },
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        WeightGoalMode.entries.forEach { mode ->
                            SegmentedButton(
                                selected = state.mode == mode,
                                onClick = { viewModel.onEvent(GoalsEditorEvent.ModeChange(mode)) },
                                shape = SegmentedButtonDefaults.itemShape(mode.ordinal, WeightGoalMode.entries.size),
                                modifier = Modifier.testTag("f01-goals-mode-${mode.name.lowercase()}"),
                            ) { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) }
                        }
                    }
                    EditorField(
                        value = state.goalWeightText,
                        onValueChange = { viewModel.onEvent(GoalsEditorEvent.GoalWeightChange(it)) },
                        label = "Target weight (${state.massUnit.symbol})",
                        placeholder = "For example, 75.0",
                        error = state.goalWeightError,
                        enabled = state.formState != GoalFormState.SAVING,
                        modifier = Modifier.fillMaxWidth().testTag("f01-goals-weight"),
                    )
                    WloCardHeader(title = "Pace and date")
                    EditorField(
                        value = state.paceText,
                        onValueChange = { viewModel.onEvent(GoalsEditorEvent.PaceChange(it)) },
                        label = "Pace (% bodyweight per week)",
                        placeholder = "For example, 0.5",
                        error = state.paceError,
                        enabled = state.formState != GoalFormState.SAVING,
                        modifier = Modifier.fillMaxWidth().testTag("f01-goals-pace"),
                    )
                    state.impliedPacePctPerWeek?.let { implied ->
                        Text(
                            text = "That date implies ${formatPace(implied)} of bodyweight per week.",
                            style = wloType.caption,
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.testTag("f01-goals-implied-pace"),
                        )
                    }
                    GoalDatePicker(
                        value = state.targetDateText,
                        onValueChange = { viewModel.onEvent(GoalsEditorEvent.TargetDateChange(it)) },
                        enabled =
                            state.forecastEligibility is WeightGoalEligibility.Eligible &&
                                state.formState != GoalFormState.SAVING,
                        error = state.dateError ?: if (!state.targetDateValid) "Choose a future date." else null,
                    )
                    WloCardHeader(title = "Optional energy settings")
                    EditorField(
                        value = state.budgetText,
                        onValueChange = { viewModel.onEvent(GoalsEditorEvent.BudgetChange(it)) },
                        label = "Daily calorie budget (kcal, optional)",
                        placeholder = "Leave blank for no calorie target",
                        error = state.budgetError,
                        enabled = state.formState != GoalFormState.SAVING,
                        modifier = Modifier.fillMaxWidth().testTag("f01-goals-budget"),
                    )
                    Text("Health context is managed in your profile.", style = wloType.caption)
                    WloBanner(
                        text = "${state.safetyCopy.title}. ${state.safetyCopy.body}",
                        tone =
                            if (state.eligibility is WeightGoalEligibility.Eligible) {
                                WloBannerTone.Info
                            } else {
                                WloBannerTone.Warning
                            },
                        modifier = Modifier.testTag("f01-goals-safety-status"),
                    )
                    if (state.eligibility is WeightGoalEligibility.Eligible &&
                        state.forecastEligibility !is WeightGoalEligibility.Eligible
                    ) {
                        val forecastCopy =
                            app.wlo.core.model.WeightGoalSafetyCopyPolicy
                                .forResult(state.forecastEligibility)
                        WloBanner(
                            text = "${forecastCopy.title}. ${forecastCopy.body}",
                            tone = WloBannerTone.Info,
                            modifier = Modifier.testTag("f01-goals-forecast-status"),
                        )
                    }
                    GoalForecastPreview(state)
                    if (state.formState == GoalFormState.CONFLICT) {
                        WloSecondaryButton(
                            label = "Reload current",
                            onClick = { viewModel.onEvent(GoalsEditorEvent.ReloadCurrent) },
                            modifier = Modifier.fillMaxWidth().testTag("f01-goals-reload"),
                        )
                        WloSecondaryButton(
                            label = "Review differences",
                            onClick = { viewModel.onEvent(GoalsEditorEvent.ReviewDifferences) },
                            modifier = Modifier.fillMaxWidth().testTag("f01-goals-review"),
                        )
                    }
                    WloButton(
                        label =
                            when {
                                state.formState == GoalFormState.SAVING -> "Saving…"
                                state.hasTargets -> "Save as new version"
                                else -> "Set goal"
                            },
                        onClick = { viewModel.onEvent(GoalsEditorEvent.Save) },
                        enabled =
                            state.formState != GoalFormState.SAVING &&
                                state.eligibility is WeightGoalEligibility.Eligible &&
                                state.targetDateValid,
                        modifier = Modifier.fillMaxWidth().testTag("f01-goals-save"),
                    )
                }

                if (state.history.isNotEmpty()) {
                    WloCard(modifier = Modifier.testTag("f01-goals-history-card")) {
                        WloCardHeader(title = "Versions")
                        state.history.forEach { record ->
                            val line =
                                "v${record.version}${if (record.isCurrent) " — current" else ""}" +
                                    " · ${record.writtenBy.wireName} · ${record.createdAtLabel}"
                            WloListRow(
                                label = line,
                                value = {
                                    if (!record.isCurrent) {
                                        WloSecondaryButton(
                                            label = "Restore",
                                            onClick = { viewModel.onEvent(GoalsEditorEvent.RevertTo(record.version)) },
                                            modifier = Modifier.testTag("f01-goals-revert-${record.version}"),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalForecastPreview(state: app.wlo.feature.f01.onboarding.state.GoalsEditorUi) {
    when (val result = state.forecast) {
        is GoalForecastResult.Available -> ForecastCard(state, result.bands, pointDateEligible = true)
        is GoalForecastResult.Developing -> {
            WloBanner(
                text =
                    "Forecast developing — ${result.usableDays} of ${result.requiredUsableDays} " +
                        "usable days. The outer range is provisional; no point date yet.",
                tone = WloBannerTone.Info,
                modifier = Modifier.testTag("f01-goals-forecast-developing"),
            )
            ForecastCard(state, result.bands, pointDateEligible = false)
        }
        is GoalForecastResult.Held ->
            WloBanner(
                text = "Forecast held — recent data quality cannot support a new date yet.",
                tone = WloBannerTone.Warning,
                modifier = Modifier.testTag("f01-goals-forecast-held"),
            )
        is GoalForecastResult.Withheld -> Unit // Shared safety copy immediately above owns this state.
        null ->
            if (state.forecastInputsMissing && state.eligibility is WeightGoalEligibility.Eligible) {
                WloBanner(
                    text = "Forecast unavailable — add age, height, current weight, and a daily budget first.",
                    tone = WloBannerTone.Info,
                    modifier = Modifier.testTag("f01-goals-forecast-missing"),
                )
            }
    }
}

@Composable
private fun ForecastCard(
    state: app.wlo.feature.f01.onboarding.state.GoalsEditorUi,
    forecast: ForecastBands,
    pointDateEligible: Boolean,
) {
    val current = state.currentWeightKg ?: return
    val target = state.goalWeightText.toDoubleOrNull()?.let(state.massUnit::toKilograms) ?: return
    WloForecastCard(
        bands =
            WloForecastBands(
                startWeightKg = current,
                goalWeightKg = target,
                startEpochDay = state.todayEpochDay,
                optimisticKg = forecast.optimistic.trajectoryKg,
                expectedKg = forecast.expected.trajectoryKg,
                pessimisticKg = forecast.pessimistic.trajectoryKg,
                optimisticFinishEpochDay = forecast.optimistic.finishEpochDay,
                expectedFinishEpochDay = forecast.expected.finishEpochDay,
                pessimisticFinishEpochDay = forecast.pessimistic.finishEpochDay,
                pointDateEligible = pointDateEligible,
                expectedPaceKgPerWeek = forecast.expected.weeklyRatesKg.firstOrNull(),
            ),
        goalWeight = DerivedValue(target, Provenance.Measured(state.now, "user-entered")),
        estimate = DerivedValue(forecast.tdeeEstimateKcal, forecast.provenance),
        plannedIntakeKcal = state.budgetText.toDoubleOrNull(),
        formatWeight = state.massUnit::format,
        formatKcal = { value -> "${value.toInt()} kcal" },
        modifier = Modifier.testTag("f01-goals-forecast-card"),
    )
}

private fun formatPace(value: Double): String = "%.2f%%".format(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDatePicker(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    error: String?,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("f01-goals-date"),
    ) {
        Text(if (value.isBlank()) "Target date (optional)" else "Target date · $value")
    }
    if (value.isNotBlank()) {
        TextButton(onClick = { onValueChange("") }, enabled = enabled) { Text("Clear target date") }
    }
    error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
    if (open) {
        val initial = runCatching { LocalDate.parse(value).toEpochDays() * MILLIS_PER_DAY }.getOrNull()
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onValueChange(LocalDate.fromEpochDays(millis / MILLIS_PER_DAY).toString())
                        }
                        open = false
                    },
                ) { Text("Use date") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    error: String?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
): Unit =
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = wloType.body,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(message) } },
        placeholder = { Text(placeholder, style = wloType.caption, color = wloExtendedColors.textTertiary) },
    )

private const val MILLIS_PER_DAY: Long = 86_400_000L
