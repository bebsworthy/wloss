package app.wlo.core.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * The WLO switch skin, centralized (WLO-0031): primary thumb/track when
 * checked, `surfaceSunken` + hairline when not. Settings screens that need a
 * bare switch use this instead of repeating [SwitchDefaults.colors].
 */
@Composable
public fun WloSwitchColors(): SwitchColors =
    SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = wloExtendedColors.surfaceSunken,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
    )

/**
 * The setting row (WLO-0031): label + skinned [Switch], one toggleable target
 * (the whole row carries the switch role — ListItem semantics, no separate
 * switch tap). Kills the hand-repeated SwitchDefaults color blocks.
 *
 * @param label setting copy — sentence case, no rationale leaks (§8)
 * @param checked current state
 * @param onCheckedChange state handler; the row (not just the switch) is the
 *   touch target
 */
@Composable
public fun WloSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
): Unit =
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = WloSpacing.ROW_INTERACTIVE)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = wloType.body,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null, colors = WloSwitchColors())
    }
