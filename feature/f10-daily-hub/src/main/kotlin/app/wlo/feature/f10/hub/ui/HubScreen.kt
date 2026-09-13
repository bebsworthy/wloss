package app.wlo.feature.f10.hub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.common.MassUnit
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloDeltaChip
import app.wlo.core.designsystem.WloForecastBands
import app.wlo.core.designsystem.WloForecastCard
import app.wlo.core.designsystem.WloHeroStat
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStatDivider
import app.wlo.core.designsystem.WloStatRow
import app.wlo.core.designsystem.WloTrendChart
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.engines.CardState
import app.wlo.core.engines.HubCard
import app.wlo.core.engines.HubCardState
import app.wlo.core.engines.HubQuickAction
import app.wlo.core.model.DerivedValue
import app.wlo.feature.f10.hub.state.DiarySliceUi
import app.wlo.feature.f10.hub.state.ExplainerUi
import app.wlo.feature.f10.hub.state.HubEvent
import app.wlo.feature.f10.hub.state.HubUiState
import app.wlo.feature.f10.hub.state.HubViewModel
import app.wlo.feature.f10.hub.state.WeekDotUi
import kotlinx.datetime.LocalDate

/**
 * The Hub (F10 surface): the Adaptive Day Model's phase-aware card stack, the
 * quick-action rail, the diary slice with the mock's week-dots row (owner
 * review WLO-0030, defect 12 — the month heatmap never belonged here), and
 * the R-A5 cold-start forecast — ESTIMATED-chipped from day zero. Every chip
 * taps through to the "how we got here" sheet; "Computed on your device" is
 * stated once, here. The user never types on the Hub (F10 §4).
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
                HubHeader(
                    todayLabel = current.todayLabel,
                    onOpenSettings = actions.onOpenSettings,
                )

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
                        onExplain = { explainer -> viewModel.onEvent(HubEvent.ShowExplainer(explainer)) },
                    )
                }

                current.diarySlice?.let { slice ->
                    DiaryCard(
                        slice = slice,
                        weekDots = current.weekDots,
                        diaryExplainer = current.diaryExplainer,
                        onOpenDiary = actions.onOpenDiary,
                        onExplain = { viewModel.onEvent(HubEvent.ShowExplainer(it)) },
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
                        formatWeight = { kg -> current.massUnit.format(kg) },
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
private fun HubHeader(
    todayLabel: String,
    onOpenSettings: () -> Unit,
): Unit =
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = WloSpacing.SCREEN),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "WLO", style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.06f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            Text(
                text = todayLabel,
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
            )
            Text(
                text = "Settings",
                style = wloType.label,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = WloSpacing.TIGHT, vertical = WloSpacing.TIGHT)
                        .testTag("hub-settings"),
            )
        }
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
                        RailItem("Log food", HubIcons.Photo, actions.onOpenCapture, actions.onQuickAddKcal)
                    HubQuickAction.WEIGH_IN ->
                        RailItem("Weigh in", HubIcons.Weigh, actions.onLogWeight)
                    HubQuickAction.POOP_LOG ->
                        RailItem("Digestion", HubIcons.Gut, actions.onGutLog)
                    HubQuickAction.WORKOUT_START ->
                        RailItem("Workout", HubIcons.Workout, actions.onWorkout)
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
    onExplain: (ExplainerUi) -> Unit,
) {
    when (card.card) {
        HubCard.WEIGH_IN ->
            WloCard(modifier = Modifier.testTag("hub-weigh-card").clickable(onClick = actions.onLogWeight)) {
                WloCardHeader(title = "Morning window")
                Text(text = "Weigh in", style = wloType.title)
                Text(
                    text = "one number — the trend does the reading",
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = wloExtendedColors.textTertiary,
                )
            }

        HubCard.TREND ->
            TrendCard(isHero = isHero, state = state, onOpenWeight = actions.onOpenWeight, onExplain = onExplain)

        HubCard.CALORIE_RING -> BudgetCard(state = state, onExplain = onExplain)

        HubCard.CLOSE_DAY ->
            WloCard(modifier = Modifier.testTag("hub-close-card").clickable(onClick = actions.onOpenDiary)) {
                WloCardHeader(title = "Close the day")
                Text(text = "See today's diary", style = wloType.title)
            }

        HubCard.RECAP ->
            WloCard(modifier = Modifier.testTag("hub-recap-card")) {
                WloCardHeader(title = "The day, so far")
                state.diarySlice?.let { slice ->
                    WloStatRow(
                        label = "logged today",
                        value = slice.kcal,
                        format = ::formatKcalValue,
                        onExplain = state.diaryExplainer?.let { handler -> { onExplain(handler) } },
                    )
                } ?: Text(
                    text = "nothing logged yet today",
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = wloExtendedColors.textTertiary,
                )
            }

        // F03's content-rendered cards (R-D14): they exist only while the
        // plan gives them content — no empty state, no upsell.
        HubCard.MEALS_TODAY -> MealsTodayCard(state, onOpenPlan = actions.onOpenPlan)

        HubCard.PLAN_TOMORROW ->
            WloCard(modifier = Modifier.testTag("hub-plan-tomorrow-card").clickable(onClick = actions.onPlanTomorrow)) {
                WloCardHeader(title = "Tomorrow")
                Text(text = "Plan tomorrow", style = wloType.title)
                Text(
                    text = "deal or check the week before the morning decides for you",
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = wloExtendedColors.textTertiary,
                )
            }

        HubCard.WORKOUT, HubCard.CHECK_IN -> Unit
    }
}

/** "Meals · today" (F10 §5): the open planned slots, tapping into the planner. */
@Composable
private fun MealsTodayCard(
    state: HubUiState.Ready,
    onOpenPlan: () -> Unit,
): Unit =
    WloCard(modifier = Modifier.testTag("hub-meals-card").clickable(onClick = onOpenPlan)) {
        WloCardHeader(title = "Meals · today")
        val open = state.plannedMealsOpen ?: 0
        Text(
            text = if (open == 1) "1 planned meal open" else "$open planned meals open",
            style = wloType.title,
        )
        Text(
            text = "tap to eat, swap, or skip — nothing owed either way",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = wloExtendedColors.textTertiary,
        )
    }

