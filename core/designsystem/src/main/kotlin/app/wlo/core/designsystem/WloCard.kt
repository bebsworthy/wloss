package app.wlo.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The WLO card (DESIGN-SYSTEM.md §1.4): 20 dp radius, 1 dp `outline` hairline,
 * no drop shadow (dark elevation is surface tint + hairline). Dense by
 * default: [WloSpacing.CARD] between children, [WloSpacing.PAD_CARD] inside.
 */
@Composable
public fun WloCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
): Unit =
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            Modifier.padding(WloSpacing.PAD_CARD),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        ) {
            content()
        }
    }
