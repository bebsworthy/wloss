package app.wlo.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import app.wlo.core.model.DerivedValue

/**
 * The hero stat (DESIGN-SYSTEM.md §2 `hero`, one per screen max): the numeral
 * is the hero — Inter Display, `tnum`, wght 600+, 52–64 sp — with the unit
 * deliberately de-emphasized as a small baseline-aligned suffix (`stat-m`
 * size, `text-secondary`). Owner review WLO-0030, defect 14: callers used to
 * bake "kg" into the formatted string so numeral and unit rendered as one
 * display-size run; here [format] must return the numeral only and the
 * [unit] renders as its own small run.
 *
 * The [ProvenanceChip] sits below the numeral (single info mark, no repeated
 * value — defect 15), and the optional [delta] slot renders inline after the
 * unit (typically a [WloDeltaChip]).
 *
 * D6: the value is a [DerivedValue] — there is deliberately no String-value
 * overload; a number this big always carries its provenance.
 *
 * @param value the derived value (domain type, never pre-flattened)
 * @param format numeral-only formatting — no unit, no suffix (grouping, precision)
 * @param unit unit symbol rendered as the small suffix ("kg", "kcal")
 * @param valueStyle display style; defaults to the `hero` ramp entry
 * @param unitStyle baseline-aligned unit style; defaults to the row-stat role
 * @param delta optional inline slot after the unit (delta chip, trend arrow)
 * @param provenance optional REPLACEMENT for the built-in chip line below the
 *   numeral. Default renders [ProvenanceChip] here; pass a slot of your own
 *   (or `{}` when the card header owns the single chip — the mock's badge
 *   top-right, owner decision WLO-0030 defect 15) to never render two chips
 *   for one number.
 */
@Composable
public fun WloHeroStat(
    value: DerivedValue<Double>,
    format: (Double) -> String,
    unit: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = wloType.hero,
    unitStyle: TextStyle = wloType.statM,
    delta: (@Composable () -> Unit)? = null,
    provenance: (@Composable () -> Unit)? = null,
): Unit =
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
        Row {
            Text(
                text = format(value.value),
                style = valueStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(WloSpacing.TIGHT))
            Text(
                text = unit,
                style = unitStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
            delta?.let {
                Spacer(Modifier.width(WloSpacing.TIGHT))
                Box(Modifier.alignByBaseline()) { it() }
            }
        }
        if (provenance != null) {
            provenance()
        } else {
            ProvenanceChip(value = value, format = format)
        }
    }
