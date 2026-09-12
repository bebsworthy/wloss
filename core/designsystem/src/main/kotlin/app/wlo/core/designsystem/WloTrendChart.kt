package app.wlo.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

/**
 * The F06 weight chart (DESIGN-SYSTEM.md §6, custom Canvas — the signature
 * weight object): scale dots (raw lowest-of-day scalars) with the smoother's
 * trend line drawn over them, and the optional progress ribbon (the band
 * between the trend and the N-days-ago reference line: accent where the trend
 * is falling — the "green band above" — neutral otherwise; red does not
 * exist, §1.2). The current trend renders as a provenance chip (D6); chrome
 * is text-tertiary; label lanes are dp-scaled so nothing clips.
 */
@Composable
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
    val axisStyle =
        TextStyle(
            fontFamily = WloFontFamily,
            fontSize = 10.sp,
            fontFeatureSettings = WloFontFeatures.TABULAR,
            color = chrome,
        )

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
                axisStyle = axisStyle,
                textMeasurer = textMeasurer,
                formatWeight = formatWeight,
            )
        }

        currentTrend?.let { value ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = WloSpacing.TIGHT)) {
                Text(
                    text = "trend now",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
                Spacer(Modifier.padding(horizontal = WloSpacing.TIGHT))
                ProvenanceChip(value = value, format = formatWeight)
            }
        }
    }
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

    // Reference (N-days-ago) line, dashed chrome.
    if (reference.size > 1) {
        val path = Path()
        reference.forEachIndexed { index, point ->
            val p = Offset(x(point.epochDay), y(point.value))
            if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, chrome.copy(alpha = 0.55f), style = Stroke(width = 1.5f))
    }

    // Progress ribbon: thin vertical strokes between reference and trend —
    // accent where the trend sits below the reference, neutral above.
    if (reference.isNotEmpty()) {
        val refByDay = reference.associateBy { it.epochDay }
        val strokeWidth = 2.dp.toPx()
        for (point in trend) {
            val ref = refByDay[point.epochDay] ?: continue
            val cx = x(point.epochDay)
            val cy = y(point.value)
            val ry = y(ref.value)
            val falling = cy > ry // smaller kg renders lower on screen
            drawLine(
                color = if (falling) ribbonUp else ribbonDown,
                start = Offset(cx, minOf(cy, ry)),
                end = Offset(cx, maxOf(cy, ry)),
                strokeWidth = strokeWidth,
            )
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

    // Axis numerals (tabular, chrome): window high top-left, low bottom-left,
    // latest top-right.
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
    drawText(
        textMeasurer = textMeasurer,
        text = formatWeight(trend.lastOrNull()?.value ?: maxV),
        style = axisStyle,
        topLeft = Offset(right - 44.dp.toPx(), 2.dp.toPx()),
    )
}