/**
 * The hero (F10 §5, mock ann. 2/3 — owner review WLO-0030): card header with
 * the derived chip TOP-RIGHT (single icon, no repeated value), the hero
 * numeral with a small unit suffix + the weekly delta inline, the 30-day
 * sparkline with its caption + "tap for history" hint — and the whole card
 * still taps through to the weight page.
 */
@Composable
private fun TrendCard(
    isHero: Boolean,
    state: HubUiState.Ready,
    onOpenWeight: () -> Unit,
    onExplain: (ExplainerUi) -> Unit,
): Unit =
    WloCard(
        modifier =
            Modifier
                .testTag("hub-trend-card")
                .clickable(onClick = onOpenWeight),
    ) {
        val trendExplainer = state.trendExplainer
        val heroDelta = state.heroDelta
        WloCardHeader(
            title = "Weight trend",
            provenance =
                if (trendExplainer != null) {
                    {
                        ProvenanceChip(
                            value = state.heroTrend,
                            format = ::formatNumeral,
                            onClick = { onExplain(trendExplainer) },
                        )
                    }
                } else {
                    null
                },
        )

        WloHeroStat(
            value = state.heroTrend,
            format = ::formatNumeral,
            unit = state.massUnit.symbol,
            valueStyle = if (isHero) wloType.hero else wloType.statL,
            delta =
                if (heroDelta != null) {
                    { DeltaChipFor(heroDelta, state.massUnit) }
                } else {
                    null
                },
            // The card header owns the single provenance chip (top-right).
            provenance = {},
        )

        WloTrendChart(
            samples = state.trendSamples,
            trend = state.trendLine,
            currentTrend = null,
            formatWeight = { kg -> "${formatNumeral(kg)} ${state.massUnit.symbol}" },
            describe = "Weight trend chart: 30 days of scale dots with the trend line.",
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Last 30 days · tap for history",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = wloExtendedColors.textTertiary,
        )
    }

