package app.wlo.feature.f13.vault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloShape
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
        Text(
            text = "Export",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("f13-export-title"),
        )
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
                        base + " ${it.warnings.size} event(s) skipped — see the receipt lines."
                    } else {
                        base
                    }
                } ?: "One RFC-4180 grid, one row per event, custom metrics as columns.",
            onClick = { viewModel.pickFormat(ExportUiState.FORMAT_CSV) },
        )

        Surface(
            shape = WloShape.Card,
            color = wloExtendedColors.surfaceSunken,
            modifier = Modifier.fillMaxWidth().testTag("f13-export-notes"),
        ) {
            Column(
                Modifier.padding(WloSpacing.PAD_CARD),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                Text(
                    "✓ Secrets are blanked — BYOK keys live in the keystore, never in an export.",
                    style = wloType.receipt,
                )
                Text(
                    "✓ Plaintext is your explicit choice here — backups (.wlo) stay passphrase-encrypted.",
                    style = wloType.receipt,
                )
                Text(
                    "✓ Photo attachments are excluded from bundles (opt-in per bundle, later).",
                    style = wloType.receipt,
                )
            }
        }

        Button(
            onClick = {
                val suggestion =
                    if (state.format == ExportUiState.FORMAT_CSV) "wlo_metrics.csv" else "wlo_export.json"
                createDocument.launch(suggestion)
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.testTag("f13-export-write"),
        ) { Text("Choose destination & export", style = wloType.label) }

        state.lastFileName?.let {
            Text(
                text = "✓ wrote $it (${formatBytes(state.lastSizeBytes)})",
                style = wloType.statS,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace,
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
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text("Done", style = wloType.label) }
        Text(
            text = "Exports ride the SAF picker: the file goes straight where you send it — WLO keeps no copy.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
        )
    }
}

@Composable
private fun FormatCard(
    tag: String,
    selected: Boolean,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = WloShape.Card,
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            ),
        modifier = Modifier.fillMaxWidth().testTag(tag),
        onClick = onClick,
    ) {
        Column(Modifier.padding(WloSpacing.PAD_CARD), verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            Text(text = if (selected) "● $title" else "○ $title", style = wloType.title)
            Text(text = body, style = wloType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
