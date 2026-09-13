package app.wlo.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wlo.core.model.DerivedValue
import kotlinx.datetime.LocalDate

/**
 * The F06 weight chart (DESIGN-SYSTEM.md §6, custom Canvas — the signature
 * weight object): scale dots (raw lowest-of-day scalars) with the smoother's
 * trend line drawn over them, and the optional progress ribbon (the band
 * between the trend and the N-days-ago reference line: accent where the trend
 * is falling — the "green band above" — neutral otherwise; red does not
 * exist, §1.2). The scale reads as a scale (owner review WLO-0030, defect 7):
 * axis numerals in `text-secondary` 11 sp carrying the unit via
 * [formatWeight], faint hairline gridlines at the window low/high, and a
 * date-range caption ("15 Jun – 12 Sep") beneath the canvas.
 *
 * The chart no longer renders the "trend now" stat row — the owning surface
 * renders that stat itself (defect 5: the built-in row duplicated the
 * surface's header + stat).
 *
 * @param samples raw daily scalars (may be empty while the trend warms up)
 * @param trend smoother output rendered as the line
 * @param currentTrend Deprecated — retained only so existing call sites
 *   compile; the chart does NOT render it. The owning surface owns the
 *   "trend now" stat (via [WloStat] or [WloHeroStat]); pass `null` in new code.
 * @param formatWeight numeral formatting including the unit ("77.6 kg")
 * @param reference optional N-days-ago line for the progress ribbon
 * @param describe accessibility description for the canvas
 */
@Composable
// The parameter is kept for API compatibility (features still pass it); the
// suppression documents the deliberate non-use — Part B removes it at call sites.
@Suppress("UnusedParameter")
public fun WloTrendChart(
    samples: List<ChartPoint>,
    trend: List<ChartPoint>,
    currentTrend: DerivedValue<Double>?,
    formatWeight: (Double) -> String,
    modifier: Modifier = Modifier,
    reference: List<ChartPoint> = emptyList(),
    describe: String = "Weight chart. Scale dots with the trend line over them.",
) {
    val textMeasurer = rememberTextMeasurer()
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val ribbonUp = wloExtendedColors.accentDim
    val ribbonDown = wloExtendedColors.neutralDelta.copy(alpha = 0.30f)
    val chrome = wloExtendedColors.textTertiary
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val axisStyle =
        TextStyle(
            fontFamily = WloFontFamily,
            fontSize = 11.sp,
            fontFeatureSettings = WloFontFeatures.TABULAR,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    val firstDay = samples.firstOrNull()?.epochDay ?: trend.firstOrNull()?.epochDay
    val lastDay = samples.lastOrNull()?.epochDay ?: trend.lastOrNull()?.epochDay

    Column(modifier = modifier) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .semantics { contentDescription = describe },
        ) {
            drawWeightChart(
                samples = samples,
                trend = trend,
                reference = reference,
                dotColor = dotColor,
                lineColor = lineColor,
                ribbonUp = ribbonUp,
                ribbonDown = ribbonDown,
                chrome = chrome,
                grid = grid,
                axisStyle = axisStyle,
                textMeasurer = textMeasurer,
                formatWeight = formatWeight,
            )
        }

        if (firstDay != null && lastDay != null) {
            Text(
                text = windowCaption(firstDay, lastDay),
                style = wloType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = WloSpacing.TIGHT),
            )
        }
    }
}

/** Window caption "15 Jun – 12 Sep" from the first/last epoch days. */
private fun windowCaption(
    firstDay: Long,
    lastDay: Long,
): String {
    val first = LocalDate.fromEpochDays(firstDay.toInt())
    val last = LocalDate.fromEpochDays(lastDay.toInt())
    return "${first.dayOfMonth} ${MONTHS[first.monthNumber - 1]} – " +
        "${last.dayOfMonth} ${MONTHS[last.monthNumber - 1]}"
}

