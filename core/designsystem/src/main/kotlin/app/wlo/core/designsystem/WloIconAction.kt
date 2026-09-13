package app.wlo.core.designsystem

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The icon action (WLO-0031): a real Material 3 [IconButton] (ghost) or
 * [FilledIconButton] — the sanctioned replacement for bare icon clickables.
 * Touch floor is [WloSpacing.TOUCH_PRIMARY] (48 dp); large custom targets
 * (the capture shutter) pass a bigger [size].
 *
 * @param imageVector the glyph
 * @param contentDescription accessibility label — never null for actions
 * @param onClick tap handler
 * @param filled false = ghost [IconButton]; true = filled circle action
 * @param size button side (48 dp floor; the shutter goes bigger)
 * @param iconSize glyph side (24 dp default; scale up with [size])
 */
@Composable
public fun WloIconAction(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = WloSpacing.TOUCH_PRIMARY,
    iconSize: Dp = DEFAULT_ICON_SIZE,
): Unit =
    if (filled) {
        FilledIconButton(
            onClick = onClick,
            modifier = modifier.size(size),
            shape = WloShape.Circle,
        ) {
            Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier.size(size)) {
            Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
        }
    }

private val DEFAULT_ICON_SIZE: Dp = 24.dp
