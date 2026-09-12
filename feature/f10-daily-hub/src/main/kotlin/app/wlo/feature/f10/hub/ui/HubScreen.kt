package app.wlo.feature.f10.hub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.common.MassUnit
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloForecastBands
import app.wlo.core.designsystem.WloForecastCard
import app.wlo.core.designsystem.WloMonthHeatmap
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStat
import app.wlo.core.designsystem.WloStatDivider
import app.wlo.core.designsystem.WloStatRow
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.engines.CardState
import app.wlo.core.engines.HubCard
import app.wlo.core.engines.HubCardState
import app.wlo.core.engines.HubQuickAction
import app.wlo.core.model.DerivedValue
import app.wlo.feature.f10.hub.state.DiarySliceUi
import app.wlo.feature.f10.hub.state.ExplainerUi
import app.wlo.feature.f10.hub.state.HeatmapUi
import app.wlo.feature.f10.hub.state.HubEvent
import app.wlo.feature.f10.hub.state.HubUiState
import app.wlo.feature.f10.hub.state.HubViewModel
import kotlin.math.abs

/**
 * The Hub (F10 surface): the Adaptive Day Model's phase-aware card stack, the
 * quick-action rail, the diary slice with the shared month heatmap (R-D4),
 * and the R-A5 cold-start forecast — ESTIMATED-chipped from day zero. Every
 * chip taps through to the "how we got here" sheet; "Computed on your device"
 * is stated once, here. The user never types on the Hub (F10 §4).
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun HubScreen(
    viewModel: HubViewModel,
    actions: HubActions,
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

                QuickActionRail(current.dayModel.quickActions, actions)

                // The engine orders the cards it gates; the trend number itself
                // is a constant of the surface (F10 §5 hero card): the start
                // weight stands in — measured, honestly — until the F10 §4
                // gate (≥3 weigh-ins) opens the delta chip.
                val engineCards = current.dayModel.cards
                val renderCards =
                    if (engineCards.any { it.card == HubCard.TREND }) {
                        engineCards
                    } else {
                        val insertAt = engineCards.indexOfFirst { it.card == HubCard.WEIGH_IN } + 1
                        engineCards
                            .toMutableList()
                            .apply { add(insertAt, HubCardState(HubCard.TREND, CardState.OPEN)) }
                    }

                for (card in renderCards) {
                    HubCardView(
                        card = card,
                        isHero = card.card == current.dayModel.heroCard,
                        state = current,
                        actions = actions,
                    )
                }

                current.diarySlice?.let { slice ->
                    DiaryCard(
                        slice = slice,
                        heatmap = current.heatmap,
                        onOpenDiary = actions.onOpenDiary,
                    )
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
                        formatWeight = { kg -> MassUnit.KILOGRAM.format(kg) },
                        formatKcal = ::formatKcalValue,
                        onExplain = { viewModel.onEvent(HubEvent.ShowExplainer(forecast.explainer)) },
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

/** The rail (F10 §5): engine-ordered; long-press the camera = kcal-only quick-add. */
@Composable
private fun QuickActionRail(
    quickActions: List<HubQuickAction>,
    actions: HubActions,
): Unit =
    Row(
        modifier = Modifier.fillMaxWidth().testTag("hub-quick-actions"),
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        for (quick in quickActions) {
            val item =
                when (quick) {
                    HubQuickAction.PHOTO_LOG ->
                        RailItem("log food", HubIcons.Photo, actions.onOpenCapture, actions.onQuickAddKcal)
                    HubQuickAction.WEIGH_IN ->
                        RailItem("weigh in", HubIcons.Weigh, actions.onLogWeight)
                    HubQuickAction.POOP_LOG ->
                        RailItem("digestion", HubIcons.Gut, actions.onGutLog)
                    HubQuickAction.WORKOUT_START ->
                        RailItem("workout", HubIcons.Workout, actions.onWorkout)
                }
            RailButton(
                item = item,
                modifier = Modifier.weight(1f),
            )
        }
    }

private data class RailItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailButton(
    item: RailItem,
    modifier: Modifier = Modifier,
): Unit =
    Surface(
        modifier =
            modifier.then(
                if (item.onLongClick != null) {
                    Modifier.combinedClickable(onClick = item.onClick, onLongClick = item.onLongClick)
                } else {
                    Modifier.clickable(onClick = item.onClick)
                },
            ),
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = WloSpacing.CARD),
        ) {
            Icon(imageVector = item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(WloSpacing.TIGHT))
            Text(text = item.label, style = wloType.label, color = wloExtendedColors.textTertiary)
        }
    }

