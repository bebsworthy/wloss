package app.wlo.feature.f02.food.ui

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloBadge
import app.wlo.core.designsystem.WloBadgeTone
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSheet
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
        WloScreenTitle(title = "Log food", modifier = Modifier.testTag("f02-log-title"))

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
        WloCard(
            modifier = Modifier.testTag("f02-search-card"),
            header = { WloCardHeader(title = "Search") },
        ) {
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
                    text = "No matches — create it below",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            }
        }

        // Rung 4 (manual): the free-text note — enters the diary as `held`
        // until an estimate lands; never guessed into numbers.
        WloCard(
            modifier = Modifier.testTag("f02-hint-card"),
            header = { WloCardHeader(title = "Or describe it in words") },
        ) {
            OutlinedTextField(
                value = state.hintText,
                onValueChange = { viewModel.onEvent(FoodLogEvent.HintChange(it)) },
                modifier = Modifier.fillMaxWidth().testTag("f02-hint-field"),
                placeholder = { Text("half the plate is dal, cooked in ghee", style = wloType.body) },
            )
            WloButton(
                label = "Add as note",
                onClick = {
                    viewModel.onEvent(FoodLogEvent.SaveTextHint(state.hintText))
                    viewModel.onEvent(FoodLogEvent.HintChange(""))
                },
                modifier = Modifier.fillMaxWidth().testTag("f02-hint-save"),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
            WloSecondaryButton(
                label = "Quick-add calories",
                onClick = { viewModel.onEvent(FoodLogEvent.OpenQuickAdd) },
                modifier = Modifier.weight(1f).testTag("f02-open-quick-add"),
            )
            WloSecondaryButton(
                label = "Create a food",
                onClick = { viewModel.onEvent(FoodLogEvent.OpenCustomFood()) },
                modifier = Modifier.weight(1f).testTag("f02-open-custom"),
            )
        }

        state.notice?.let {
            WloBanner(
                text = it.text,
                tone = if (it.state == NoticeState.RAIL) WloBannerTone.Warning else WloBannerTone.Info,
                actionLabel = "Dismiss",
                action = { viewModel.onEvent(FoodLogEvent.DismissNotice) },
            )
        }
    }

    state.selection?.let { selection ->
        WloSheet(
            onDismissRequest = { viewModel.onEvent(FoodLogEvent.DismissPortion) },
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
        WloSheet(
            onDismissRequest = { viewModel.onEvent(FoodLogEvent.DismissQuickAdd) },
            title = "Quick-add calories",
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
        WloSheet(
            onDismissRequest = { viewModel.onEvent(FoodLogEvent.DismissCustom) },
            title = if (draft.editId == null) "Create a food" else "Edit food",
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
        MealSlot.BREAKFAST -> "Breakfast"
        MealSlot.LUNCH -> "Lunch"
        MealSlot.DINNER -> "Dinner"
        MealSlot.SNACK -> "Snack"
        MealSlot.DRINK -> "Drinks"
    }

@Composable
private fun SearchHitRow(
    hit: FoodHitUi,
    onClick: () -> Unit,
): Unit =
    WloListRow(
        label = hit.food.name,
        secondary = hit.food.brand,
        value = {
            if (hit.exactMatch) {
                // The exact-match promotion is VISIBLE (F02 §3 rung 5).
                WloBadge(text = "Exact", tone = WloBadgeTone.Accent)
            }
        },
        trailing = per100gTrailing(hit.food.kcalPer100g),
        onClick = onClick,
    )

/** The per-100 g receipt line for a hit's trailing slot (null when unknown). */
@Composable
private fun per100gTrailing(per100: Double?): (@Composable () -> Unit)? =
    if (per100 == null) {
        null
    } else {
        {
            Text(
                text = "${FoodLogViewModel.formatQuantity(per100)} / 100 g",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
    }
