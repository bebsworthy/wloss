package app.wlo.core.designsystem

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * WLO's primary action (owner review WLO-0030, defect 4: bordered Surfaces
 * with left-aligned text are not buttons). A real Material 3 filled
 * [Button]: centered label, WLO type via the mapped Material typography,
 * native M3 shape role, and the §3 `touch-primary` 48 dp floor for daily-use
 * targets. Label copy is the caller's job (sentence case, zero guilt, §8).
 *
 * Fill comes from the theme's `primary` (WLO accent); use
 * [WloSecondaryButton] for config/secondary actions.
 *
 * @param label button copy — the caller writes the words
 */
@Composable
public fun WloButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
): Unit =
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = WloSpacing.TOUCH_PRIMARY),
        enabled = enabled,
    ) {
        Text(text = label)
    }

/**
 * WLO's secondary action — a real Material 3 [OutlinedButton] with the same
 * geometry as [WloButton] (centered label, native shape, 48 dp touch floor);
 * the hairline `outline` stroke is M3's own. Use for config, secondary and
 * destructive-adjacent actions; the filled button stays primary (§3: primary
 * actions bottom-third, config top-right or behind long-press).
 *
 * @param label button copy — the caller writes the words
 */
@Composable
public fun WloSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
): Unit =
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = WloSpacing.TOUCH_PRIMARY),
        enabled = enabled,
    ) {
        Text(text = label)
    }
