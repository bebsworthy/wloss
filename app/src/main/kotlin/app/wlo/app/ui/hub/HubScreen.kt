package app.wlo.app.ui.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStat
import app.wlo.core.designsystem.WloStatDivider
import app.wlo.core.designsystem.WloStatRow
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType

/**
 * Hub (F10 surface, M1 demo): a realistic daily-summary — the trend stat,
 * then dense energy rows — with every number rendered through the
 * provenance-chip path (one measured, one derived, one held). This is the
 * end-to-end demonstration of criterion 4: no number without its chip.
 */
@Composable
public fun HubScreen(
    viewModel: HubViewModel,
    modifier: Modifier = Modifier,
) {
    val state: HubUiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        HubHeader(state.todayLabel)

        val trend = state.stats.firstOrNull { it.id == "weight-trend" }
        if (trend != null) {
            WloCard {
                Text(
                    text = "Morning",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
                Spacer(Modifier.height(WloSpacing.CARD))
                WloStat(
                    label = trend.label,
                    value = trend.value,
                    format = ::identity,
                    modifier = Modifier,
                )
            }
        }

        val energy = state.stats.filter { it.id != "weight-trend" }
        if (energy.isNotEmpty()) {
            WloCard {
                Text(
                    text = "Energy",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
                Spacer(Modifier.height(WloSpacing.TIGHT))
                energy.forEachIndexed { index, stat ->
                    if (index > 0) WloStatDivider()
                    WloStatRow(
                        label = stat.label,
                        value = stat.value,
                        format = ::identity,
                        modifier = Modifier,
                    )
                }
            }
        }

        Text(
            text = "Every number here carries where it came from — tap a chip to see how we got here.",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
        )
    }
}

@Composable
private fun HubHeader(todayLabel: String): Unit =
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = WloSpacing.SCREEN),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "WLO", style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.06f))
        Text(
            text = todayLabel,
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
    }

/** WLO card: 20 dp radius, 1 dp outline hairline, no shadow (§1.4). */
@Composable
private fun WloCard(content: @Composable () -> Unit): Unit =
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(WloSpacing.PAD_CARD)) { content() }
    }

/** Identity formatter: demo stat values are display-ready (unit glyph included). */
private fun identity(value: String): String = value
