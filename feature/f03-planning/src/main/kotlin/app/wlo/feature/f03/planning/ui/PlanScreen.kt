package app.wlo.feature.f03.planning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloAdherenceCell
import app.wlo.core.designsystem.WloAdherenceCellState
import app.wlo.core.designsystem.WloAdherenceStrip
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloEmptyState
import app.wlo.core.designsystem.WloFitBadge
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloPrimaryRow
import app.wlo.core.designsystem.WloSegmentedBar
import app.wlo.core.designsystem.WloSheet
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloTag
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.PlannedSlotState
import app.wlo.feature.f03.planning.state.AdherenceUi
import app.wlo.feature.f03.planning.state.PlanDayUi
import app.wlo.feature.f03.planning.state.PlanEvent
import app.wlo.feature.f03.planning.state.PlanUiState
import app.wlo.feature.f03.planning.state.PlanViewModel
import app.wlo.feature.f03.planning.state.PlannedSlotUi
import app.wlo.feature.f03.planning.state.RecipeRowUi
import app.wlo.feature.f03.planning.state.WhyPlanUi

/**
 * The Plan tab (F03 + F04 pipeline, IA §1): segmented Plan · Recipes · List ·
 * Pantry. This composable owns the PLAN and RECIPES segments; List and Pantry
 * are F04 destinations the :app nav graph wires in (D2).
 */
@Composable
public fun PlanScreen(
    viewModel: PlanViewModel,
    segment: PlanSegment,
    onSegmentSelect: (PlanSegment) -> Unit,
    onOpenDiet: () -> Unit = {},
    onOpenList: () -> Unit,
    onOpenPantry: () -> Unit,
    onEditRecipe: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state: PlanUiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WloSpacing.SCREEN),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        ) {
            androidx.compose.material3.TextButton(
                onClick = onOpenDiet,
                modifier = Modifier.testTag("plan-diet-preferences"),
            ) { androidx.compose.material3.Text("Diet plan") }
            WloSegmentedBar(
                segments = SEGMENT_LABELS,
                selected = segment.ordinal,
                onSelect = { index -> onSegmentSelect(PlanSegment.entries[index]) },
                modifier = Modifier.testTag("f03-segments"),
            )
        }

        when (segment) {
            PlanSegment.PLAN ->
                PlanSegmentContent(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onOpenList = onOpenList,
                    onOpenRecipes = { onSegmentSelect(PlanSegment.RECIPES) },
                )

            PlanSegment.RECIPES -> RecipesSegment(state, viewModel::onEvent, onEditRecipe)

            PlanSegment.LIST -> PipelineJump("Open the shopping list", "f03-open-list", onOpenList)

            PlanSegment.PANTRY -> PipelineJump("Open the pantry", "f03-open-pantry", onOpenPantry)
        }

        state.slotSheet?.let { sheet ->
            WloSheet(
                onDismissRequest = { viewModel.onEvent(PlanEvent.DismissSlotSheet) },
                modifier = Modifier.testTag("f03-slot-sheet"),
            ) {
                SlotSheetContent(
                    sheet = sheet,
                    onConfirm = { viewModel.onEvent(PlanEvent.Confirm(sheet.slot.id)) },
                    onSkip = { viewModel.onEvent(PlanEvent.Skip(sheet.slot.id)) },
                    onReplace = { entryId -> viewModel.onEvent(PlanEvent.Replace(sheet.slot.id, entryId)) },
                    onSwap = { viewModel.onEvent(PlanEvent.RequestSwap(sheet.slot.id)) },
                )
            }
        }

        state.swapSheet?.let { sheet ->
            WloSheet(
                onDismissRequest = { viewModel.onEvent(PlanEvent.DismissSwapSheet) },
                modifier = Modifier.testTag("f03-swap-sheet"),
            ) {
                SwapSheetContent(
                    sheet = sheet,
                    onPick = { recipeId -> viewModel.onEvent(PlanEvent.Swap(sheet.slot.id, recipeId)) },
                )
            }
        }
    }
}

