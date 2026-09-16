package app.wlo.feature.f06.weight.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloIcons
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloMotion
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSheet
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStatDivider
import app.wlo.core.designsystem.WloSwipeRevealRow
import app.wlo.core.designsystem.rememberWloHaptics
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f06.weight.state.DeletedUi
import app.wlo.feature.f06.weight.state.EditSheetUi
import app.wlo.feature.f06.weight.state.LogbookContentState
import app.wlo.feature.f06.weight.state.LogbookEvent
import app.wlo.feature.f06.weight.state.LogbookMonthUi
import app.wlo.feature.f06.weight.state.LogbookRowUi
import app.wlo.feature.f06.weight.state.LogbookUiState
import app.wlo.feature.f06.weight.state.LogbookViewModel
import app.wlo.feature.f06.weight.state.WeighInUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The full-page logbook (WLO-0055): every weigh-in verbatim, newest first,
 * under sticky month headers that carry the month so the rows don't have to.
 * The feed is windowed — the latest few months first, "Load earlier" below —
 * so the list stays bounded no matter how many years it holds. The delete
 * door (WLO-0050) lives here on the raw rows: swipe to reveal, release past
 * the trigger to fire. Undo lives in the Scaffold snackbar host so it remains
 * reachable independently of scroll position and accessibility timeout.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun LogbookScreen(viewModel: LogbookViewModel) {
    val state: LogbookUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deleted: DeletedUi? by viewModel.deletedState.collectAsStateWithLifecycle()
    val edit: EditSheetUi? by viewModel.editState.collectAsStateWithLifecycle()
    val notice: String? by viewModel.noticeState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val accessibility = LocalAccessibilityManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(deleted?.operationId, deleted?.restoreFailed, deleted?.restoreAttempt) {
        val pending = deleted ?: return@LaunchedEffect
        val timeoutMillis =
            accessibility?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = LogbookViewModel.UNDO_WINDOW_MS,
                containsIcons = false,
                containsText = true,
                containsControls = true,
            ) ?: LogbookViewModel.UNDO_WINDOW_MS
        // One foreground-only deadline owns the receipt lifetime. Restarting
        // the delay after STOPPED intentionally gives back any partial slice;
        // background time can never consume the Undo opportunity.
        val expiry =
            if (pending.restoreFailed) {
                null
            } else {
                launch {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        delay(timeoutMillis)
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                }
            }
        val result =
            snackbarHostState.showSnackbar(
                message =
                    if (pending.restoreFailed) {
                        "Restore failed for ${pending.label}. Recovery copy retained."
                    } else {
                        "Deleted ${pending.label}"
                    },
                actionLabel = if (pending.restoreFailed) "Retry" else "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite,
            )
        expiry?.cancel()
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.onEvent(LogbookEvent.Undo)
        } else if (!pending.restoreFailed) {
            viewModel.onEvent(LogbookEvent.FinalizeDelete)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it)
                    .padding(horizontal = WloSpacing.SCREEN)
                    .padding(bottom = WloSpacing.SCREEN),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
        ) {
            state.range?.let {
                Text("Filtered to ${state.rangeLabel}", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.onEvent(LogbookEvent.ClearRange) }) { Text("Clear filter") }
            }
            when (state.contentState) {
                LogbookContentState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                LogbookContentState.Error ->
                    LogbookMessage("Couldn't load the logbook.", "Retry") {
                        viewModel.onEvent(LogbookEvent.RetryLoad)
                    }
                LogbookContentState.Empty -> LogbookMessage("No weigh-ins yet.")
                LogbookContentState.FilteredEmpty ->
                    LogbookMessage("No weigh-ins in this date range.", "Clear filter") {
                        viewModel.onEvent(LogbookEvent.ClearRange)
                    }
                LogbookContentState.Ready ->
                    LogbookFeed(
                        state = state,
                        deletionEnabled = deleted == null,
                        viewModel = viewModel,
                    )
            }

            notice?.let { message ->
                Text(
                    text = message,
                    style = wloType.caption,
                    color = wloExtendedColors.held,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("f06-logbook-notice"),
                )
            }
        }
    }

    edit?.let { current ->
        WloSheet(
            onDismissRequest = {
                if (!current.isSaving) viewModel.onEvent(LogbookEvent.CancelEdit)
            },
            modifier = Modifier.testTag("f06-edit-sheet"),
            title = "Edit weigh-in",
        ) {
            EditSheetContent(
                sheet = current,
                unitSymbol = state.massUnit.symbol,
                onChange = { viewModel.onEvent(LogbookEvent.EditWeightChange(it)) },
                onDayChange = { viewModel.onEvent(LogbookEvent.EditDayChange(it)) },
                onTimeChange = { viewModel.onEvent(LogbookEvent.EditTimeChange(it)) },
                onSave = { viewModel.onEvent(LogbookEvent.SaveEdit) },
            )
        }
    }
}

