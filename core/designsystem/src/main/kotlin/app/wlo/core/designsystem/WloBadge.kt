package app.wlo.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The status-pill tones (WLO-0031). Every tinted pill — day status, "exact",
 * staple/tag pills, StatusChip / StateBadge / version badges — collapses into
 * one of these. The wash/border alpha pair is the one [WloBanner] convention.
 */
public enum class WloBadgeTone {
    /** Grey — descriptive, stateless ("v1.x", tags, provider names). */
    Neutral,

    /** Accent green — positive/confirmed state ("exact", logged). */
    Accent,

    /** Blue — informational state ("developing", on-device). */
    Info,

    /** Amber — held / needs-attention state; the strongest state color (R-D1). */
    Held,
}

/**
 * Static, non-interactive status pill (WLO-0031): [WloShape.Pill], the
 * 0.12/0.45 alpha convention, `label` type. Nothing here is clickable —
 * selection chips are [SelectChip], actions are buttons.
 *
 * @param text pill copy — short, sentence case (§8: no ALL-CAPS shouts)
 * @param tone [WloBadgeTone] pick
 */
@Composable
public fun WloBadge(
    text: String,
    tone: WloBadgeTone,
    modifier: Modifier = Modifier,
): Unit =
    Surface(
        modifier = modifier,
        shape = WloShape.Pill,
        color = WloBadgeToneColor(tone).copy(alpha = WLO_WASH_ALPHA),
        contentColor = WloBadgeToneColor(tone),
        border = BorderStroke(1.dp, WloBadgeToneColor(tone).copy(alpha = WLO_HAIRLINE_ALPHA)),
    ) {
        Text(
            text = text,
            style = wloType.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }

/** The neutral shorthand of [WloBadge] — a tag: grey pill, no state claim. */
@Composable
public fun WloTag(
    text: String,
    modifier: Modifier = Modifier,
): Unit = WloBadge(text = text, tone = WloBadgeTone.Neutral, modifier = modifier)

/** Pill color per [WloBadgeTone]. */
@Composable
private fun WloBadgeToneColor(tone: WloBadgeTone): Color =
    when (tone) {
        WloBadgeTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        WloBadgeTone.Accent -> MaterialTheme.colorScheme.primary
        WloBadgeTone.Info -> wloExtendedColors.developing
        WloBadgeTone.Held -> wloExtendedColors.held
    }
