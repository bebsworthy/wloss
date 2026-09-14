package app.wlo.feature.f06.weight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloDeltaChip
import app.wlo.core.designsystem.WloHeroStat
import app.wlo.core.designsystem.WloIconAction
import app.wlo.core.designsystem.WloIcons
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSheet
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStatDivider
import app.wlo.core.designsystem.WloTrendChart
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.TrendMethod
import app.wlo.feature.f06.weight.state.ChartWindowUi
import app.wlo.feature.f06.weight.state.DeletedUi
import app.wlo.feature.f06.weight.state.LogbookRowUi
import app.wlo.feature.f06.weight.state.SheetUi
import app.wlo.feature.f06.weight.state.VerdictUi
import app.wlo.feature.f06.weight.state.WeighInEvent
import app.wlo.feature.f06.weight.state.WeighInUiState
import app.wlo.feature.f06.weight.state.WeighInViewModel

/**
 * The F06 weight surface (owner review WLO-0030): trend-first hero — one
 * "Weight" card header, the hero numeral + small unit + weekly delta, the
 * last raw reading line, a real weigh-in button — then the verbatim day log
 * (both re-weighs listed; lowest-of-day marked and explained; every entry
 * deletable with a one-tap undo — R-B8 amendment, WLO-0035), the trend
 * chart with its smoother tuner (α visible, R-A2 default 0.15; a non-default
 * selection is a labeled PREVIEW — the saved trend keeps the default), and
 * the outlier guard's one-line keep-or-delete. The weigh-in sheet opens over
 * this surface (wlo://weight/log) — the typed path is first-class, R-U15.
 */
