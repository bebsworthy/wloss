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
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
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
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        EditorField(
                            value = state.goalWeightText,
                            onValueChange = { viewModel.onEvent(GoalsEditorEvent.GoalWeightChange(it)) },
                            placeholder = "goal kg",
                            modifier = Modifier.weight(1f).testTag("f01-goals-weight"),
                        )
                        EditorField(
                            value = state.paceText,
                            onValueChange = { viewModel.onEvent(GoalsEditorEvent.PaceChange(it)) },
                            placeholder = "% / week",
                            modifier = Modifier.weight(1f).testTag("f01-goals-pace"),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        EditorField(
                            value = state.targetDateText,
                            onValueChange = { viewModel.onEvent(GoalsEditorEvent.TargetDateChange(it)) },
                            placeholder = "by YYYY-MM-DD (optional)",
                            modifier = Modifier.weight(1f).testTag("f01-goals-date"),
                        )
                        EditorField(
                            value = state.budgetText,
                            onValueChange = { viewModel.onEvent(GoalsEditorEvent.BudgetChange(it)) },
                            placeholder = "daily kcal",
                            modifier = Modifier.weight(1f).testTag("f01-goals-budget"),
                        )
                    }
                    WloButton(
                        label = "Save as new version",
                        onClick = { viewModel.onEvent(GoalsEditorEvent.Save) },
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
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
): Unit =
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = wloType.body,
        placeholder = { Text(placeholder, style = wloType.caption, color = wloExtendedColors.textTertiary) },
    )
