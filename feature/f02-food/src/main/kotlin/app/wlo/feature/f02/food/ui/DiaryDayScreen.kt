package app.wlo.feature.f02.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloDialog
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSheet
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f02.food.state.DayStatusUi
import app.wlo.feature.f02.food.state.DiaryEvent
import app.wlo.feature.f02.food.state.DiaryUiState
import app.wlo.feature.f02.food.state.DiaryViewModel
import app.wlo.feature.f02.food.state.EntryRowUi
import app.wlo.feature.f02.food.state.NoticeState
import app.wlo.feature.f02.food.state.SlotUi
import java.util.Locale

/**
 * The diary day view (F02 §5: meal groupings, kcal + provenance per entry,
 * the day-status marker in the day header, edit/delete with the honest
 * revision history behind every entry's provenance sheet — the M2
 * "how we got here" bottom-sheet pattern).
 */
@Composable
public fun DiaryDayScreen(
    viewModel: DiaryViewModel,
    modifier: Modifier = Modifier,
) {
    val state: DiaryUiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        DayHeader(state) { viewModel.onEvent(DiaryEvent.CycleStatus) }

        WloCard(
            modifier = Modifier.testTag("f02-totals-card"),
            header = {
                WloCardHeader(
                    title = "Eaten so far",
                    provenance = {
                        state.totals?.let {
                            ProvenanceChip(
                                value = it,
                                format = ::formatKcal,
                            )
                        } ?: Text("Nutrition incomplete")
                    },
                )
            },
        ) {
            // The numeral renders HERE; the chip in the header carries the
            // kind + info mark only (never repeats the value).
            Text(
                text = state.totals?.value?.let(::formatKcal) ?: "Nutrition incomplete",
                style = wloType.statL,
                modifier = Modifier.testTag("f02-day-total"),
            )
            if (state.macroLine.isNotBlank()) {
                Text(text = state.macroLine, style = wloType.receipt, color = wloExtendedColors.textTertiary)
            }
        }

        WloButton(
            label = "Add water (500 ml)",
            onClick = { viewModel.onEvent(DiaryEvent.WaterQuickAdd) },
            modifier = Modifier.fillMaxWidth().testTag("f02-water-add"),
        )

        for (slot in state.slots) {
            SlotSection(slot) { entryId -> viewModel.onEvent(DiaryEvent.EntryTap(entryId)) }
        }

        if (state.slots.isEmpty()) {
            Text(
                text = "nothing logged yet — the day fills in as you go",
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
        }

        state.notice?.let {
            WloBanner(
                text = it.text,
                tone = if (it.state == NoticeState.RAIL) WloBannerTone.Warning else WloBannerTone.Info,
                actionLabel = "Dismiss",
                action = { viewModel.onEvent(DiaryEvent.DismissNotice) },
            )
        }
    }

    state.openEntry?.let { detail ->
        WloSheet(
            onDismissRequest = { viewModel.onEvent(DiaryEvent.DismissEntry) },
            title = "How we got here",
            modifier = Modifier.testTag("f02-entry-sheet"),
        ) {
            EntrySheetContent(
                detail = detail,
                onEditBegin = { viewModel.onEvent(DiaryEvent.BeginEdit) },
                onEditChange = { viewModel.onEvent(DiaryEvent.EditChange(it)) },
                onEditCancel = { viewModel.onEvent(DiaryEvent.CancelEdit) },
                onEditSave = { viewModel.onEvent(DiaryEvent.SaveEdit) },
                onDelete = { viewModel.onEvent(DiaryEvent.DeleteEntry) },
            )
        }
    }
}

@Composable
private fun DayHeader(
    state: DiaryUiState,
    onCycleStatus: () -> Unit,
): Unit =
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WloScreenTitle(
            title = "Diary · ${state.dayLabel}",
            modifier = Modifier.testTag("f02-diary-title"),
        )
        state.dayStatus?.let { status ->
            // The day-status marker (F02 §3): one tap cycles it; a gap is
            // never shamed — the marker states what the day was. A badge is
            // static, so the status word rides a real button and the cycle is
            // spoken in the description.
            WloSecondaryButton(
                label = dayStatusLabel(status),
                onClick = onCycleStatus,
                modifier =
                    Modifier
                        .testTag("f02-day-status")
                        .semantics {
                            contentDescription = "Day status ${status.label}. Activate to cycle the day status."
                        },
            )
        }
    }

