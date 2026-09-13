package app.wlo.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The border accent of a [WloCard] (WLO-0031): which color the 1 dp hairline
 * carries. [None] is the plain `outline` card; [Primary] marks
 * selected/confirmed emphasis; [Warning] marks held/needs-attention state
 * (amber — no alarm-red exists, R-D1). This kills the hand-built bordered
 * card variants.
 */
public enum class WloCardAccent { None, Primary, Warning }

/**
 * The WLO card (DESIGN-SYSTEM.md §1.4): 20 dp radius, 1 dp `outline` hairline,
 * no drop shadow (dark elevation is surface tint + hairline). Dense by
 * default: [WloSpacing.CARD] between children, [WloSpacing.PAD_CARD] inside.
 *
 * WLO-0031 upgrades:
 * - [onClick] renders a real Material 3 clickable card (correct ripple +
 *   elevation handling). The touch floor is deliberately NOT forced — cards
 *   stay content-sized; rows/controls inside carry their own floors.
 * - [header] is the card's top slot, documented to host [WloCardHeader] —
 *   no more ad-hoc title Texts above or inside cards.
 * - [accent] tints the hairline via [WloCardAccent] instead of hand-built
 *   bordered `Surface` look-alikes.
 *
 * @param onClick optional tap handler; null (default) keeps the card static
 * @param header optional top slot — host [WloCardHeader] here
 * @param accent hairline emphasis, [WloCardAccent.None] by default
 * @param content card body, padded and spaced by the card anatomy
 */
@Composable
public fun WloCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    accent: WloCardAccent = WloCardAccent.None,
    content: @Composable () -> Unit,
): Unit =
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = WloShape.Card,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            border = BorderStroke(1.dp, WloCardBorderColor(accent)),
            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = WloElevation.Level0,
                    pressedElevation = WloElevation.Level0,
                    focusedElevation = WloElevation.Level0,
                    hoveredElevation = WloElevation.Level0,
                    draggedElevation = WloElevation.Level0,
                    disabledElevation = WloElevation.Level0,
                ),
        ) {
            WloCardBody(header, content)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = WloShape.Card,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, WloCardBorderColor(accent)),
        ) {
            WloCardBody(header, content)
        }
    }

/** Hairline color per [WloCardAccent]. */
@Composable
private fun WloCardBorderColor(accent: WloCardAccent): Color =
    when (accent) {
        WloCardAccent.None -> MaterialTheme.colorScheme.outline
        WloCardAccent.Primary -> MaterialTheme.colorScheme.primary
        WloCardAccent.Warning -> wloExtendedColors.held
    }

/** The shared card anatomy: optional header slot, then the body, dense. */
@Composable
private fun WloCardBody(
    header: (@Composable () -> Unit)?,
    content: @Composable () -> Unit,
): Unit =
    Column(
        Modifier.padding(WloSpacing.PAD_CARD),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        header?.invoke()
        content()
    }
