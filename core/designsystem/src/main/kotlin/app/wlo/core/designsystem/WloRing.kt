package app.wlo.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The ring sweep spec — DESIGN-SYSTEM.md §4.1, F10 "Budget ring sweep" row,
 * verbatim: "400 ms ease-out per log; soft tick at sweep end" (token M3, i.e.
 * the enter/settle decel curve, not the soft spring — the row froze a tween).
 */
private val RingSweep: AnimationSpec<Float> = WloMotion.enter(ms = 400)

/**
 * The WLO progress ring (the Hub calorie ring, F10 "budget ring sweep";
 * DESIGN-SYSTEM.md §4.1): a determinate circle on Canvas — full track in the
 * `outline` chrome, the progress arc starting at 12 o'clock sweeping
 * clockwise, and [centerContent] centered in the ring (the consumed stat plus
 * the small "of 1,900 kcal" line). The arc is a valence-free fill: the atom
 * never judges the fraction (no red exists, R-D1) — the owning surface decides
 * what "over budget" means.
 *
 * Motion is the F10 row verbatim: the displayed fraction tweens to its target
 * over 400 ms on the enter/settle easing, per change — and on first
 * composition too, so a Hub card appearing mid-day sweeps from 0 to its value
 * rather than popping. A soft tick lands at sweep end ([WloHaptic.Tick], the
 * §5 "soft tick" mapping); the valence-dependent in-band-evening landing
 * stays with the owning surface, which knows the band.
 *
 * @param progress 0..1, clamped — consumed/budget, capped at 1.0
 * @param ringColor arc fill; defaults to the extended palette's
 *   positive-progress accent ([wloExtendedColors.accentDim], the schedule
 *   bars' and trend ribbon's fill)
 * @param trackColor full-circle chrome behind the arc; `outline` hairline token
 * @param strokeWidth arc thickness, round-capped like every WLO mark
 * @param centerContent slot rendered centered in the ring (stats, labels)
 */
@Composable
public fun WloRing(
    progress: Float,
    modifier: Modifier = Modifier,
    ringColor: Color = wloExtendedColors.accentDim,
    trackColor: Color = MaterialTheme.colorScheme.outline,
    strokeWidth: Dp = 10.dp,
    centerContent: @Composable BoxScope.() -> Unit = {},
) {
    val haptics = rememberWloHaptics()
    val target = progress.coerceIn(0f, 1f)

    // The first composition animates too: the ring starts empty and sweeps to
    // its value instead of popping in at full fraction.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val sweep by animateFloatAsState(
        targetValue = if (appeared) target else 0f,
        animationSpec = RingSweep,
        label = "WloRingSweep",
        finishedListener = { haptics.perform(WloHaptic.Tick) },
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arc = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Skip the empty arc: a round cap would paint a dot at 12 o'clock.
            if (sweep > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Box(modifier = Modifier.align(Alignment.Center), content = centerContent)
    }
}
