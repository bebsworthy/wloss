package app.wlo.feature.f06.weight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardAccent
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloDeltaChip
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloHeroStat
import app.wlo.core.designsystem.WloIcons
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSheet
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStatDivider
import app.wlo.core.designsystem.WloStatRow
import app.wlo.core.designsystem.WloTrendChart
import app.wlo.core.designsystem.formatDay
import app.wlo.core.designsystem.rememberWloHaptics
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.engines.MilestoneLadder
import app.wlo.core.model.TrendMethod
import app.wlo.feature.f06.weight.state.BodyFatUiState
import app.wlo.feature.f06.weight.state.BodyFatViewModel
import app.wlo.feature.f06.weight.state.BodySectionUi
import app.wlo.feature.f06.weight.state.ChartWindowUi
import app.wlo.feature.f06.weight.state.GoalProgressState
import app.wlo.feature.f06.weight.state.GoalProgressUi
import app.wlo.feature.f06.weight.state.HistoryBucketUi
import app.wlo.feature.f06.weight.state.HistoryRange
import app.wlo.feature.f06.weight.state.HistoryTier
import app.wlo.feature.f06.weight.state.RatiosUi
import app.wlo.feature.f06.weight.state.SheetUi
import app.wlo.feature.f06.weight.state.VerdictUi
import app.wlo.feature.f06.weight.state.WeighInConfirmationUi
import app.wlo.feature.f06.weight.state.WeighInEditIntent
import app.wlo.feature.f06.weight.state.WeighInEvent
import app.wlo.feature.f06.weight.state.WeighInSubmissionState
import app.wlo.feature.f06.weight.state.WeighInUiState
import app.wlo.feature.f06.weight.state.WeighInViewModel
import app.wlo.feature.f06.weight.state.WeightLoadState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The F06 weight surface (owner review WLO-0030): trend-first hero — one
 * "Weight" card header, the hero numeral + small unit + weekly delta, the
 * last raw reading line, a real weigh-in button — then the compressed
 * history (WLO-0055: one row per bucket, coarser with distance — days,
 * weeks, months, quarters; rows tap through), the trend chart with its
 * smoother tuner (α visible, R-A2 default 0.15; a non-default selection is
 * a labeled PREVIEW — the saved trend keeps the default), and the outlier
 * guard's one-line keep-or-delete. Every verbatim entry — and the
 * swipe-to-reveal delete with its inline undo (R-B8 amendment, WLO-0035 +
 * WLO-0050) — lives on the full logbook screen this card opens. The
 * weigh-in sheet opens over this surface (wlo://weight/log) — the typed
 * path is first-class, R-U15. One screen, two segments (R2, WLO-0035):
 * Weight (this) and Body fat (per-method series + tape + calculator) —
 * never a separate route.
 */
@Composable
@Suppress("LongMethod") // One ordered semantic dashboard; extracted cards own each section's detail.
public fun WeightScreen(
    viewModel: WeighInViewModel,
    bodyFatViewModel: BodyFatViewModel,
    onOpenMath: () -> Unit,
    onOpenLogbook: (HistoryRange?) -> Unit,
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state: WeighInUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet: SheetUi? by viewModel.sheetState.collectAsStateWithLifecycle()
    val verdict: VerdictUi? by viewModel.verdictState.collectAsStateWithLifecycle()
    val confirmation: WeighInConfirmationUi? by viewModel.confirmationState.collectAsStateWithLifecycle()
    val notice: String? by viewModel.noticeState.collectAsStateWithLifecycle()
    val submission: WeighInSubmissionState by viewModel.submissionState.collectAsStateWithLifecycle()
    val bodyFatState: BodyFatUiState by bodyFatViewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberWloHaptics()
    WeightLifecycleRefresh(viewModel)

    LaunchedEffect(confirmation?.eventId, confirmation?.hapticPending) {
        val current = confirmation
        if (current?.hapticPending == true) {
            haptics.perform(WloHaptic.Tick)
            viewModel.onEvent(WeighInEvent.ConfirmationHapticConsumed(current.eventId))
        }
    }
    LaunchedEffect(bodyFatState.committedOperationId) {
        if (bodyFatState.committedOperationId != null) viewModel.onEvent(WeighInEvent.Refresh)
    }

    val scrollState = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val useBottomAction = maxHeight < 600.dp || LocalDensity.current.fontScale >= 1.5f
        val useSupportingPane = maxWidth >= 840.dp
        val openSheet = { viewModel.onEvent(WeighInEvent.OpenSheet()) }
        Scaffold(
            floatingActionButton = {
                if (!useBottomAction && state.section == BodySectionUi.WEIGHT && sheet == null) {
                    ExtendedFloatingActionButton(
                        onClick = openSheet,
                        icon = { Icon(WloIcons.Plus, contentDescription = null) },
                        text = { Text("Weigh in") },
                        modifier = Modifier.testTag("f06-open-sheet"),
                    )
                }
            },
            bottomBar = {
                if (useBottomAction && state.section == BodySectionUi.WEIGHT && sheet == null) {
                    WloButton(
                        label = "Weigh in",
                        onClick = openSheet,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = WloSpacing.SCREEN, vertical = WloSpacing.CARD)
                                .testTag("f06-open-sheet"),
                    )
                }
            },
        ) { scaffoldPadding ->
            Row(modifier = Modifier.fillMaxWidth().padding(scaffoldPadding)) {
                Column(
                    modifier =
                        Modifier
                            .weight(if (useSupportingPane) 0.64f else 1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = WloSpacing.SCREEN)
                            .padding(bottom = WloSpacing.SCREEN),
                    verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
                ) {
                    when (val load = state.loadState) {
                        WeightLoadState.Loading -> CircularProgressIndicator(modifier = Modifier.testTag("f06-loading"))
                        is WeightLoadState.Error ->
                            WloBanner(
                                text = load.message,
                                tone = WloBannerTone.Warning,
                                actionLabel = "Retry",
                                action = { viewModel.onEvent(WeighInEvent.Refresh) },
                                modifier = Modifier.testTag("f06-load-error"),
                            )
                        WeightLoadState.Content, WeightLoadState.Empty -> Unit
                    }

                    // One screen, different series (R2, WLO-0035) — segments, not routes.
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(BodySectionUi.WEIGHT to "Weight", BodySectionUi.BODY_FAT to "Body fat")
                            .forEachIndexed { index, (section, label) ->
                                SegmentedButton(
                                    selected = state.section == section,
                                    onClick = { viewModel.onEvent(WeighInEvent.SectionChange(section)) },
                                    shape = SegmentedButtonDefaults.itemShape(index, BodySectionUi.entries.size),
                                    modifier = Modifier.testTag("f06-section-${section.name.lowercase()}"),
                                ) {
                                    Text(label)
                                }
                            }
                    }

                    if (state.section == BodySectionUi.BODY_FAT) {
                        BodyFatSection(
                            state = state,
                            bodyFatViewModel = bodyFatViewModel,
                            onWindowChange = { viewModel.onEvent(WeighInEvent.BodyFatWindowChange(it)) },
                        )
                    } else {
                        // Weight segment: the weigh-in ritual, the trend, the logbook.

                        confirmation?.let { current ->
                            WeighInConfirmationCard(
                                confirmation = current,
                                state = state,
                                onDone = { viewModel.onEvent(WeighInEvent.DismissConfirmation) },
                            )
                        }

                        verdict?.let { current ->
                            // The outlier guard's one line (F06 §4): describe, offer both taps,
                            // never judge. The event is already stored — this only confirms.
                            Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                                WloBanner(
                                    text =
                                        "${current.weightLabel} is ${current.residualLabel} vs your trend — " +
                                            "keep or correct?",
                                    tone = WloBannerTone.Warning,
                                    modifier = Modifier.testTag("f06-outlier-banner"),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                                    WloButton(
                                        label = "Keep",
                                        onClick = { viewModel.onEvent(WeighInEvent.KeepFlagged) },
                                        modifier = Modifier.weight(1f).testTag("f06-outlier-keep"),
                                    )
                                    WloSecondaryButton(
                                        label = "Correct",
                                        onClick = { viewModel.onEvent(WeighInEvent.CorrectFlagged) },
                                        modifier = Modifier.weight(1f).testTag("f06-outlier-correct"),
                                    )
                                }
                            }
                        }

                        run {
                            WloCard(modifier = Modifier.testTag("f06-hero-card")) {
                                val trend = state.trend
                                val current = trend?.current
                                val delta7 = trend?.delta7
                                WloCardHeader(
                                    title = "Weight trend",
                                    provenance =
                                        if (current != null) {
                                            {
                                                // The chip opens the math sheet — real "how we got
                                                // here" content, never a dead info mark.
                                                ProvenanceChip(
                                                    value = current,
                                                    format = state.massUnit::format,
                                                    onClick = onOpenMath,
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                )
                                if (current != null) {
                                    WloHeroStat(
                                        value = current,
                                        format = state.massUnit::formatNumber,
                                        unit = state.massUnit.symbol,
                                        delta =
                                            if (delta7 != null) {
                                                {
                                                    WloDeltaChip(
                                                        value = delta7,
                                                        format = { magnitude ->
                                                            val weight = state.massUnit.formatNumber(magnitude)
                                                            "$weight ${state.massUnit.symbol} / 7 d"
                                                        },
                                                        style = wloType.statM,
                                                        context = "trend delta",
                                                    )
                                                }
                                            } else {
                                                null
                                            },
                                        // The card header owns the single provenance chip (top-right).
                                        provenance = {},
                                        modifier = Modifier.testTag("f06-trend-stat"),
                                    )
                                }
                                if (current == null) {
                                    Text(
                                        text =
                                            trend?.stateCopy
                                                ?: "Trend is forming from your canonical daily weights.",
                                        style = wloType.caption,
                                        color = wloExtendedColors.textTertiary,
                                    )
                                }
                                state.lastWeighInLabel?.let { last ->
                                    Text(
                                        text = "Last raw reading $last",
                                        style = wloType.receipt,
                                        color = wloExtendedColors.textTertiary,
                                    )
                                }
                            }

                            WeightTrendCard(
                                state = state,
                                viewModel = viewModel,
                                onOpenMath = onOpenMath,
                                onOpenLogbook = { onOpenLogbook(null) },
                            )

                            GoalProgressCard(progress = state.goalProgress, state = state, onEditGoal = onEditGoal)

                            WloCard(modifier = Modifier.testTag("f06-history-card")) {
                                WloCardHeader(title = "History")
                                if (state.history.isEmpty()) {
                                    Text(
                                        text =
                                            "No weigh-ins yet — the morning window reads steadiest, " +
                                                "whenever you get to it.",
                                        style = wloType.caption,
                                        color = wloExtendedColors.textTertiary,
                                    )
                                }
                                // One row per bucket, coarser with distance (WLO-0055): the
                                // tier captions mark the compression, and every row taps
                                // through to the verbatim feed — delete lives there, on the
                                // raw rows, never on an aggregate. Hairline dividers inside
                                // a tier, tier captions at the boundaries — the same list
                                // rhythm as the logbook (WLO-0056).
                                var previousTier: HistoryTier? = null
                                state.history.forEach { bucket ->
                                    if (bucket.tier != previousTier) {
                                        previousTier = bucket.tier
                                        Text(
                                            text = bucket.tier.label,
                                            style = wloType.label,
                                            color = wloExtendedColors.textTertiary,
                                        )
                                    } else {
                                        WloStatDivider()
                                    }
                                    HistoryRow(
                                        bucket = bucket,
                                        onClick = {
                                            onOpenLogbook(
                                                HistoryRange(bucket.startDayInclusive, bucket.endDayExclusive),
                                            )
                                        },
                                    )
                                }
                                WloSecondaryButton(
                                    label = "View logbook",
                                    onClick = { onOpenLogbook(null) },
                                    modifier = Modifier.fillMaxWidth().testTag("f06-open-logbook"),
                                )
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
                }
                if (useSupportingPane) {
                    Column(
                        modifier =
                            Modifier
                                .weight(0.36f)
                                .padding(end = WloSpacing.SCREEN, bottom = WloSpacing.SCREEN),
                        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
                    ) {
                        WloCard(modifier = Modifier.testTag("f06-supporting-pane")) {
                            WloCardHeader(title = "At a glance")
                            WloListRow(
                                label = "Current trend",
                                value = {
                                    Text(
                                        state.trend
                                            ?.current
                                            ?.value
                                            ?.let(state.massUnit::format) ?: "Forming",
                                        style = wloType.statS,
                                    )
                                },
                            )
                            WloSecondaryButton(
                                label = "Edit goal",
                                onClick = onEditGoal,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            WloSecondaryButton(
                                label = "View logbook",
                                onClick = { onOpenLogbook(null) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    sheet?.let { current ->
        WloSheet(
            onDismissRequest = { viewModel.onEvent(WeighInEvent.DismissSheet) },
            modifier = Modifier.testTag("f06-weighin-sheet"),
            title = if (current.intent is WeighInEditIntent.CorrectReading) "Correct weigh-in" else "Weigh in",
        ) {
            WeighInSheetContent(
                sheet = current,
                unitSymbol = state.massUnit.symbol,
                submission = submission,
                onChange = { viewModel.onEvent(WeighInEvent.WeightChange(it)) },
                onDayChange = { viewModel.onEvent(WeighInEvent.SheetDayChange(it)) },
                onTimeChange = { viewModel.onEvent(WeighInEvent.SheetTimeChange(it)) },
                onStepUp = { viewModel.onEvent(WeighInEvent.StepperUp) },
                onStepDown = { viewModel.onEvent(WeighInEvent.StepperDown) },
                onSave = { viewModel.onEvent(WeighInEvent.Save) },
                onCancel = { viewModel.onEvent(WeighInEvent.DismissSheet) },
            )
        }
    }
}

@Composable
private fun GoalProgressCard(
    progress: GoalProgressUi,
    state: WeighInUiState,
    onEditGoal: () -> Unit,
) {
    var milestonesExpanded by rememberSaveable { mutableStateOf(false) }
    WloCard(modifier = Modifier.testTag("f06-goal-progress")) {
        WloCardHeader(title = "Goal")
        when (progress.state) {
            GoalProgressState.NO_GOAL -> {
                Text(
                    text = "No weight goal is active. Weight tracking works without one.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
                WloSecondaryButton(
                    label = "Set a goal",
                    onClick = onEditGoal,
                    modifier = Modifier.fillMaxWidth().testTag("f06-set-goal"),
                )
            }
            GoalProgressState.UNAVAILABLE ->
                Text(
                    text = "Goal progress couldn't refresh. Weight tracking still works.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            GoalProgressState.TREND_FORMING -> {
                Text(
                    text = "Keep weighing — goal progress starts from the canonical trend, not a single low reading.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
                progress.targetWeightKg?.let { target ->
                    WloListRow(
                        label = "Target",
                        value = { Text(state.massUnit.format(target), style = wloType.statS) },
                    )
                }
                WloSecondaryButton(
                    label = "Edit goal",
                    onClick = onEditGoal,
                    modifier = Modifier.fillMaxWidth().testTag("f06-edit-goal"),
                )
            }
            GoalProgressState.LOSS,
            GoalProgressState.MAINTENANCE,
            GoalProgressState.GAIN,
            -> {
                progress.targetWeightKg?.let { target ->
                    WloListRow(
                        label = "Target · ${progress.state.modeLabel()}",
                        value = { Text(state.massUnit.format(target), style = wloType.statS) },
                    )
                }
                val visibleRungs =
                    if (milestonesExpanded) {
                        progress.rungs
                    } else {
                        progress.rungs
                            .filterNot {
                                it.state == MilestoneLadder.State.COMPLETED
                            }.take(1)
                    }
                visibleRungs.forEach { rung ->
                    WloListRow(
                        label =
                            buildString {
                                append(state.massUnit.format(rung.weightKg))
                                if (rung.isGoal) append(" · goal")
                            },
                        secondary =
                            when {
                                rung.state == MilestoneLadder.State.COMPLETED -> "Reached by your trend"
                                rung.rangeEpochDays != null -> {
                                    val (early, late) = checkNotNull(rung.rangeEpochDays)
                                    "${formatDay(early)} – ${formatDay(late)}"
                                }
                                else -> "Date unavailable — progress still counts"
                            },
                        leading = {
                            Text(
                                text = if (rung.state == MilestoneLadder.State.COMPLETED) "Done" else "Next",
                                style = wloType.label,
                                color = wloExtendedColors.textTertiary,
                            )
                        },
                    )
                }
                progress.forecastCopy?.let { copy ->
                    Text(
                        text = copy,
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                }
                if (progress.rungs.size > 1) {
                    TextButton(onClick = { milestonesExpanded = !milestonesExpanded }) {
                        Text(if (milestonesExpanded) "Hide milestones" else "Milestones")
                    }
                }
                WloSecondaryButton(
                    label = "Edit goal",
                    onClick = onEditGoal,
                    modifier = Modifier.fillMaxWidth().testTag("f06-edit-goal"),
                )
            }
        }
    }
}

private fun GoalProgressState.modeLabel(): String =
    when (this) {
        GoalProgressState.LOSS -> "loss"
        GoalProgressState.MAINTENANCE -> "maintenance"
        GoalProgressState.GAIN -> "gain"
        else -> "goal"
    }

/**
 * Material 3 card receipt for WLO-0070. The derived trend is only promoted to
 * hero after three canonical samples; before that, the card names the warm-up
 * state and keeps the persisted raw reading as supporting evidence.
 */
@Composable
private fun WeighInConfirmationCard(
    confirmation: WeighInConfirmationUi,
    state: WeighInUiState,
    onDone: () -> Unit,
) {
    val hasTrend = confirmation.sampleCount >= MIN_CONFIRMATION_TREND_SAMPLES && confirmation.trend != null
    val rawLabel = state.massUnit.format(confirmation.rawWeightKg)
    val sourceLabel = confirmation.source.replace('-', ' ')
    val trendLabel = confirmation.trend?.let { state.massUnit.format(it.value) }
    val deltaLabel = confirmation.delta7?.let { signedDeltaLabel(it.value, state) }
    val announcement =
        if (hasTrend) {
            buildString {
                append("Weight saved. Weight trend $trendLabel.")
                deltaLabel?.let { append(" Seven-day change $it.") }
                append(" Raw reading $rawLabel from $sourceLabel.")
            }
        } else {
            val readingWord = if (confirmation.sampleCount == 1) "reading" else "readings"
            "Weight saved. Trend is still learning from ${confirmation.sampleCount} $readingWord. " +
                "Raw reading $rawLabel from $sourceLabel."
        }

    WloCard(
        modifier = Modifier.testTag("f06-confirmation-card"),
        accent = WloCardAccent.Primary,
        header = { WloCardHeader(title = "Weight saved") },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = announcement
                        liveRegion = LiveRegionMode.Polite
                    },
            verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            if (hasTrend) {
                Text(
                    text = "Weight trend",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
                WloHeroStat(
                    value = checkNotNull(confirmation.trend),
                    format = state.massUnit::formatNumber,
                    unit = state.massUnit.symbol,
                    delta =
                        confirmation.delta7?.let { delta ->
                            {
                                WloDeltaChip(
                                    value = delta,
                                    format = { state.massUnit.format(it) },
                                    context = "7-day trend change",
                                )
                            }
                        },
                    provenance = {},
                    modifier = Modifier.testTag("f06-confirmation-trend"),
                )
            } else {
                Text(
                    text = "Trend is still learning",
                    style = wloType.statL,
                    modifier = Modifier.testTag("f06-confirmation-sparse"),
                )
                Text(
                    text =
                        if (confirmation.sampleCount == 0) {
                            "Your saved reading will appear as soon as the trend reloads."
                        } else {
                            "Add ${MIN_CONFIRMATION_TREND_SAMPLES - confirmation.sampleCount} more " +
                                "to establish a trend."
                        },
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            }
            Text(
                text = "Raw reading $rawLabel · $sourceLabel",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.testTag("f06-confirmation-raw"),
            )
        }
        WloButton(
            label = "Done",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().testTag("f06-confirmation-done"),
        )
    }
}

private fun signedDeltaLabel(
    deltaKg: Double,
    state: WeighInUiState,
): String {
    val display = state.massUnit.fromKilograms(deltaKg)
    val direction =
        when {
            display < 0.0 -> "down"
            display > 0.0 -> "up"
            else -> "unchanged"
        }
    return if (display == 0.0) {
        direction
    } else {
        "$direction ${state.massUnit.formatNumber(kotlin.math.abs(deltaKg))} ${state.massUnit.symbol}"
    }
}

@Composable
private fun WeightTrendCard(
    state: WeighInUiState,
    viewModel: WeighInViewModel,
    onOpenMath: () -> Unit,
    onOpenLogbook: () -> Unit,
) {
    val trend = state.trend ?: return
    WloCard(modifier = Modifier.testTag("f06-trend-card")) {
        WloCardHeader(title = "Trend")
        WindowSegmentedControl(
            selected = state.window,
            onSelect = { viewModel.onEvent(WeighInEvent.WindowChange(it)) },
            testPrefix = "f06-window",
        )
        WloTrendChart(
            samples = trend.samples,
            trend = trend.trend,
            currentTrend = null,
            formatWeight = state.massUnit::format,
            windowStartDay = trend.windowStartDay,
            windowEndDay = trend.windowEndDay,
            describe = trend.description,
            alwaysShowTickYear = trend.alwaysShowTickYear,
            emptyMessage = trend.stateCopy,
            emptyActionLabel = trend.emptyActionWindow?.let { "Show ${it.label}" },
            onEmptyAction =
                trend.emptyActionWindow?.let { target ->
                    { viewModel.onEvent(WeighInEvent.WindowChange(target)) }
                },
            onViewRawReadings = onOpenLogbook,
        )
        trend.delta30?.let { delta ->
            val formatted = state.massUnit.format(delta.value)
            val signed = if (delta.value > 0.0) "+$formatted" else formatted
            Text(
                text = "30-day trend $signed",
                style = wloType.receipt,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("f06-trend-delta-30"),
            )
        }
        if (trend.samples.isNotEmpty() && trend.stateCopy != null) {
            Text(trend.stateCopy, style = wloType.caption, color = wloExtendedColors.textTertiary)
        }
        if (trend.samples.isNotEmpty()) {
            SmootherTuner(
                method = state.method,
                alpha = state.alpha,
                onMethod = { viewModel.onEvent(WeighInEvent.MethodChange(it)) },
                onAlpha = { viewModel.onEvent(WeighInEvent.AlphaChange(it)) },
            )
            if (trend.preview) {
                Text(
                    text = "Preview — the saved trend keeps the default smoother.",
                    style = wloType.caption,
                    color = wloExtendedColors.held,
                )
            }
        }
        WloSecondaryButton(
            label = "How the math works",
            onClick = onOpenMath,
            modifier = Modifier.fillMaxWidth().testTag("f06-open-math"),
        )
    }
}

@Composable
private fun WeightLifecycleRefresh(viewModel: WeighInViewModel) {
    val refreshScope = rememberCoroutineScope()
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(WeighInEvent.Refresh)
        val boundaryMonitor =
            refreshScope.launch {
                while (currentCoroutineContext().isActive) {
                    delay(BOUNDARY_POLL_MILLIS)
                    viewModel.onEvent(WeighInEvent.BoundaryCheck)
                }
            }
        onPauseOrDispose { boundaryMonitor.cancel() }
    }
}

private const val BOUNDARY_POLL_MILLIS: Long = 60_000L
private const val MIN_CONFIRMATION_TREND_SAMPLES: Int = 3

/** The smoother selection + the visible α tuner (R-A2: default 0.15, in the open). */
@Composable
private fun SmootherTuner(
    method: TrendMethod,
    alpha: Double,
    onMethod: (TrendMethod) -> Unit,
    onAlpha: (Double) -> Unit,
): Unit =
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
        Text(
            text = "Smoother",
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            for (candidate in TrendMethod.entries) {
                SelectChip(
                    label = methodLabel(candidate),
                    selected = candidate == method,
                    onClick = { onMethod(candidate) },
                    modifier = Modifier.testTag("f06-method-${candidate.wireName}"),
                )
            }
        }
        Text(
            text = "Responsiveness α ${format2(alpha)}",
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
        Slider(
            value = alpha.toFloat(),
            onValueChange = { onAlpha(it.toDouble()) },
            valueRange = ALPHA_MIN..ALPHA_MAX,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().testTag("f06-alpha-slider"),
        )
    }

/**
 * One compressed-history bucket row (WLO-0055), on the design system's
 * standard [WloListRow] anatomy — headline range, supporting weigh-in count
 * (always present, so the list rhythm stays even), the Δ and closing day's
 * weight in the value slot. An empty bucket keeps its row — gaps are data.
 * Tapping any row opens the full logbook, where the individual entries live.
 */
@Composable
private fun HistoryRow(
    bucket: HistoryBucketUi,
    onClick: () -> Unit,
) {
    WloListRow(
        label = bucket.label,
        secondary = bucket.countLabel,
        modifier = Modifier.testTag("f06-history-row"),
        value = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                bucket.deltaLabel?.let { delta ->
                    Text(
                        text = delta,
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                }
                if (bucket.weightLabel == null) {
                    Text(
                        text = "—",
                        style = wloType.statS,
                        color = wloExtendedColors.textTertiary,
                        modifier = Modifier.testTag("f06-history-weight"),
                    )
                } else {
                    Text(
                        text = bucket.weightLabel,
                        style = wloType.statS,
                        modifier = Modifier.testTag("f06-history-weight"),
                    )
                }
            }
        },
        onClick = onClick,
    )
}

/**
 * The typed weigh-in path (R-U15): first-class, never a fallback, and
 * back-datable (F06 §4) — an ISO date plus an optional HH:MM (blank reads
 * as noon, the R-B5 normalization). Children land in [WloSheet]'s padded,
 * spaced column.
 */
@Composable
private fun WeighInSheetContent(
    sheet: SheetUi,
    unitSymbol: String,
    submission: WeighInSubmissionState,
    onChange: (String) -> Unit,
    onDayChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val focusManager: FocusManager = LocalFocusManager.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding(),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(
            text = sheet.prefillContext,
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.testTag("f06-prefill-context"),
        )
        OutlinedTextField(
            value = sheet.weightText,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().testTag("f06-weight-field"),
            singleLine = true,
            enabled = submission != WeighInSubmissionState.SAVING,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (submission != WeighInSubmissionState.SAVING) onSave()
                    },
                ),
            textStyle = wloType.statL,
            label = { Text("Weight ($unitSymbol)") },
            placeholder = { Text("Enter weight", style = wloType.body, color = wloExtendedColors.textTertiary) },
            isError = sheet.weightError != null,
            supportingText =
                sheet.weightError?.let { message ->
                    {
                        Text(
                            text = message,
                            modifier =
                                Modifier
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                                    .testTag("f06-weight-error"),
                        )
                    }
                },
        )
        WeightDatePickerButton(
            value = sheet.dayText,
            onValueChange = onDayChange,
            modifier = Modifier.fillMaxWidth(),
            testTag = "f06-day-field",
            enabled = submission != WeighInSubmissionState.SAVING,
        )
        WeightTimePickerButton(
            value = sheet.timeText,
            onValueChange = onTimeChange,
            modifier = Modifier.fillMaxWidth(),
            testTag = "f06-time-field",
            enabled = submission != WeighInSubmissionState.SAVING,
        )
        sheet.whenError?.let { message ->
            Text(
                text = message,
                style = wloType.caption,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("f06-when-error"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
            WloSecondaryButton(
                label = "−0.1",
                onClick = onStepDown,
                enabled = submission != WeighInSubmissionState.SAVING,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Decrease weight by 0.1 $unitSymbol"
                            stateDescription = "Current weight ${sheet.weightText} $unitSymbol"
                        }.testTag("f06-step-down"),
            )
            WloSecondaryButton(
                label = "+0.1",
                onClick = onStepUp,
                enabled = submission != WeighInSubmissionState.SAVING,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Increase weight by 0.1 $unitSymbol"
                            stateDescription = "Current weight ${sheet.weightText} $unitSymbol"
                        }.testTag("f06-step-up"),
            )
        }
        sheet.saveError?.let { message ->
            WloBanner(
                text = message,
                tone = WloBannerTone.Warning,
                modifier =
                    Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("f06-save-error"),
            )
        }
        WloButton(
            label = if (submission == WeighInSubmissionState.SAVING) "Saving…" else "Save weigh-in",
            onClick = onSave,
            enabled = submission != WeighInSubmissionState.SAVING,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("f06-save-weighin"),
        )
        TextButton(
            onClick = onCancel,
            enabled = submission != WeighInSubmissionState.SAVING,
            modifier = Modifier.fillMaxWidth().testTag("f06-cancel-weighin"),
        ) {
            Text("Cancel")
        }
    }
}

private fun methodLabel(method: TrendMethod): String =
    when (method) {
        TrendMethod.EWMA -> "trend (default)"
        TrendMethod.ZERO_PHASE_EWMA -> "zero-phase"
        TrendMethod.MOVING_AVERAGE_7D -> "7-day avg"
    }

private const val ALPHA_MIN = 0.05f
private const val ALPHA_MAX = 0.5f

/**
 * The body-fat segment (R2, WLO-0035): the estimate series and the waist
 * tape chart over the SAME window as weight, then the calculator card —
 * tape in, estimate out, both saved to their own series.
 */
@Composable
private fun BodyFatSection(
    state: WeighInUiState,
    bodyFatViewModel: BodyFatViewModel,
    onWindowChange: (ChartWindowUi) -> Unit,
) {
    var showWaist by rememberSaveable { mutableStateOf(false) }
    WloCard(modifier = Modifier.testTag("f06-bfseries-card")) {
        WloCardHeader(title = "Body fat")
        WindowSegmentedControl(
            selected = state.bodyFatWindow,
            onSelect = onWindowChange,
            testPrefix = "f06-body-window",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            SelectChip(
                label = "Body fat %",
                selected = !showWaist,
                onClick = { showWaist = false },
                modifier = Modifier.testTag("f06-series-bodyfat"),
            )
            SelectChip(
                label = "Waist",
                selected = showWaist,
                onClick = { showWaist = true },
                modifier = Modifier.testTag("f06-series-waist"),
            )
        }
        if (showWaist) {
            if (state.waistPoints.isEmpty()) {
                Text(
                    text = "No tape yet — a saved estimate stores your measurements too.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            } else {
                WloTrendChart(
                    samples = state.waistPoints,
                    trend = emptyList(),
                    currentTrend = null,
                    formatWeight = { cm -> "${format1(cm)} cm" },
                    describe = "Waist tape series in centimetres.",
                )
            }
        } else {
            if (state.bodyFatSeries.isEmpty()) {
                Text(
                    text = "No estimates yet. Add a body measurement below with Navy tape or RFM.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            } else {
                state.bodyFatSeries.forEach { series ->
                    Text(
                        text = "${series.methodLabel} · ${series.sourceLabel}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    WloTrendChart(
                        samples = series.points,
                        trend = emptyList(),
                        currentTrend = null,
                        formatWeight = { pct -> "${format1(pct)} %" },
                        describe =
                            "${series.methodLabel} body-fat estimates in percent. " +
                                "Different methods are shown as separate series and are not directly comparable.",
                    )
                }
            }
        }
    }
    RatiosCard(
        ratios = state.ratios,
        modifier = Modifier.testTag("f06-ratios-card"),
    )
    BodyFatCalculatorCard(viewModel = bodyFatViewModel)
}

@Composable
private fun WindowSegmentedControl(
    selected: ChartWindowUi,
    onSelect: (ChartWindowUi) -> Unit,
    testPrefix: String,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ChartWindowUi.entries.forEachIndexed { index, candidate ->
            SegmentedButton(
                selected = candidate == selected,
                onClick = { onSelect(candidate) },
                shape = SegmentedButtonDefaults.itemShape(index, ChartWindowUi.entries.size),
                modifier = Modifier.testTag("$testPrefix-${candidate.name.lowercase()}"),
            ) {
                Text(candidate.label)
            }
        }
    }
}

/**
 * The ratios card (F06 §3 + §6, WLO-0043): computed, provenance-badged,
 * ranges-not-verdicts copy — and BMI strictly behind an on-request chip
 * ("shown on request only" is the spec's own rule).
 */
@Composable
private fun RatiosCard(
    ratios: RatiosUi?,
    modifier: Modifier = Modifier,
) {
    var bmiShown by rememberSaveable { mutableStateOf(false) }
    WloCard(modifier = modifier) {
        WloCardHeader(title = "Ratios")
        val waistHeight = ratios?.waistToHeight
        val waistHip = ratios?.waistToHip
        if (waistHeight == null && waistHip == null) {
            Text(
                text = "Save a tape measurement and the ratios read themselves.",
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
        }
        waistHeight?.let { ratio ->
            WloStatRow(
                label = "Waist ÷ height",
                value = ratio,
                format = ::format2,
            )
            Text(
                text = "0.40–0.53 reads healthy (Ashwell) — a range, not a verdict.",
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
        }
        waistHip?.let { ratio ->
            WloStatRow(
                label = "Waist ÷ hip",
                value = ratio,
                format = ::format2,
            )
            Text(
                text = "WHO: risk rises above 0.90 (men) / 0.85 (women).",
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
        }
        ratios?.bmi?.let { bmi ->
            if (!bmiShown) {
                WloSecondaryButton(
                    label = "Show BMI",
                    onClick = { bmiShown = true },
                    modifier = Modifier.testTag("f06-show-bmi"),
                )
            } else {
                WloStatRow(
                    label = "BMI",
                    value = bmi,
                    format = ::format1,
                    modifier = Modifier.testTag("f06-bmi"),
                )
                Text(
                    text = "WHO adult band 18.5–24.9 — shown on request only, never a verdict.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            }
        }
    }
}
