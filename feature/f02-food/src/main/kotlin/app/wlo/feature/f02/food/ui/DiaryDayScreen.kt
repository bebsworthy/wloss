package app.wlo.feature.f02.food.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f02.food.state.DiaryEvent
import app.wlo.feature.f02.food.state.DiaryUiState
import app.wlo.feature.f02.food.state.DiaryViewModel
import app.wlo.feature.f02.food.state.EntryRowUi
import app.wlo.feature.f02.food.state.NoticeState
import app.wlo.feature.f02.food.state.SlotUi

/**
 * The diary day view (F02 §5: meal groupings, kcal + provenance per entry,
 * the day-status marker in the day header, edit/delete with the honest
 * revision history behind every entry's provenance sheet — the M2
 * "how we got here" bottom-sheet pattern).
 */
@OptIn(ExperimentalMaterial3Api::class)
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

        WloCard(modifier = Modifier.testTag("f02-totals-card")) {
            Text(text = "eaten so far", style = wloType.label, color = wloExtendedColors.textTertiary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The numeral renders HERE; the chip carries the kind + info
                // mark only (new chip anatomy never repeats the value).
                Text(
                    text = formatKcal(state.totals.value),
                    style = wloType.statL,
                    modifier = Modifier.testTag("f02-day-total"),
                )
                Spacer(Modifier.width(WloSpacing.TIGHT))
                ProvenanceChip(
                    value = state.totals,
                    format = ::formatKcal,
                )
            }
            if (state.macroLine.isNotBlank()) {
                Text(text = state.macroLine, style = wloType.receipt, color = wloExtendedColors.textTertiary)
            }
        }

        ActionRow(
            label = "add 500 ml water",
            modifier = Modifier.fillMaxWidth().testTag("f02-water-add"),
        ) { viewModel.onEvent(DiaryEvent.WaterQuickAdd) }

        for (slot in state.slots) {
            SlotSection(slot) { entryId -> viewModel.onEvent(DiaryEvent.EntryTap(entryId)) }
        }

        if (state.slots.isEmpty()) {
            Text(
                text = "nothing logged yet — the day fills in as you go",
                style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                color = wloExtendedColors.textTertiary,
            )
        }

        state.notice?.let {
            NoticeLine(
                it.text,
                rail = it.state == NoticeState.RAIL,
                onDismiss = { viewModel.onEvent(DiaryEvent.DismissNotice) },
            )
        }
    }

    state.openEntry?.let { detail ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(DiaryEvent.DismissEntry) },
            shape = WloShape.SheetTop,
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = WloSpacing.SCREEN),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Diary · ${state.dayLabel}",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier = Modifier.testTag("f02-diary-title"),
        )
        state.dayStatus?.let { status ->
            // The day-status marker (F02 §3): one tap cycles it; a gap is
            // never shamed — the marker states what the day was.
            Surface(
                onClick = onCycleStatus,
                shape = WloShape.Pill,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("f02-day-status"),
            ) {
                Text(
                    text = status.label,
                    style = wloType.label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }

@Composable
private fun SlotSection(
    slot: SlotUi,
    onEntryTap: (String) -> Unit,
): Unit =
    Column(
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        modifier = Modifier.testTag("f02-slot-${slot.slot.wireName}"),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = WloSpacing.TIGHT),
        ) {
            Text(
                text = slotLabel(slot.slot),
                style = wloType.title,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.padding(horizontal = WloSpacing.TIGHT))
            Text(
                text = formatKcal(slot.kcal),
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
        WloCard {
            slot.entries.forEachIndexed { index, entry ->
                EntryRow(entry, onEntryTap)
                if (index < slot.entries.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                }
            }
        }
    }

@Composable
private fun EntryRow(
    entry: EntryRowUi,
    onEntryTap: (String) -> Unit,
): Unit =
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = WloSpacing.ROW_INTERACTIVE)
                .clickable { onEntryTap(entry.id) },
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = wloType.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.subtitle + if (entry.edited) " · edited" else "",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
            )
        }
        ProvenanceChip(
            value = entry.kcal,
            format = ::formatKcal,
            onClick = { onEntryTap(entry.id) },
        )
    }

@Composable
private fun EntrySheetContent(
    detail: app.wlo.feature.f02.food.state.EntryDetailUi,
    onEditBegin: () -> Unit,
    onEditChange: (String) -> Unit,
    onEditCancel: () -> Unit,
    onEditSave: () -> Unit,
    onDelete: () -> Unit,
): Unit =
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(text = "How we got here", style = wloType.title)

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
                        style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = value, style = wloType.receipt)
                }
            }
        }

        if (detail.revisions.isNotEmpty()) {
            Text(
                text = "history — every correction keeps its prior version",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
            )
            WloCard(modifier = Modifier.testTag("f02-revision-list")) {
                detail.revisions.forEachIndexed { index, rev ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = WloSpacing.ROW_MIN),
                        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "version ${rev.revision.revision}",
                            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${rev.revision.quantity} ${rev.revision.unit} · ",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                        ProvenanceChip(value = rev.kcal, format = ::formatKcal)
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
                ActionRow(
                    label = "save correction",
                    modifier = Modifier.weight(1f).testTag("f02-edit-save"),
                ) { onEditSave() }
                ActionRow(label = "cancel", modifier = Modifier.weight(1f)) { onEditCancel() }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                ActionRow(
                    label = "correct",
                    modifier = Modifier.weight(1f).testTag("f02-entry-correct"),
                ) { onEditBegin() }
                ActionRow(
                    label = "remove",
                    modifier = Modifier.weight(1f).testTag("f02-entry-delete"),
                ) { onDelete() }
            }
        }
        Text(
            text = "removing keeps the row in your history — restorable, never a silent rewrite",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = wloExtendedColors.textTertiary,
        )
        Spacer(Modifier.height(WloSpacing.ROW_MIN))
    }

private fun formatKcal(value: Double): String = "%,d kcal".format(value.toInt())
