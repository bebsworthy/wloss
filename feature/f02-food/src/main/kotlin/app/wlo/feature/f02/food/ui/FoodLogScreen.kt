package app.wlo.feature.f02.food.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.MealSlot
import app.wlo.feature.f02.food.state.FoodHitUi
import app.wlo.feature.f02.food.state.FoodLogEvent
import app.wlo.feature.f02.food.state.FoodLogUiState
import app.wlo.feature.f02.food.state.FoodLogViewModel
import app.wlo.feature.f02.food.state.NoticeState

/**
 * The F02 manual input ladder (F02 §3 rungs 4–5, R-U15): the text-hint path,
 * the FTS-backed catalog search with the visible exact-match promotion, the
 * custom-food form with the energy-density rail surfaced as copy, and the
 * portion sheet with serving presets + the g/ml/serving converter (R-D10).
 * Photo/voice/barcode rungs arrive with the capture flow (M6+); the manual
 * ladder is their equal-status landing, never a fallback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun FoodLogScreen(
    viewModel: FoodLogViewModel,
    modifier: Modifier = Modifier,
) {
    val state: FoodLogUiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        Text(
            text = "Log food",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier =
                Modifier
                    .padding(top = WloSpacing.SCREEN)
                    .testTag("f02-log-title"),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT), modifier = Modifier.fillMaxWidth()) {
            for (slot in MealSlot.entries) {
                SelectChip(
                    label = slotLabel(slot),
                    selected = slot == state.slot,
                    onClick = { viewModel.onEvent(FoodLogEvent.SlotChange(slot)) },
                )
            }
        }

        // Rung 5: local catalog search — plain lookups, no consent needed.
        WloCard(modifier = Modifier.testTag("f02-search-card")) {
            Text(text = "Search", style = wloType.title)
            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onEvent(FoodLogEvent.QueryChange(it)) },
                modifier = Modifier.fillMaxWidth().testTag("f02-search-field"),
                singleLine = true,
                placeholder = { Text("oats, grilled chicken, brand names…", style = wloType.body) },
            )
            if (state.searching) {
                Text(text = "searching…", style = wloType.label, color = wloExtendedColors.textTertiary)
            }
            if (state.results.isNotEmpty()) {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .testTag("f02-search-results"),
                ) {
                    items(state.results, key = { it.food.id }) { hit ->
                        SearchHitRow(hit) { viewModel.onEvent(FoodLogEvent.PickHit(hit)) }
                        if (hit.food.id !=
                            state.results
                                .last()
                                .food.id
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                        }
                    }
                }
            } else if (state.query.isNotBlank() && !state.searching) {
                Text(
                    text = "no matches — create it below and it stays searchable",
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = wloExtendedColors.textTertiary,
                )
            }
        }

        // Rung 4 (manual): the free-text note — enters the diary as `held`
        // until an estimate lands; never guessed into numbers.
        WloCard(modifier = Modifier.testTag("f02-hint-card")) {
            Text(text = "Or describe it in words", style = wloType.title)
            OutlinedTextField(
                value = state.hintText,
                onValueChange = { viewModel.onEvent(FoodLogEvent.HintChange(it)) },
                modifier = Modifier.fillMaxWidth().testTag("f02-hint-field"),
                placeholder = { Text("half the plate is dal, cooked in ghee", style = wloType.body) },
            )
            ActionRow(label = "add as note", modifier = Modifier.fillMaxWidth().testTag("f02-hint-save")) {
                viewModel.onEvent(FoodLogEvent.SaveTextHint(state.hintText))
                viewModel.onEvent(FoodLogEvent.HintChange(""))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
            ActionRow(
                label = "quick-add calories",
                modifier = Modifier.weight(1f).testTag("f02-open-quick-add"),
            ) { viewModel.onEvent(FoodLogEvent.OpenQuickAdd) }
            ActionRow(
                label = "create a food",
                modifier = Modifier.weight(1f).testTag("f02-open-custom"),
            ) { viewModel.onEvent(FoodLogEvent.OpenCustomFood()) }
        }

        state.notice?.let {
            NoticeLine(
                it.text,
                rail = it.state == NoticeState.RAIL,
                onDismiss = { viewModel.onEvent(FoodLogEvent.DismissNotice) },
            )
        }
    }

    state.selection?.let { selection ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(FoodLogEvent.DismissPortion) },
            shape = WloShape.SheetTop,
            modifier = Modifier.testTag("f02-portion-sheet"),
        ) {
            PortionSheetContent(
                selection = selection,
                onSave = { viewModel.onEvent(FoodLogEvent.SavePortion) },
                onQuantity = { viewModel.onEvent(FoodLogEvent.QuantityChange(it)) },
                onUnit = { viewModel.onEvent(FoodLogEvent.UnitChange(it)) },
                onPreset = { viewModel.onEvent(FoodLogEvent.PresetChange(it)) },
            )
        }
    }

    state.quickAdd?.let { draft ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(FoodLogEvent.DismissQuickAdd) },
            shape = WloShape.SheetTop,
            modifier = Modifier.testTag("f02-quick-add-sheet"),
        ) {
            QuickAddSheetContent(
                kcalText = draft.kcalText,
                name = draft.name,
                onKcal = { viewModel.onEvent(FoodLogEvent.QuickAddChange(it, draft.name)) },
                onName = { viewModel.onEvent(FoodLogEvent.QuickAddChange(draft.kcalText, it)) },
                onSave = { viewModel.onEvent(FoodLogEvent.SaveQuickAdd) },
            )
        }
    }

    state.customDraft?.let { draft ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(FoodLogEvent.DismissCustom) },
            shape = WloShape.SheetTop,
            modifier = Modifier.testTag("f02-custom-sheet"),
        ) {
            CustomFoodSheetContent(
                draft = draft,
                onChange = { viewModel.onEvent(FoodLogEvent.CustomChange(it)) },
                onSave = { viewModel.onEvent(FoodLogEvent.SaveCustomFood) },
            )
        }
    }
}

/** Meal-slot labels (DRINK is the water/drinks bucket, F02 §3). */
internal fun slotLabel(slot: MealSlot): String =
    when (slot) {
        MealSlot.BREAKFAST -> "breakfast"
        MealSlot.LUNCH -> "lunch"
        MealSlot.DINNER -> "dinner"
        MealSlot.SNACK -> "snack"
        MealSlot.DRINK -> "drinks"
    }