/** One Day Model card, rendered content-first; the hero slot gets the big numeral. */
@Composable
private fun HubCardView(
    card: HubCardState,
    isHero: Boolean,
    state: HubUiState.Ready,
    actions: HubActions,
) {
    when (card.card) {
        HubCard.WEIGH_IN ->
            WloCard(modifier = Modifier.testTag("hub-weigh-card").clickable(onClick = actions.onLogWeight)) {
                Text(
                    text = "the morning window",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
                Text(text = "Weigh in", style = wloType.title)
                Text(
                    text = "one number — the trend does the reading",
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = wloExtendedColors.textTertiary,
                )
            }

        HubCard.TREND -> TrendCard(isHero = isHero, state = state, onOpenWeight = actions.onOpenWeight)

        HubCard.CALORIE_RING -> BudgetCard(state = state)

        HubCard.CLOSE_DAY ->
            WloCard(modifier = Modifier.testTag("hub-close-card").clickable(onClick = actions.onOpenDiary)) {
                Text(
                    text = "close the day",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
                Text(text = "See today's diary", style = wloType.title)
            }

        HubCard.RECAP ->
            WloCard(modifier = Modifier.testTag("hub-recap-card")) {
                Text(
                    text = "the day, so far",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
                state.diarySlice?.let { slice ->
                    WloStatRow(label = "logged today", value = slice.kcal, format = ::formatKcalValue)
                } ?: Text(
                    text = "nothing logged yet today",
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = wloExtendedColors.textTertiary,
                )
            }

        // Content-rendered surfaces that arrive with F03/F05/F07 (R-D14):
        // absence is silent — no empty state, no upsell.
        HubCard.MEALS_TODAY, HubCard.WORKOUT, HubCard.PLAN_TOMORROW, HubCard.CHECK_IN -> Unit
    }
}

@Composable
private fun TrendCard(
    isHero: Boolean,
    state: HubUiState.Ready,
    onOpenWeight: () -> Unit,
): Unit =
    WloCard(
        modifier =
            Modifier
                .testTag("hub-trend-card")
                .clickable(onClick = onOpenWeight),
    ) {
        Text(
            text = "Weight trend",
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
        Spacer(Modifier.height(WloSpacing.CARD))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WloStat(
                label = "trend",
                value = state.heroTrend,
                format = ::identity,
                valueStyle = if (isHero) wloType.hero else wloType.statL,
                modifier = Modifier.weight(1f),
            )
            state.heroDelta?.let { delta -> DeltaChipFor(delta) }
        }
    }

/** Delta chip (§7.1): sign-forward, valence-free hues — down = accent, up = neutral. */
@Composable
private fun DeltaChipFor(delta: DerivedValue<Double>): Unit =
    Row(verticalAlignment = Alignment.CenterVertically) {
        val color =
            when {
                delta.value < 0.0 -> MaterialTheme.colorScheme.primary
                delta.value > 0.0 -> wloExtendedColors.neutralDelta
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        val sign =
            when {
                delta.value < 0.0 -> "− "
                delta.value > 0.0 -> "+ "
                else -> "± "
            }
        Text(
            text = "$sign${MassUnit.KILOGRAM.format(abs(delta.value))} / 7 d",
            style = wloType.statM,
            color = color,
        )
    }

private fun identity(value: String): String = value

@Composable
private fun BudgetCard(state: HubUiState.Ready): Unit =
    WloCard(modifier = Modifier.testTag("hub-energy-card")) {
        Text(
            text = "Calories",
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
        Spacer(Modifier.height(WloSpacing.TIGHT))
        state.budget?.let {
            WloStatRow(
                label = "of today's budget",
                value = it,
                format = ::identity,
                modifier = Modifier.testTag("hub-budget-row"),
            )
        }
        state.burn?.let {
            WloStatDivider()
            WloStatRow(
                label = "estimated burn, today",
                value = it,
                format = ::identity,
                modifier = Modifier.testTag("hub-burn-row"),
            )
        }
    }

/** The diary slice (F10 renders today; F02 owns the diary, R-B1) + the R-D4 heatmap. */
@Composable
private fun DiaryCard(
    slice: DiarySliceUi,
    heatmap: HeatmapUi?,
    onOpenDiary: () -> Unit,
): Unit =
    WloCard(modifier = Modifier.testTag("hub-diary-card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Diary · today",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${slice.entryCount} entries",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
        WloStatRow(label = slice.slotSummary, value = slice.kcal, format = ::formatKcalValue)
        heatmap?.let { hm ->
            WloMonthHeatmap(
                values = hm.values,
                initialMonth = hm.monthStart,
                upTo = hm.upTo,
                describeCell = { date, intensity ->
                    val dayLabel = "${date.month.name.lowercase().take(3)} ${date.dayOfMonth}"
                    when {
                        intensity == null -> "$dayLabel — not logged"
                        intensity <= 0.0 -> "$dayLabel — logged, no energy"
                        else -> "$dayLabel — logged"
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("hub-heatmap"),
            )
        }
        Surface(
            onClick = onOpenDiary,
            modifier = Modifier.fillMaxWidth().testTag("hub-open-diary"),
            shape = WloShape.Chip,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text(
                text = "open the diary",
                style = wloType.title.copy(fontSize = 15.sp),
                modifier = Modifier.padding(WloSpacing.CARD),
            )
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

/** Identity formatter for display-ready strings (chips already carry units). */
private fun formatKcalValue(value: Double): String = "%,d kcal".format(value.toInt())
