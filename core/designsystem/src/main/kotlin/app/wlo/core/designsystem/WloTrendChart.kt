package app.wlo.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wlo.core.model.DerivedValue
import kotlinx.datetime.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

internal data class TrendAxisBounds(
    val low: Double,
    val high: Double,
)

internal data class TrendAxisTick(
    val epochDay: Long,
    val label: String,
)

private data class TrendChartPaints(
    val dot: Color,
    val line: Color,
    val grid: Color,
    val axisStyle: TextStyle,
)

internal fun trendAxisBounds(values: List<Double>): TrendAxisBounds {
    val minimum = values.minOrNull() ?: 0.0
    val maximum = values.maxOrNull() ?: minimum
    val span = maximum - minimum
    val step =
        when {
            span <= 1.0 -> 0.1
            span <= 5.0 -> 0.5
            else -> 1.0
        }
    var low = floor(minimum / step) * step
    var high = ceil(maximum / step) * step
    if (high - low < step / 2.0) {
        low -= step
        high += step
    }
    return TrendAxisBounds(roundAxis(low), roundAxis(high))
}

/** Corner labels belong to the axis only; "latest" is the owning surface's hero value. */
internal fun trendAxisLabels(
    values: List<Double>,
    format: (Double) -> String,
): List<String> {
    val bounds = trendAxisBounds(values)
    return listOf(format(bounds.high), format(bounds.low)).distinct()
}

internal fun trendXFraction(
    epochDay: Long,
    windowStartDay: Long,
    windowEndDay: Long,
): Float {
    val span = (windowEndDay - windowStartDay).coerceAtLeast(1L)
    return ((epochDay - windowStartDay).toDouble() / span).toFloat().coerceIn(0f, 1f)
}

/** Finds the nearest point in time; an exact tie resolves to the earlier day. */
internal fun closestTrendPoint(
    points: List<ChartPoint>,
    targetEpochDay: Double,
): ChartPoint? =
    points.minWithOrNull(
        compareBy<ChartPoint> { kotlin.math.abs(it.epochDay - targetEpochDay) }
            .thenBy { it.epochDay },
    )

internal fun trendAxisTicks(
    windowStartDay: Long,
    windowEndDay: Long,
    alwaysShowYear: Boolean,
): List<TrendAxisTick> {
    val span = (windowEndDay - windowStartDay).coerceAtLeast(1L)
    val count =
        when {
            span <= 45L -> 5
            span <= 120L -> 4
            span <= 400L -> 7
            else -> 6
        }
    val crossesYear =
        LocalDate.fromEpochDays(windowStartDay).year != LocalDate.fromEpochDays(windowEndDay).year
    return List(count) { index ->
        val day = windowStartDay + (span.toDouble() * index / (count - 1)).roundToLong()
        val date = LocalDate.fromEpochDays(day)
        val month = TREND_MONTHS[date.monthNumber - 1]
        val label =
            when {
                span <= 45L -> "${date.dayOfMonth} $month"
                alwaysShowYear || crossesYear -> "$month ’${date.year.toString().takeLast(2)}"
                else -> month
            }
        TrendAxisTick(day, label)
    }
}

internal fun trendWindowCaption(
    windowStartDay: Long,
    windowEndDay: Long,
    dataStartDay: Long?,
    dataEndDay: Long?,
): String {
    val start = LocalDate.fromEpochDays(windowStartDay)
    val end = LocalDate.fromEpochDays(windowEndDay)
    val window =
        if (start.year == end.year) {
            "${start.dayOfMonth} ${TREND_MONTHS[start.monthNumber - 1]} – " +
                "${end.dayOfMonth} ${TREND_MONTHS[end.monthNumber - 1]} ${end.year}"
        } else {
            "${start.dayOfMonth} ${TREND_MONTHS[start.monthNumber - 1]} ${start.year} – " +
                "${end.dayOfMonth} ${TREND_MONTHS[end.monthNumber - 1]} ${end.year}"
        }
    if (dataStartDay == null || dataEndDay == null || dataEndDay >= windowEndDay) return window
    return "$window · data ${compactDateRange(dataStartDay, dataEndDay)}"
}

