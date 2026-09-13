package app.wlo.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Determinate-or-indeterminate progress with its caption (WLO-0031): the
 * sanctioned replacement for text-only "applying…" states in backup /
 * restore / import flows (M3 [LinearProgressIndicator], proven in the zoo).
 *
 * @param progress 0..1 when known; null renders the indeterminate track
 * @param label caption above the track — say what is happening, sentence case
 */
@Composable
public fun WloProgress(
    progress: Float?,
    label: String,
    modifier: Modifier = Modifier,
): Unit =
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(text = label, style = wloType.caption, color = wloExtendedColors.textTertiary)
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
