package app.wlo.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One selection chip (DESIGN-SYSTEM.md §7.1 shared atom), re-based on
 * Material 3's FilterChip (owner review WLO-0030, defect 3): alignment, the
 * 32 dp height and the hairline stroke come from M3 defaults, not hand-rolled
 * surfaces. State colors stay WLO: selected is a 16% primary wash with a
 * primary label and primary stroke; unselected is transparent with an
 * `onSurfaceVariant` label and `outline` stroke. Label renders in `label`
 * (§2). Shared by F02 (slots, presets, units), F06 (smoothers, methods) and
 * F10 (quick actions).
 */
@Composable
public fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
): Unit =
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, style = wloType.label) },
        modifier = modifier,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
    )
