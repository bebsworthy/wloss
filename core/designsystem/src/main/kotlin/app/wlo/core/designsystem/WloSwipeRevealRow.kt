package app.wlo.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The swipe-to-reveal trigger row (WLO-0050). Material 3's
 * `SwipeToDismissBox` cannot implement WLO's release-gated, velocity-blind
 * trigger at a custom 90% threshold: its anchored swipe state may settle from
 * velocity and models dismissal rather than a non-overlapping reveal. This
 * custom gesture therefore keeps the content as one horizontally moving piece,
 * revealing a trailing action in the space it vacates — never a color fill,
 * never an overlap.
 *
 * The action NEVER fires mid-drag and velocity is ignored entirely — a
 * flick can never trigger it. The decision is made exactly once, on
 * release: past the trigger distance the action fires; short of it the row
 * springs back. Crossing the trigger mid-drag arms the affordance —
 * [reveal]'s `armed` flag flips (pair it with a color morph or equivalent)
 * and a single tick haptic marks the moment, so the point of no return is
 * visible before the user lifts the finger.
 *
 * The content is expected to be opaque in the card's own color: it masks
 * the action layer while at rest and reads as the piece being swiped.
 * Vertical scrolling keeps priority — the drag claims the gesture only
 * after horizontal touch slop.
 *
 * @param revealWidth how far the content can travel (the action strip's width)
 * @param onTrigger called on release with the drag held at/after the trigger
 * @param reveal the trailing action; [progress] 0..1 drives any fade-in,
 *   `armed` is true once the trigger is crossed
 * @param triggerFraction fraction of [revealWidth] that arms the action
 * @param content the row itself
 */
@Composable
public fun WloSwipeRevealRow(
    revealWidth: Dp,
    onTrigger: () -> Unit,
    reveal: @Composable (progress: Float, armed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    triggerFraction: Float = 0.9f,
    content: @Composable () -> Unit,
) {
    val revealPx = with(LocalDensity.current) { revealWidth.toPx() }
    val triggerPx = revealPx * triggerFraction
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val haptics = rememberWloHaptics()

    // The arm tick: exactly one haptic per crossing, in either direction.
    LaunchedEffect(revealPx, triggerPx) {
        snapshotFlow { offset.value >= triggerPx }
            .distinctUntilChanged()
            .collect { armed -> if (armed) haptics.perform(WloHaptic.Tick) }
    }

    Box(modifier = modifier.clipToBounds()) {
        // The action strip, trailing side — sized to the content's own
        // height via matchParentSize (fillMaxHeight alone cannot work here:
        // lazy containers measure children with unbounded height, which
        // would collapse the strip and top-align it). It is revealed in the
        // space the content vacates, drawn without any fill of its own.
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(revealWidth),
            ) {
                val progress by remember(revealPx) { derivedStateOf { (offset.value / revealPx).coerceIn(0f, 1f) } }
                val armed by remember(triggerPx) { derivedStateOf { offset.value >= triggerPx } }
                reveal(progress, armed)
            }
        }
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(-offset.value.roundToInt(), 0) }
                    .pointerInput(revealPx, triggerPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val dragPointer =
                                awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                    change.consume()
                                }
                            if (dragPointer == null) return@awaitEachGesture
                            var travelled = 0f
                            val completed =
                                horizontalDrag(dragPointer.id) { change ->
                                    val dx = change.positionChange().x
                                    change.consume()
                                    if (dx != 0f) {
                                        travelled = (travelled - dx).coerceIn(0f, revealPx)
                                        scope.launch { offset.snapTo(travelled) }
                                    }
                                }
                            if (completed && travelled >= triggerPx) {
                                scope.launch { offset.animateTo(revealPx, WloMotion.Springs.Snap) }
                                onTrigger()
                            } else {
                                scope.launch { offset.animateTo(0f, WloMotion.Springs.Snap) }
                            }
                        }
                    },
        ) { content() }
    }
}
