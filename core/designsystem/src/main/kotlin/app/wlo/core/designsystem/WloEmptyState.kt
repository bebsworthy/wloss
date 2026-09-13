package app.wlo.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The teaching empty state (DESIGN-SYSTEM.md §7.1 "Empty state (teaching)",
 * promised there and never shipped — WLO-0031): centered glyph, title, body,
 * and the one next action. No blank failures: F02/F06 rule — the empty state
 * teaches the next action.
 *
 * @param title what this space is, one line
 * @param icon optional glyph above the title
 * @param body optional teaching line — the next action in words
 * @param actionLabel CTA copy — sentence case, no guilt (§8)
 * @param action CTA handler; rendered only with [actionLabel]
 */
@Composable
public fun WloEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    body: String? = null,
    actionLabel: String? = null,
    action: (() -> Unit)? = null,
): Unit =
    Column(
        modifier = modifier.fillMaxWidth().padding(WloSpacing.SCREEN),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD, Alignment.CenterVertically),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = wloExtendedColors.textTertiary,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(text = title, style = wloType.title, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                text = body,
                style = wloType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null && actionLabel != null) {
            WloButton(label = actionLabel, onClick = action)
        }
    }
