package app.wlo.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wlo.core.model.DerivedValue
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The 7-bar weekday/weekend calorie schedule (F01 §3 step 6, Lose It!-lineage
 * cycling): drag any bar and its six siblings compensate in real time — the
 * pinned weekly total is the visible invariant (Appendix A validation 3: the
 * schedule sums EXACTLY to the weekly budget). The dashed reference line is
 * the flat budget the drag started from. Bars refuse to cross the calorie
 * floor; a refused drag loses the un-absorbable remainder (never a silent
 * sum drift). Weekday/weekend tints follow the accent-dim two-step.
 *
 * The pinned total is D6-typed ([DerivedValue]); bar numerals are drawn by
 * this designsystem component itself (the sanctioned single rendering owner).
 */
@Composable
public fun WloScheduleBarChart(
    scheduleKcal: List<Double>,
    flatKcal: Double,
    floorKcal: Double,
    pinnedWeeklyTotal: DerivedValue<Double>,
    formatKcal: (Double) -> String,
    onScheduleChange: (List<Double>) -> Unit,
    modifier: Modifier = Modifier,
    onDetent: (() -> Unit)? = null,
) {
    // Bar numerals are compact (no unit) so seven of them fit side by side;
    // the unit-bearing figures live in the pinned-total chip + the caption.
    val formatBar: (Double) -> String = { value -> formatKcal(value).removeSuffix(" kcal") }
    val textMeasurer = rememberTextMeasurer()
    // Drag state: which bar is grabbed and how far it has travelled (kcal).
    var dragIndex by remember { mutableFloatStateOf(-1f) }
    var lastDetent by remember { mutableFloatStateOf(0f) }

    val barFill = wloExtendedColors.accentDim
    val weekendFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    val chrome = wloExtendedColors.textTertiary
    val outline = MaterialTheme.colorScheme.outline
    val labelStyle =
        TextStyle(
            fontFamily = WloFontFamily,
            fontSize = 10.sp,
            fontFeatureSettings = WloFontFeatures.TABULAR,
            color = MaterialTheme.colorScheme.onSurface,
        )
    val axisStyle = labelStyle.copy(color = chrome)

    val description =
        "Calorie schedule chart, seven bars, Monday to Sunday. Drag a bar and the " +
            "others compensate so the weekly total stays ${formatKcal(pinnedWeeklyTotal.value)}."

    Column(modifier = modifier) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .semantics { contentDescription = description }
                    .pointerInput(scheduleKcal, floorKcal) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragIndex = barAt(offset.x, size.width.toFloat(), scheduleKcal.size).toFloat()
                            },
                            onDragEnd = {
                                dragIndex = -1f
                                lastDetent = 0f
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            val index = dragIndex.toInt()
                            if (index in scheduleKcal.indices) {
                                val delta = -dragAmount.y * KCAL_PER_PX
                                val next = pinnedDrag(scheduleKcal, index, delta.toDouble(), floorKcal)
                                val moved = next[index] - scheduleKcal[index]
                                if (abs(moved - lastDetent) >= DETENT_KCAL) {
                                    lastDetent = moved.toFloat()
                                    onDetent?.invoke()
                                }
                                if (next != scheduleKcal) onScheduleChange(next)
                            }
                        }
                    },
        ) {
            drawSchedule(
                schedule = scheduleKcal,
                flatKcal = flatKcal,
                floorKcal = floorKcal,
                barFill = barFill,
                weekendFill = weekendFill,
                chrome = chrome,
                outline = outline,
                labelStyle = labelStyle,
                axisStyle = axisStyle,
                textMeasurer = textMeasurer,
                formatKcal = formatBar,
                dragIndex = dragIndex.toInt(),
            )
        }

        Spacer(Modifier.height(WloSpacing.TIGHT))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "pinned weekly total",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
            )
            Spacer(Modifier.padding(horizontal = WloSpacing.TIGHT))
            ProvenanceChip(value = pinnedWeeklyTotal, format = formatKcal)
        }
    }
}

