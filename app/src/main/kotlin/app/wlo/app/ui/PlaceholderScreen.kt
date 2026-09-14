package app.wlo.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloType

/**
 * M1 tab placeholder: states what will live on the surface, teaches nothing
 * false, keeps the tone kind and free of ruling/feature IDs (R-D11).
 */
@Composable
public fun PlaceholderScreen(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
): Unit =
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        WloScreenTitle(
            title = title,
            modifier = Modifier.testTag("title-${title.lowercase()}"),
        )
        Text(
            text = body,
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

/** The placeholder copy per tab (R-D11: no IDs, next-action tone). */
public object PlaceholderCopy {
    public const val PLAN: String = "The plan-to-pantry pipeline lands here — week grid, recipes, list and pantry."
    public const val INSIGHTS: String = "Your weekly report card, stats hub and milestones land here."
    public const val ARCHIVE: String = "Your silhouette timeline lands here — it opens behind its lock."
    public const val DIGESTION: String = "Your gut log, fiber target and correlations land here."
}
