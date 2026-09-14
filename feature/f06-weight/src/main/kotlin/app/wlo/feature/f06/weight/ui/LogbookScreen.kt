package app.wlo.feature.f06.weight.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloIcons
import app.wlo.core.designsystem.WloMotion
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStatDivider
import app.wlo.core.designsystem.WloSwipeRevealRow
import app.wlo.core.designsystem.rememberWloHaptics
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f06.weight.state.DeletedUi
import app.wlo.feature.f06.weight.state.LogbookEvent
import app.wlo.feature.f06.weight.state.LogbookMonthUi
import app.wlo.feature.f06.weight.state.LogbookRowUi
import app.wlo.feature.f06.weight.state.LogbookUiState
import app.wlo.feature.f06.weight.state.LogbookViewModel
import app.wlo.feature.f06.weight.state.WeighInUiState
import kotlinx.coroutines.delay

/**
 * The full-page logbook (WLO-0055): every weigh-in verbatim, newest first,
 * under sticky month headers that carry the month so the rows don't have to.
 * The feed is windowed — the latest few months first, "Load earlier" below —
 * so the list stays bounded no matter how many years it holds. The delete
 * door (WLO-0050) lives here on the raw rows: swipe to reveal, release past
 * the trigger to fire, undo inline where the row was with a visible
 * countdown. No dialogs, no page-level banners, no color fills.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
public fun LogbookScreen(viewModel: LogbookViewModel) {
    val state: LogbookUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deleted: DeletedUi? by viewModel.deletedState.collectAsStateWithLifecycle()
    val notice: String? by viewModel.noticeState.collectAsStateWithLifecycle()

    // Rendering mirror of the pending delete: it holds the notice through
    // the slide-away exit so the feed slot doesn't pop when the timer clears.
    var renderPending by remember { mutableStateOf<DeletedUi?>(null) }
    LaunchedEffect(deleted) {
        when {
            deleted != null -> renderPending = deleted
            renderPending != null -> {
                delay(EXIT_ANIM_MS)
                renderPending = null
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        WloScreenTitle(
            title = "Logbook",
            modifier = Modifier.testTag("f06-logbook-title"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            Text(
                text = "${state.totalEntries} weigh-ins · newest first",
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
            Text(
                text = WeighInUiState.LOWEST_COPY,
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f).testTag("f06-logbook-list"),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            val pending = renderPending
            state.months.forEach { month ->
                stickyHeader(key = "month-${month.startDay}") { MonthHeader(month) }
                if (pending != null && pending.dayEpochDay in month.startDay..month.endDay) {
                    item(key = "undo-${pending.dayEpochDay}") {
                        // The notice keeps its slot through the exit:
                        // AnimatedVisibility slides it away when the timer
                        // empties (WLO-0050).
                        AnimatedVisibility(
                            visible = deleted != null,
                            enter =
                                expandVertically(tween(WloMotion.DURATION_MEDIUM_MS, easing = WloMotion.EasingEnter)) +
                                    fadeIn(tween(WloMotion.DURATION_MEDIUM_MS, easing = WloMotion.EasingEnter)),
                            exit =
                                shrinkOut(tween(WloMotion.DURATION_SHORT_MS, easing = WloMotion.EasingExit)) +
                                    fadeOut(tween(WloMotion.DURATION_SHORT_MS, easing = WloMotion.EasingExit)),
                        ) {
                            InlineUndoRow(
                                deleted = pending,
                                onUndo = { viewModel.onEvent(LogbookEvent.Undo) },
                            )
                        }
                    }
                }
                month.rows.forEachIndexed { index, row ->
                    item(key = row.id) {
                        LogbookRow(
                            row = row,
                            onDelete = { viewModel.onEvent(LogbookEvent.Delete(row.id)) },
                        )
                    }
                    // The M3 list rhythm: items separated by hairline
                    // dividers, never by boxed rows.
                    if (index < month.rows.lastIndex) {
                        item(key = "div-${row.id}") { WloStatDivider() }
                    }
                }
            }
            val remaining = state.totalEntries - state.loadedEntries
            if (remaining > 0) {
                item(key = "load-earlier") {
                    WloSecondaryButton(
                        label = "Load earlier ($remaining more)",
                        onClick = { viewModel.onEvent(LogbookEvent.LoadEarlier) },
                        modifier = Modifier.fillMaxWidth().testTag("f06-load-earlier"),
                    )
                }
            }
        }

        notice?.let {
            Text(
                text = it,
                style = wloType.caption,
                color = wloExtendedColors.held,
            )
        }
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
    onDelete: () -> Unit,
) {
    val haptics = rememberWloHaptics()
    WloSwipeRevealRow(
        revealWidth = REVEAL_WIDTH,
        onTrigger = {
            haptics.perform(WloHaptic.Settle)
            onDelete()
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
        // The standard M3 list item supplies the metrics (56 dp one-line,
        // 16 dp inner edges, trailing alignment) — the swipe wrapper only
        // adds the reveal mechanics around it. Container in the page's own
        // color: the item reads as one opaque sliding piece with no slab
        // seams, and masks the action until its space is vacated.
        ListItem(
            headlineContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = row.dateLabel, style = wloType.body)
                    Text(
                        text = row.timeLabel,
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                    )
                    if (row.isLowest) {
                        Text(
                            text = "day's weight",
                            style = wloType.label,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (row.flagged) {
                        Text(
                            text = "flagged — kept",
                            style = wloType.label,
                            color = wloExtendedColors.held,
                        )
                    }
                }
            },
            trailingContent = {
                Text(
                    text = row.weightLabel,
                    style = wloType.statS,
                    modifier = Modifier.testTag("f06-row-weight"),
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier =
                Modifier
                    .semantics {
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Delete ${row.weightLabel} at ${row.dateLabel} ${row.timeLabel}") {
                                    onDelete()
                                    true
                                },
                            )
                    }.testTag("f06-row"),
        )
    }
}

/**
 * The undo notice, rendered inline where the row was (R-B8 amendment): the
 * fact, one Undo action, and a visible countdown hairline — when it empties
 * the notice slides away on its own (WLO-0050). Copy states the fact and
 * the action; no mood lines.
 */
@Composable
private fun InlineUndoRow(
    deleted: DeletedUi,
    onUndo: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().testTag("f06-deleted-banner")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            Text(
                text = "Deleted ${deleted.label}",
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUndo, modifier = Modifier.testTag("f06-undo-delete")) { Text("Undo") }
        }
        UndoCountdown()
    }
}

/** The visible timer: a 2 dp hairline depleting over [LogbookViewModel.UNDO_WINDOW_MS]. */
@Composable
private fun UndoCountdown() {
    val remaining = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        remaining.snapTo(1f)
        remaining.animateTo(
            0f,
            tween(LogbookViewModel.UNDO_WINDOW_MS.toInt(), easing = LinearEasing),
        )
    }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = WloSpacing.TIGHT)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(remaining.value.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}

/** How far a logbook entry travels to arm its delete action (WLO-0050). */
private val REVEAL_WIDTH = 112.dp

/** How long the exit slide-away takes before the feed slot unmounts (WLO-0050). */
private const val EXIT_ANIM_MS = 320L