/** The pipeline segments (IA §1) — List/Pantry are F04-owned surfaces. */
public enum class PlanSegment {
    PLAN,
    RECIPES,
    LIST,
    PANTRY,
}

internal val SEGMENT_LABELS: List<String> = listOf("Plan", "Recipes", "List", "Pantry")

@Composable
private fun PlanSegmentContent(
    state: PlanUiState,
    onEvent: (PlanEvent) -> Unit,
    onOpenList: () -> Unit,
    onOpenRecipes: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        state.notice?.let { notice -> NoticeBanner(notice) }

        if (!state.hasPlan) {
            EmptyPlanCard(generating = state.generating, onEvent = onEvent)
        } else {
            WeekHeaderCard(state, onEvent, onOpenRecipes)

            // The week grid: one day section per planned day — the 390 dp reflow
            // of the prototype's 7-column grid (same data, denser).
            state.days.forEach { day ->
                DaySection(
                    day = day,
                    isFocusDay = day.dayEpochDay == state.focusDay,
                    focusSlot = state.focusSlot,
                    onOpenSlot = { slotId -> onEvent(PlanEvent.OpenSlot(slotId)) },
                    modifier = Modifier.testTag("f03-day-${day.dayEpochDay}"),
                )
            }

            state.adherence?.let { adherence -> AdherenceCard(adherence) }

            PrimaryRow(
                label = "Build list",
                modifier = Modifier.testTag("f03-build-list"),
                onClick = onOpenList,
            )
        }
    }
}

@Composable
private fun NoticeBanner(notice: String): Unit =
    WloBanner(text = notice, tone = WloBannerTone.Info, modifier = Modifier.testTag("f03-notice"))

@Composable
private fun EmptyPlanCard(
    generating: Boolean,
    onEvent: (PlanEvent) -> Unit,
): Unit =
    WloCard(modifier = Modifier.testTag("f03-empty-card")) {
        WloEmptyState(
            title = "The week ahead",
            body =
                "Breakfast, lunch and dinner dealt from your recipe library against " +
                    "this week's budget — re-dealt in seconds.",
        )
        if (generating) {
            Text(
                text = "Dealing…",
                style = wloType.label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("f03-generating"),
            )
        } else {
            PrimaryRow(
                label = "Deal a week of meals",
                modifier = Modifier.testTag("f03-generate"),
                onClick = { onEvent(PlanEvent.Generate()) },
            )
        }
    }

@Composable
private fun WeekHeaderCard(
    state: PlanUiState,
    onEvent: (PlanEvent) -> Unit,
    onOpenRecipes: () -> Unit,
): Unit =
    WloCard(
        modifier = Modifier.testTag("f03-week-card"),
        header = {
            WloCardHeader(
                title = "Week of ${state.weekLabel.orEmpty()}",
                provenance = weekProvenance(state),
            )
        },
    ) {
        val today = state.days.firstOrNull { it.isToday }
        if (today?.budgetKcal != null) {
            Text(
                text = "%,d kcal/day".format(today.budgetKcal.value.toInt()),
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }

        // "Why this plan" — visible reasoning (F03 §8 [v1]): report numbers and
        // the hardest rules, honest about open slots, no blame anywhere.
        state.why?.let { why -> WhyThisPlan(why) }

        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
            PrimaryRow(
                label = if (state.generating) "Dealing…" else "Deal again",
                modifier = Modifier.weight(1f).testTag("f03-deal-again"),
                enabled = !state.generating,
                onClick = { onEvent(PlanEvent.DealAgain) },
            )
            PrimaryRow(
                label = "Recipes",
                modifier = Modifier.weight(1f).testTag("f03-open-recipes"),
                onClick = onOpenRecipes,
            )
        }
    }

/** The plan version word, in the header's provenance slot when a deal exists. */
@Composable
private fun weekProvenance(state: PlanUiState): (@Composable () -> Unit)? {
    val version = state.planVersion ?: return null
    return {
        Text(
            text = "deal v$version",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )
    }
}

