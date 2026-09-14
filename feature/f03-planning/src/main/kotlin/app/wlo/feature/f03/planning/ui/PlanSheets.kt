package app.wlo.feature.f03.planning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloListRow
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
 *
 * Both contents render inside [WloSheet]'s padded, spaced column — they carry
 * no chrome of their own.
 */

@Composable
public fun SlotSheetContent(
    sheet: SlotSheetUi,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onReplace: (String) -> Unit,
    onSwap: () -> Unit,
) {
    val slot = sheet.slot
    WloCardHeader(
        title = slot.recipeName ?: "An open slot",
        modifier = Modifier.testTag("f03-slot-sheet-title"),
    )
    Text(
        text = "${dayWord(slot.dayEpochDay)} · ${slotWord(slot.mealSlot)} · ${stateWord(slot)}",
        style = wloType.receipt,
        color = wloExtendedColors.textTertiary,
    )
    SlotMacroCard(sheet)
    SlotStateSection(sheet, onConfirm, onSkip, onReplace, onSwap)
    Spacer(Modifier.height(WloSpacing.ROW_MIN))
}

/** Recipe macros (per serving — the recipe row is the provenance). */
@Composable
private fun SlotMacroCard(sheet: SlotSheetUi): Unit =
    WloCard {
        val slot = sheet.slot
        slot.kcalPerServing?.let { kcal ->
            MacroLine("kcal / serving", "${kcal.toInt()}", tag = "f03-sheet-kcal")
            slot.proteinGPerServing?.let { MacroLine("protein", "${it.toInt()} g") }
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
                style = wloType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

/** The R-B1 state machine's taps, per slot state. */
@Composable
private fun SlotStateSection(
    sheet: SlotSheetUi,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onReplace: (String) -> Unit,
    onSwap: () -> Unit,
) {
    val slot = sheet.slot
    when (slot.state) {
        PlannedSlotState.PLANNED -> SlotPlannedActions(sheet, onConfirm, onSkip, onReplace, onSwap)

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
}

@Composable
private fun SlotPlannedActions(
    sheet: SlotSheetUi,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onReplace: (String) -> Unit,
    onSwap: () -> Unit,
) {
    val slot = sheet.slot
    if (slot.recipeId == null) {
        Text(
            text = "an open slot — add a recipe to your library and re-deal, or skip it honestly.",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
        PrimaryRow(
            label = "Skip",
            modifier = Modifier.testTag("f03-skip"),
            onClick = onSkip,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
        PrimaryRow(
            label = "Ate this",
            modifier = Modifier.weight(1f).testTag("f03-confirm"),
            onClick = onConfirm,
        )
        PrimaryRow(
            label = "Swap",
            modifier = Modifier.weight(1f).testTag("f03-swap-open"),
            onClick = onSwap,
        )
    }
    PrimaryRow(
        label = "Skip — nothing owed",
        modifier = Modifier.testTag("f03-skip"),
        onClick = onSkip,
    )
    if (sheet.diaryCandidates.isEmpty()) {
        Text(
            text =
                "Ate something else instead? Log it in the diary — then this " +
                    "slot can link the entry. The diary entry owns its numbers.",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
    } else {
        Text(
            text = "or point the slot at a meal you already logged:",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
        for (candidate in sheet.diaryCandidates) {
            DiaryCandidateRow(candidate, onPick = { onReplace(candidate.entryId) })
        }
    }
}

@Composable
private fun DiaryCandidateRow(
    candidate: DiaryCandidateUi,
    onPick: () -> Unit,
): Unit =
    WloListRow(
        label = candidate.label,
        chevron = true,
        onClick = onPick,
        modifier = Modifier.testTag("f03-replace-pick"),
    )

@Composable
public fun SwapSheetContent(
    sheet: SwapSheetUi,
    onPick: (String) -> Unit,
) {
    WloCardHeader(
        title = "Swap ${slotWord(sheet.slot.mealSlot)}",
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
        text = "Your checked items stay checked.",
        style = wloType.caption,
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
    WloListRow(
        label = suggestion.name,
        secondary = "${suggestion.kcalPerServing.toInt()} kcal/serv",
        value = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
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
            }
        },
        chevron = true,
        onClick = onPick,
        modifier = Modifier.testTag("f03-swap-pick-$index"),
    )

@Composable
private fun MacroLine(
    label: String,
    value: String,
    tag: String? = null,
): Unit =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = wloType.caption,
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
