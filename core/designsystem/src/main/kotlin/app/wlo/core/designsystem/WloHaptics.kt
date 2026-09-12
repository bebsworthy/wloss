package app.wlo.core.designsystem

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The "kind haptics" vocabulary (DESIGN-SYSTEM.md §5, ruling R-D6): every
 * data-outcome haptic is Confirm-class, short and light, regardless of whether
 * the news is good. [WloHaptic.Reject] is reserved for true blocking errors
 * (invalid BYOK key, corrupt import, the calorie-floor wall) — never for a
 * data result. Haptics bypass reduced motion and are the fallback feedback
 * channel when animation is off.
 *
 * Single implementation ([ViewWloHaptics]); SDK-gated so API 30/34 behaviors
 * degrade gracefully on API 29 (ClockTick/VirtualKey/LongPress fallbacks).
 */
public enum class WloHaptic {
    /** "tick", "soft tick", "micro-tick" — Confirm-class (R-D6). */
    Tick,

    /** "double-tick", "crisp tick-tick" — Confirm x2 (apply, ate-this, shield close). */
    DoubleTick,

    /** Detent / notch / per-point tick — sliders, carousels, scrubs. */
    SegmentTick,

    /** Dense detents — portion sliders, live scrubs. */
    SegmentFrequentTick,

    /** "one low 'settled'", "soft settle" — save arc, drag land. */
    Settle,

    /** "deep single impact" — PR banner only. */
    Impact,

    /** The one sanctioned reject — blocking errors only (R-D6), never data outcomes. */
    Reject,

    /** "two-note celebration", rising motif — milestones only. */
    Celebrate,
}

/** Composable-level haptics API; fakes may implement for previews/tests. */
public interface WloHaptics {
    /** Perform [haptic]; degrades gracefully where the platform cannot express it. */
    public fun perform(haptic: WloHaptic): Unit
}

/** The one implementation: [View.performHapticFeedback] + Vibrator, SDK-gated. */
private class ViewWloHaptics(
    private val view: View,
) : WloHaptics {
    private val confirmOrTick: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }

    override fun perform(haptic: WloHaptic) {
        when (haptic) {
            WloHaptic.Tick -> view.performHapticFeedback(confirmOrTick)

            WloHaptic.DoubleTick -> {
                view.performHapticFeedback(confirmOrTick)
                view.postDelayed({ view.performHapticFeedback(confirmOrTick) }, DOUBLE_TICK_GAP_MS)
            }

            WloHaptic.SegmentTick ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }

            WloHaptic.SegmentFrequentTick ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }

            WloHaptic.Settle ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }

            WloHaptic.Impact -> predefined(VibrationEffect.EFFECT_HEAVY_CLICK)

            WloHaptic.Reject ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                } else {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }

            WloHaptic.Celebrate -> {
                // Rising motif: light first beat, heavier landing beat.
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                view.postDelayed(
                    { predefined(VibrationEffect.EFFECT_CLICK) },
                    CELEBRATE_RISE_MS,
                )
            }
        }
    }

    /** Deep impacts go through the Vibrator's predefined effects; falls back to a view tick. */
    private fun predefined(effectId: Int) {
        val vibrator = vibrator()
        if (vibrator != null) {
            vibrator.vibrate(VibrationEffect.createPredefined(effectId))
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun vibrator(): Vibrator? {
        val context: Context = view.context
        val service = context.getSystemService(Context.VIBRATOR_SERVICE)
        return service as? Vibrator
    }

    private companion object {
        const val DOUBLE_TICK_GAP_MS = 80L
        const val CELEBRATE_RISE_MS = 120L
    }
}

/** Remember the app's [WloHaptics] bound to the current view. */
@Composable
public fun rememberWloHaptics(): WloHaptics {
    val view = LocalView.current
    return remember(view) { ViewWloHaptics(view) }
}
