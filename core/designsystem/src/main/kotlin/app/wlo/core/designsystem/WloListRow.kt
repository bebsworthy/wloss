package app.wlo.core.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The WLO list row (WLO-0031), re-based on Material 3 [ListItem]: one row
 * anatomy for search hits, diary entries, wizard route rows, settings rows
 * and pick rows. Headline is the label, [secondary] the supporting line, the
 * [value] slot renders the row's number — D6: numbers go through provenance
 * atoms ([ProvenanceChip], [WloStatRow]), never a String here.
 *
 * Rows render on a transparent container so they sit on cards or plain
 * surfaces alike. [onClick] adds ripple + the [WloSpacing.ROW_INTERACTIVE]
 * floor; without it the row is display-only ([WloSpacing.ROW_MIN] keep).
 *
 * @param label the row's headline copy
 * @param secondary optional supporting line (caption style)
 * @param value optional trailing slot for the row's number / state
 * @param leading optional leading slot (glyph, thumbnail)
 * @param trailing optional trailing slot overriding the default chevron
 * @param chevron render [WloIcons.ChevronRight] when no [trailing] is given
 * @param onClick optional tap handler
 */
@Composable
public fun WloListRow(
    label: String,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    value: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
): Unit =
    ListItem(
        modifier =
            if (onClick != null) {
                modifier
                    .heightIn(min = WloSpacing.ROW_INTERACTIVE)
                    .clickable(onClickLabel = label, onClick = onClick)
            } else {
                modifier.heightIn(min = WloSpacing.ROW_MIN)
            },
        headlineContent = { Text(text = label, style = wloType.body) },
        supportingContent =
            secondary?.let { line ->
                {
                    Text(text = line, style = wloType.caption, color = wloExtendedColors.textTertiary)
                }
            },
        leadingContent = leading,
        trailingContent = wloListRowTrailing(value, trailing, chevron),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )

/** Trailing slot assembly: [value], then [trailing], else the [chevron]. */
private fun wloListRowTrailing(
    value: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    chevron: Boolean,
): (@Composable () -> Unit)? =
    if (value == null && trailing == null && !chevron) {
        null
    } else {
        {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                value?.invoke()
                if (trailing != null) {
                    trailing()
                } else if (chevron) {
                    Icon(
                        imageVector = WloIcons.ChevronRight,
                        contentDescription = null,
                        tint = wloExtendedColors.textTertiary,
                    )
                }
            }
        }
    }
