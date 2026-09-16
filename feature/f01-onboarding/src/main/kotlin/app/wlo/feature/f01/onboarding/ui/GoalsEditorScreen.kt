package app.wlo.feature.f01.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloForecastBands
import app.wlo.core.designsystem.WloForecastCard
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloScreenTitle
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
import app.wlo.feature.f01.onboarding.state.GoalsEditorEvent
import app.wlo.feature.f01.onboarding.state.GoalsEditorViewModel

/**
 * The goals editor (WLO-0035 W4): goal weight, pace, target date, daily
 * budget — written as a new Targets version through the STUDIO_F01 door with
 * the diff ribbon shown, plus the versions ledger with revert. The wizard is
 * this editor's first run, never its replacement.
 */
@Composable
public fun GoalsEditorScreen(
    viewModel: GoalsEditorViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        WloScreenTitle(
            title = "Goals",
            modifier = Modifier.testTag("f01-goals-title"),
        )

        when {
            state.loading -> Unit

            !state.hasTargets ->
                WloCard(modifier = Modifier.testTag("f01-goals-empty")) {
                    Text(
                        text = "No plan yet — the plan's targets appear here after the plan exists.",
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                }

            else -> {
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
                        label = "Current trend",
                        value = {
                            Text(
                                state.currentWeightKg?.let(state.massUnit::format) ?: "Not available",
                                style = wloType.body,
                            )
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        WeightGoalMode.entries.forEach { mode ->
                            SelectChip(
                                label = mode.name.lowercase().replaceFirstChar(Char::uppercase),
                                selected = state.mode == mode,
                                onClick = { viewModel.onEvent(GoalsEditorEvent.ModeChange(mode)) },
                                modifier = Modifier.testTag("f01-goals-mode-${mode.name.lowercase()}"),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        EditorField(
                            value = state.goalWeightText,
                            onValueChange = { viewModel.onEvent(GoalsEditorEvent.GoalWeightChange(it)) },
                            placeholder = "goal ${state.massUnit.symbol}",
                            modifier = Modifier.weight(1f).testTag("f01-goals-weight"),
                        )
                        EditorField(
                            value = state.paceText,
                            onValueChange = { viewModel.onEvent(GoalsEditorEvent.PaceChange(it)) },
                            placeholder = "% / week",
                            modifier = Modifier.weight(1f).testTag("f01-goals-pace"),
                        )
                    }
                    state.impliedPacePctPerWeek?.let { implied ->
                        Text(
                            text = "That date implies ${formatPace(implied)} of bodyweight per week.",
                            style = wloType.caption,
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.testTag("f01-goals-implied-pace"),
                        )
                    }
                    if (!state.targetDateValid) {
                        WloBanner(
                            text = "Use a future date in YYYY-MM-DD format.",
                            tone = WloBannerTone.Warning,
                            modifier = Modifier.testTag("f01-goals-date-error"),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        EditorField(
                            value =
                                if (state.forecastEligibility is WeightGoalEligibility.Eligible) {
                                    state.targetDateText
                                } else {
                                    ""
                                },
                            onValueChange = { viewModel.onEvent(GoalsEditorEvent.TargetDateChange(it)) },
                            placeholder =
                                if (state.forecastEligibility is WeightGoalEligibility.Eligible) {
                                    "by YYYY-MM-DD (optional)"
                                } else {
                                    "date held pending safety check"
                                },
                            enabled = state.forecastEligibility is WeightGoalEligibility.Eligible,
                            modifier = Modifier.weight(1f).testTag("f01-goals-date"),
                        )
                        EditorField(
                            value = state.budgetText,
                            onValueChange = { viewModel.onEvent(GoalsEditorEvent.BudgetChange(it)) },
                            placeholder = "daily kcal",
                            modifier = Modifier.weight(1f).testTag("f01-goals-budget"),
                        )
                    }
                    WloCardHeader(title = "Goal safety check")
                    WeightGoalSafetyControls(
                        pregnant = state.pregnant,
                        breastfeeding = state.breastfeeding,
                        eatingDisorderConcern = state.eatingDisorderConcern,
                        medicallyInfluencedWeight = state.medicallyInfluencedWeight,
                        onAnswer = { question, answer ->
                            viewModel.onEvent(GoalsEditorEvent.SafetyChange(question, answer))
                        },
                        testTagPrefix = "f01-goals-safety",
                    )
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
                    WloButton(
                        label = "Save as new version",
                        onClick = { viewModel.onEvent(GoalsEditorEvent.Save) },
                        enabled = state.eligibility is WeightGoalEligibility.Eligible && state.targetDateValid,
                        modifier = Modifier.fillMaxWidth().testTag("f01-goals-save"),
                    )
                }

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

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
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
        placeholder = { Text(placeholder, style = wloType.caption, color = wloExtendedColors.textTertiary) },
    )
