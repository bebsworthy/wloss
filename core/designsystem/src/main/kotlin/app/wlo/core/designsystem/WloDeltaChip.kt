package app.wlo.core.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import app.wlo.core.model.DerivedValue

/**
 * Delta chip (DESIGN-SYSTEM.md §7.1 shared atom): sign-forward, count-up/pop,
 * describes and never judges. Valence-free hues only — `accent` for a falling
 * trend, `neutral-delta` for a rising one; red does not exist (R-D1/R-D5).
 * D6: the value is a [DerivedValue] — there is no raw-double overload.
 *
 * @param value signed delta (negative = downward for weight semantics)
 * @param format display formatting with unit glyph (R-D12)
 */
@Composable
public fun WloDeltaChip(
    value: DerivedValue<Double>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = wloType.statM,
    context: String = "delta",
) {
    val color: Color =
        when {
            value.value < 0.0 -> MaterialTheme.colorScheme.primary
            value.value > 0.0 -> wloExtendedColors.neutralDelta
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val signed = signForward(value.value, format)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$context: $signed" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = signed, style = style, color = color)
    }
}

private fun signForward(
    value: Double,
    format: (Double) -> String,
): String =
    when {
        value < 0 -> "− ${format(-value)}"
        value > 0 -> "+ ${format(value)}"
        else -> "± ${format(0.0)}"
    }
