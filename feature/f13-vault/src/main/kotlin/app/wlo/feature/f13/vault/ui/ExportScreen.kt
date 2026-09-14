package app.wlo.feature.f13.vault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloBadge
import app.wlo.core.designsystem.WloBadgeTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardAccent
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f13.vault.state.ExportUiState
import app.wlo.feature.f13.vault.state.ExportViewModel
import kotlinx.coroutines.launch

/**
 * The export wizard (F13 §3): pick the documented format — the versioned JSON
 * bundle or the per-metric CSV — then write it wherever the user's SAF action
 * sends it. Exports are PLAINTEXT BY CHOICE (R-U5) and secrets are blanked
 * (F13 §3): both facts stated on the surface, never buried.
 */
@Composable
public fun ExportScreen(
    viewModel: ExportViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // CREATE propagates the user's chosen destination + the file name.
    val createDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                scope.launch {
                    val (name, bytes) = viewModel.render()
                    viewModel.onDestinationPicked(uri.toString(), name, bytes)
                }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("f13-export"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        WloScreenTitle(title = "Export", modifier = Modifier.testTag("f13-export-title"))
        Text(
            text =
                "Formats that outlive the app: both are documented in the repo so third-party tooling " +
                    "can read them — the anti-lock-in guarantee.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FormatCard(
            tag = "f13-format-bundle",
            selected = state.format == ExportUiState.FORMAT_BUNDLE,
            title = "Versioned JSON bundle",
            body =
                "Everything: profile, measurements, diary (with revisions), foods, recipes, plans, " +
                    "list, pantry, settings, consent ledger. Selecting it writes ONE readable .json file.",
            onClick = { viewModel.pickFormat(ExportUiState.FORMAT_BUNDLE) },
        )
        FormatCard(
            tag = "f13-format-csv",
            selected = state.format == ExportUiState.FORMAT_CSV,
            title = "Per-metric CSV",
            body =
                state.csvPreview?.let {
                    val base =
                        "One RFC-4180 grid, one row per event: ${it.dataRows} data rows, " +
                            "built-in columns plus any custom metrics."
                    if (it.warnings.isNotEmpty()) {
                        base + " ${it.warnings.size} rows skipped — the report lists each one."
                    } else {
                        base
                    }
                } ?: "One RFC-4180 grid, one row per event, custom metrics as columns.",
            onClick = { viewModel.pickFormat(ExportUiState.FORMAT_CSV) },
        )

        WloCard(modifier = Modifier.fillMaxWidth().testTag("f13-export-notes")) {
            Text(
                "API keys stay in the keystore — they never enter an export.",
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
            Text(
                "Plaintext is your explicit choice here — backups (.wlo) stay passphrase-encrypted.",
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
            Text(
                "Photo attachments are not part of bundles.",
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
        }

        WloButton(
            label = "Choose destination & export",
            onClick = {
                val suggestion =
                    if (state.format == ExportUiState.FORMAT_CSV) "wlo_metrics.csv" else "wlo_export.json"
                createDocument.launch(suggestion)
            },
            modifier = Modifier.testTag("f13-export-write"),
        )

        state.lastFileName?.let {
            Text(
                text = "Wrote $it (${formatBytes(state.lastSizeBytes)})",
                style = wloType.receipt,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("f13-export-outcome"),
            )
        }
        state.failure?.let {
            Text(
                text = it,
                style = wloType.body,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("f13-export-failure"),
            )
        }
        HorizontalDivider()
        WloButton(label = "Done", onClick = onDone)
        Text(
            text = "You pick the folder — the file is written there. WLO keeps no copy.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
        )
    }
}

/**
 * One exclusive format choice: a clickable [WloCard], the primary hairline +
 * a "Selected" badge carrying the radio state (the old text-glyph ● / ○
 * radio is gone — state is words and the card accent, never glyphs).
 */
@Composable
private fun FormatCard(
    tag: String,
    selected: Boolean,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    WloCard(
        onClick = onClick,
        accent = if (selected) WloCardAccent.Primary else WloCardAccent.None,
        header = {
            WloCardHeader(
                title = title,
                provenance =
                    if (selected) {
                        { WloBadge(text = "Selected", tone = WloBadgeTone.Accent) }
                    } else {
                        null
                    },
            )
        },
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Text(text = body, style = wloType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