/** Sentence-case status word for the cycle button (labels are single words). */
private fun dayStatusLabel(status: DayStatusUi): String = status.label.uppercase(Locale.ROOT)

@Composable
private fun SlotSection(
    slot: SlotUi,
    onEntryTap: (String) -> Unit,
): Unit =
    WloCard(
        modifier = Modifier.testTag("f02-slot-${slot.slot.wireName}"),
        header = {
            WloCardHeader(
                title = slotLabel(slot.slot),
                provenance = {
                    Text(
                        text = slot.kcal?.let(::formatKcal) ?: "Incomplete",
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                    )
                },
            )
        },
    ) {
        slot.entries.forEachIndexed { index, entry ->
            EntryRow(entry, onEntryTap)
            if (index < slot.entries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
        }
    }

@Composable
private fun EntryRow(
    entry: EntryRowUi,
    onEntryTap: (String) -> Unit,
): Unit =
    WloListRow(
        label = entry.title,
        secondary = entry.subtitle + if (entry.edited) " · edited" else "",
        value = { entry.kcal?.let { ProvenanceChip(value = it, format = ::formatKcal) } ?: Text("Unknown kcal") },
        onClick = { onEntryTap(entry.id) },
    )

@Composable
private fun EntrySheetContent(
    detail: app.wlo.feature.f02.food.state.EntryDetailUi,
    onEditBegin: () -> Unit,
    onEditChange: (String) -> Unit,
    onEditCancel: () -> Unit,
    onEditSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmRemove by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        WloCard {
            detail.rows.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = WloSpacing.ROW_MIN),
                    horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
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

        if (detail.revisions.isNotEmpty()) {
            WloCard(
                modifier = Modifier.testTag("f02-revision-list"),
                header = { WloCardHeader(title = "History") },
            ) {
                detail.revisions.forEachIndexed { index, rev ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = WloSpacing.ROW_MIN),
                        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "version ${rev.revision.revision}",
                            style = wloType.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${rev.revision.quantity} ${rev.revision.unit}",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                        rev.kcal?.let { ProvenanceChip(value = it, format = ::formatKcal) } ?: Text("Unknown kcal")
                    }
                }
            }
        }

        if (detail.editing) {
            OutlinedTextField(
                value = detail.editText,
                onValueChange = onEditChange,
                modifier = Modifier.fillMaxWidth().testTag("f02-edit-field"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = wloType.statM,
                placeholder = { Text("corrected amount", style = wloType.body) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                WloButton(
                    label = "Save correction",
                    onClick = onEditSave,
                    modifier = Modifier.weight(1f).testTag("f02-edit-save"),
                )
                WloSecondaryButton(label = "Cancel", onClick = onEditCancel, modifier = Modifier.weight(1f))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                WloSecondaryButton(
                    label = "Correct",
                    onClick = onEditBegin,
                    modifier = Modifier.weight(1f).testTag("f02-entry-correct"),
                )
                WloSecondaryButton(
                    label = "Remove",
                    onClick = { confirmRemove = true },
                    modifier = Modifier.weight(1f).testTag("f02-entry-delete"),
                )
            }
        }
    }

    if (confirmRemove) {
        // Destructive actions confirm first; removal keeps the row in the
        // revision history, so the dialog carries that fact to the decision.
        WloDialog(
            title = "Remove this entry?",
            text = "It stays in your history — corrections keep the original.",
            confirmLabel = "Remove",
            onConfirm = {
                confirmRemove = false
                onDelete()
            },
            dismissLabel = "Cancel",
            onDismiss = { confirmRemove = false },
            destructive = true,
        )
    }
}

private fun formatKcal(value: Double): String = "%,d kcal".format(value.toInt())
