package app.wlo.app.ui.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.common.MassUnit
import app.wlo.core.designsystem.WloForecastBands
import app.wlo.core.designsystem.WloForecastCard
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStat
import app.wlo.core.designsystem.WloStatDivider
import app.wlo.core.designsystem.WloStatRow
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType

/**
 * Hub (F10 surface, M2): trend hero, daily budget, and the R-A5 cold-start
 * forecast — ESTIMATED-chipped from day zero. Every chip taps through to the
 * "how we got here" sheet; "Computed on your device" is stated once, here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun HubScreen(
    viewModel: HubViewModel,
    modifier: Modifier = Modifier,
) {
    val state: HubUiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        HubUiState.Loading, HubUiState.Fresh -> Column(modifier = modifier.fillMaxSize()) {}
        is HubUiState.Ready -> {
            Column(
                modifier =
                    modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = WloSpacing.SCREEN),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
            ) {
                HubHeader(current.todayLabel)

                WloCard(modifier = Modifier.testTag("hub-trend-card")) {
                    Text(
                        text = "Morning",
                        style = wloType.label,
                        color = wloExtendedColors.textTertiary,
                    )
                    Spacer(Modifier.height(WloSpacing.CARD))
                    WloStat(
                        label = "Weight trend",
                        value = current.trend,
                        format = ::identity,
                    )
                }

                if (current.budget != null || current.forecast != null) {
                    WloCard(modifier = Modifier.testTag("hub-energy-card")) {
                        Text(
                            text = "Energy",
                            style = wloType.label,
                            color = wloExtendedColors.textTertiary,
                        )
                        Spacer(Modifier.height(WloSpacing.TIGHT))
                        current.budget?.let {
                            WloStatRow(
                                label = "of today's budget",
                                value = it,
                                format = ::identity,
                                modifier = Modifier.testTag("hub-budget-row"),
                            )
                        }
                        current.forecast?.let { forecast ->
                            if (current.budget != null) WloStatDivider()
                            WloStatRow(
                                label = "estimated burn, today",
                                value = forecast.estimate,
                                format = ::formatKcalValue,
                                modifier = Modifier.testTag("hub-burn-row"),
                            )
                        }
                    }
                }

                current.forecast?.let { forecast ->
                    WloForecastCard(
                        bands =
                            WloForecastBands(
                                startWeightKg = forecast.bands.startWeightKg,
                                goalWeightKg = forecast.bands.goalWeightKg,
                                startEpochDay = forecast.bands.startEpochDay,
                                optimisticKg = forecast.bands.optimisticKg,
                                expectedKg = forecast.bands.expectedKg,
                                pessimisticKg = forecast.bands.pessimisticKg,
                                optimisticFinishEpochDay = forecast.bands.optimisticFinishEpochDay,
                                expectedFinishEpochDay = forecast.bands.expectedFinishEpochDay,
                                pessimisticFinishEpochDay = forecast.bands.pessimisticFinishEpochDay,
                            ),
                        goalWeight = forecast.goalWeight,
                        estimate = forecast.estimate,
                        formatWeight = ::formatKg,
                        formatKcal = ::formatKcalValue,
                        onExplain = {
                            viewModel.onEvent(HubEvent.ShowExplainer(forecast.explainer))
                        },
                        modifier = Modifier.testTag("hub-forecast-card"),
                    )
                }

                Text(
                    text = "Computed on your device — tap any chip for how we got here.",
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = wloExtendedColors.textTertiary,
                    modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
                )
            }

            current.explainer?.let { explainer ->
                ModalBottomSheet(
                    onDismissRequest = { viewModel.onEvent(HubEvent.DismissExplainer) },
                    shape = WloShape.SheetTop,
                    modifier = Modifier.testTag("hub-explainer-sheet"),
                ) {
                    ExplainerSheetContent(explainer)
                }
            }
        }
    }
}

@Composable
private fun ExplainerSheetContent(explainer: ExplainerUi) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(text = explainer.headline, style = wloType.title)
        WloCard {
            explainer.rows.forEachIndexed { index, (label, value) ->
                if (index > 0) WloStatDivider()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(WloSpacing.ROW_MIN),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = value, style = wloType.receipt)
                }
            }
        }
        Text(
            text = explainer.note,
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = wloExtendedColors.textTertiary,
        )
        Spacer(Modifier.height(WloSpacing.ROW_MIN))
    }
}

@Composable
private fun HubHeader(todayLabel: String): Unit =
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = WloSpacing.SCREEN),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "WLO", style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.06f))
        Text(
            text = todayLabel,
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
    }

/** WLO card: 20 dp radius, 1 dp outline hairline, no shadow (§1.4). */
@Composable
private fun WloCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
): Unit =
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(WloSpacing.PAD_CARD)) { content() }
    }

/** Identity formatter for display-ready strings (chips already carry units). */
private fun identity(value: String): String = value

/** Raw-double formatters for the forecast card's numerals (metric default, R-D10). */
private fun formatKg(value: Double): String = MassUnit.KILOGRAM.format(value)

private fun formatKcalValue(value: Double): String = "%,d kcal".format(value.toInt())
