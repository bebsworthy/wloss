package app.wlo.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
 * @param bands the three integrated trajectories (null = not computed yet)
 * @param goalWeight the goal the cone lands on (D6: DerivedValue-typed)
 * @param estimate the engine's expenditure estimate backing the bands, when shown
 * @param formatWeight display formatting with unit glyph (R-D10/R-D12)
 * @param formatKcal display formatting for the estimate chip
 * @param onExplain tap-through to "how we got here" (chart + chip)
 */
@Composable
public fun WloForecastCard(
    bands: WloForecastBands,
    goalWeight: DerivedValue<Double>,
    estimate: DerivedValue<Double>?,
    formatWeight: (Double) -> String,
    formatKcal: (Double) -> String,
    modifier: Modifier = Modifier,
    onExplain: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(WloSpacing.PAD_CARD)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
            ) {
                Text(
                    text = "Forecast to ${formatWeight(goalWeight.value)}",
                    style = wloType.title,
                    modifier = Modifier.weight(1f),
                )
                if (estimate != null && isEstimated(estimate.provenance)) {
                    EstimatedStamp()
                }
            }

            Spacer(Modifier.height(WloSpacing.CARD))

            WloForecastChart(
                bands = bands,
                formatWeight = formatWeight,
                onClick = onExplain,
            )

            Spacer(Modifier.height(WloSpacing.CARD))

            FinishDateRow(bands = bands)

            Text(
                text = "an estimate, not a promise — it sharpens as you log",
                style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.padding(top = WloSpacing.TIGHT),
            )

            if (estimate != null) {
                Spacer(Modifier.height(WloSpacing.TIGHT))
                ProvenanceChip(
                    value = estimate,
                    format = formatKcal,
                    onClick = onExplain,
                )
            }
        }
    }
}

/** The uppercase quality stamp (developing blue, hairline pill, §1.2). */
@Composable
public fun EstimatedStamp(modifier: Modifier = Modifier): Unit =
    Surface(
        modifier = modifier,
        shape = WloShape.Chip,
        color = wloExtendedColors.developing.copy(alpha = 0.14f),
        contentColor = wloExtendedColors.developing,
        border = BorderStroke(1.dp, wloExtendedColors.developing.copy(alpha = 0.45f)),
    ) {
        Text(
            text = "ESTIMATED",
            style = wloType.label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }

/** "on trend <expected date> · range <optimistic> – <pessimistic>" (range-first copy). */
@Composable
public fun FinishDateRow(
    bands: WloForecastBands,
    modifier: Modifier = Modifier,
) {
    val expected = bands.expectedFinishEpochDay?.let(::formatDay)
    val fast = bands.optimisticFinishEpochDay?.let(::formatDay)
    val slow = bands.pessimisticFinishEpochDay?.let(::formatDay)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(text = expected ?: "beyond the horizon", style = wloType.statM)
        if (expected != null && fast != null && slow != null) {
            Text(
                text = "on trend · range $fast – $slow",
                style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                color = wloExtendedColors.textTertiary,
            )
        }
    }
}

private const val BLOOM_MS = 600

/**
 * The cone itself: 600 ms outward bloom (F01 §4), center line `text-primary`,
 * bands `accent-dim` stepping opacity (darkest at the center), finish-date
 * ticks per band, `text-tertiary` chrome, tabular axis figures.
 */