@Composable
private fun SearchHitRow(
    hit: FoodHitUi,
    onClick: () -> Unit,
): Unit =
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = WloSpacing.ROW_INTERACTIVE)
                .clickable(onClick = onClick)
                .padding(vertical = WloSpacing.TIGHT),
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = hit.food.name,
                style = wloType.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            hit.food.brand?.let { brand ->
                Text(text = brand, style = wloType.label, color = wloExtendedColors.textTertiary)
            }
        }
        if (hit.exactMatch) {
            // The exact-match promotion is VISIBLE (F02 §3 rung 5).
            Surface(
                shape = WloShape.Chip,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            ) {
                Text(
                    text = "exact",
                    style = wloType.label,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        hit.food.kcalPer100g?.let { per100 ->
            Text(
                text = "${FoodLogViewModel.formatQuantity(per100)} / 100 g",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
    }

/** A full-width row-level button (48 dp floor — daily-use target, §3). */
@Composable
internal fun ActionRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
): Unit =
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = WloSpacing.TOUCH_PRIMARY),
        shape = WloShape.Chip,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = label,
            style = wloType.title.copy(fontSize = 15.sp),
            modifier = Modifier.padding(horizontal = WloSpacing.CARD, vertical = WloSpacing.CARD),
        )
    }

/** A one-line notice; tap dismisses. Rail rejections render amber (§1.2). */
@Composable
internal fun NoticeLine(
    text: String,
    rail: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint =
        if (rail) wloExtendedColors.held else MaterialTheme.colorScheme.primary
    return Surface(
        onClick = onDismiss,
        modifier = modifier.fillMaxWidth(),
        shape = WloShape.Chip,
        color = tint.copy(alpha = 0.12f),
        contentColor = tint,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            modifier = Modifier.padding(horizontal = WloSpacing.CARD, vertical = WloSpacing.CARD),
        )
    }
}
