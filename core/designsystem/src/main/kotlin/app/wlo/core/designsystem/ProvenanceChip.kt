package app.wlo.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Provenance

/**
 * Provenance chip — THE sanctioned way a number the user sees carries its
 * provenance (FEATURES §2.1, R-D11, D6). Anatomy: the provenance word plus a
 * single trailing info mark (the tap-through to "how we got here"). The chip
 * never repeats the value — the big numeral next to it already shows it
 * (owner review WLO-0030, defect 15) — and it carries exactly one icon; the
 * old leading kind glyph is gone. State-bearing: `held` renders amber,
 * `estimated` renders developing blue; red does not exist (R-D1/R-D5).
 *
 * This is the only component family in the design system that accepts a
 * [DerivedValue]; there is deliberately no String-value overload — a number
 * without provenance cannot be rendered (D6).
 *
 * The actionable form is a Material 3 [AssistChip]. The read-only form uses
 * a plain [Surface] because Material 3 has no non-interactive chip: disabling
 * an action chip would announce an unavailable control instead of a status
 * label (WLO-0063).
 *
 * @param value the derived value (domain type, never pre-flattened)
 * @param format display formatting for the value — used only for the
 *   accessibility description, so talk-back still reads word + value
 * @param onClick tap-through to the "how we got here" sheet when available;
 *   when wired, the info mark tints to the state color to signal tappability
 */
@Composable
@Suppress("UnusedParameter")
public fun <T : Any> ProvenanceChip(
    value: DerivedValue<T>,
    format: (T) -> String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val provenance = value.provenance
    val state = stateColor(provenance)
    val word = userWord(provenance)
    val description = word
    val border = BorderStroke(1.dp, state.copy(alpha = BORDER_ALPHA))

    val anatomy: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .heightIn(min = 22.dp),
        ) {
            Text(
                text = word,
                style = wloType.label,
                color = state,
            )
            if (onClick != null) {
                Spacer(Modifier.width(WloSpacing.TIGHT))
                Icon(
                    imageVector = WloProvenanceGlyphs.Info,
                    contentDescription = null,
                    tint = state,
                )
            }
        }
    }

    val outer =
        modifier.semantics(mergeDescendants = true) { contentDescription = description }

    if (onClick == null) {
        Surface(
            modifier = outer,
            shape = WloShape.Pill,
            color = Color.Transparent,
            contentColor = Color.Unspecified,
            border = border,
        ) {
            anatomy()
        }
    } else {
        AssistChip(
            onClick = onClick,
            label = {
                Text(
                    text = word,
                    style = wloType.label,
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = WloProvenanceGlyphs.Info,
                    contentDescription = null,
                    tint = state,
                )
            },
            modifier = outer,
            shape = WloShape.Pill,
            colors =
                AssistChipDefaults.assistChipColors(
                    containerColor = Color.Transparent,
                    labelColor = state,
                    trailingIconContentColor = state,
                ),
            border =
                AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = state.copy(alpha = BORDER_ALPHA),
                    borderWidth = 1.dp,
                ),
        )
    }
}

/** User-facing chip words (R-D11: user words, lowercase, never mechanism names). */
private fun userWord(provenance: Provenance): String =
    when (provenance) {
        is Provenance.Measured -> "measured"
        is Provenance.Estimated -> if (provenance.modelId != null) "ai-estimated" else "estimated"
        is Provenance.Derived -> "derived"
        is Provenance.Held -> "held"
    }

/** Quality-state styling: held amber, estimated/developing blue, others neutral. */
@Composable
private fun stateColor(provenance: Provenance): Color =
    when (provenance) {
        is Provenance.Measured -> MaterialTheme.colorScheme.onSurfaceVariant
        is Provenance.Derived -> wloExtendedColors.textTertiary
        is Provenance.Estimated -> wloExtendedColors.developing
        is Provenance.Held -> wloExtendedColors.held
    }

private const val BORDER_ALPHA = 0.4f
