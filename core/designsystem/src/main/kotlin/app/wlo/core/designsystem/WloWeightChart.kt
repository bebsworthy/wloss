package app.wlo.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sqrt

/** Same linear scale for recorded values, trend and goal; include padding for endpoint labels. */
internal fun weightChartBounds(
    values: List<Double>,
    goal: Double?,
): TrendAxisBounds {
    val bounds = trendAxisBounds(values + listOfNotNull(goal))
    val padding = ((bounds.high - bounds.low) * 0.08).coerceAtLeast(0.15)
    return TrendAxisBounds(bounds.low - kotlin.math.ceil(padding * 2) / 2, kotlin.math.ceil(bounds.high + padding))
}

/** Calendar months for a quarter; three readable ticks for a short window. */
internal fun weightAxisTicks(
    start: Long,
    end: Long,
): List<TrendAxisTick> {
    val span = end - start
    if (span > 120) return trendAxisTicks(start, end, true)
    val days =
        if (span <= 45) {
            listOf(start, start + span / 2, end).distinct()
        } else {
            val first =
                java.time.LocalDate
                    .ofEpochDay(start)
                    .withDayOfMonth(1)
            generateSequence(if (first.toEpochDay() < start) first.plusMonths(1) else first) { it.plusMonths(1) }
                .takeWhile { it.toEpochDay() <= end }
                .map { it.toEpochDay() }
                .toList()
        }
    val formatter =
        java.time.format.DateTimeFormatter
            .ofPattern("d MMM", java.util.Locale.getDefault())
    return days.map {
        TrendAxisTick(
            it,
            java.time.LocalDate
                .ofEpochDay(it)
                .format(formatter),
        )
    }
}

private fun weightTickLabelOffset(
    index: Int,
    last: Int,
    width: Int,
): Float =
    when (index) {
        0 -> 0f
        last -> width.toFloat()
        else -> width / 2f
    }

/** Select the actual painted point, not merely the nearest day in another series. */
internal fun nearestWeightPoint(
    positions: List<Offset>,
    tap: Offset,
    radius: Float,
): Int? =
    positions.indices
        .minByOrNull { (positions[it] - tap).getDistanceSquared() }
        ?.takeIf { sqrt((positions[it] - tap).getDistanceSquared()) <= radius }

private fun handleChartKey(
    key: Key,
    points: List<ChartPoint>,
    move: (Int) -> Boolean,
    select: (String?) -> Unit,
): Boolean =
    when (key) {
        Key.DirectionLeft -> move(-1)
        Key.DirectionRight -> move(1)
        Key.MoveHome -> {
            select(points.firstOrNull()?.stableKey)
            true
        }
        Key.MoveEnd -> {
            select(points.lastOrNull()?.stableKey)
            true
        }
        Key.Escape -> {
            select(null)
            true
        }
        else -> false
    }

private fun weightPointDescription(
    point: ChartPoint,
    formatWeight: (Double) -> String,
): String {
    val role = if (point.role == ChartSeriesRole.RAW) "Recorded weight" else "Trend weight"
    return "$role, ${weightPointDate(point.epochDay)}, ${formatWeight(point.value)}"
}

private fun weightPointDate(day: Long): String =
    java.time.LocalDate.ofEpochDay(day).format(
        java.time.format.DateTimeFormatter
            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM),
    )

private fun nextWeightPointKey(
    points: List<ChartPoint>,
    selected: ChartPoint?,
    offset: Int,
): String {
    val index = selected?.let(points::indexOf) ?: if (offset > 0) -1 else points.size
    return points[(index + offset).coerceIn(points.indices)].stableKey
}

/**
 * WLO-0104: Material 3 has no time-series chart or data-point inspection component.
 * This custom plot composes M3 Surface/Text for its tooltip and supplies equivalent
 * keyboard/accessibility point actions. The page owns M3 range/settings controls.
 * Raw events retain identity (including multiple readings on one day); the goal
 * shares their linear scale and can never be cropped out of the plot.
 */
