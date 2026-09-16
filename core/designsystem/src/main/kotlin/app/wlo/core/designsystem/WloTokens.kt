package app.wlo.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing tokens (DESIGN-SYSTEM.md §3): a 4 dp base grid, dense by default.
 * Touch floors are absolute — primary daily-use targets never drop below
 * [WloSpacing.TOUCH_PRIMARY]; nothing is ever below 24 dp (WCAG 2.2 §2.5.8 AA).
 */
public object WloSpacing {
    /** Screen margins, between-card spacing. */
    public val SCREEN: Dp = 16.dp

    /** Intra-card stack spacing (dense by default). */
    public val CARD: Dp = 8.dp

    /** Stat row pairs, chip clusters. */
    public val TIGHT: Dp = 4.dp

    /** Card padding (14–16 dp per spec). */
    public val PAD_CARD: Dp = 15.dp

    /** Display-only dense list rows. */
    public val ROW_MIN: Dp = 40.dp

    /** Secondary interactive rows. */
    public val ROW_INTERACTIVE: Dp = 48.dp

    /** All primary daily-use targets (shutter, confirm, check-off...). */
    public val TOUCH_PRIMARY: Dp = 48.dp
}

/**
 * Shape tokens (DESIGN-SYSTEM.md §1.4): cards 20 dp, chips/stamps 8 dp,
 * sheets 28 dp top, full-round pills. Dark elevation is surface tint steps
 * plus 1 dp hairlines — no drop shadows.
 */
public object WloShape {
    public val Card: RoundedCornerShape = RoundedCornerShape(20.dp)
    public val Chip: RoundedCornerShape = RoundedCornerShape(8.dp)
    public val SheetTop: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    public val Pill: RoundedCornerShape = RoundedCornerShape(50)
    public val Circle: Shape = CircleShape
}

/**
 * Elevation tokens. WLO's dark theme expresses elevation as surface tint
 * steps (`bg -> surface -> surface-raised`, i.e. M3 `surfaceContainer*`
 * roles) plus 1 dp `outline` hairlines — shadows read as dirt at low
 * luminance. The dp ladder exists only for M3 components that require one.
 */
public object WloElevation {
    public val Level0: Dp = 0.dp
    public val Level1: Dp = 1.dp
    public val Level2: Dp = 3.dp
    public val Level3: Dp = 6.dp
}
