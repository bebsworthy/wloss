package app.wlo.feature.f10.hub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.wlo.core.designsystem.WloIconAction
import app.wlo.core.designsystem.WloRailButton
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSheet
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
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                    modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
                )
            }

            current.explainer?.let { explainer ->
                WloSheet(
                    onDismissRequest = { viewModel.onEvent(HubEvent.DismissExplainer) },
                    modifier = Modifier.testTag("hub-explainer-sheet"),
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
                                    style = wloType.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(text = value, style = wloType.receipt)
                            }
                        }
                    }
                    Text(
                        text = explainer.note,
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                    Spacer(Modifier.height(WloSpacing.ROW_MIN))
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
        // The app wordmark: plain `title` — a `titleL` here would outweigh the
        // rest of the header row, and the ramp has no arithmetic escapes.
        Text(text = "WLO", style = wloType.title)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            Text(
                text = todayLabel,
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
            )
            // Real affordance, not a text clickable: the ghost 48 dp
            // WloIconAction keeps the header quiet (a bordered
            // WloSecondaryButton would fight the rail buttons right below);
            // the tradeoff is icon-only discoverability, covered by the
            // contentDescription.
            WloIconAction(
                imageVector = HubIcons.Settings,
                contentDescription = "Settings",
                onClick = onOpenSettings,
                modifier = Modifier.testTag("hub-settings"),
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
            when (quick) {
                HubQuickAction.PHOTO_LOG ->
                    WloRailButton(
                        icon = HubIcons.Photo,
                        label = "Log food",
                        onClick = actions.onOpenCapture,
                        onLongClick = actions.onQuickAddKcal,
                        modifier = Modifier.weight(1f),
                    )
                HubQuickAction.WEIGH_IN ->
                    WloRailButton(
                        icon = HubIcons.Weigh,
                        label = "Weigh in",
                        onClick = actions.onLogWeight,
                        modifier = Modifier.weight(1f),
                    )
                HubQuickAction.POOP_LOG ->
                    WloRailButton(
                        icon = HubIcons.Gut,
                        label = "Digestion",
                        onClick = actions.onGutLog,
                        modifier = Modifier.weight(1f),
                    )
                HubQuickAction.WORKOUT_START ->
                    WloRailButton(
                        icon = HubIcons.Workout,
                        label = "Workout",
                        onClick = actions.onWorkout,
                        modifier = Modifier.weight(1f),
                    )
            }
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
            WloCard(
                onClick = actions.onLogWeight,
                modifier = Modifier.testTag("hub-weigh-card"),
            ) {
                WloCardHeader(title = "Weigh in")
                Text(
                    text = "One number — the trend does the reading.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            }

        HubCard.TREND ->
            TrendCard(isHero = isHero, state = state, onOpenWeight = actions.onOpenWeight, onExplain = onExplain)

        HubCard.CALORIE_RING -> BudgetCard(state = state, onExplain = onExplain)

        HubCard.CLOSE_DAY ->
            WloCard(
                onClick = actions.onOpenDiary,
                modifier = Modifier.testTag("hub-close-card"),
            ) {
                WloCardHeader(title = "Close the day")
                Text(
                    text = "See today's diary.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
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
                    text = "Nothing logged yet today.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            }

        // F03's content-rendered cards (R-D14): they exist only while the
        // plan gives them content — no empty state, no upsell.
        HubCard.MEALS_TODAY -> MealsTodayCard(state, onOpenPlan = actions.onOpenPlan)

        HubCard.PLAN_TOMORROW ->
            WloCard(
                onClick = actions.onPlanTomorrow,
                modifier = Modifier.testTag("hub-plan-tomorrow-card"),
            ) {
                WloCardHeader(title = "Plan tomorrow")
                Text(
                    text = "Deal the week before the morning decides for you.",
                    style = wloType.caption,
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
    WloCard(
        onClick = onOpenPlan,
        modifier = Modifier.testTag("hub-meals-card"),
    ) {
        WloCardHeader(title = "Meals · today")
        val open = state.plannedMealsOpen ?: 0
        Text(
            text = if (open == 1) "1 planned meal open" else "$open planned meals open",
            style = wloType.title,
        )
        Text(
            text = "Tap to eat, swap, or skip — nothing owed either way.",
            style = wloType.caption,
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
        onClick = onOpenWeight,
        modifier = Modifier.testTag("hub-trend-card"),
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
            style = wloType.caption,
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

/**
 * The mock's week-dots row: current week M..S, filled when the day has logged
 * food — with an 11 sp legend (audit F7: the encoding must be readable, not
 * only announced to screen readers).
 */
@Composable
private fun WeekDotsRow(weekDots: List<WeekDotUi>): Unit =
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
        val outline = wloExtendedColors.textTertiary.copy(alpha = 0.45f)
        Row(
            modifier = Modifier.fillMaxWidth().testTag("hub-week-dots"),
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        ) {
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text(text = "Logged", style = wloType.label, color = wloExtendedColors.textTertiary)
            Spacer(Modifier.height(WloSpacing.TIGHT))
            Box(modifier = Modifier.size(8.dp).border(1.dp, outline, CircleShape))
            Text(text = "Not yet", style = wloType.label, color = wloExtendedColors.textTertiary)
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

/** Identity formatter for display-ready strings (chips already carry units). */
private fun identity(value: String): String = value

/** kcal formatting for the diary/energy chips. */
private fun formatKcalValue(value: Double): String = "%,d kcal".format(value.toInt())
