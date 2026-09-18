package app.wlo.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The forecast card (flow-07 frame 3, "Forecast bloom"): the standard
 * [WloCardHeader] micro-label with the SAME provenance pill every card uses
 * in its header slot (ONE pill total — mock pin 12: the hedge appears exactly
 * once), the fan chart on ONE shared time axis with month ticks, the arrival
 * row, and the legend row under the chart.
 *
 * @param bands the three integrated trajectories (null = not computed yet)
 * @param goalWeight the goal the cone lands on (D6: DerivedValue-typed)
 * @param estimate the engine's expenditure estimate backing the bands
 * @param plannedIntakeKcal the planned daily intake the path integrates, when
 *   known — the unreached-goal arrival row states intake vs burn as a fact
 * @param formatWeight display formatting with unit glyph (R-D10/R-D12)
 * @param formatKcal display formatting for the pill and the arrival row
 * @param onExplain tap-through to "how we got here" (chart + pill)
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
    plannedIntakeKcal: Double? = null,
) {
    WloCard(
        modifier = modifier,
        header = {
            WloCardHeader(
                // The goal weight already reads on the chart's goal line and in
                // the arrival row; the header stays a one-word micro-label like
                // every other card ("FORECAST TO 74.0 KG" shouted in caps).
                title = "Forecast",
                provenance =
                    if (estimate != null) {
                        {
                            ProvenanceChip(
                                value = estimate,
                                format = formatKcal,
                                onClick = onExplain,
                            )
                        }
                    } else {
                        null
                    },
            )
        },
    ) {
        WloForecastChart(
            bands = bands,
            formatWeight = formatWeight,
            describe =
                "Weight forecast chart: from ${formatWeight(bands.startWeightKg)} toward " +
                    "${formatWeight(goalWeight.value)} over " +
                    "${formatMonthTick(windowEndDay(bands))}. Three bands widen with the " +
                    "horizon; an estimate, not a promise.",
            onClick = onExplain,
        )

        // Mock `.sch-legend`: swatch + word, caption chrome (R-D11 user words).
        ForecastLegend()

        ArrivalRow(
            bands = bands,
            plannedIntakeKcal = plannedIntakeKcal,
            estimate = estimate,
            formatKcal = formatKcal,
        )

        // THE single hedge (mock pin 12 — it appears exactly once).
        Text(
            text = "an estimate, not a promise — it sharpens as you log",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
    }
}

/**
 * The chart legend (mock `.sch-legend` pattern): tiny drawn swatches + words,
 * caption typography — never glyphs or emoji. Three entries: the expected
 * line, the range band, the goal hairline.
 */
@Composable
private fun ForecastLegend(): Unit =
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Chart legend: expected line, range band, goal line"
                },
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendEntry(
            label = "expected",
            swatch = {
                Box(
                    modifier =
                        Modifier
                            .size(width = 14.dp, height = 2.dp)
                            .background(MaterialTheme.colorScheme.onSurface),
                )
            },
        )
        LegendEntry(
            label = "range",
            swatch = {
                Box(
                    modifier =
                        Modifier
                            .size(width = 14.dp, height = 8.dp)
                            .background(wloExtendedColors.accentDim.copy(alpha = 0.22f)),
                )
            },
        )
        LegendEntry(
            label = "goal",
            swatch = {
                Box(
                    modifier =
                        Modifier
                            .size(width = 14.dp, height = 1.dp)
                            .background(MaterialTheme.colorScheme.outline),
                )
            },
        )
    }

/** One legend entry: a drawn swatch + the word in caption chrome. */
@Composable
private fun LegendEntry(
    label: String,
    swatch: @Composable () -> Unit,
): Unit =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        Box(modifier = Modifier.width(14.dp), contentAlignment = Alignment.Center) { swatch() }
        Text(
            text = label,
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
    }

