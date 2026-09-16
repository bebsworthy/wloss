package app.wlo.core.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Card header — a sentence-case Material title. Sits on the card's top row: the header word on
 * the left, an optional provenance slot aligned center-right (typically a
 * [ProvenanceChip] for the card's headline number).
 *
 * @param title card title copy, preserved in sentence case
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
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        provenance?.invoke()
    }
