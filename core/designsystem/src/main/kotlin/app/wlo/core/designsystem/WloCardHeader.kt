package app.wlo.core.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import java.util.Locale

/**
 * Card header — the mock's uppercase letterspaced micro-label ("WEIGHT
 * TREND", "CALORIES"; owner review WLO-0030, defect 6: card titles are
 * sentence-case text today). Sits on the card's top row: the header word on
 * the left, an optional provenance slot aligned center-right (typically a
 * [ProvenanceChip] for the card's headline number).
 *
 * The label is the [WloTypography.label][wloType] style re-weighted for the
 * header role: 11 sp, wght 600, +8% tracking, uppercase, `text-secondary`.
 *
 * @param title header copy; uppercased here with [Locale.ROOT] (locale-independent)
 * @param provenance optional trailing slot for a provenance chip or delta
 */
@Composable
public fun WloCardHeader(
    title: String,
    modifier: Modifier = Modifier,
    provenance: (@Composable () -> Unit)? = null,
): Unit =
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(Locale.ROOT),
            style =
                wloType.label.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (0.08f).em,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        provenance?.invoke()
    }