@Composable
private fun ColumnScope.LogbookFeed(
    state: LogbookUiState,
    deletionEnabled: Boolean,
    viewModel: LogbookViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
        Text(
            "${state.totalEntries} weigh-ins · newest first",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
        Text(WeighInUiState.LOWEST_COPY, style = wloType.caption, color = wloExtendedColors.textTertiary)
    }
    LazyColumn(
        modifier = Modifier.weight(1f).testTag("f06-logbook-list"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        state.months.forEach { month ->
            stickyHeader(key = "month-${month.startDay}") { MonthHeader(month) }
            month.rows.forEachIndexed { index, row ->
                item(key = row.id) {
                    LogbookRow(
                        row = row,
                        deletionEnabled = deletionEnabled,
                        onEdit = { viewModel.onEvent(LogbookEvent.BeginEdit(row.id)) },
                        onExplain = { viewModel.onEvent(LogbookEvent.Explain(row.id)) },
                        onDelete = { viewModel.onEvent(LogbookEvent.Delete(row.id)) },
                    )
                }
                if (index < month.rows.lastIndex) item(key = "div-${row.id}") { WloStatDivider() }
            }
        }
        val remaining = state.totalEntries - state.loadedEntries
        if (state.range == null && remaining > 0) {
            item(key = "load-earlier") {
                WloSecondaryButton(
                    label = "Load earlier ($remaining more)",
                    onClick = { viewModel.onEvent(LogbookEvent.LoadEarlier) },
                    modifier = Modifier.fillMaxWidth().testTag("f06-load-earlier"),
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.LogbookMessage(
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = wloType.body, modifier = Modifier.testTag("f06-logbook-state"))
        action?.let { TextButton(onClick = onAction) { Text(it) } }
    }
}

/**
 * F06 §5's inline edit (R-U15's typed path, pointed at an existing entry):
 * correct the value and/or when; saving replaces the entry and the log says
 * so — the supporting line reads "edited". Blank time reads as noon (R-B5).
 */
@Composable
private fun EditSheetContent(
    sheet: EditSheetUi,
    unitSymbol: String,
    onChange: (String) -> Unit,
    onDayChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding(),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(
            text = "Update the reading or when it was captured.",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
        OutlinedTextField(
            value = sheet.weightText,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().testTag("f06-edit-weight"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            textStyle = wloType.statL,
            label = { Text("Weight ($unitSymbol)") },
            isError = sheet.weightError != null,
            supportingText =
                sheet.weightError?.let { message ->
                    {
                        Text(
                            text = message,
                            modifier =
                                Modifier
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                                    .testTag("f06-edit-weight-error"),
                        )
                    }
                },
        )
        WeightDatePickerButton(
            value = sheet.dayText,
            onValueChange = onDayChange,
            modifier = Modifier.fillMaxWidth(),
            testTag = "f06-edit-day",
        )
        WeightTimePickerButton(
            value = sheet.timeText,
            onValueChange = onTimeChange,
            modifier = Modifier.fillMaxWidth(),
            testTag = "f06-edit-time",
        )
        sheet.whenError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = wloType.caption,
                modifier =
                    Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("f06-edit-when-error"),
            )
        }
        sheet.saveError?.let { message ->
            WloBanner(
                text = message,
                tone = WloBannerTone.Warning,
                modifier =
                    Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("f06-edit-save-error"),
            )
        }
        WloButton(
            label = if (sheet.isSaving) "Saving…" else "Save changes",
            onClick = onSave,
            enabled = !sheet.isSaving,
            modifier = Modifier.fillMaxWidth().testTag("f06-edit-save"),
        )
    }
}

/** The sticky month header — it carries the month so the rows can omit it. */
@Composable
private fun MonthHeader(month: LogbookMonthUi) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                // Opaque in the screen's own color: rows scroll underneath.
                .background(MaterialTheme.colorScheme.background)
                .padding(top = WloSpacing.TIGHT, bottom = WloSpacing.CARD),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        Text(
            text = month.headerLabel,
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
        month.statLabel?.let { stat ->
            Text(
                text = stat,
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
        }
    }
}

/**
 * One verbatim entry (F06 §5) behind the swipe-to-delete door (WLO-0050
 * owner spec): the entry slides as one opaque piece, revealing a tinted
 * Delete action in the space it vacates. The delete fires only on release
 * with the drag held past the trigger — velocity is ignored, so a flick
 * never acts — and crossing the trigger is visible before the lift: the
 * glyph + label morph muted → error and tick once. TalkBack gets the same
 * door as a custom row action.
 */
@Composable
private fun LogbookRow(
    row: LogbookRowUi,
    deletionEnabled: Boolean,
    onEdit: () -> Unit,
    onExplain: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = rememberWloHaptics()
    WloSwipeRevealRow(
        revealWidth = REVEAL_WIDTH,
        onTrigger = {
            if (deletionEnabled) {
                haptics.perform(WloHaptic.Settle)
                onDelete()
            }
        },
        reveal = { progress, armed ->
            val actionColor by animateColorAsState(
                targetValue =
                    if (armed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                animationSpec = WloMotion.enter(WloMotion.DURATION_SHORT_MS),
                label = "delete-arm-color",
            )
            val iconScale by animateFloatAsState(
                targetValue = if (armed) 1.15f else 1f,
                animationSpec = WloMotion.Springs.Soft,
                label = "delete-arm-scale",
            )
            Box(
                modifier = Modifier.fillMaxSize().testTag("f06-row-delete"),
                contentAlignment = Alignment.CenterEnd,
            ) {
                val fade = 0.35f + 0.65f * progress
                Row(
                    modifier =
                        Modifier
                            .padding(horizontal = WloSpacing.SCREEN)
                            .alpha(fade),
                    horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = WloIcons.Close,
                        contentDescription = null,
                        tint = actionColor,
                        modifier = Modifier.scale(iconScale),
                    )
                    Text(text = "Delete", style = wloType.label, color = actionColor)
                }
            }
        },
    ) {
        // The design system's standard row anatomy (WLO-0031) — headline
        // date, supporting time + provenance marks, the weight in the value
        // slot — so every list in the app reads the same. Tap opens the edit
        // sheet (F06 §5 inline edit), the same ripple every actionable row
        // in the app carries; the swipe wrapper adds only the delete reveal.
        // The box masks the action with the page's own color until the row's
        // space is vacated (WloListRow itself renders on a transparent
        // container).
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            val meta =
                buildString {
                    append("${row.timeLabel} · ${row.sourceLabel}")
                    if (row.isLowest) append(" · day's weight")
                    if (row.flagged) append(" · flagged — kept")
                    if (row.edited) append(" · edited")
                }
            WloListRow(
                label = row.dateLabel,
                secondary = meta,
                trailing = {
                    RowActions(row, deletionEnabled, onEdit, onExplain, onDelete)
                },
                onClick = onEdit,
                modifier =
                    Modifier
                        .semantics {
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(
                                        "Edit ${row.weightLabel} at ${row.dateLabel} ${row.timeLabel}",
                                    ) {
                                        onEdit()
                                        true
                                    },
                                    CustomAccessibilityAction(
                                        "Explain ${row.weightLabel} at ${row.dateLabel} ${row.timeLabel}",
                                    ) {
                                        onExplain()
                                        true
                                    },
                                ) +
                                if (deletionEnabled) {
                                    listOf(
                                        CustomAccessibilityAction(
                                            "Delete ${row.weightLabel} at ${row.dateLabel} ${row.timeLabel}",
                                        ) {
                                            onDelete()
                                            true
                                        },
                                    )
                                } else {
                                    emptyList()
                                }
                        }.testTag("f06-row"),
            )
        }
    }
}

@Composable
private fun RowActions(
    row: LogbookRowUi,
    deletionEnabled: Boolean,
    onEdit: () -> Unit,
    onExplain: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Text(row.weightLabel, style = wloType.statS, modifier = Modifier.testTag("f06-row-weight"))
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("f06-row-actions"),
        ) {
            Icon(
                WloIcons.MoreVertical,
                contentDescription = "Actions for ${row.dateLabel} ${row.timeLabel} ${row.weightLabel}",
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = {
                expanded = false
                onEdit()
            })
            DropdownMenuItem(text = { Text("Explain") }, onClick = {
                expanded = false
                onExplain()
            })
            DropdownMenuItem(
                text = { Text("Delete") },
                enabled = deletionEnabled,
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** How far a logbook entry travels to arm its delete action (WLO-0050). */
private val REVEAL_WIDTH = 112.dp