@Composable
private fun WhyThisPlan(why: WhyPlanUi): Unit =
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        Text(
            text = "Why this plan",
            style = wloType.caption,
            modifier = Modifier.testTag("f03-why-title"),
        )
        Text(
            text =
                "${why.filledSlots} slots filled · ${why.distinctRecipes} recipes · " +
                    "${why.cookEvents} cook events · ${trim1(why.leftoverServings)} leftover servings · " +
                    "${why.sharedIngredients} shared ingredients",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.testTag("f03-why-report"),
        )
        if (why.hardestRules.isNotEmpty()) {
            Text(
                text = "hardest rules: ${why.hardestRules.joinToString(" · ")}",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
        if (why.unfillableSlots > 0) {
            Text(
                text =
                    "${why.unfillableSlots} slot(s) stayed open — a library gap, not " +
                        "a failure. Add recipes and re-deal.",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
        if (why.objectivesConflict) {
            Text(
                text = "variety and reuse pulled against each other this week — the deal favors your budget fit.",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
    }

@Composable
private fun DaySection(
    day: PlanDayUi,
    isFocusDay: Boolean,
    focusSlot: String?,
    onOpenSlot: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Past days without open slots collapse to one line (the prototype's rule);
    // today, focused days and days still owing decisions stay expanded.
    val collapsed = day.isPast && !isFocusDay && day.openCount == 0
    WloCard(
        modifier = modifier,
        header = {
            WloCardHeader(
                title = if (day.isToday) "${day.label} — today" else day.label,
                provenance = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                    ) {
                        day.fit?.let { fit ->
                            WloFitBadge(
                                kcalDeltaPct = fit.kcalDeltaPct,
                                proteinDeltaG = fit.proteinDeltaG,
                                withinTolerance = fit.withinTolerance,
                                modifier = Modifier.testTag("f03-fit-${day.dayEpochDay}"),
                            )
                        }
                        Text(
                            text = if (day.openCount == 0) "all set" else "${day.openCount} open",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                },
            )
        },
    ) {
        if (collapsed) {
            val summary = day.slots.mapNotNull { it.recipeName }.joinToString(" · ")
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            day.slots.forEach { slot ->
                SlotRow(
                    slot = slot,
                    focused = isFocusDay && focusSlot == slot.mealSlot,
                    onOpen = { onOpenSlot(slot.id) },
                )
            }
        }
    }
}

@Composable
private fun SlotRow(
    slot: PlannedSlotUi,
    focused: Boolean,
    onOpen: () -> Unit,
): Unit =
    WloListRow(
        label = slot.recipeName ?: "Add anything",
        secondary = slotSecondaryLine(slot),
        leading = {
            Box(modifier = Modifier.widthIn(min = 18.dp)) {
                Text(
                    text = slotInitial(slot.mealSlot),
                    style = wloType.label,
                    color =
                        if (focused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            wloExtendedColors.textTertiary
                        },
                )
            }
        },
        value =
            if (slot.state == PlannedSlotState.CONFIRMED) {
                {
                    Text(
                        text = "Eaten",
                        style = wloType.label,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
        chevron = true,
        onClick = onOpen,
        modifier = Modifier.testTag("f03-slot-${slot.id}"),
    )

/** The slot's supporting line: per-serving words, then any open questions. */
private fun slotSecondaryLine(slot: PlannedSlotUi): String =
    buildList {
        slot.kcalPerServing?.let { add("${it.toInt()} kcal/serv") }
        if (slot.isCookEvent) add("cook" + (slot.batchServings?.let { " ×${trim1(it)}" } ?: ""))
        if (slot.isLeftover) add("leftover of the batch")
        add(stateWord(slot))
        slot.unfillableReason?.let { add(it) }
        if (slot.state == PlannedSlotState.SKIPPED) add("the plan met real life — replanned. Nothing owed.")
    }.joinToString(" · ")

private fun slotInitial(mealSlot: String): String =
    when (mealSlot) {
        "breakfast" -> "B"
        "lunch" -> "L"
        "dinner" -> "D"
        else -> mealSlot.take(1).uppercase()
    }

internal fun stateWord(slot: PlannedSlotUi): String =
    when (slot.state) {
        PlannedSlotState.PLANNED -> "planned"
        PlannedSlotState.CONFIRMED -> "eaten"
        PlannedSlotState.SWAPPED -> "swapped"
        PlannedSlotState.SKIPPED -> "skipped"
        PlannedSlotState.REPLACED -> "replaced"
    }

@Composable
private fun AdherenceCard(adherence: AdherenceUi): Unit =
    WloCard(
        modifier = Modifier.testTag("f03-adherence"),
        header = {
            WloCardHeader(
                title = "Adherence",
                provenance = {
                    Text(
                        text =
                            if (adherence.meaningful) {
                                adherence.planCoveragePct?.let { coverage -> "coverage ${trim1(coverage)}%" }
                                    ?: "Fallback plan"
                            } else {
                                adherence.gateReason ?: "not yet meaningful"
                            },
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                    )
                },
            )
        },
    ) {
        if (adherence.cells.isNotEmpty()) {
            WloAdherenceStrip(
                cells =
                    adherence.cells.map { cell ->
                        WloAdherenceCell(
                            label = cell.label,
                            state =
                                when {
                                    cell.confirmed -> WloAdherenceCellState.CONFIRMED
                                    cell.skipped -> WloAdherenceCellState.SKIPPED
                                    else -> WloAdherenceCellState.OPEN
                                },
                        )
                    },
            )
        }
        if (adherence.meaningful && adherence.energyFidelityKcal != null) {
            Text(
                text = "median |actual − planned|: ${trim1(adherence.energyFidelityKcal)} kcal/day",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
    }

@Composable
private fun RecipesSegment(
    state: PlanUiState,
    onEvent: (PlanEvent) -> Unit,
    onEditRecipe: (String?) -> Unit,
): Unit =
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        OutlinedTextField(
            value = state.recipeQuery,
            onValueChange = { query -> onEvent(PlanEvent.QueryRecipes(query)) },
            modifier = Modifier.fillMaxWidth().testTag("f03-recipe-search"),
            placeholder = { Text("search the library", style = wloType.body) },
            singleLine = true,
            textStyle = wloType.body,
        )
        PrimaryRow(
            label = "New recipe",
            modifier = Modifier.testTag("f03-recipe-new"),
            onClick = { onEditRecipe(null) },
        )
        if (state.recipes.isEmpty()) {
            WloCard(header = { WloCardHeader(title = "Your library") }) {
                Text(
                    text =
                        "no recipes match yet — the shipped seeds arrive with the first deal, all editable.",
                    style = wloType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (recipe in state.recipes) {
            RecipeLibraryRow(recipe = recipe, onClick = { onEditRecipe(recipe.id) })
        }
    }

@Composable
private fun RecipeLibraryRow(
    recipe: RecipeRowUi,
    onClick: () -> Unit,
): Unit =
    WloCard(
        onClick = onClick,
        modifier = Modifier.testTag("f03-recipe-row"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = recipe.name, style = wloType.body)
                Text(
                    text =
                        "${recipe.slots.joinToString("/")} · ${recipe.kcalPerServing.toInt()} " +
                            "kcal/serv · v${recipe.version}",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
            Text(
                text = recipe.sourceWord,
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
            )
        }
        if (recipe.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                recipe.tags.take(4).forEach { tag ->
                    WloTag(text = tag)
                }
            }
        }
    }

@Composable
private fun PipelineJump(
    label: String,
    tag: String,
    onOpen: () -> Unit,
): Unit =
    Column(
        modifier = Modifier.fillMaxSize().padding(WloSpacing.SCREEN),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrimaryRow(label = label, modifier = Modifier.testTag(tag), onClick = onOpen)
    }

/** Local alias — the shared atom lives in :core:designsystem (single owner). */
@Composable
internal fun PrimaryRow(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Unit = WloPrimaryRow(label = label, modifier = modifier, enabled = enabled, onClick = onClick)

internal fun trim1(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded == kotlin.math.floor(rounded)) rounded.toInt().toString() else rounded.toString()
}