@Composable
public fun WloForecastChart(
    bands: WloForecastBands,
    formatWeight: (Double) -> String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val chartModifier =
        if (onClick == null) {
            modifier
        } else {
            modifier.clickable(onClickLabel = "how we got here") { onClick() }
        }
    val bloom by animateFloatAsState(
        animationSpec = tween(BLOOM_MS, easing = WloMotion.EasingEnter),
        targetValue = 1f,
        label = "forecast-bloom",
    )
    val outline = MaterialTheme.colorScheme.outline
    val centerLine = MaterialTheme.colorScheme.onSurface
    val accentDim = wloExtendedColors.accentDim
    val accent = MaterialTheme.colorScheme.primary
    val chrome = wloExtendedColors.textTertiary
    val axisStyle =
        TextStyle(
            fontFamily = WloFontFamily,
            fontSize = 10.sp,
            fontFeatureSettings = WloFontFeatures.TABULAR,
            color = chrome,
        )
    val description =
        "Weight forecast chart: from ${formatWeight(bands.startWeightKg)} toward " +
            "${formatWeight(bands.goalWeightKg)}. Three bands widen with the horizon; " +
            "an estimate, not a promise."
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier =
            chartModifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics { contentDescription = description },
    ) {
        // Axis/label lanes are dp-scaled so the chrome never clips (the
        // bottom lane holds the "today" annotation at any density/font
        // scale — the pre-M3 px lanes clipped it on dense screens).
        val axisLane = 22.dp.toPx()
        val labelGap = 6.dp.toPx()
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - axisLane

        // Series prep: each path starts at (start, startWeight).
        fun series(values: List<Double>): List<Pair<Float, Float>> {
            val ys = listOf(bands.startWeightKg) + values
            val stepCount = ys.size.coerceAtLeast(2)
            return ys.mapIndexed { index, kg ->
                val x = left + (right - left) * index / (stepCount - 1).coerceAtLeast(1)
                x to kg.toFloat()
            }
        }

        val fast = series(bands.optimisticKg)
        val mid = series(bands.expectedKg)
        val slow = series(bands.pessimisticKg)

        val allWeights = (fast + mid + slow).map { it.second } + bands.goalWeightKg.toFloat()
        val wMax = allWeights.max()
        val wMin = allWeights.min()
        val span = (wMax - wMin).takeIf { it > 0.01f } ?: 1f

        fun toOffset(point: Pair<Float, Float>): Offset {
            val x = point.first
            val y = bottom - (point.second - wMin) / span * (bottom - top)
            return Offset(x, y)
        }

        // Weight → y, for the goal line.
        fun yOf(kg: Float): Float = bottom - (kg - wMin) / span * (bottom - top)

        // Bloom: reveal left→right (the eye reads range, not point).
        val reveal = left + (right - left) * bloom

        fun clipped(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
            if (reveal >= right) return points
            val out = mutableListOf<Pair<Float, Float>>()
            for (i in points.indices) {
                val (x, y) = points[i]
                if (x <= reveal) {
                    out += x to y
                } else {
                    if (i > 0) {
                        val (px, py) = points[i - 1]
                        val t = (reveal - px) / ((x - px).takeIf { it != 0f } ?: 1f)
                        out += reveal to (py + (y - py) * t)
                    }
                    break
                }
            }
            return out
        }

        fun bandPath(
            upper: List<Pair<Float, Float>>,
            lower: List<Pair<Float, Float>>,
        ): Path {
            val u = clipped(upper)
            val l = clipped(lower)
            val path = Path()
            if (u.isEmpty() || l.isEmpty()) return path
            u.forEachIndexed { index, point ->
                val o = toOffset(point)
                if (index == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            l.reversed().forEach { point ->
                val o = toOffset(point)
                path.lineTo(o.x, o.y)
            }
            path.close()
            return path
        }

        // Outer band (pessimistic..optimistic) light, inner (expected..optimistic) darker:
        // the center is where the probability mass sits (fan-chart grammar, §1.3).
        drawPath(bandPath(fast, slow), accentDim.copy(alpha = 0.16f))
        drawPath(bandPath(fast, mid), accentDim.copy(alpha = 0.22f))

        fun line(points: List<Pair<Float, Float>>): Path {
            val path = Path()
            clipped(points).forEachIndexed { index, point ->
                val o = toOffset(point)
                if (index == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            return path
        }

        drawPath(line(fast), accentDim, style = Stroke(1.5f))
        drawPath(line(slow), accentDim, style = Stroke(1.5f))
        drawPath(line(mid), centerLine, style = Stroke(2.8f, cap = StrokeCap.Round))

        // Goal line: hairline across at the target weight.
        val goalY = yOf(bands.goalWeightKg.toFloat())
        drawLine(outline, Offset(left, goalY), Offset(right, goalY), 1f)

        // Finish-date ticks: one per band, accent for the expected date.
        fun tick(epochDay: Long?) {
            if (epochDay == null) return
            val horizon = bands.startEpochDay + HORIZON_STEP_DAYS * slow.size.coerceAtLeast(1)
            val totalDays = (bands.startEpochDay..horizon).count()
            val fraction = (epochDay - bands.startEpochDay).toFloat() / (totalDays - 1).coerceAtLeast(1)
            if (fraction > bloom) return
            val x = left + (right - left) * fraction
            drawLine(accent, Offset(x, top - 6f), Offset(x, top + 2f), 2f)
        }
        tick(bands.optimisticFinishEpochDay)
        tick(bands.pessimisticFinishEpochDay)
        tick(bands.expectedFinishEpochDay)

        // Chrome: "today" and goal annotations (tabular figures). The goal
        // label is clamped inside the canvas so it never clips at the top.
        drawText(
            textMeasurer = textMeasurer,
            text = "today · ${formatWeight(bands.startWeightKg)}",
            style = axisStyle,
            topLeft = Offset(left, bottom + labelGap),
        )
        drawText(
            textMeasurer = textMeasurer,
            text = formatWeight(bands.goalWeightKg),
            style = axisStyle,
            topLeft =
                Offset(
                    (right - 96.dp.toPx()).coerceAtLeast(left),
                    (goalY - 16.dp.toPx()).coerceAtLeast(2.dp.toPx()),
                ),
        )
    }
}

/** Month/day label for finish ticks and date rows (epoch days → "May 6"). */
public fun formatDay(epochDay: Long): String {
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    return "${MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}"
}

private val MONTHS: List<String> =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

private const val HORIZON_STEP_DAYS = 7
