package app.wlo.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.wlo.core.model.DerivedValue

/**
 * Card-level stat (DESIGN-SYSTEM.md §2 `stat-l`): label, big tabular value,
 * provenance chip beneath. D6: the value parameter is a [DerivedValue] —
 * there is no String overload, so a number rendered here always carries its
 * provenance. Formatting (units, grouping) happens via [format] only.
 */
@Composable
public fun <T : Any> WloStat(
    label: String,
    value: DerivedValue<T>,
    format: (T) -> String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = wloType.statL,
): Unit =
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
        Text(
            text = label,
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = format(value.value),
                style = valueStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(WloSpacing.TIGHT))
        }
        ProvenanceChip(value = value, format = format)
    }

/**
 * Dense row-level stat (CSS `.stat-row`): label left, VALUE in the middle
 * (the chip never repeats the value — owner review WLO-0030, defect 15 — so
 * the row renders the number itself), provenance chip right, hairline-divided
 * by the caller. D6: value is a [DerivedValue] — no String overload exists.
 *
 * @param onExplain tap-through wired into the chip's info mark — WLO-0030
 *   defect 11: no chip with an info mark may render dead
 */
@Composable
public fun <T : Any> WloStatRow(
    label: String,
    value: DerivedValue<T>,
    format: (T) -> String,
    modifier: Modifier = Modifier,
    onExplain: (() -> Unit)? = null,
): Unit =
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = WloSpacing.ROW_MIN),
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = wloType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = format(value.value), style = wloType.statM)
        Spacer(Modifier.width(WloSpacing.TIGHT))
        ProvenanceChip(value = value, format = format, onClick = onExplain)
    }

/**
 * Hairline divider between stat rows (1 dp `outline`, never a shadow — §1.4).
 */
@Composable
public fun WloStatDivider(modifier: Modifier = Modifier): Unit =
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outline,
        thickness = 1.dp,
    )