private fun DrawScope.drawSchedule(
    schedule: List<Double>,
    flatKcal: Double,
    floorKcal: Double,
    barFill: Color,
    weekendFill: Color,
    chrome: Color,
    outline: Color,
    labelStyle: TextStyle,
    axisStyle: TextStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    formatKcal: (Double) -> String,
    dragIndex: Int,
) {
    val n = schedule.size.coerceAtLeast(1)
    val left = 4f
    val right = size.width - 4f
    // Label lanes are dp-scaled so the chrome never clips: the top lane holds
    // the bar numerals, the bottom lane the weekday glyphs (both used to be
    // fixed px lanes and clipped on dense screens).
    val top = 34.dp.toPx()
    val axisLane = 22.dp.toPx()
    val bottom = size.height - axisLane
    val numeralLane = 22.dp.toPx()
    val dayLabelGap = 5.dp.toPx()
    val slot = (right - left) / n
    val barWidth = (slot * 0.62f).coerceAtMost(34.dp.toPx())

    val maxKcal = (schedule.maxOrNull() ?: flatKcal).coerceAtLeast(flatKcal).coerceAtLeast(floorKcal) * 1.12

    fun barTop(kcal: Double): Float = bottom - ((kcal / maxKcal) * (bottom - top)).toFloat()

    // Flat reference (the budget you started from), dashed, chrome.
    drawLine(
        color = chrome,
        start = Offset(left, barTop(flatKcal)),
        end = Offset(right, barTop(flatKcal)),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
    )

    // Floor: hairline — bars refuse to cross it.
    drawLine(
        color = outline,
        start = Offset(left, barTop(floorKcal)),
        end = Offset(right, barTop(floorKcal)),
        strokeWidth = 1f,
    )

    schedule.forEachIndexed { index, kcal ->
        val cx = left + slot * index + slot / 2f
        val fill = if (index >= 5) weekendFill else barFill
        val extraLift = if (index == dragIndex) 2.dp.toPx() else 0f
        drawRoundRect(
            color = fill,
            topLeft = Offset(cx - barWidth / 2f, barTop(kcal) - extraLift),
            size = Size(barWidth, bottom - barTop(kcal) + extraLift),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(6f, 6f),
        )
        // Numeral above the bar (tabular figures; the component owns rendering).
        drawText(
            textMeasurer = textMeasurer,
            text = formatKcal(kcal),
            style = labelStyle,
            topLeft = Offset(cx - 23.dp.toPx(), barTop(kcal) - numeralLane),
        )
        // Day letter — sits fully inside the bottom lane, never clipped.
        drawText(
            textMeasurer = textMeasurer,
            text = DAYS[index],
            style = axisStyle,
            topLeft = Offset(cx - 6f, bottom + dayLabelGap),
        )
    }
}

/** Map an x position to a bar index (drag hit-test). */
private fun barAt(
    x: Float,
    width: Float,
    count: Int,
): Int {
    val left = 4f
    val right = width - 4f
    val slot = (right - left) / count.coerceAtLeast(1)
    val index = ((x - left) / slot).toInt()
    return index.coerceIn(0, count - 1)
}

/**
 * The pinned-total drag: [added] kcal go to `schedule[index]`, taken evenly
 * from the six siblings, each clamped at [floorKcal]; whatever the siblings
 * could not absorb is dropped (the bar simply stops rising). Sum is invariant.
 */
internal fun pinnedDrag(
    schedule: List<Double>,
    index: Int,
    added: Double,
    floorKcal: Double,
): List<Double> {
    if (index !in schedule.indices || added == 0.0) return schedule
    val result = schedule.toMutableList()
    var remaining = added
    val siblings = (schedule.indices).filter { it != index }
    repeat(2) {
        if (abs(remaining) < MIN_DRAG_KCAL) return result
        val per = remaining / siblings.size
        var absorbed = 0.0
        for (j in siblings) {
            val target = result[j] - per
            val clamped = target.coerceAtLeast(floorKcal)
            absorbed += result[j] - clamped
            result[j] = clamped
        }
        val barTarget = (result[index] + absorbed).coerceAtLeast(floorKcal)
        val actual = barTarget - result[index]
        result[index] = barTarget
        remaining -= actual
    }
    return result.toList()
}

/** Rounds schedule bars to whole kcal for a stable display (total stays pinned via re-spread). */
public fun normalizeSchedule(schedule: List<Double>): List<Double> {
    if (schedule.isEmpty()) return schedule
    val rounded = schedule.map { it.roundToInt().toDouble() }
    val drift = schedule.sum() - rounded.sum()
    val richest = rounded.indices.maxBy { rounded[it] }
    return rounded.mapIndexed { index, value -> if (index == richest) value + drift else value }
}

private val DAYS: List<String> = listOf("M", "T", "W", "T", "F", "S", "S")

private const val DETENT_KCAL = 25.0
private const val MIN_DRAG_KCAL = 1.0
private const val KCAL_PER_PX = 9f
