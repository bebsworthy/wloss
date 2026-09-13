package app.wlo.feature.f03.planning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloAdherenceCell
import app.wlo.core.designsystem.WloAdherenceCellState
import app.wlo.core.designsystem.WloAdherenceStrip
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloFitBadge
import app.wlo.core.designsystem.WloPrimaryRow
import app.wlo.core.designsystem.WloSegmentedBar
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PlanScreen(
    viewModel: PlanViewModel,
    segment: PlanSegment,
    onSegmentSelect: (PlanSegment) -> Unit,
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
            Text(
                text = "Plan",
                style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
                modifier =
                    Modifier
                        .padding(top = WloSpacing.SCREEN)
                        .testTag("title-plan"),
            )
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

            PlanSegment.LIST -> PipelineJump("open the shopping list", "f03-open-list", onOpenList)

            PlanSegment.PANTRY -> PipelineJump("open the pantry", "f03-open-pantry", onOpenPantry)
        }

        state.slotSheet?.let { sheet ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.onEvent(PlanEvent.DismissSlotSheet) },
                shape = WloShape.SheetTop,
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
            ModalBottomSheet(
                onDismissRequest = { viewModel.onEvent(PlanEvent.DismissSwapSheet) },
                shape = WloShape.SheetTop,
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
                label = "build list · week pre-selected",
                modifier = Modifier.testTag("f03-build-list"),
                onClick = onOpenList,
            )
            Text(
                text = "builds in under a second, offline · your aisle order will be yours",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.padding(horizontal = WloSpacing.TIGHT),
            )
        }
    }
}

@Composable
private fun NoticeBanner(notice: String): Unit =
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("f03-notice"),
        shape = WloShape.Chip,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = notice,
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            modifier = Modifier.padding(WloSpacing.CARD),
        )
    }

@Composable
private fun EmptyPlanCard(
    generating: Boolean,
    onEvent: (PlanEvent) -> Unit,
): Unit =
    WloCard(modifier = Modifier.testTag("f03-empty-card")) {
        Text(
            text = "the week ahead",
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
        Text(text = "Deal a week of meals", style = wloType.title)
        Text(
            text =
                "the on-device engine fills breakfast · lunch · dinner from your library " +
                    "against this week's budget — deterministic, offline, re-dealt in seconds.",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (generating) {
            Text(
                text = "dealing…",
                style = wloType.label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("f03-generating"),
            )
        } else {
            PrimaryRow(
                label = "generate week",
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
    WloCard(modifier = Modifier.testTag("f03-week-card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text = "Week of ${state.weekLabel.orEmpty()}", style = wloType.title)
                val today = state.days.firstOrNull { it.isToday }
                if (today?.budgetKcal != null) {
                    Text(
                        text = "%,d kcal/day".format(today.budgetKcal.value.toInt()),
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                    )
                }
            }
            state.planVersion?.let { version ->
                Text(
                    text = "deal v$version",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
        }

        // "Why this plan" — visible reasoning (F03 §8 [v1]): report numbers and
        // the hardest rules, honest about open slots, no blame anywhere.
        state.why?.let { why -> WhyThisPlan(why) }

        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
            PrimaryRow(
                label = if (state.generating) "dealing…" else "deal again",
                modifier = Modifier.weight(1f).testTag("f03-deal-again"),
                enabled = !state.generating,
                onClick = { onEvent(PlanEvent.DealAgain) },
            )
            PrimaryRow(
                label = "recipes",
                modifier = Modifier.weight(1f).testTag("f03-open-recipes"),
                onClick = onOpenRecipes,
            )
        }
    }

@Composable
private fun WhyThisPlan(why: WhyPlanUi): Unit =
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        Text(
            text = "Why this plan",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
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
        Text(
            text = "generated on-device · deterministic for its seed",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )
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
    WloCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = day.label + if (day.isToday) " · today" else "",
                style = wloType.body.copy(fontSize = 13.sp),
                modifier = Modifier.weight(1f),
            )
            day.fit?.let { fit ->
                WloFitBadge(
                    kcalDeltaPct = fit.kcalDeltaPct,
                    proteinDeltaG = fit.proteinDeltaG,
                    withinTolerance = fit.withinTolerance,
                    modifier = Modifier.testTag("f03-fit-${day.dayEpochDay}"),
                )
            }
            Spacer(Modifier.width(WloSpacing.TIGHT))
            Text(
                text = if (day.openCount == 0) "✓ all set" else "${day.openCount} open",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }

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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = WloSpacing.ROW_INTERACTIVE)
                .clickable(onClick = onOpen)
                .testTag("f03-slot-${slot.id}")
                .padding(vertical = WloSpacing.TIGHT),
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.widthIn(min = 18.dp)) {
            Text(
                text = slotInitial(slot.mealSlot),
                style = wloType.label,
                color = if (focused) MaterialTheme.colorScheme.primary else wloExtendedColors.textTertiary,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = slot.recipeName ?: "add anything",
                style = wloType.body,
                color =
                    if (slot.recipeName == null) {
                        wloExtendedColors.textTertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            val words =
                buildList {
                    slot.kcalPerServing?.let { add("${it.toInt()} kcal/serv") }
                    if (slot.isCookEvent) add("cook" + (slot.batchServings?.let { " ×${trim1(it)}" } ?: ""))
                    if (slot.isLeftover) add("leftover of the batch")
                    add(stateWord(slot))
                }
            Text(
                text = words.joinToString(" · "),
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
            slot.unfillableReason?.let {
                Text(
                    text = it,
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
            if (slot.state == PlannedSlotState.SKIPPED) {
                Text(
                    text = "the plan met real life — replanned. Nothing owed.",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
        }
        if (slot.state == PlannedSlotState.CONFIRMED) {
            Text(
                text = "eaten ✓",
                style = wloType.label,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

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
    WloCard(modifier = Modifier.testTag("f03-adherence")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Adherence",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    if (adherence.meaningful) {
                        adherence.planCoveragePct?.let { coverage -> "coverage ${trim1(coverage)}%" }
                            ?: "plans that survive contact with Tuesdays"
                    } else {
                        adherence.gateReason ?: "not yet meaningful"
                    },
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
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
        Text(
            text = "open slots stay open, never penalized — the strip reads the planning, not the person.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )
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
            label = "new recipe",
            modifier = Modifier.testTag("f03-recipe-new"),
            onClick = { onEditRecipe(null) },
        )
        if (state.recipes.isEmpty()) {
            WloCard {
                Text(
                    text = "your library",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
                Text(
                    text =
                        "no recipes match yet — the shipped seeds arrive with the first deal, all editable.",
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
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
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .testTag("f03-recipe-row"),
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
                    Text(
                        text = tag,
                        style = wloType.label,
                        color = wloExtendedColors.textTertiary,
                        modifier =
                            Modifier
                                .background(wloExtendedColors.surfaceRaised, WloShape.Chip)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
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