private fun DrawScope.drawWeightChart(
    samples: List<ChartPoint>,
    trend: List<ChartPoint>,
    reference: List<ChartPoint>,
    dotColor: Color,
    lineColor: Color,
    ribbonUp: Color,
    ribbonDown: Color,
    chrome: Color,
    grid: Color,
    axisStyle: TextStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    formatWeight: (Double) -> String,
) {
    if (samples.isEmpty() && trend.isEmpty()) return
    val left = 2.dp.toPx()
    val right = size.width - 2.dp.toPx()
    // dp-scaled lanes: top numerals, bottom date glyphs — never clip.
    val top = 26.dp.toPx()
    val bottom = size.height - 22.dp.toPx()

    val allValues = samples.map { it.value } + trend.map { it.value }
    val minV = allValues.minOrNull() ?: 0.0
    val maxV = allValues.maxOrNull() ?: 1.0
    val span = (maxV - minV).takeIf { it > 1e-9 } ?: 1.0
    val pad = span * 0.08
    val lo = minV - pad
    val hi = maxV + pad

    val firstDay = samples.firstOrNull()?.epochDay ?: trend.first().epochDay
    val lastDay = samples.lastOrNull()?.epochDay ?: trend.last().epochDay
    val daySpan = (lastDay - firstDay).coerceAtLeast(1L).toFloat()

    fun x(epochDay: Long): Float = left + ((epochDay - firstDay) / daySpan) * (right - left)

    fun y(kg: Double): Float = bottom - (((kg - lo) / (hi - lo)).toFloat()) * (bottom - top)

    // Faint gridlines at the window low/high — the scale reads as a scale.
    drawLine(grid, Offset(left, y(hi)), Offset(right, y(hi)), strokeWidth = 1.dp.toPx())
    drawLine(grid, Offset(left, y(lo)), Offset(right, y(lo)), strokeWidth = 1.dp.toPx())

    // Reference (N-days-ago) line, dashed chrome.
    if (reference.size > 1) {
        val path = Path()
        reference.forEachIndexed { index, point ->
            val p = Offset(x(point.epochDay), y(point.value))
            if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, chrome.copy(alpha = 0.55f), style = Stroke(width = 1.5f))
    }

    // Progress ribbon: one smooth filled band between the N-days-ago
    // reference and the trend — uniform valence-free color (accent while
    // falling, neutral otherwise), never per-point strokes (reads as bars).
    if (reference.isNotEmpty()) {
        val refByDay = reference.associateBy { it.epochDay }
        val paired =
            trend.mapNotNull { point ->
                refByDay[point.epochDay]?.let { ref -> Triple(point, ref, x(point.epochDay)) }
            }
        if (paired.size > 1) {
            val falling = paired.last().second.value >= paired.last().first.value
            val band = Path()
            paired.forEachIndexed { index, (point, _, cx) ->
                val cy = y(point.value)
                if (index == 0) band.moveTo(cx, cy) else band.lineTo(cx, cy)
            }
            for (index in paired.indices.reversed()) {
                val (_, ref, cx) = paired[index]
                band.lineTo(cx, y(ref.value))
            }
            band.close()
            val fill = if (falling) ribbonUp else ribbonDown
            drawPath(band, fill.copy(alpha = 0.22f))
        }
    }

    // Scale dots (raw daily scalars).
    for (sample in samples) {
        drawCircle(
            color = dotColor,
            radius = 3.dp.toPx(),
            center = Offset(x(sample.epochDay), y(sample.value)),
        )
    }

    // Trend line over the dots.
    if (trend.size > 1) {
        val path = Path()
        trend.forEachIndexed { index, point ->
            val p = Offset(x(point.epochDay), y(point.value))
            if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2.5f))
    }

    // Axis numerals (tabular, text-secondary): window high top-left, low
    // bottom-left, latest top-right (right-aligned so the unit never clips).
    drawText(
        textMeasurer = textMeasurer,
        text = formatWeight(hi),
        style = axisStyle,
        topLeft = Offset(left, 2.dp.toPx()),
    )
    drawText(
        textMeasurer = textMeasurer,
        text = formatWeight(lo),
        style = axisStyle,
        topLeft = Offset(left, bottom + 4.dp.toPx()),
    )
    val latestLayout = textMeasurer.measure(formatWeight(trend.lastOrNull()?.value ?: maxV), axisStyle)
    drawText(
        textLayoutResult = latestLayout,
        topLeft = Offset(right - latestLayout.size.width, 2.dp.toPx()),
    )
}