/** The weekly delta chip (§7.1): sign-forward, valence-free hues — down = accent, up = neutral. */
@Composable
private fun DeltaChipFor(
    delta: DerivedValue<Double>,
    unit: MassUnit,
): Unit =
    WloDeltaChip(
        value = delta,
        format = { magnitude -> "${formatNumeral(magnitude)} ${unit.symbol} / 7 d" },
        context = "trend delta",
    )

/** Numeral-only formatting (no unit) — the hero's numeral, the chart axis, the chip a11y text. */
private fun formatNumeral(kg: Double): String {
    val tenths = (kg * 10).toLong()
    val whole = tenths / 10
    val tenth = tenths % 10
    return "$whole.$tenth"
}

@Composable
private fun BudgetCard(
    state: HubUiState.Ready,
    onExplain: (ExplainerUi) -> Unit,
): Unit =
    WloCard(modifier = Modifier.testTag("hub-energy-card")) {
        WloCardHeader(title = "Calories")
        state.budget?.let {
            WloStatRow(
                label = "of today's budget",
                value = it,
                format = ::identity,
                onExplain = state.budgetExplainer?.let { handler -> { onExplain(handler) } },
                modifier = Modifier.testTag("hub-budget-row"),
            )
        }
        if (state.budget != null && state.burn != null) {
            WloStatDivider()
        }
        state.burn?.let {
            WloStatRow(
                label = "estimated burn, today",
                value = it,
                format = ::identity,
                onExplain = state.burnExplainer?.let { handler -> { onExplain(handler) } },
                modifier = Modifier.testTag("hub-burn-row"),
            )
        }
    }

/** The diary slice (F10 renders today; F02 owns the diary, R-B1) + the week dots. */
@Composable
private fun DiaryCard(
    slice: DiarySliceUi,
    weekDots: List<WeekDotUi>,
    diaryExplainer: ExplainerUi?,
    onOpenDiary: () -> Unit,
    onExplain: (ExplainerUi) -> Unit,
): Unit =
    WloCard(modifier = Modifier.testTag("hub-diary-card")) {
        WloCardHeader(
            title = "Diary · today",
            provenance = {
                Text(
                    text = "${slice.entryCount} entries",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            },
        )
        WloStatRow(
            label = slice.slotSummary,
            value = slice.kcal,
            format = ::formatKcalValue,
            onExplain = diaryExplainer?.let { handler -> { onExplain(handler) } },
        )
        WeekDotsRow(weekDots)
        WloSecondaryButton(
            label = "Open diary",
            onClick = onOpenDiary,
            modifier = Modifier.fillMaxWidth().testTag("hub-open-diary"),
        )
    }

/** The mock's week-dots row: current week M..S, filled when the day has logged food. */
@Composable
private fun WeekDotsRow(weekDots: List<WeekDotUi>): Unit =
    Row(
        modifier = Modifier.fillMaxWidth().testTag("hub-week-dots"),
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        val outline = wloExtendedColors.textTertiary.copy(alpha = 0.45f)
        for (dot in weekDots) {
            val label = weekdayShort(dot.epochDay)
            val description =
                when {
                    dot.logged -> "$label — logged"
                    dot.isFuture -> "$label — not yet"
                    else -> "$label — not logged"
                }
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .then(
                            if (dot.logged) {
                                Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier.border(1.dp, outline, CircleShape)
                            },
                        ).semantics { this.contentDescription = description },
            )
        }
    }

private fun weekdayShort(epochDay: Long): String {
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    val weekday =
        date.dayOfWeek.name
            .lowercase()
            .take(3)
            .replaceFirstChar { it.uppercase() }
    return "$weekday ${date.dayOfMonth}"
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
private fun identity(value: String): String = value

/** kcal formatting for the diary/energy chips. */
private fun formatKcalValue(value: Double): String = "%,d kcal".format(value.toInt())