/**
 * The F06 weight chart. Its x-domain is the selected window, never the data's
 * own extent, so sparse or stale data keeps its honest position in time.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UnusedParameter") // Retained for source compatibility; detail uses the typed point provenance.
public fun WloTrendChart(
    samples: List<ChartPoint>,
    trend: List<ChartPoint>,
    currentTrend: DerivedValue<Double>?,
    formatWeight: (Double) -> String,
    windowStartDay: Long = samples.firstOrNull()?.epochDay ?: trend.firstOrNull()?.epochDay ?: 0L,
    windowEndDay: Long = samples.lastOrNull()?.epochDay ?: trend.lastOrNull()?.epochDay ?: windowStartDay + 1L,
    modifier: Modifier = Modifier,
    describe: String = "Weight chart.",
    alwaysShowTickYear: Boolean = false,
    emptyMessage: String? = null,
    emptyActionLabel: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    onExplain: ((ChartPoint) -> Unit)? = null,
    onViewRawReadings: (() -> Unit)? = null,
) {
    val firstDataDay = samples.firstOrNull()?.epochDay ?: trend.firstOrNull()?.epochDay
    val lastDataDay = samples.lastOrNull()?.epochDay ?: trend.lastOrNull()?.epochDay
    val caption = trendWindowCaption(windowStartDay, windowEndDay, firstDataDay, lastDataDay)

    val selectable = (samples + trend).distinctBy { it.stableKey }.sortedBy { it.epochDay }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showData by rememberSaveable { mutableStateOf(false) }
    val selected = selectable.firstOrNull { it.stableKey == selectedKey }
    LaunchedEffect(selectable.map { it.stableKey }) {
        if (selectedKey != null && selected == null) selectedKey = null
    }

    fun selectOffset(offset: Int) {
        val current = selected?.let(selectable::indexOf) ?: if (offset > 0) -1 else selectable.size
        selectable.getOrNull(current + offset)?.let { selectedKey = it.stableKey }
    }

    Column(modifier = modifier) {
        if (samples.isEmpty() && trend.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 88.dp)
                        .semantics { contentDescription = describe }
                        .padding(vertical = WloSpacing.CARD),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = emptyMessage ?: "No weigh-ins in this window.",
                    style = wloType.body,
                    color = wloExtendedColors.textTertiary,
                )
                if (emptyActionLabel != null && onEmptyAction != null) {
                    TextButton(onClick = onEmptyAction) { Text(emptyActionLabel) }
                }
            }
        } else {
            WeightChartCanvas(
                samples = samples,
                trend = trend,
                windowStartDay = windowStartDay,
                windowEndDay = windowEndDay,
                alwaysShowTickYear = alwaysShowTickYear,
                formatWeight = formatWeight,
                describe = describe,
                selected = selected,
                onSelect = { selectedKey = it.stableKey },
                onPrevious = { selectOffset(-1) },
                onNext = { selectOffset(1) },
            )
            Text(
                text = "Measured points · Trend line",
                style = wloType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { selectOffset(-1) },
                    enabled = selected != null && selectable.indexOf(selected) > 0,
                ) {
                    Icon(WloIcons.ArrowBack, contentDescription = "Previous sample")
                }
                IconButton(
                    onClick = { selectOffset(1) },
                    enabled = selected != null && selectable.indexOf(selected) < selectable.lastIndex,
                ) {
                    Icon(WloIcons.ChevronRight, contentDescription = "Next sample")
                }
                TextButton(onClick = { showData = true }) { Text("View data") }
            }
            selected?.let { point ->
                ChartPointDetail(point, formatWeight, onExplain)
            }
        }
        Text(
            text = caption,
            style = wloType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = WloSpacing.TIGHT),
        )
    }
    if (showData) {
        ModalBottomSheet(onDismissRequest = { showData = false }) {
            Text(
                "Chart data",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = WloSpacing.CARD),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(selectable, key = { it.stableKey }) { point ->
                    ListItem(
                        headlineContent = { Text(chartPointDate(point)) },
                        supportingContent = { Text(chartPointDescription(point, formatWeight)) },
                        modifier =
                            Modifier.semantics {
                                contentDescription = chartPointDescription(point, formatWeight)
                            },
                    )
                    HorizontalDivider()
                }
            }
            TextButton(
                onClick = { showData = false },
                modifier = Modifier.padding(WloSpacing.TIGHT),
            ) { Text("Close") }
            if (onViewRawReadings != null) {
                TextButton(
                    onClick = {
                        showData = false
                        onViewRawReadings()
                    },
                    modifier = Modifier.padding(horizontal = WloSpacing.TIGHT),
                ) { Text("Raw readings") }
            }
        }
    }
}

@Composable
private fun ChartPointDetail(
    point: ChartPoint,
    formatWeight: (Double) -> String,
    onExplain: ((ChartPoint) -> Unit)?,
) {
    var explanationVisible by rememberSaveable(point.stableKey) { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Selected. ${chartPointDescription(point, formatWeight)}" }
                .padding(vertical = WloSpacing.TIGHT),
    ) {
        Text(chartPointDate(point), style = MaterialTheme.typography.titleSmall)
        Text(chartPointDescription(point, formatWeight), style = wloType.body)
        TextButton(
            onClick = {
                if (onExplain != null) onExplain(point) else explanationVisible = !explanationVisible
            },
        ) { Text("Explain") }
        if (explanationVisible) {
            Text(
                point.provenance ?: "This point is shown from ${point.sourceEventIds.size} contributing source(s).",
                style = wloType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun chartPointDate(point: ChartPoint): String {
    val date = LocalDate.fromEpochDays(point.epochDay)
    return "${date.dayOfMonth} ${TREND_MONTHS[date.monthNumber - 1]} ${date.year}"
}

private fun chartPointDescription(
    point: ChartPoint,
    formatWeight: (Double) -> String,
): String {
    val value = if (point.displayUnit.isBlank()) formatWeight(point.value) else "${point.value} ${point.displayUnit}"
    val role =
        point.methodLabel ?: point.role.name
            .lowercase()
            .replace('_', ' ')
    val status = point.holdReason?.let { "Held: $it" } ?: point.provenance ?: "Available"
    val sources = point.sourceEventIds.size.let { "$it source${if (it == 1) "" else "s"}" }
    return "$value · $role · $status · $sources"
}

@Composable
private fun WeightChartCanvas(
    samples: List<ChartPoint>,
    trend: List<ChartPoint>,
    windowStartDay: Long,
    windowEndDay: Long,
    alwaysShowTickYear: Boolean,
    formatWeight: (Double) -> String,
    describe: String,
    selected: ChartPoint?,
    onSelect: (ChartPoint) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisStyle =
        TextStyle(
            fontFamily = WloFontFamily,
            fontSize = 11.sp,
            fontFeatureSettings = WloFontFeatures.TABULAR,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    val ticks = trendAxisTicks(windowStartDay, windowEndDay, alwaysShowTickYear)
    val paints =
        TrendChartPaints(
            dot = MaterialTheme.colorScheme.onSurfaceVariant,
            line = MaterialTheme.colorScheme.primary,
            grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            axisStyle = axisStyle,
        )

    val selectable = (samples + trend).distinctBy { it.stableKey }.sortedBy { it.epochDay }
    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(selectable, windowStartDay, windowEndDay) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val day = windowStartDay + fraction * (windowEndDay - windowStartDay)
                        closestTrendPoint(selectable, day.toDouble())?.let(onSelect)
                    }
                }.onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onPrevious()
                            true
                        }
                        Key.DirectionRight -> {
                            onNext()
                            true
                        }
                        else -> false
                    }
                }.focusable()
                .semantics {
                    contentDescription =
                        "$describe ${selectable.size} available data points. " +
                        "Use previous and next controls or View data."
                },
    ) {
        drawWeightChart(
            samples = samples,
            trend = trend,
            windowStartDay = windowStartDay,
            windowEndDay = windowEndDay,
            ticks = ticks,
            paints = paints,
            textMeasurer = textMeasurer,
            formatWeight = formatWeight,
            selected = selected,
        )
    }
}

private fun DrawScope.drawWeightChart(
    samples: List<ChartPoint>,
    trend: List<ChartPoint>,
    windowStartDay: Long,
    windowEndDay: Long,
    ticks: List<TrendAxisTick>,
    paints: TrendChartPaints,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    formatWeight: (Double) -> String,
    selected: ChartPoint?,
) {
    val left = 2.dp.toPx()
    val right = size.width - 2.dp.toPx()
    val top = 24.dp.toPx()
    val bottom = size.height - 42.dp.toPx()
    val bounds = trendAxisBounds(samples.map { it.value } + trend.map { it.value })
    val axisLabels = trendAxisLabels(samples.map { it.value } + trend.map { it.value }, formatWeight)

    fun x(epochDay: Long): Float = left + trendXFraction(epochDay, windowStartDay, windowEndDay) * (right - left)

    fun y(value: Double): Float = trendYCoordinate(value, bounds, top, bottom)

    drawLine(paints.grid, Offset(left, y(bounds.high)), Offset(right, y(bounds.high)), strokeWidth = 1.dp.toPx())
    drawLine(paints.grid, Offset(left, y(bounds.low)), Offset(right, y(bounds.low)), strokeWidth = 1.dp.toPx())

    samples.forEach { sample ->
        drawCircle(paints.dot, 3.dp.toPx(), Offset(x(sample.epochDay), y(sample.value)))
    }
    if (trend.size > 1) drawSeries(trend, ::x, ::y, paints.line, 2.5f)
    selected?.let { point ->
        drawCircle(
            color = paints.line,
            radius = 7.dp.toPx(),
            center = Offset(x(point.epochDay), y(point.value)),
            style = Stroke(width = 2.dp.toPx()),
        )
    }

    drawText(textMeasurer, axisLabels.first(), Offset(left, 2.dp.toPx()), paints.axisStyle)
    axisLabels.getOrNull(1)?.let { lowLabel ->
        drawText(textMeasurer, lowLabel, Offset(left, bottom + 3.dp.toPx()), paints.axisStyle)
    }

    var lastTickRight = Float.NEGATIVE_INFINITY
    ticks.forEach { tick ->
        val layout = textMeasurer.measure(tick.label, paints.axisStyle)
        val desiredLeft = x(tick.epochDay) - layout.size.width / 2f
        val tickLeft = desiredLeft.coerceIn(left, right - layout.size.width)
        if (tickLeft >= lastTickRight + 4.dp.toPx()) {
            drawText(layout, topLeft = Offset(tickLeft, bottom + 20.dp.toPx()))
            lastTickRight = tickLeft + layout.size.width
        }
    }
}

private fun trendYCoordinate(
    value: Double,
    bounds: TrendAxisBounds,
    top: Float,
    bottom: Float,
): Float {
    val fraction = ((value - bounds.low) / (bounds.high - bounds.low)).toFloat()
    return bottom - fraction * (bottom - top)
}

private fun DrawScope.drawSeries(
    points: List<ChartPoint>,
    x: (Long) -> Float,
    y: (Double) -> Float,
    color: Color,
    width: Float,
) {
    val path = Path()
    points.forEachIndexed { index, point ->
        val offset = Offset(x(point.epochDay), y(point.value))
        if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
    }
    drawPath(path, color, style = Stroke(width = width))
}

private fun compactDateRange(
    firstDay: Long,
    lastDay: Long,
): String {
    val first = LocalDate.fromEpochDays(firstDay)
    val last = LocalDate.fromEpochDays(lastDay)
    return if (firstDay == lastDay) {
        "${first.dayOfMonth} ${TREND_MONTHS[first.monthNumber - 1]}"
    } else if (first.monthNumber == last.monthNumber && first.year == last.year) {
        "${first.dayOfMonth}–${last.dayOfMonth} ${TREND_MONTHS[last.monthNumber - 1]}"
    } else {
        "${first.dayOfMonth} ${TREND_MONTHS[first.monthNumber - 1]}–" +
            "${last.dayOfMonth} ${TREND_MONTHS[last.monthNumber - 1]}"
    }
}

private fun roundAxis(value: Double): Double = (value * 10.0).roundToLong() / 10.0

private val TREND_MONTHS: List<String> =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