/**
 * The arrival row (mock pin 11, range-first copy). Dates known → "May 6 ·
 * on trend · range Mar 22 – Jul 5". Goal not reached within the horizon →
 * the informative fact, never a dead end: the window length in human terms,
 * and the planned-intake-vs-burn relationship with the resulting pace (sign
 * shown as a fact — zero guilt, R-D1/R-D5).
 */
@Composable
private fun ArrivalRow(
    bands: WloForecastBands,
    plannedIntakeKcal: Double?,
    estimate: DerivedValue<Double>?,
    formatKcal: (Double) -> String,
) {
    provisionalRangeCopy(bands)?.let { copy ->
        Text(text = copy.range, style = wloType.statM)
        Text(
            text = copy.qualifier,
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
        return
    }
    val expected = bands.expectedFinishEpochDay?.let(::formatDay)
    if (expected != null) {
        val fast = bands.optimisticFinishEpochDay?.let(::formatDay)
        val slow = bands.pessimisticFinishEpochDay?.let(::formatDay)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        ) {
            Text(text = expected, style = wloType.statM)
            if (fast != null && slow != null) {
                Text(
                    text = "on trend · range $fast – $slow",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            }
        }
        return
    }

    // Unreached: headline = the window length, caption = the why + the pace.
    val paceKgPerWeek = bands.expectedPaceKgPerWeek
    Text(text = horizonWords(bands), style = wloType.statM)
    val paceFact =
        when {
            paceKgPerWeek == null -> "the goal sits beyond this window at the current plan"
            paceKgPerWeek < -PACE_FLAT_KG_PER_WEEK ->
                "the plan adds ~${formatKgPerWeek(-paceKgPerWeek)} — " +
                    intakeBurnFact(plannedIntakeKcal, estimate, formatKcal)

            paceKgPerWeek > PACE_FLAT_KG_PER_WEEK ->
                "at ~${formatKgPerWeek(paceKgPerWeek)} the goal sits past this window"

            else -> "planned intake ≈ estimated burn — the path holds about flat"
        }
    Text(
        text = paceFact,
        style = wloType.caption,
        color = wloExtendedColors.textTertiary,
    )
}

internal data class ProvisionalRangeCopy(
    val range: String,
    val qualifier: String,
)

/** WLO-0073: developing evidence may show outer bounds, never a point date. */
internal fun provisionalRangeCopy(bands: WloForecastBands): ProvisionalRangeCopy? {
    if (bands.pointDateEligible) return null
    val optimistic = bands.optimisticFinishEpochDay?.let(::formatDay) ?: return null
    val pessimistic = bands.pessimisticFinishEpochDay?.let(::formatDay) ?: return null
    return ProvisionalRangeCopy(
        range = "$optimistic – $pessimistic",
        qualifier = "provisional range · sharpens as your measured pattern forms",
    )
}

/** The intake-vs-burn relationship, stated as a fact (both numbers when known). */
private fun intakeBurnFact(
    plannedIntakeKcal: Double?,
    estimate: DerivedValue<Double>?,
    formatKcal: (Double) -> String,
): String =
    when {
        plannedIntakeKcal != null && estimate != null ->
            "planned intake ${formatKcal(plannedIntakeKcal)} vs ${formatKcal(estimate.value)} burn"

        else -> "planned intake sits above your estimated burn"
    }

/** The engine's pace sign is loss-positive; under ~this the path is a hold. */
private const val PACE_FLAT_KG_PER_WEEK: Double = 0.005

/**
 * The horizon in human terms from the trajectory length (one point per weekly
 * engine step): 260 steps → "5 years".
 */
internal fun horizonWords(bands: WloForecastBands): String {
    val weeks =
        maxOf(bands.optimisticKg.size, bands.expectedKg.size, bands.pessimisticKg.size)
            .coerceAtLeast(1)
    val years = weeks / 52.0
    return if (abs(years - years.roundToInt()) < 0.05) {
        "${years.roundToInt()} years"
    } else {
        "~${"%.1f".format(years)} years"
    }
}

private const val BLOOM_MS = 600

/**
 * The cone itself (flow-07 frame 3): 600 ms outward bloom (F01 §4, pin 10),
 * center line `text-primary`, bands `accent-dim` stepping opacity (darkest at
 * the center), finish-date ticks per band on the SAME shared time axis, month
 * tick labels along the bottom, "today · N kg" + goal annotations, heavier =
 * up (as [WloTrendChart]). Whole chart taps through (pin 12).
 *
 * @param describe the merged a11y description (composed by the card — the
 *   D6-typed goal lives there, not in the raw geometry)
 */
@Composable
public fun WloForecastChart(
    bands: WloForecastBands,
    formatWeight: (Double) -> String,
    describe: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    goalColor: androidx.compose.ui.graphics.Color? = null,
    rangeOnly: Boolean = false,
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
    val accentDim = if (rangeOnly) wloExtendedColors.developing else wloExtendedColors.accentDim
    val chartBoundary = wloExtendedColors.chartBoundary
    val accent = MaterialTheme.colorScheme.primary
    val chrome = wloExtendedColors.textTertiary
    val axisStyle =
        TextStyle(
            fontFamily = WloFontFamily,
            fontSize = 10.sp,
            fontFeatureSettings = WloFontFeatures.TABULAR,
            color = chrome,
        )
    val textMeasurer = rememberTextMeasurer()

    // ONE shared time window for every band AND every tick (the per-band
    // stretch bug: each band used to normalize to its own point count).
    val window = forecastTimeWindow(bands)

    Canvas(
        modifier =
            chartModifier
                .fillMaxWidth()
                .height(172.dp)
                .semantics { contentDescription = describe },
    ) {
        // Lanes are dp-scaled so the chrome never clips at any density/font
        // scale: headroom for the goal label, then the plot, then the "today"
        // annotation lane, then the month-tick lane along the bottom.
        val labelGap = 2.dp.toPx()
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 14.dp.toPx()
        val tickLane = 16.dp.toPx()
        val todayLane = 17.dp.toPx()
        val bottom = size.height - tickLane - todayLane

        fun xOf(fraction: Float): Float = left + (right - left) * fraction

        // Series prep: point i sits at start + i·step on the SHARED axis
        // (fraction 0..1); a band that finishes early holds its last value —
        // the goal — to the window edge, so the fan closes exactly at the
        // pessimistic arrival (the cone lands ON the goal, never past it).
        fun series(values: List<Double>): List<Pair<Float, Float>> {
            val out = ArrayList<Pair<Float, Float>>(values.size + 2)
            out += 0f to bands.startWeightKg.toFloat()
            for ((index, kg) in values.withIndex()) {
                val fraction = window.fractionAt(index + 1)
                if (fraction < 1f) {
                    out += fraction to kg.toFloat()
                } else {
                    out += 1f to kg.toFloat()
                    return out
                }
            }
            if (out.last().first < 1f) out += 1f to out.last().second
            return out
        }

        val fast = series(bands.optimisticKg)
        val mid = series(bands.expectedKg)
        val slow = series(bands.pessimisticKg)

        val allWeights = (fast + mid + slow).map { it.second } + bands.goalWeightKg.toFloat()
        val wMax = allWeights.max()
        val wMin = allWeights.min()
        val span = (wMax - wMin).takeIf { it > 0.01f } ?: 1f

        // Weight → y: heavier = up (consistent with the trend chart).
        fun yOf(kg: Float): Float = bottom - (kg - wMin) / span * (bottom - top)

        fun toOffset(point: Pair<Float, Float>): Offset = Offset(xOf(point.first), yOf(point.second))

        // Bloom: reveal left→right (the eye reads range, not point).
        val reveal = bloom

        fun clipped(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
            if (reveal >= 1f) return points
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
        if (!rangeOnly) drawPath(bandPath(fast, mid), accentDim.copy(alpha = 0.22f))

        fun line(points: List<Pair<Float, Float>>): Path {
            val path = Path()
            clipped(points).forEachIndexed { index, point ->
                val o = toOffset(point)
                if (index == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            return path
        }

        if (!rangeOnly) {
            drawPath(line(fast), chartBoundary, style = Stroke(1.5f))
            drawPath(line(slow), chartBoundary, style = Stroke(1.5f))
            drawPath(line(mid), centerLine, style = Stroke(2.8f, cap = StrokeCap.Round))
        }

        // Goal line: hairline across at the target weight.
        val goalY = yOf(bands.goalWeightKg.toFloat())
        drawLine(
            goalColor ?: outline,
            Offset(left, goalY),
            Offset(right, goalY),
            if (rangeOnly) 1.dp.toPx() else 1f,
            pathEffect =
                if (rangeOnly) {
                    androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 4.dp.toPx()),
                    )
                } else {
                    null
                },
        )

        // Finish-date ticks: one per band, ON the shared scale, accent for the
        // expected date. Out-of-window ticks (band finished past the display
        // cap) are honestly absent.
        fun finishTick(epochDay: Long?) {
            if (epochDay == null || epochDay > window.endEpochDay) return
            val fraction = window.fraction(epochDay)
            if (fraction > reveal) return
            val x = xOf(fraction)
            drawLine(accent, Offset(x, top - 6f), Offset(x, top + 2f), 2f)
        }
        if (!rangeOnly) {
            finishTick(bands.optimisticFinishEpochDay)
            finishTick(bands.pessimisticFinishEpochDay)
        }
        if (bands.pointDateEligible) finishTick(bands.expectedFinishEpochDay)

        // Month ticks along the bottom (mock "Sep ’26 · Jan ’27 · May ’27"),
        // text-only chrome like the mock's fan-axis, revealed with the bloom.
        val ticks = forecastMonthTicks(window.startEpochDay, window.endEpochDay)
        val labelTop = bottom + todayLane + 2.dp.toPx()
        for (tick in ticks) {
            val fraction = window.fraction(tick)
            if (fraction > reveal) continue
            val x = xOf(fraction)
            val layout = textMeasurer.measure(formatMonthTick(tick), axisStyle)
            val labelX = (x - layout.size.width / 2f).coerceIn(left, right - layout.size.width)
            drawText(layout, topLeft = Offset(labelX, labelTop))
        }

        // Chrome: "today" (bottom-left, above the tick lane) and the goal
        // weight (right, on the goal line, clamped inside the canvas).
        drawText(
            textMeasurer = textMeasurer,
            text = "today · ${formatWeight(bands.startWeightKg)}",
            style = axisStyle,
            topLeft = Offset(left, bottom + labelGap),
        )
        drawText(
            textMeasurer = textMeasurer,
            text = formatWeight(bands.goalWeightKg),
            style = axisStyle.copy(color = goalColor ?: chrome),
            topLeft =
                Offset(
                    (right - 96.dp.toPx()).coerceAtLeast(left),
                    (goalY - 16.dp.toPx()).coerceAtLeast(2.dp.toPx()),
                ),
        )
    }
}

// Axis formatters over epoch days.

/** Month/day label for finish ticks and date rows (epoch days → "May 6"). */
public fun formatDay(epochDay: Long): String {
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    return "${MONTHS[date.monthNumber - 1]} ${date.dayOfMonth}"
}

/** Month-tick label (epoch days → "Sep ’26") — the mock's `fan-axis` chrome. */
public fun formatMonthTick(epochDay: Long): String {
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    return "${MONTHS[date.monthNumber - 1]} ’${date.year % 100}"
}

/** Month abbreviations shared by date formatters in this module. */
internal val MONTHS: List<String> =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * kg/week at the house format ("0.46 kg/wk") — the arrival row's pace fact.
 * The sign is the USER's (positive = gaining); the caller flips the engine's
 * loss-positive rate before calling.
 */
public fun formatKgPerWeek(value: Double): String = "%.2f kg/wk".format(value)

/**
 * The trajectory step length in days, mirrored from
 * `ConstantsRegistry.FORECAST_STEP_DAYS` (the neutral design-system model
 * stays decoupled from :core:engines; every production band integrates in
 * weekly steps, and the shared time axis bakes that geometry).
 */
internal const val FORECAST_STEP_DAYS: Long = 7L

/**
 * The display cap for UNREACHED paths (weeks). The engine integrates 260
 * weeks when the goal never lands; plotting all of it makes the timeframe
 * unreadable, so the shared window caps at 2 years — a gaining 260-step path
 * still reads as a fan. Reached paths are never capped: the window closes at
 * the last band arrival however far out it is.
 */
internal const val FORECAST_DISPLAY_CAP_WEEKS: Int = 104

/**
 * ONE shared display window, start .. end (epoch days), derived from the
 * data (flow-07: Sep ’26 → May ’27 for an 8-month plan):
 *
 * - any band finished → the window closes at the LAST arrival date, so the
 *   fan visibly closes on the goal at the right edge;
 * - nothing finished → the window closes at the last integrated point, capped
 *   at [FORECAST_DISPLAY_CAP_WEEKS] so an unreached 260-week path still reads.
 */
internal fun forecastTimeWindow(bands: WloForecastBands): ForecastTimeWindow {
    val finishDays: List<Long> =
        listOfNotNull(
            bands.expectedFinishEpochDay,
            bands.optimisticFinishEpochDay,
            bands.pessimisticFinishEpochDay,
        )
    val lastPointDay =
        bands.startEpochDay +
            maxOf(bands.optimisticKg.size, bands.expectedKg.size, bands.pessimisticKg.size)
                .coerceAtLeast(1) * FORECAST_STEP_DAYS
    val end =
        when {
            finishDays.isNotEmpty() -> finishDays.max()
            else -> minOf(lastPointDay, bands.startEpochDay + FORECAST_DISPLAY_CAP_WEEKS * FORECAST_STEP_DAYS)
        }
    return ForecastTimeWindow(startEpochDay = bands.startEpochDay, endEpochDay = maxOf(end, bands.startEpochDay + 1))
}

/** The pure start..end display window (see [forecastTimeWindow]). */
internal data class ForecastTimeWindow(
    val startEpochDay: Long,
    val endEpochDay: Long,
) {
    val spanDays: Long = (endEpochDay - startEpochDay).coerceAtLeast(1)

    /** x fraction (0..1) for the trajectory point at [stepIndex] engine steps. */
    fun fractionAt(stepIndex: Int): Float = ((stepIndex * FORECAST_STEP_DAYS).toFloat() / spanDays).coerceIn(0f, 1f)

    /** x fraction (0..1) for an absolute epoch day inside the window. */
    fun fraction(epochDay: Long): Float = ((epochDay - startEpochDay).toFloat() / spanDays).coerceIn(0f, 1f)
}

/**
 * Month tick labels along the bottom: 3–4 evenly spaced ticks over the window
 * (3 when the window is under ~4 months so labels never collide), always
 * including both ends. Pure — JVM-tested.
 */
internal fun forecastMonthTicks(
    startEpochDay: Long,
    endEpochDay: Long,
    maxTicks: Int = 4,
): List<Long> {
    val span = endEpochDay - startEpochDay
    if (span <= 0) return listOf(startEpochDay)
    val count = if (span >= 120) maxTicks.coerceIn(2, 5) else 3
    return (0 until count)
        .map { index -> startEpochDay + Math.round(span * index.toDouble() / (count - 1)) }
        .distinct()
}

/** The window's end as an epoch day (for the a11y description). */
private fun windowEndDay(bands: WloForecastBands): Long = forecastTimeWindow(bands).endEpochDay
