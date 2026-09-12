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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloDeltaChip
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStat
import app.wlo.core.designsystem.WloStatDivider
import app.wlo.core.designsystem.WloTrendChart
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.TrendMethod
import app.wlo.feature.f06.weight.state.SheetUi
import app.wlo.feature.f06.weight.state.VerdictUi
import app.wlo.feature.f06.weight.state.WeighInEvent
import app.wlo.feature.f06.weight.state.WeighInRowUi
import app.wlo.feature.f06.weight.state.WeighInUiState
import app.wlo.feature.f06.weight.state.WeighInViewModel

/**
 * The F06 weight surface: trend-first hero (the raw number stays small
 * beneath), the verbatim day log (both re-weighs listed; lowest-of-day marked
 * and explained), the trend chart with its smoother tuner (α visible,
 * R-A2 default 0.15), and the outlier guard's one-line keep-or-correct. The
 * weigh-in sheet opens over this surface (wlo://weight/log) — the typed path
 * is first-class, R-U15.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        Text(
            text = "Weight",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("f06-title"),
        )

        verdict?.let { current ->
            // The outlier guard's one line (F06 §4): describe, offer both taps,
            // never judge. The event is already stored — this only confirms.
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("f06-outlier-banner"),
                shape = WloShape.Chip,
                color = wloExtendedColors.held.copy(alpha = 0.14f),
                contentColor = wloExtendedColors.held,
                border = androidx.compose.foundation.BorderStroke(1.dp, wloExtendedColors.held.copy(alpha = 0.45f)),
            ) {
                Column(
                    Modifier.padding(WloSpacing.CARD),
                    verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                ) {
                    Text(
                        text = "${current.weightLabel} kg is ${current.residualLabel} vs your trend — keep or correct?",
                        style = wloType.body,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                        PrimaryRow(label = "keep", modifier = Modifier.weight(1f).testTag("f06-outlier-keep")) {
                            viewModel.onEvent(WeighInEvent.KeepFlagged)
                        }
                        PrimaryRow(label = "correct", modifier = Modifier.weight(1f).testTag("f06-outlier-correct")) {
                            viewModel.onEvent(WeighInEvent.CorrectFlagged)
                        }
                    }
                }
            }
        }

        WloCard(modifier = Modifier.testTag("f06-hero-card")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "trend",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                state.trend?.delta7?.let { delta ->
                    WloDeltaChip(
                        value = delta,
                        format = { "${format1(it)} kg / 7 d" },
                        style = wloType.statM,
                        context = "trend delta",
                    )
                }
            }
            state.trend?.current?.let { current ->
                WloStat(
                    label = "trend now",
                    value = current,
                    format = { "${format1(it)} kg" },
                    modifier = Modifier.testTag("f06-trend-stat"),
                )
            }
            state.lastWeighInLabel?.let { last ->
                Text(
                    text = "last raw reading $last",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
            PrimaryRow(
                label = "weigh in",
                modifier = Modifier.fillMaxWidth().testTag("f06-open-sheet"),
            ) { viewModel.onEvent(WeighInEvent.OpenSheet()) }
        }

        state.trend?.let { trend ->
            WloCard(modifier = Modifier.testTag("f06-trend-card")) {
                Text(text = "90 days", style = wloType.title)
                WloTrendChart(
                    samples = trend.samples,
                    trend = trend.trend,
                    currentTrend = trend.current,
                    formatWeight = { "${format1(it)} kg" },
                    reference = trend.reference,
                    describe = trend.description,
                )
                if (!trend.trendLineVisible) {
                    Text(
                        text = "keep weighing — the trend forms in a few days",
                        style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                        color = wloExtendedColors.textTertiary,
                    )
                }
                SmootherTuner(
                    method = state.method,
                    alpha = state.alpha,
                    onMethod = { viewModel.onEvent(WeighInEvent.MethodChange(it)) },
                    onAlpha = { viewModel.onEvent(WeighInEvent.AlphaChange(it)) },
                )
                PrimaryRow(label = "how the math works", modifier = Modifier.fillMaxWidth().testTag("f06-open-math")) {
                    onOpenMath()
                }
            }
        }

        WloCard(modifier = Modifier.testTag("f06-day-card")) {
            Text(text = "today's weigh-ins", style = wloType.title)
            if (state.rows.isEmpty()) {
                Text(
                    text = "none yet — the morning window reads steadiest, whenever you get to it",
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = wloExtendedColors.textTertiary,
                )
            }
            state.rows.forEachIndexed { index, row ->
                if (index > 0) WloStatDivider()
                DayRow(row)
            }
            if (state.rows.size > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                Text(
                    text = WeighInUiState.LOWEST_COPY,
                    style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                    color = wloExtendedColors.textTertiary,
                )
            }
            PrimaryRow(label = "body fat methods", modifier = Modifier.fillMaxWidth().testTag("f06-open-bodyfat")) {
                onOpenBodyFat()
            }
        }

        notice?.let {
            Text(
                text = it,
                style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                color = wloExtendedColors.held,
            )
        }
    }

    sheet?.let { current ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(WeighInEvent.DismissSheet) },
            shape = WloShape.SheetTop,
            modifier = Modifier.testTag("f06-weighin-sheet"),
        ) {
            WeighInSheetContent(
                weightText = current.weightText,
                onChange = { viewModel.onEvent(WeighInEvent.WeightChange(it)) },
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
            text = "smoother",
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
            text = "responsiveness α ${format2(alpha)}",
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
        Text(
            text = "lower reads calmer, higher reads faster — the formulas live behind “how the math works”",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = wloExtendedColors.textTertiary,
        )
    }

@Composable
private fun DayRow(row: WeighInRowUi): Unit =
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
    }

@Composable
private fun WeighInSheetContent(
    weightText: String,
    onChange: (String) -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    onSave: () -> Unit,
): Unit =
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(text = "Weigh in", style = wloType.title)
        Text(
            text = "same conditions help the trend read true — never demanded",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
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
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
            PrimaryRow(label = "− 0.1", modifier = Modifier.weight(1f).testTag("f06-step-down")) { onStepDown() }
            PrimaryRow(label = "+ 0.1", modifier = Modifier.weight(1f).testTag("f06-step-up")) { onStepUp() }
        }
        PrimaryRow(label = "save weigh-in", modifier = Modifier.fillMaxWidth().testTag("f06-save-weighin")) { onSave() }
    }

private fun methodLabel(method: TrendMethod): String =
    when (method) {
        TrendMethod.EWMA -> "trend (default)"
        TrendMethod.ZERO_PHASE_EWMA -> "zero-phase"
        TrendMethod.MOVING_AVERAGE_7D -> "7-day avg"
    }

/** A row-level button (48 dp floor — daily-use target, §3). */
@Composable
internal fun PrimaryRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
): Unit =
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = WloSpacing.TOUCH_PRIMARY),
        shape = WloShape.Chip,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = label,
            style = wloType.title.copy(fontSize = 15.sp),
            modifier = Modifier.padding(WloSpacing.CARD),
        )
    }

private const val ALPHA_MIN = 0.05f
private const val ALPHA_MAX = 0.5f
