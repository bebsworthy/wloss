package app.wlo.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion tokens (DESIGN-SYSTEM.md §4): two easing curves and two springs cover
 * everything. Durations snap to the M3 scale. Reduced motion (animator scale
 * off) collapses every animation to an instant settle or a 100 ms crossfade —
 * haptics are unaffected and become the primary feedback channel.
 */
public object WloMotion {
    /** Enter / settle = EmphasizedDecelerate (0.05, 0.7, 0.1, 1.0). */
    public val EasingEnter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Exit / dismiss = EmphasizedAccelerate (0.3, 0, 0.8, 0.15). */
    public val EasingExit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** M3-scale duration tokens, in ms (short 3 / medium 2 / long 2 / XL 1). */
    public const val DURATION_SHORT_MS: Int = 150
    public const val DURATION_MEDIUM_MS: Int = 300
    public const val DURATION_LONG_MS: Int = 500
    public const val DURATION_XL_MS: Int = 700

    /** The one permitted reduced-motion crossfade. */
    public const val DURATION_REDUCED_MOTION_MS: Int = 100

    /** Enter/settle tween on [EasingEnter]; [ms] snaps to the M3 duration scale. */
    public fun <T> enter(ms: Int = DURATION_MEDIUM_MS): AnimationSpec<T> = tween(ms, easing = EasingEnter)

    /** Exit/dismiss tween on [EasingExit]; [ms] snaps to the M3 duration scale. */
    public fun <T> exit(ms: Int = DURATION_SHORT_MS): AnimationSpec<T> = tween(ms, easing = EasingExit)

    /** Springs for count-ups, ring fills, card entrances (damping 0.8, stiffness 380). */
    public object Springs {
        public val Soft: AnimationSpec<Float> =
            spring(dampingRatio = 0.8f, stiffness = 380f)

        /** Detents, check-offs, confirm morphs (damping 1.0, stiffness 800+). */
        public val Snap: AnimationSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f)
    }

    /** Under reduced motion every spec collapses to this 100 ms crossfade. */
    public val ReducedMotion: AnimationSpec<Float> = tween(DURATION_REDUCED_MOTION_MS, easing = LinearEasing)
}
