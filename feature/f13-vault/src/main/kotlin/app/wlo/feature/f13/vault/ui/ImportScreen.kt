package app.wlo.feature.f13.vault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f13.vault.state.CsvMappingRow
import app.wlo.feature.f13.vault.state.ImportUiState
import app.wlo.feature.f13.vault.state.ImportViewModel

/**
 * The import surface (F13 §3): a WLO JSON bundle re-imports through the same
 * staged funnel as restore; a generic CSV goes through the R-S4 column-mapping
 * wizard — sniffer-prefilled, user-corrected, REMEMBERED. Unparseable rows are
 * skipped WITH reasons; nothing fatal, nothing guessed.
 */
@Composable
public fun ImportScreen(
    viewModel: ImportViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val bytes =
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
                    }.getOrNull()
                if (bytes != null) {
                    val name =
                        uri.lastPathSegment
                            ?.substringAfterLast('/')
                            ?.substringBefore('?') ?: "import"
                    viewModel.onFilePicked(name, bytes)
                }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("f13-import"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(
            text = "Import",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("f13-import-title"),
        )
        Text(
            text =
                "Bring data in from a WLO export bundle or any CSV. Everything lands in a staging " +
                    "area first — nothing merges into your data until the report says so and you confirm.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (state.step) {
            ImportUiState.Step.Pick -> {
                Button(
                    onClick = {
                        filePicker.launch(
                            arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "*/*"),
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("f13-import-pick"),
                ) { Text("Choose a file", style = wloType.label) }
            }

            ImportUiState.Step.Mapping ->
                Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                    Text(
                        text =
                            "File: ${state.fileName} · ${state.csvPreview?.rowCount ?: 0} data rows · " +
                                "map each column (memory remembers your mapping next time)",
                        style = wloType.receipt,
                        fontFamily = FontFamily.Monospace,
                    )
                    state.mapping.forEach { row ->
                        MappingRowEditor(
                            row = row,
                            targets =
                                listOf(
                                    "day" to "Day",
                                    "weight" to "Weight (kg)",
                                    "trend" to "Trend (kg)",
                                    "intake" to "kcal in",
                                    "burn" to "kcal out",
                                    "body-fat" to "Body fat (%)",
                                    "custom" to "Custom metric…",
                                    null to "Ignore",
                                ),
                            onSelect = { kind, unit, customName ->
                                viewModel.setMapping(row.sourceColumn, kind, unit, customName)
                            },
                        )
                    }
                    Button(
                        onClick = viewModel::stage,
                        enabled = state.mapping.any { it.targetKind != null },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("f13-import-stage"),
                    ) { Text("Validate rows", style = wloType.label) }
                }

            ImportUiState.Step.Report -> {
                val staged = state.staged
                Surface(
                    shape = WloShape.Card,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().testTag("f13-import-report"),
                ) {
                    Column(
                        Modifier.padding(WloSpacing.PAD_CARD),
                        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                    ) {
                        Text(
                            "Staged ✓ — nothing applied yet",
                            style = wloType.title,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text =
                                "${staged?.stagedRows ?: 0} row(s) parsed and ready" +
                                    (if (state.isBundle) " (bundle import)" else ""),
                            style = wloType.body,
                            modifier = Modifier.testTag("f13-import-staged-count"),
                        )
                        staged?.warnings?.takeIf { it.isNotEmpty() }?.forEach { warning ->
                            Text(
                                text = "⚠ $warning",
                                style = wloType.receipt,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("f13-import-warning"),
                            )
                        }
                        Text(
                            text = "Weak rows are skipped with reasons — never guessed, never fatal.",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                    OutlinedButton(
                        onClick = viewModel::reset,
                        modifier = Modifier.testTag("f13-import-back"),
                    ) { Text("Back", style = wloType.label) }
                    Button(
                        onClick = viewModel::commit,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("f13-import-commit"),
                    ) { Text("Apply ${state.staged?.stagedRows ?: 0} rows", style = wloType.label) }
                }
            }

            ImportUiState.Step.Applying ->
                Text(
                    "Applying…",
                    style = wloType.body,
                    modifier = Modifier.testTag("f13-import-applying"),
                )

            ImportUiState.Step.Done ->
                Surface(
                    shape = WloShape.Card,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().testTag("f13-import-done"),
                ) {
                    Column(
                        Modifier.padding(WloSpacing.PAD_CARD),
                        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                    ) {
                        Text("Imported ✓", style = wloType.title, color = MaterialTheme.colorScheme.primary)
                        Text(text = "${state.committedRows} row(s) merged into your data.", style = wloType.body)
                        Button(
                            onClick = onDone,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) { Text("Done", style = wloType.label) }
                    }
                }

            ImportUiState.Step.Failed ->
                Surface(
                    shape = WloShape.Card,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().testTag("f13-import-failed"),
                ) {
                    Column(
                        Modifier.padding(WloSpacing.PAD_CARD),
                        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                    ) {
                        Text("Import stopped", style = wloType.title, color = MaterialTheme.colorScheme.error)
                        Text(
                            text = "Nothing was changed. Your data on this device is exactly as it was.",
                            style = wloType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("f13-import-untouched"),
                        )
                        state.failure?.let {
                            Text(
                                text = it,
                                style = wloType.receipt,
                                fontFamily = FontFamily.Monospace,
                                color = wloExtendedColors.textTertiary,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                            OutlinedButton(
                                onClick = viewModel::reset,
                                modifier = Modifier.testTag("f13-import-retry"),
                            ) { Text("Try another file", style = wloType.label) }
                            OutlinedButton(onClick = onDone) { Text("Done", style = wloType.label) }
                        }
                    }
                }
        }

        HorizontalDivider()
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text("Close", style = wloType.label) }
        Text(
            text =
                "Competitor converters (MFP, Lose It!, Paprika, Mealime) land in v1.x — generic " +
                    "CSV already works today.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
        )
    }
}

@Composable
private fun MappingRowEditor(
    row: CsvMappingRow,
    targets: List<Pair<String?, String>>,
    onSelect: (String?, String?, String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = WloSpacing.TIGHT).testTag("f13-map-${row.sourceColumn}")) {
        Text(text = row.sourceColumn, style = wloType.statS, fontFamily = FontFamily.Monospace)
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            targets.forEach { (kind, label) ->
                val selected =
                    when {
                        kind == null -> row.targetKind == null
                        kind == "custom" -> row.targetKind == "custom"
                        else -> row.targetKind == kind
                    }
                SelectChip(label = label, selected = selected, onClick = {
                    when (kind) {
                        "custom" -> onSelect("custom", "unit", row.sourceColumn)
                        else -> onSelect(kind, kind?.let { defaultUnitFor(it) }, null)
                    }
                })
            }
        }
    }
}

private fun defaultUnitFor(kind: String): String? =
    when (kind) {
        "weight", "trend" -> "kg"
        "intake", "burn" -> "kcal"
        "body-fat" -> "%"
        "day" -> null
        else -> null
    }
