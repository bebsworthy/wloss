package app.wlo.feature.f03.planning.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.PlannedSlotState
import app.wlo.feature.f03.planning.state.DiaryCandidateUi
import app.wlo.feature.f03.planning.state.SlotSheetUi
import app.wlo.feature.f03.planning.state.SwapSheetUi
import app.wlo.feature.f03.planning.state.SwapSuggestionUi

/**
 * The plan's two bottom sheets (F03 §4): the slot detail (macros, ingredient
 * count, and the R-B1 state machine's taps) and the swap sheet (pick-from-3,
 * zero typing; delta chips describe the diff — targets never silently re-based).
 */

@Composable
public fun SlotSheetContent(
    sheet: SlotSheetUi,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onReplace: (String) -> Unit,
    onSwap: () -> Unit,
): Unit =
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        val slot = sheet.slot
        Text(
            text = slot.recipeName ?: "an open slot",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.12f),
            modifier = Modifier.testTag("f03-slot-sheet-title"),
        )
        Text(
            text = "${dayWord(slot.dayEpochDay)} · ${slotWord(slot.mealSlot)} · ${stateWord(slot)}",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )

        // Recipe macros (per serving — the recipe row is the provenance).
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = WloShape.Chip,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(WloSpacing.CARD), verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                slot.kcalPerServing?.let { kcal ->
                    MacroLine("kcal / serving", "${kcal.toInt()}", tag = "f03-sheet-kcal")
                    sheet.slot.proteinGPerServing?.let { MacroLine("protein", "${it.toInt()} g") }
                    Text(
                        text =
                            "nutrition ${sheet.nutritionBasisWord} · ${sheet.ingredientCount} ingredients" +
                                if (sheet.tags.isNotEmpty()) " · ${sheet.tags.joinToString(", ")}" else "",
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                    )
                }
                slot.unfillableReason?.let {
                    Text(
                        text = it,
                        style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        when (slot.state) {
            PlannedSlotState.PLANNED -> {
                if (slot.recipeId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                        PrimaryRow(
                            label = "ate this",
                            modifier = Modifier.weight(1f).testTag("f03-confirm"),
                            onClick = onConfirm,
                        )
                        PrimaryRow(
                            label = "swap",
                            modifier = Modifier.weight(1f).testTag("f03-swap-open"),
                            onClick = onSwap,
                        )
                    }
                    PrimaryRow(
                        label = "skip — nothing owed",
                        modifier = Modifier.testTag("f03-skip"),
                        onClick = onSkip,
                    )
                    if (sheet.diaryCandidates.isEmpty()) {
                        Text(
                            text =
                                "ate something else instead? log it in the diary — " +
                                    "then this slot can link the entry (R-B1: the entry owns the numbers).",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                    } else {
                        Text(
                            text = "or point the slot at a meal you already logged:",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                        for (candidate in sheet.diaryCandidates) {
                            DiaryCandidateRow(candidate, onPick = { onReplace(candidate.entryId) })
                        }
                    }
                } else {
                    Text(
                        text = "an open slot — add a recipe to your library and re-deal, or skip it honestly.",
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                    )
                    PrimaryRow(
                        label = "skip",
                        modifier = Modifier.testTag("f03-skip"),
                        onClick = onSkip,
                    )
                }
            }

            else -> {
                Text(
                    text =
                        when (slot.state) {
                            PlannedSlotState.CONFIRMED -> "eaten — the day record carries its nutrition."
                            PlannedSlotState.SKIPPED -> "skipped — replanned, nothing owed."
                            PlannedSlotState.REPLACED -> "replaced — the diary entry owns the numbers."
                            PlannedSlotState.SWAPPED -> "retired by a swap — its successor carries the plan."
                            else -> "planned"
                        },
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(WloSpacing.ROW_MIN))
    }

@Composable
private fun DiaryCandidateRow(
    candidate: DiaryCandidateUi,
    onPick: () -> Unit,
): Unit =
    Surface(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth().heightIn(min = WloSpacing.ROW_INTERACTIVE).testTag("f03-replace-pick"),
        shape = WloShape.Chip,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(horizontal = WloSpacing.CARD, vertical = WloSpacing.TIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = candidate.label, style = wloType.body, modifier = Modifier.weight(1f))
            Text(text = "link", style = wloType.label, color = MaterialTheme.colorScheme.primary)
        }
    }

@Composable
public fun SwapSheetContent(
    sheet: SwapSheetUi,
    onPick: (String) -> Unit,
): Unit =
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(
            text = "Swap ${slotWord(sheet.slot.mealSlot)}",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.12f),
            modifier = Modifier.testTag("f03-swap-title"),
        )
        sheet.slot.kcalPerServing?.let { kcal ->
            Text(
                text = "out: ${sheet.slot.recipeName.orEmpty()} · ${kcal.toInt()} kcal/serv",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        Text(
            text = "top swaps for the day's fit",
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
        sheet.suggestions.forEachIndexed { index, suggestion ->
            SuggestionRow(suggestion, index) { onPick(suggestion.recipeId) }
        }
        Text(
            text = "never suggests your allergens · the list reconciles by delta — your checked items stay checked",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )
        Spacer(Modifier.height(WloSpacing.ROW_MIN))
    }

@Composable
private fun SuggestionRow(
    suggestion: SwapSuggestionUi,
    index: Int,
    onPick: () -> Unit,
): Unit =
    Surface(
        onClick = onPick,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = WloSpacing.ROW_INTERACTIVE)
                .testTag("f03-swap-pick-$index"),
        shape = WloShape.Chip,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(horizontal = WloSpacing.CARD, vertical = WloSpacing.CARD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = suggestion.name, style = wloType.body)
                Text(
                    text = "${suggestion.kcalPerServing.toInt()} kcal/serv",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
            Text(
                text = deltaWord(suggestion.kcalDelta, "kcal"),
                style = wloType.label,
                color = wloExtendedColors.developing,
            )
            Text(
                text = deltaWord(suggestion.proteinDeltaG, "g P"),
                style = wloType.label,
                color = wloExtendedColors.developing,
            )
            Text(text = "pick", style = wloType.label, color = MaterialTheme.colorScheme.primary)
        }
    }

@Composable
private fun MacroLine(
    label: String,
    value: String,
    tag: String? = null,
): Unit =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = wloType.statS,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
        )
    }

private fun deltaWord(
    value: Double,
    unit: String,
): String {
    val rounded = kotlin.math.round(value)
    return when {
        rounded < 0 -> "−${kotlin.math.abs(rounded).toInt()} $unit"
        rounded > 0 -> "+${rounded.toInt()} $unit"
        else -> "±0 $unit"
    }
}

internal fun slotWord(mealSlot: String): String =
    when (mealSlot) {
        "breakfast" -> "breakfast"
        "lunch" -> "lunch"
        "dinner" -> "dinner"
        else -> mealSlot
    }

internal fun dayWord(dayEpochDay: Long): String {
    val date = kotlinx.datetime.LocalDate.fromEpochDays(dayEpochDay.toInt())
    val weekday =
        date.dayOfWeek.name
            .lowercase()
            .replaceFirstChar { it.uppercase() }
            .take(3)
    return "$weekday ${date.dayOfMonth}"
}