@Composable
public fun WeightScreen(
    viewModel: WeighInViewModel,
    onOpenMath: () -> Unit,
    onOpenBodyFat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state: WeighInUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet: SheetUi? by viewModel.sheetState.collectAsStateWithLifecycle()
    val verdict: VerdictUi? by viewModel.verdictState.collectAsStateWithLifecycle()
    val deleted: DeletedUi? by viewModel.deletedState.collectAsStateWithLifecycle()
    val notice: String? by viewModel.noticeState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        WloScreenTitle(
            title = "Weight",
            modifier = Modifier.testTag("f06-title"),
        )

        verdict?.let { current ->
            // The outlier guard's one line (F06 §4): describe, offer both taps,
            // never judge. The event is already stored — this only confirms.
            Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                WloBanner(
                    text = "${current.weightLabel} is ${current.residualLabel} vs your trend — keep or correct?",
                    tone = WloBannerTone.Warning,
                    modifier = Modifier.testTag("f06-outlier-banner"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                    WloSecondaryButton(
                        label = "Keep",
                        onClick = { viewModel.onEvent(WeighInEvent.KeepFlagged) },
                        modifier = Modifier.weight(1f).testTag("f06-outlier-keep"),
                    )
                    WloButton(
                        label = "Delete",
                        onClick = { viewModel.onEvent(WeighInEvent.DeleteFlagged) },
                        modifier = Modifier.weight(1f).testTag("f06-outlier-delete"),
                    )
                }
            }
        }

        deleted?.let { current ->
            // The undo notice (R-B8 amendment): one line, one tap to put it back.
            Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                WloBanner(
                    text = "Deleted ${current.label} — the day reads without it.",
                    tone = WloBannerTone.Info,
                    modifier = Modifier.testTag("f06-deleted-banner"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                    WloButton(
                        label = "Undo",
                        onClick = { viewModel.onEvent(WeighInEvent.UndoDelete) },
                        modifier = Modifier.weight(1f).testTag("f06-undo-delete"),
                    )
                    WloSecondaryButton(
                        label = "Dismiss",
                        onClick = { viewModel.onEvent(WeighInEvent.DismissDelete) },
                        modifier = Modifier.weight(1f).testTag("f06-dismiss-delete"),
                    )
                }
            }
        }

        WloCard(modifier = Modifier.testTag("f06-hero-card")) {
            val trend = state.trend
            val current = trend?.current
            val delta7 = trend?.delta7
            WloCardHeader(
                title = "Weight",
                provenance =
                    if (current != null) {
                        {
                            // The chip opens the math sheet — real "how we got
                            // here" content, never a dead info mark.
                            ProvenanceChip(
                                value = current,
                                format = { kg -> "${format1(kg)} kg" },
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
                    format = ::format1,
                    unit = "kg",
                    delta =
                        if (delta7 != null) {
                            {
                                WloDeltaChip(
                                    value = delta7,
                                    format = { magnitude -> "${format1(magnitude)} kg / 7 d" },
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
            state.lastWeighInLabel?.let { last ->
                Text(
                    text = "Last raw reading $last",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
            WloButton(
                label = "Weigh in",
                onClick = { viewModel.onEvent(WeighInEvent.OpenSheet()) },
                modifier = Modifier.fillMaxWidth().testTag("f06-open-sheet"),
            )
        }

        state.trend?.let { trend ->
            WloCard(modifier = Modifier.testTag("f06-trend-card")) {
                WloCardHeader(title = "Trend")
                Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                    for (candidate in ChartWindowUi.entries) {
                        SelectChip(
                            label = candidate.label,
                            selected = candidate == state.window,
                            onClick = { viewModel.onEvent(WeighInEvent.WindowChange(candidate)) },
                            modifier = Modifier.testTag("f06-window-${candidate.name.lowercase()}"),
                        )
                    }
                }
                WloTrendChart(
                    samples = trend.samples,
                    trend = trend.trend,
                    currentTrend = null,
                    formatWeight = { kg -> "${format1(kg)} kg" },
                    reference = trend.reference,
                    describe = trend.description,
                )
                if (!trend.trendLineVisible) {
                    Text(
                        text = "Keep weighing — the trend forms in a few days.",
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                }
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
                WloSecondaryButton(
                    label = "How the math works",
                    onClick = onOpenMath,
                    modifier = Modifier.fillMaxWidth().testTag("f06-open-math"),
                )
            }
        }

        WloCard(modifier = Modifier.testTag("f06-logbook-card")) {
            WloCardHeader(title = "Logbook")
            if (state.logbook.isEmpty()) {
                Text(
                    text = "None yet — the morning window reads steadiest, whenever you get to it.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            }
            state.logbook.forEachIndexed { dayIndex, day ->
                if (dayIndex > 0) WloStatDivider()
                Text(
                    text = day.dayLabel,
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
                day.rows.forEach { row ->
                    LogbookRow(
                        row = row,
                        onDelete = { viewModel.onEvent(WeighInEvent.DeleteWeighIn(row.id)) },
                    )
                }
                if (day.isToday && day.rows.size > 1) {
                    Text(
                        text = WeighInUiState.LOWEST_COPY,
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                }
            }
            WloSecondaryButton(
                label = "Body-fat methods",
                onClick = onOpenBodyFat,
                modifier = Modifier.fillMaxWidth().testTag("f06-open-bodyfat"),
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

    sheet?.let { current ->
        WloSheet(
            onDismissRequest = { viewModel.onEvent(WeighInEvent.DismissSheet) },
            modifier = Modifier.testTag("f06-weighin-sheet"),
        ) {
            WeighInSheetContent(
                weightText = current.weightText,
                dayText = current.dayText,
                timeText = current.timeText,
                onChange = { viewModel.onEvent(WeighInEvent.WeightChange(it)) },
                onDayChange = { viewModel.onEvent(WeighInEvent.SheetDayChange(it)) },
                onTimeChange = { viewModel.onEvent(WeighInEvent.SheetTimeChange(it)) },
                onStepUp = { viewModel.onEvent(WeighInEvent.StepperUp) },
                onStepDown = { viewModel.onEvent(WeighInEvent.StepperDown) },
                onSave = { viewModel.onEvent(WeighInEvent.Save) },
            )
        }
    }
}

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

@Composable
private fun LogbookRow(
    row: LogbookRowUi,
    onDelete: () -> Unit,
): Unit =
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = WloSpacing.ROW_MIN),
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.timeLabel,
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.weight(1f),
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
        Text(text = row.weightLabel, style = wloType.statS)
        // The delete affordance (R-B8 amendment): every entry can go, one tap.
        WloIconAction(
            imageVector = WloIcons.Close,
            contentDescription = "delete ${row.weightLabel} at ${row.timeLabel}",
            onClick = onDelete,
            modifier = Modifier.testTag("f06-row-delete"),
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
    weightText: String,
    dayText: String,
    timeText: String,
    onChange: (String) -> Unit,
    onDayChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    onSave: () -> Unit,
) {
    Text(text = "Weigh in", style = wloType.title)
    Text(
        text = "Same conditions help the trend read true. Missed a day? Enter it below — blank time reads as noon.",
        style = wloType.caption,
        color = wloExtendedColors.textTertiary,
    )
    OutlinedTextField(
        value = weightText,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().testTag("f06-weight-field"),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = wloType.statL,
        placeholder = { Text("kg", style = wloType.body, color = wloExtendedColors.textTertiary) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
        OutlinedTextField(
            value = dayText,
            onValueChange = onDayChange,
            modifier = Modifier.weight(1f).testTag("f06-day-field"),
            singleLine = true,
            textStyle = wloType.body,
            placeholder = { Text("YYYY-MM-DD", style = wloType.caption, color = wloExtendedColors.textTertiary) },
        )
        OutlinedTextField(
            value = timeText,
            onValueChange = onTimeChange,
            modifier = Modifier.weight(1f).testTag("f06-time-field"),
            singleLine = true,
            textStyle = wloType.body,
            placeholder = { Text("HH:MM", style = wloType.caption, color = wloExtendedColors.textTertiary) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
        WloSecondaryButton(
            label = "−0.1",
            onClick = onStepDown,
            modifier = Modifier.weight(1f).testTag("f06-step-down"),
        )
        WloSecondaryButton(
            label = "+0.1",
            onClick = onStepUp,
            modifier = Modifier.weight(1f).testTag("f06-step-up"),
        )
    }
    WloButton(
        label = "Save weigh-in",
        onClick = onSave,
        modifier = Modifier.fillMaxWidth().testTag("f06-save-weighin"),
    )
}

private fun methodLabel(method: TrendMethod): String =
    when (method) {
        TrendMethod.EWMA -> "trend (default)"
        TrendMethod.ZERO_PHASE_EWMA -> "zero-phase"
        TrendMethod.MOVING_AVERAGE_7D -> "7-day avg"
    }

private const val ALPHA_MIN = 0.05f
private const val ALPHA_MAX = 0.5f
