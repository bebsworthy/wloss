package app.wlo.feature.f01.onboarding.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.model.DerivedValue
import app.wlo.feature.f01.onboarding.state.IntakeTargetState
import kotlin.math.ceil
import kotlin.math.floor
import app.wlo.core.engines.IntakeProjection as ProjectionData

@Composable
internal fun IntakeProjection(state: IntakeTargetState) {
    val bands = state.forecast
    Column(Modifier.padding(top = 15.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = (24.5f * LocalDensity.current.fontScale).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("With this intake", style = intakeText(13, weight = androidx.compose.ui.text.font.FontWeight.Medium))
            bands?.let {
                ProvenanceChip(
                    DerivedValue(state.intake ?: 0.0, it.provenance),
                    ::kcal,
                    compact = true,
                    label = if (state.measured) "From your logs" else "Estimated",
                    labelColor = wloExtendedColors.developing,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(166.dp), contentAlignment = Alignment.Center) {
            if (bands != null && state.weight != null) {
                IntakeProjectionChart(state, bands)
            } else {
                Text(
                    state.projectionNote,
                    style = intakeText(12, 18f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            if (bands == null) "" else intakeProjectionOutcome(state),
            minLines = 2,
            style = intakeText(12, 18f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Flow 10 chart anatomy. M3 has no forecast/chart component; Canvas renders only engine-provided
 * trajectories, with standard M3 Text and provenance components around it (WLO-0126).
 * A fixed six-month viewport renders the intake scenario, including beyond a target crossing.
 */
@Composable
private fun IntakeProjectionChart(
    state: IntakeTargetState,
    bands: ProjectionData,
) {
    val start = state.weight ?: return
    val goal =
        state.record
            ?.document
            ?.goal
            ?.targetWeightKg ?: start
    val fast = bands.lowerKg
    val slow = bands.upperKg
    val range = fast + slow + goal
    val maximum = ceil(range.max()) + 1
    val minimum = floor(range.min()) - 1
    val measurer = rememberTextMeasurer()
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outlineVariant
    val foreground = MaterialTheme.colorScheme.onSurface
    val gold = wloExtendedColors.chartGoal
    val fill = wloExtendedColors.developing.copy(alpha = 0.20f)
    val axisStyle = intakeText(11)
    Canvas(Modifier.fillMaxWidth().height(166.dp).semantics { contentDescription = intakeProjectionOutcome(state) }) {
        // The approved SVG's 340×166 viewBox is centered inside its 344px column.
        val scale = minOf(size.width / 340f, size.height / 166f)
        val inset = (size.width - 340f * scale) / 2

        fun x(week: Int): Float = inset + (12 + week / bands.weeks.toFloat() * 276) * scale

        fun y(weight: Double): Float = (16 + (maximum - weight) / (maximum - minimum) * 118).toFloat() * scale

        fun label(
            text: String,
            left: Float,
            baseline: Float,
            goalLabel: Boolean = false,
        ) {
            val layout = measurer.measure(text, axisStyle.copy(color = if (goalLabel) gold else secondary))
            drawText(layout, topLeft = Offset(left, baseline - layout.firstBaseline))
        }
        drawLine(line, Offset(x(0), y(start)), Offset(inset + 290 * scale, y(start)), 1.dp.toPx())
        val path = Path()
        fast.forEachIndexed { index, weight ->
            if (index == 0) path.moveTo(x(index), y(weight)) else path.lineTo(x(index), y(weight))
        }
        slow.indices.reversed().forEach { path.lineTo(x(it), y(slow[it])) }
        path.close()
        drawPath(path, fill)
        if (fast == slow) {
            for (i in 1 until fast.size) {
                drawLine(foreground, Offset(x(i - 1), y(fast[i - 1])), Offset(x(i), y(fast[i])), 1.dp.toPx())
            }
        }
        drawLine(
            gold,
            Offset(x(0), y(goal)),
            Offset(inset + 290 * scale, y(goal)),
            1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
        drawCircle(foreground, 3 * scale, Offset(x(0), y(start)))
        label(massUnit(state).formatNumber(start), inset + 299 * scale, y(start) + 4 * scale)
        label(massUnit(state).formatNumber(goal), inset + 299 * scale, y(goal) + 4 * scale, true)
        label("Now", x(0), 160 * scale)
        val middleLabel = horizonLabel(bands.weeks / 2)
        val middle = measurer.measure(middleLabel, axisStyle)
        label(middleLabel, inset + 148 * scale - middle.size.width / 2, 160 * scale)
        val endLabel = horizonLabel(bands.weeks)
        val end = measurer.measure(endLabel, axisStyle)
        label(endLabel, inset + 288 * scale - end.size.width, 160 * scale)
    }
}

/** Goal-window copy is derived from supported outer-band arrivals, never a fabricated point date. */
internal fun intakeProjectionOutcome(state: IntakeTargetState): String {
    val bands = state.forecast ?: return state.projectionNote
    val start = bands.expectedKg.first()
    val goal =
        state.record
            ?.document
            ?.goal
            ?.targetWeightKg ?: start
    val end = bands.expectedKg.last()
    if (kotlin.math.abs(end - start) < 0.01) return "Around your current weight"
    if (goal == start) return "Moving away from maintenance"
    if ((end - start) * (goal - start) < 0) return "Moving away from your weight goal"
    val (early, late) = bands.crossingWeeks(goal)
    if (early == null) {
        val limit = bands.equilibriumKg
        if (limit != null && (goal - limit) * (start - limit) <= 0) {
            return "At this intake, the projection levels off before your goal"
        }
        return "A goal arrival is beyond the model’s calculation range"
    }
    if (late == null) return "From ${horizonLabel(ceil(early).toInt())} · later arrival uncertain"
    return if (late < 13) {
        "Estimated goal window · ${ceil(early).toInt().coerceAtLeast(1)}–${ceil(late).toInt().coerceAtLeast(1)} weeks"
    } else {
        "Estimated goal window · ${ceil(early / 4.345).toInt().coerceAtLeast(1)}–${ceil(late / 4.345).toInt()} months"
    }
}

private fun horizonLabel(weeks: Int): String =
    if (weeks < 9) "$weeks weeks" else "${kotlin.math.round(weeks / 4.345).toInt()} months"