@Composable
public fun WloWeightChart(
    samples: List<ChartPoint>,
    trend: List<ChartPoint>,
    goalKg: Double?,
    startDay: Long,
    endDay: Long,
    formatWeight: (Double) -> String,
    modifier: Modifier = Modifier,
    formatAxis: (Double) -> String = formatWeight,
) {
    val points =
        remember(samples, trend) {
            (samples + trend).sortedWith(
                compareBy<ChartPoint> { it.epochDay }.thenBy {
                    it.captureTimeEpochMs
                        ?: Long.MAX_VALUE
                },
            )
        }
    var selectedKey by rememberSaveable(startDay, endDay) { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    val selected = points.firstOrNull { it.stableKey == selectedKey }
    val bounds = weightChartBounds(points.map { it.value }, goalKg)
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val goalColor = wloExtendedColors.chartGoal
    val textStyle =
        MaterialTheme.typography.labelSmall.copy(
            color = secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
        )
    val textMeasurer = rememberTextMeasurer()
    val pointDescription = selected?.let { weightPointDescription(it, formatWeight) }

    fun move(offset: Int): Boolean {
        if (points.isEmpty()) return false
        selectedKey = nextWeightPointKey(points, selected, offset)
        return true
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // WLO-0114: reference SVG is 360×285 inside a chart 8dp wider than its 364dp content.
        val height = maxWidth * (285f / 360f * 372f / 364f) * density.fontScale.coerceAtLeast(1f)
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { height.toPx() }
        val left = with(density) { 4.dp.toPx() }
        val labelWidth =
            listOf(bounds.low, bounds.high, goalKg ?: bounds.low)
                .maxOf { textMeasurer.measure(formatAxis(it).replace(Regex("[.,]0$"), ""), textStyle).size.width }
        val axisGap = with(density) { 12.dp.toPx() }
        val right = minOf(widthPx * 0.875f, widthPx - labelWidth - axisGap).coerceAtLeast(left + 1f)
        val top = heightPx * 22f / 285f
        val bottom = heightPx * 232f / 285f

        fun position(point: ChartPoint): Offset =
            Offset(
                left + trendXFraction(point.epochDay, startDay, endDay) * (right - left),
                bottom - ((point.value - bounds.low) / (bounds.high - bounds.low)).toFloat() * (bottom - top),
            )
        val positions = points.map(::position)
        val hitRadius = with(density) { 24.dp.toPx() }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .testTag("f06-weight-chart")
                .border(
                    if (focused) 1.dp else 0.dp,
                    if (focused) primary else androidx.compose.ui.graphics.Color.Transparent,
                ).pointerInput(points, bounds, startDay, endDay, widthPx) {
                    detectTapGestures { tap ->
                        selectedKey = nearestWeightPoint(positions, tap, hitRadius)?.let { points[it].stableKey }
                    }
                }.pointerInput(points, bounds, startDay, endDay, widthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { tap ->
                            selectedKey = nearestWeightPoint(positions, tap, hitRadius)?.let { points[it].stableKey }
                        },
                    ) { change, _ ->
                        val role = points.firstOrNull { it.stableKey == selectedKey }?.role
                        if (role != null) {
                            selectedKey =
                                points.indices
                                    .filter { points[it].role == role }
                                    .minByOrNull { abs(positions[it].x - change.position.x) }
                                    ?.let { points[it].stableKey }
                            change.consume()
                        }
                    }
                }.onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    handleChartKey(event.key, points, ::move) { selectedKey = it }
                }.onFocusChanged { focused = it.isFocused }
                .focusable()
                .semantics {
                    contentDescription =
                        "Weight chart. ${samples.size} recorded weights and ${trend.size} trend values." +
                        (goalKg?.let { " Goal ${formatWeight(it)}." } ?: "")
                    stateDescription = pointDescription ?: "No point selected"
                    liveRegion = LiveRegionMode.Polite
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Next point") { move(1) },
                            CustomAccessibilityAction("Previous point") { move(-1) },
                            CustomAccessibilityAction("Clear selection") {
                                selectedKey = null
                                true
                            },
                        )
                },
        ) {
            val tickStep = if (bounds.high - bounds.low > 4.0) 2.0 else 0.5
            val axisValues =
                generateSequence(bounds.high) { it - tickStep }
                    .takeWhile { it >= bounds.low }
                    .toList()
            axisValues.forEach { value ->
                val y = position(ChartPoint(startDay, value)).y
                val goalY = goalKg?.let { position(ChartPoint(startDay, it)).y }
                if (goalY != null && abs(y - goalY) < 24.dp.toPx() * density.fontScale) return@forEach
                drawLine(grid, Offset(left, y), Offset(right, y))
                val label = textMeasurer.measure(formatAxis(value).replace(Regex("[.,]0$"), ""), textStyle)
                drawText(label, topLeft = Offset(right + axisGap, y - label.size.height / 2f))
            }
            goalKg?.let { goal ->
                val y = position(ChartPoint(startDay, goal)).y
                drawLine(
                    goalColor,
                    Offset(left, y),
                    Offset(right, y),
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
                val goalLabel = formatWeight(goal).replace(".0 ", " ").replace(",0 ", " ")
                val label =
                    textMeasurer.measure(
                        "Goal $goalLabel",
                        textStyle.copy(color = goalColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                    )
                drawText(
                    label,
                    topLeft =
                        Offset(
                            left,
                            (y - label.size.height - 4.dp.toPx()).coerceAtLeast(0f),
                        ),
                )
                val axisLabel =
                    textMeasurer.measure(
                        formatAxis(goal).replace(Regex("[.,]0$"), ""),
                        textStyle.copy(color = goalColor, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                    )
                drawText(axisLabel, topLeft = Offset(right + axisGap, y - axisLabel.size.height / 2f))
            }
            samples.forEach { drawCircle(secondary, 3.dp.toPx(), position(it)) }
            if (trend.size > 1) {
                val path = Path()
                trend.forEachIndexed { index, point ->
                    val offset = position(point)
                    if (index == 0 || point.epochDay - trend[index - 1].epochDay > 7) {
                        path.moveTo(offset.x, offset.y)
                    } else {
                        path.lineTo(offset.x, offset.y)
                    }
                }
                drawPath(path, primary, style = Stroke(2.5.dp.toPx()))
                drawCircle(primary, 4.dp.toPx(), position(trend.last()))
            }
            selected?.let { drawCircle(primary, 7.dp.toPx(), position(it), style = Stroke(2.dp.toPx())) }
            val ticks = weightAxisTicks(startDay, endDay)
            val endLabel = ticks.lastOrNull()?.let { textMeasurer.measure(it.label, textStyle) }
            val endX = endLabel?.let { (right - it.size.width).coerceAtLeast(left) } ?: right
            val tickGap = 16.dp.toPx() * density.fontScale
            var tickRight = Float.NEGATIVE_INFINITY
            ticks.forEachIndexed { index, tick ->
                val label = textMeasurer.measure(tick.label, textStyle)
                val tickX = position(ChartPoint(tick.epochDay, bounds.low)).x
                val offset = weightTickLabelOffset(index, ticks.lastIndex, label.size.width)
                val x = (tickX - offset).coerceIn(left, (right - label.size.width).coerceAtLeast(left))
                val last = index == ticks.lastIndex
                val fitsBeforeEnd = x + label.size.width + tickGap <= endX
                if (x > tickRight + tickGap && (last || fitsBeforeEnd)) {
                    drawText(label, topLeft = Offset(x, heightPx * 257f / 285f))
                    tickRight = x + label.size.width
                }
            }
        }
        selected?.let { point ->
            val position = position(point)
            val tooltipWidth = minOf(176.dp, maxWidth)
            val x =
                with(density) { position.x.toDp() - tooltipWidth / 2 }
                    .coerceIn(0.dp, (maxWidth - tooltipWidth).coerceAtLeast(0.dp))
            val y = with(density) { position.y.toDp() - 82.dp * density.fontScale }.coerceAtLeast(0.dp)
            Surface(
                modifier = Modifier.offset(x, y).widthIn(max = tooltipWidth).testTag("f06-point-tooltip"),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 3.dp,
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(weightPointDate(point.epochDay), style = MaterialTheme.typography.labelMedium)
                    Text(formatWeight(point.value), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
