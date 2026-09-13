package app.wlo.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The Hub quick-action tile (WLO-0031): icon over label on a bordered surface
 * card — the sanctioned replacement for hand-rolled clickable Columns in the
 * F10 action rail. Long-press is the config escape hatch (§3: config lives
 * behind long-press); haptics stay caller-side ([WloHaptics]).
 *
 * @param icon the tile's glyph, tinted `primary`
 * @param label short tile copy, sentence case
 * @param onClick tap handler
 * @param onLongClick optional long-press (config/secondary) handler
 */
@Composable
public fun WloRailButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
): Unit =
    Surface(
        modifier =
            modifier
                .clip(WloShape.Card)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(WloSpacing.PAD_CARD).widthIn(min = WloSpacing.TOUCH_PRIMARY),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = wloType.label,
                textAlign = TextAlign.Center,
            )
        }
    }
