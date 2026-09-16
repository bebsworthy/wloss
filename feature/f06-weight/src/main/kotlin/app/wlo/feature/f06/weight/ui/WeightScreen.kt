package app.wlo.feature.f06.weight.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import app.wlo.core.designsystem.WloStatRow
import app.wlo.core.designsystem.WloTrendChart
import app.wlo.core.designsystem.WloWeightChart
import app.wlo.core.designsystem.rememberWloHaptics
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.TrendMethod
import app.wlo.feature.f06.weight.state.BodyFatUiState
import app.wlo.feature.f06.weight.state.BodyFatViewModel
import app.wlo.feature.f06.weight.state.BodySectionUi
import app.wlo.feature.f06.weight.state.ChartWindowUi
import app.wlo.feature.f06.weight.state.HistoryRange
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
 * WLO-0104 page-based weight overview: latest trend, selected-period change,
 * goal and raw-event chart. Settings owns smoothing/math and body-metric
 * access; history opens the event log. The reserved bottom action opens the
 * existing weigh-in sheet without obscuring scroll content.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod") // One ordered semantic dashboard; extracted cards own each section's detail.
public fun WeightScreen(
    viewModel: WeighInViewModel,
    bodyFatViewModel: BodyFatViewModel,
    onOpenMath: () -> Unit,
    onOpenLogbook: (HistoryRange?) -> Unit,
    onEditGoal: () -> Unit,
    onOpenMeasurements: () -> Unit = {},
    modifier: Modifier = Modifier,
    showTopBar: Boolean = false,
    initialGoalOpen: Boolean = false,
    onGoalClosed: () -> Unit = {},
) {
    val goalDraft by viewModel.goalTargetEditor.uiState.collectAsStateWithLifecycle()
    var openedGoal by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialGoalOpen) {
        if (initialGoalOpen) viewModel.openGoal()
    }
    LaunchedEffect(goalDraft) {
        if (goalDraft != null) {
            openedGoal = true
        } else if (openedGoal && initialGoalOpen) {
            onGoalClosed()
        }
    }
    var showChartSettings by rememberSaveable { mutableStateOf(false) }
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
        val useSupportingPane = maxWidth >= 840.dp
        val openSheet = { viewModel.onEvent(WeighInEvent.OpenSheet()) }
        Scaffold(
            topBar = {
                if (showTopBar) {
                    TopAppBar(
                        title = { Text("Weight") },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        actions = {
                            IconButton(
                                onClick = { showChartSettings = true },
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Icon(WloIcons.Tune, contentDescription = "Chart settings")
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (state.section == BodySectionUi.WEIGHT && sheet == null) {
                    Button(
                        onClick = openSheet,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                                .height(52.dp)
                                .testTag("f06-open-sheet"),
                    ) {
                        Text("+", style = WeightOverviewTypography.action)
                        Spacer(Modifier.width(8.dp))
                        Text("Weigh in", style = WeightOverviewTypography.action)
                    }
                }
            },
        ) { scaffoldPadding ->
            Row(modifier = Modifier.fillMaxWidth().padding(scaffoldPadding)) {
                Column(
                    modifier =
                        Modifier
                            .weight(if (useSupportingPane) 0.64f else 1f)
                            .verticalScroll(scrollState)
                            .padding(horizontal = if (state.section == BodySectionUi.WEIGHT) 8.dp else 24.dp)
                            .padding(top = 15.dp)
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
                    if (state.section == BodySectionUi.BODY_FAT) {
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
                            WeightOverview(
                                showSettings = showChartSettings,
                                onSettingsChange = { showChartSettings = it },
                                showInlineSettings = !showTopBar,
                                state = state,
                                viewModel = viewModel,
                                onOpenMath = onOpenMath,
                                onOpenLogbook = { onOpenLogbook(null) },
                            )
                            app.wlo.core.designsystem.WloListRow(
                                label = "Body measurements",
                                secondary = "Body fat, waist, hips and more",
                                chevron = true,
                                onClick = onOpenMeasurements,
                            )

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

    goalDraft?.let { draft ->
        GoalTargetSheet(
            draft = draft,
            currentKg = state.goalProgress.currentTrend?.value,
            onEdit = viewModel.goalTargetEditor::edit,
            onDismiss = viewModel.goalTargetEditor::dismiss,
            onSave = viewModel::saveGoal,
        )
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
                onBodyFatChange = { viewModel.onEvent(WeighInEvent.BodyFatChange(it)) },
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
private fun WeightOverview(
    showSettings: Boolean,
    onSettingsChange: (Boolean) -> Unit,
    showInlineSettings: Boolean,
    state: WeighInUiState,
    viewModel: WeighInViewModel,
    onOpenMath: () -> Unit,
    onOpenLogbook: () -> Unit,
) {
    val trend = state.trend ?: return
    val first = trend.trend.firstOrNull()
    val last = trend.trend.lastOrNull()
    val change =
        if (first != null && last != null && first.epochDay != last.epochDay) last.value - first.value else null
    val reset = {
        viewModel.onEvent(WeighInEvent.MethodChange(TrendMethod.EWMA))
        viewModel.onEvent(WeighInEvent.AlphaChange(ConstantsRegistry.EWMA_ALPHA_DEFAULT))
    }
    Column(modifier = Modifier.testTag("f06-weight-overview")) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Weight trend",
                    style = WeightOverviewTypography.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (showInlineSettings) {
                    IconButton(onClick = { onSettingsChange(true) }) {
                        Icon(WloIcons.Tune, contentDescription = "Chart settings")
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            trend.current?.let { current ->
                WloHeroStat(
                    value = current,
                    format = state.massUnit::formatNumber,
                    unit = state.massUnit.symbol,
                    valueStyle = WeightOverviewTypography.hero,
                    unitStyle = WeightOverviewTypography.unit,
                    provenance = {},
                    modifier = Modifier.testTag("f06-trend-stat"),
                )
                Spacer(Modifier.height(5.dp))
                trend.currentDay?.let {
                    val prefix = if (it == trend.windowEndDay) "Today " else ""
                    Text(
                        prefix + overviewDate(it, fullMonth = true),
                        style = WeightOverviewTypography.date,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(23.dp))
            if (trend.preview) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Preview", color = wloExtendedColors.chartGoal)
                    TextButton(onClick = reset, modifier = Modifier.testTag("f06-reset-preview")) { Text("Reset") }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    change?.let { delta ->
                        Text(
                            (if (delta > 0) "+" else "") + state.massUnit.format(delta).replace('-', '−'),
                            style = WeightOverviewTypography.change,
                            modifier = Modifier.testTag("f06-period-change"),
                        )
                        Text(
                            "Change · ${overviewDate(checkNotNull(first).epochDay)} – " +
                                overviewDate(checkNotNull(last).epochDay),
                            style = WeightOverviewTypography.supporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(
                    onClick = viewModel::openGoal,
                    contentPadding = PaddingValues(0.dp),
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    modifier = Modifier.weight(1f).testTag("f06-goal-summary"),
                ) {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                        val target = state.goalProgress.targetWeightKg
                        Text(
                            target?.let { "${state.massUnit.format(it)} goal" } ?: "Set a goal",
                            style = WeightOverviewTypography.goal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            color = wloExtendedColors.chartGoal,
                        )
                        if (target != null) {
                            trend.current?.let { current ->
                                Text(
                                    "${state.massUnit.format(kotlin.math.abs(target - current.value))} to goal",
                                    style = WeightOverviewTypography.supporting,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(17.dp))
            WindowSegmentedControl(
                selected = state.window,
                onSelect = { viewModel.onEvent(WeighInEvent.WindowChange(it)) },
                testPrefix = "f06-window",
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "${overviewDate(trend.windowStartDay)} – ${overviewDate(trend.windowEndDay)}" +
                    if (first != null && first.epochDay > trend.windowStartDay) {
                        " · readings from ${overviewDate(first.epochDay)}"
                    } else {
                        ""
                    },
                style = WeightOverviewTypography.supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WloWeightChart(
                samples = trend.rawSamples,
                trend = trend.trend,
                goalKg = state.goalProgress.targetWeightKg,
                startDay = trend.windowStartDay,
                endDay = trend.windowEndDay,
                formatWeight = state.massUnit::format,
                formatAxis = state.massUnit::formatNumber,
            )
            trend.stateCopy?.let {
                Text(
                    it,
                    style = WeightOverviewTypography.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trend.emptyActionWindow?.let { target ->
                TextButton(onClick = { viewModel.onEvent(WeighInEvent.WindowChange(target)) }) {
                    Text("Show ${target.label}")
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        // Full-width standard ListItem aligns its built-in 16dp inset with the page's 24dp gutter.
        ListItem(
            headlineContent = { Text("Weigh-in history", style = WeightOverviewTypography.history) },
            supportingContent = {
                state.lastWeighInLabel?.let {
                    Text(
                        "Last reading · $it",
                        style = WeightOverviewTypography.supporting,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            },
            trailingContent = { Icon(WloIcons.ChevronRight, contentDescription = null) },
            colors =
                androidx.compose.material3.ListItemDefaults
                    .colors(containerColor = MaterialTheme.colorScheme.background),
            modifier =
                Modifier
                    .padding(top = 4.dp)
                    .testTag("f06-open-logbook")
                    .clickable(onClickLabel = "Weigh-in history", onClick = onOpenLogbook),
        )
    }
    if (showSettings) {
        WloSheet(onDismissRequest = { onSettingsChange(false) }, title = "Chart settings") {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                SmootherTuner(
                    method = state.method,
                    alpha = state.alpha,
                    onMethod = { viewModel.onEvent(WeighInEvent.MethodChange(it)) },
                    onAlpha = { viewModel.onEvent(WeighInEvent.AlphaChange(it)) },
                )
                Text(
                    "Alternative settings preview the chart. Your saved trend stays unchanged.",
                    style = wloType.caption,
                )
                TextButton(onClick = reset) { Text("Restore defaults") }
                TextButton(onClick = {
                    onSettingsChange(false)
                    onOpenMath()
                }) { Text("How the trend is calculated") }
                WloButton(label = "Done", onClick = { onSettingsChange(false) }, modifier = Modifier.fillMaxWidth())
            }
        }
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

/** Advanced preview controls, shown only inside chart settings (WLO-0104, R-A2). */
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
        for (candidate in TrendMethod.entries) {
            ListItem(
                modifier =
                    Modifier
                        .testTag("f06-method-${candidate.wireName}")
                        .selectable(
                            selected = candidate == method,
                            role = Role.RadioButton,
                            onClick = { onMethod(candidate) },
                        ),
                headlineContent = { Text(methodLabel(candidate)) },
                supportingContent = {
                    Text(
                        when (candidate) {
                            TrendMethod.EWMA -> "Smooths daily fluctuations using earlier readings."
                            TrendMethod.ZERO_PHASE_EWMA -> "Uses later readings; past values can change."
                            TrendMethod.MOVING_AVERAGE_7D -> "Available readings within seven calendar days."
                        },
                    )
                },
                leadingContent = {
                    RadioButton(
                        selected = candidate == method,
                        onClick = null,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    methodLabel(candidate)
                            },
                    )
                },
            )
        }
        if (method != TrendMethod.MOVING_AVERAGE_7D) {
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("f06-alpha-slider")
                        .semantics { contentDescription = "Trend responsiveness" },
            )
        }
    }

@Composable
private fun WeighInSheetContent(
    sheet: SheetUi,
    unitSymbol: String,
    submission: WeighInSubmissionState,
    onChange: (String) -> Unit,
    onBodyFatChange: (String) -> Unit,
    onDayChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var showBodyFat by rememberSaveable { mutableStateOf(sheet.bodyFatText.isNotBlank()) }
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
        if (sheet.intent == app.wlo.feature.f06.weight.state.WeighInEditIntent.NewReading) {
            if (showBodyFat) {
                OutlinedTextField(
                    value = sheet.bodyFatText,
                    onValueChange = onBodyFatChange,
                    label = { Text("Body fat from scale (%, optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = submission != WeighInSubmissionState.SAVING,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { showBodyFat = true }) { Text("Add body fat") }
            }
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
                icon = {},
                onClick = { onSelect(candidate) },
                shape = SegmentedButtonDefaults.itemShape(index, ChartWindowUi.entries.size),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                modifier = Modifier.height(44.dp).testTag("$testPrefix-${candidate.name.lowercase()}"),
            ) {
                Text(
                    candidate.label.replace("all", "All"),
                    style =
                        WeightOverviewTypography.control.copy(
                            fontWeight =
                                if (candidate == selected) {
                                    androidx.compose.ui.text.font.FontWeight.SemiBold
                                } else {
                                    androidx.compose.ui.text.font.FontWeight.Normal
                                },
                        ),
                )
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

/** Day-first labels keep the date readable and consistent with the overview reference. */
private fun overviewDate(
    epochDay: Long,
    fullMonth: Boolean = false,
): String =
    java.time.LocalDate.ofEpochDay(epochDay).format(
        java.time.format.DateTimeFormatter.ofPattern(
            if (fullMonth) "d MMMM" else "d MMM",
            java.util.Locale.getDefault(),
        ),
    )
