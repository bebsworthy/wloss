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
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType

/**
 * A documented stub surface: the deep link resolves and lands somewhere real —
 * this screen — with an honest "lands in a later milestone" note. Never a
 * blank frame, never a crash, never a pretend feature.
 */
@Composable
public fun StubScreen(
    title: String,
    modifier: Modifier = Modifier,
): Unit =
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(
            text = title,
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier =
                Modifier
                    .padding(top = WloSpacing.SCREEN)
                    .testTag("title-stub"),
        )
        Text(
            text = "This surface lands in a later milestone — the link already routes here.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Everything logged so far stays on this device and feeds it when it arrives.",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = wloExtendedColors.textTertiary,
        )
    }

/** The stub title per registry route (user words, no IDs — R-D11). */
public val STUB_TITLES: Map<String, String> =
    mapOf(
        "stub/energy" to "Energy",
        "stub/checkin" to "Check-in",
        "stub/studio" to "Plan Studio",
        "stub/vault" to "Data Vault",
        "stub/algorithms" to "Algorithms",
        "stub/ai-receipts" to "AI receipts",
        "stub/plan-tomorrow" to "Plan tomorrow",
        "stub/log-planned" to "Planned meals",
        "stub/gut" to "Digestion log",
        "stub/exercise" to "Workout",
        "stub/insights-report" to "Report card",
        "stub/insights-streaks" to "Streaks",
        "stub/archive-compare" to "Compare",
        "stub/archive-capture" to "Capture",
    )
