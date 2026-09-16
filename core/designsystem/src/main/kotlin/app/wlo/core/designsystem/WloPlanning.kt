package app.wlo.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// F03/F04 shared atoms (DESIGN-SYSTEM.md §6 component inventory, M5): the
// segmented bar (IA §1's Plan-tab pipeline), the day-fit badge (R-S7 ±5 %),
// the adherence strip (F03 §5), and the 48 dp check row (F04 §4's in-aisle
// touch floor). Shapes speak domain-neutral values — engine types map onto
// them in the owning feature.

/** A Material 3 single-choice segmented button row for compact planning filters. */
@Composable
public fun WloSegmentedBar(
    segments: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
): Unit =
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp),
    ) {
        segments.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = segments.size),
                modifier = Modifier.weight(1f),
                icon = {},
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

/**
 * The day-fit badge (R-S7, F03 §5): ✓ inside ±[WloFitBadgeDefaults.TOLERANCE_PCT] kcal
 * rendered as a neutral/positive mark; an honest miss renders the percentage —
 * the number is the information, never a verdict (no red exists, R-D1).
 * Protein gaps ride along as a signed gram delta when present.
 */
public object WloFitBadgeDefaults {
    /** R-S7: ±5 % kcal, aligned with F07's proposal semantics. */
    public const val TOLERANCE_PCT: Double = 5.0
}

@Composable
public fun WloFitBadge(
    kcalDeltaPct: Double,
    proteinDeltaG: Double?,
    modifier: Modifier = Modifier,
    withinTolerance: Boolean = kotlin.math.abs(kcalDeltaPct) <= WloFitBadgeDefaults.TOLERANCE_PCT,
) {
    val tint = if (withinTolerance) MaterialTheme.colorScheme.primary else wloExtendedColors.held
    val kcalText =
        if (withinTolerance) {
            "kcal ✓"
        } else {
            "kcal ${signed1(kcalDeltaPct)}%"
        }
    val proteinText = proteinDeltaG?.let { " · P ${signed0(it)} g" }.orEmpty()
    Text(
        text = "$kcalText$proteinText",
        style = wloType.label,
        color = tint,
        modifier =
            modifier
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        if (withinTolerance) {
                            "day fit: kcal within ±5%"
                        } else {
                            "day fit: kcal ${signed1(kcalDeltaPct)}% from target"
                        }
                }.background(tint.copy(alpha = 0.12f), WloShape.Chip)
                .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/**
 * The adherence strip (F03 §5): one segment per day of the plan week; a
 * confirmed day fills accent, a skipped day fills the neutral second series —
 * misses carry no guilt hue — open days stay hairline. Below the data gate the
 * caller renders "not yet meaningful" instead of this strip's numbers.
 */
public enum class WloAdherenceCellState {
    /** Every slot resolved confirmed (or replaced — eaten, honestly logged). */
    CONFIRMED,

    /** The day closed with skips — honest, neutral, never a failure mark. */
    SKIPPED,

    /** Still open (or in the future) — simply not resolved yet. */
    OPEN,
}

public data class WloAdherenceCell(
    public val label: String,
    public val state: WloAdherenceCellState,
)

@Composable
public fun WloAdherenceStrip(
    cells: List<WloAdherenceCell>,
    modifier: Modifier = Modifier,
): Unit =
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (cell in cells) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val fill =
                    when (cell.state) {
                        WloAdherenceCellState.CONFIRMED -> MaterialTheme.colorScheme.primary
                        WloAdherenceCellState.SKIPPED -> wloExtendedColors.series[1]
                        WloAdherenceCellState.OPEN -> Color.Transparent
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 12.dp)
                            .background(fill, WloShape.Chip)
                            .border(
                                1.dp,
                                if (cell.state == WloAdherenceCellState.OPEN) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    fill
                                },
                                WloShape.Chip,
                            ),
                )
                Text(
                    text = cell.label,
                    style = wloType.label.copy(fontSize = wloType.label.fontSize * 0.87f),
                    color = wloExtendedColors.textTertiary,
                )
            }
        }
    }

/** One Material 3 list item with a row-wide checkbox action for in-aisle use. */
@Composable
public fun WloCheckRow(
    name: String,
    qtyLabel: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    deltaLabel: String? = null,
    leading: (@Composable () -> Unit)? = null,
): Unit =
    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = WloSpacing.TOUCH_PRIMARY)
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.invoke()
                Checkbox(checked = checked, onCheckedChange = null)
            }
        },
        headlineContent = {
            Text(
                text = name,
                style = wloType.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
        },
        supportingContent =
            subLabel?.let { supporting ->
                {
                    Text(
                        text = supporting,
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                deltaLabel?.let {
                    Text(
                        text = it,
                        style = wloType.label,
                        color = wloExtendedColors.developing,
                        modifier =
                            Modifier
                                .background(wloExtendedColors.developing.copy(alpha = 0.12f), WloShape.Chip)
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                Text(
                    text = qtyLabel,
                    style = wloType.statS,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                )
            }
        },
    )

/**
 * A small status dot — urgency by position and copy, never by alarm hue (R-D1).
 */
@Composable
public fun WloStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
): Unit = Box(modifier = modifier.size(8.dp).background(color, CircleShape))

/** "−3.2" / "+7.0" — one decimal, typographic minus. */
internal fun signed1(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return when {
        rounded < 0 -> "−${kotlin.math.abs(rounded)}"
        rounded > 0 -> "+$rounded"
        else -> "±0"
    }
}

/** "−6" / "+4" — whole grams, typographic minus. */
internal fun signed0(value: Double): String {
    val rounded = kotlin.math.round(value)
    return when {
        rounded < 0 -> "−${kotlin.math.abs(rounded).toInt()}"
        rounded > 0 -> "+${rounded.toInt()}"
        else -> "±0"
    }
}

/**
 * A full-width Material 3 tonal button with WLO's 48 dp primary-action floor.
 * Shared by the Plan, recipe editor and shopping surfaces.
 */
@Composable
public fun WloPrimaryRow(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Unit =
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = WloSpacing.TOUCH_PRIMARY),
        shape = WloShape.Chip,
    ) {
        Text(text = label)
    }
