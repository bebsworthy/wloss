package app.wlo.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * THE callout convention (WLO-0031): a banner is a 0.12 alpha wash of its
 * tone color with a 0.45 alpha 1 dp hairline — no other alpha pair is
 * sanctioned. Replaces the divergent ErrorCard / WloWallCard / NoticeBanner /
 * ReconciliationBanner / outlier / verdict callouts (washes previously 0.10,
 * 0.12, 0.14, 0.16).
 */
internal const val WLO_WASH_ALPHA: Float = 0.12f

/** [WLO_WASH_ALPHA]'s pair: the 1 dp hairline over a wash reads at 0.45. */
internal const val WLO_HAIRLINE_ALPHA: Float = 0.45f

/** The two callout tones (§1.2 semantic states, no moral valence). */
public enum class WloBannerTone {
    /** Blue informational callout — facts, receipts, provenance notes. */
    Info,

    /** Amber callout — held data, needs-attention, destructive-adjacent. */
    Warning,
}

/**
 * The ONE callout component (WLO-0031): tinted surface + optional action.
 * Copy is the caller's job (§8: no guilt, no rationale leaks, sentence case).
 * Numbers rendered inside a banner body must go through a provenance atom —
 * this component takes prose only (D6).
 *
 * @param text the callout copy
 * @param tone [WloBannerTone.Info] or [WloBannerTone.Warning]
 * @param actionLabel label of the optional trailing action — copy, sentence case
 * @param action optional tap handler rendered next to [actionLabel]
 */
@Composable
public fun WloBanner(
    text: String,
    tone: WloBannerTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    action: (() -> Unit)? = null,
): Unit =
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = WloShape.Chip,
        color = WloBannerToneColor(tone).copy(alpha = WLO_WASH_ALPHA),
        contentColor = WloBannerToneColor(tone),
        border = BorderStroke(1.dp, WloBannerToneColor(tone).copy(alpha = WLO_HAIRLINE_ALPHA)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = WloSpacing.PAD_CARD, vertical = WloSpacing.CARD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        ) {
            Text(
                text = text,
                style = wloType.body,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (action != null && actionLabel != null) {
                Text(
                    text = actionLabel,
                    style = wloType.label,
                    modifier =
                        Modifier
                            .heightIn(min = WloSpacing.ROW_INTERACTIVE)
                            .clickable(onClickLabel = actionLabel, onClick = action)
                            .padding(vertical = WloSpacing.CARD),
                )
            }
        }
    }

/** Callout color per [WloBannerTone]. */
@Composable
private fun WloBannerToneColor(tone: WloBannerTone): Color =
    when (tone) {
        WloBannerTone.Info -> wloExtendedColors.info
        WloBannerTone.Warning -> wloExtendedColors.held
    }
