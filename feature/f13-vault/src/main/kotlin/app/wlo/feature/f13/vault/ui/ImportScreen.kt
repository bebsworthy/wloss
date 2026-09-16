package app.wlo.feature.f13.vault.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloBadge
import app.wlo.core.designsystem.WloBadgeTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardAccent
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloProgress
import app.wlo.core.designsystem.WloSecondaryButton
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
@OptIn(ExperimentalMaterial3Api::class)
public fun ImportScreen(
    viewModel: ImportViewModel,
    onDone: () -> Unit,
    registerUpHandler: ((() -> Unit)?) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exitBlocked = state.step == ImportUiState.Step.Reading || state.step == ImportUiState.Step.Applying
    val requestBack: () -> Unit = { if (!exitBlocked) onDone() }
    BackHandler(onBack = requestBack)
    DisposableEffect(exitBlocked) {
        registerUpHandler(requestBack)
        onDispose { registerUpHandler(null) }
    }
    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.onSourcePicked(it.toString()) }
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
            text =
                "Bring data in from a WLO export bundle or any CSV. Everything lands in a staging " +
                    "area first — nothing merges into your data until the report says so and you confirm.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (state.step) {
            ImportUiState.Step.Pick -> {
                WloButton(
                    label = "Choose a file",
                    onClick = {
                        filePicker.launch(
                            arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "*/*"),
                        )
                    },
                    modifier = Modifier.testTag("f13-import-pick"),
                )
            }

            ImportUiState.Step.Reading ->
                WloProgress(
                    progress = null,
                    label = "Reading and staging file…",
                    modifier = Modifier.testTag("f13-import-reading"),
                )

            ImportUiState.Step.Mapping ->
                Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                    Text(
                        text = "File: ${state.fileName} · ${state.csvPreview?.rowCount ?: 0} data rows",
                        style = wloType.receipt,
                    )
                    Text(
                        text = "Map each column — your column mapping is remembered for next time.",
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                    state.mapping.forEach { row ->
                        MappingRowEditor(
                            row = row,
                            examples =
                                state.csvPreview
                                    ?.samples
                                    ?.get(row.sourceColumn)
                                    .orEmpty(),
                            targets =
                                listOf(
                                    null to "Ignore",
                                    "day" to "Date",
                                    "weight" to "Weight",
                                    "intake" to "Energy in",
                                    "burn" to "Energy out",
                                    "body-fat" to "Body fat",
                                    "custom" to "Custom metric…",
                                ),
                            onSelect = { kind, unit, customName ->
                                viewModel.setMapping(row.sourceColumn, kind, unit, customName)
                            },
                        )
                    }
                    state.mappingError?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = wloType.body)
                    }
                    WloButton(
                        label = "Review import",
                        onClick = viewModel::stage,
                        modifier = Modifier.testTag("f13-import-stage"),
                    )
                    WloSecondaryButton(
                        label = "Choose another file",
                        onClick = viewModel::chooseAnotherFile,
                    )
                }

            ImportUiState.Step.Reviewing -> {
                val staged = state.staged
                WloCard(
                    accent = WloCardAccent.Primary,
                    modifier = Modifier.fillMaxWidth().testTag("f13-import-report"),
                ) {
                    WloCardHeader(
                        title = "Nothing applied yet",
                        provenance = { WloBadge(text = "Staged", tone = WloBadgeTone.Accent) },
                    )
                    Text(
                        text = "File: ${state.fileName}",
                        style = wloType.receipt,
                    )
                    Text(
                        text =
                            "${staged?.stagedRows ?: 0} measurement(s) accepted; " +
                                "${staged?.rejectedRows ?: 0} source row(s) need attention.",
                        style = wloType.body,
                        modifier = Modifier.testTag("f13-import-staged-count"),
                    )
                    staged?.warnings?.takeIf { it.isNotEmpty() }?.forEach { warning ->
                        Text(
                            text = warning,
                            style = wloType.receipt,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("f13-import-warning"),
                        )
                    }
                    Text(
                        text =
                            if (state.isBundle) {
                                "This bundle follows its versioned staged-restore rules."
                            } else {
                                "Dates are ISO YYYY-MM-DD. Weight is converted once to kg. " +
                                    "CSV has no capture-time metadata, so imported rows use the import capture time."
                            },
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                    Text(
                        text = staged?.duplicatePolicy.orEmpty(),
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                    if (!state.isBundle) {
                        WloSecondaryButton(
                            label = "Back to mapping",
                            onClick = viewModel::backToMapping,
                            modifier = Modifier.testTag("f13-import-back"),
                        )
                    }
                    WloButton(
                        label = "Import ${state.staged?.stagedRows ?: 0}",
                        onClick = viewModel::commit,
                        enabled = (state.staged?.stagedRows ?: 0) > 0,
                        modifier = Modifier.testTag("f13-import-commit"),
                    )
                }
            }

            ImportUiState.Step.Applying ->
                WloProgress(
                    progress = null,
                    label = "Applying…",
                    modifier = Modifier.testTag("f13-import-applying"),
                )

            ImportUiState.Step.Done ->
                WloCard(
                    accent = WloCardAccent.Primary,
                    modifier = Modifier.fillMaxWidth().testTag("f13-import-done"),
                ) {
                    WloCardHeader(
                        title = "Import complete",
                        provenance = { WloBadge(text = "Imported", tone = WloBadgeTone.Accent) },
                    )
                    Text(text = "${state.committedRows} row(s) merged into your data.", style = wloType.body)
                    WloButton(label = "Done", onClick = onDone)
                }

            ImportUiState.Step.RecoverableError ->
                WloCard(
                    accent = WloCardAccent.Warning,
                    modifier = Modifier.fillMaxWidth().testTag("f13-import-failed"),
                ) {
                    WloCardHeader(
                        title = "Import stopped",
                        provenance = { WloBadge(text = "Failed", tone = WloBadgeTone.Held) },
                    )
                    Text(
                        text =
                            if (state.recoveryPending) {
                                "Room data may already be committed. Recovery is queued and will safely continue."
                            } else {
                                "The import did not finish. The staged source and your choices are still available."
                            },
                        style = wloType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("f13-import-untouched"),
                    )
                    state.failure?.let {
                        Text(
                            text = it,
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        WloSecondaryButton(
                            label = "Retry",
                            onClick = viewModel::retry,
                            modifier = Modifier.testTag("f13-import-retry"),
                        )
                        WloSecondaryButton(label = "Choose another file", onClick = viewModel::chooseAnotherFile)
                    }
                }
        }

        if (state.step != ImportUiState.Step.Applying) {
            HorizontalDivider()
            WloSecondaryButton(label = "Close", onClick = onDone)
        }
        Text(
            text = "CSV imports need one date column — every other column is optional.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
        )
    }
}

@Composable
private fun MappingRowEditor(
    row: CsvMappingRow,
    examples: List<String>,
    targets: List<Pair<String?, String>>,
    onSelect: (String?, String?, String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("f13-map-${row.sourceColumn}")) {
        ListItem(
            headlineContent = { Text(row.sourceColumn) },
            supportingContent = {
                Text(
                    examples.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Examples: ")
                        ?: "No non-empty example values",
                )
            },
        )
        MappingDropdown(row, targets, onSelect)
        if (row.targetKind == "weight") {
            UnitDropdown(row, onSelect)
        } else if (row.targetKind == "custom") {
            OutlinedTextField(
                value = row.unit.orEmpty(),
                onValueChange = { onSelect("custom", it, row.customName ?: row.sourceColumn) },
                label = { Text("Unit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MappingDropdown(
    row: CsvMappingRow,
    targets: List<Pair<String?, String>>,
    onSelect: (String?, String?, String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = targets.firstOrNull { it.first == row.targetKind }?.second ?: "Ignore"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Destination") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            targets.forEach { (kind, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        if (kind == "custom") {
                            onSelect(kind, row.unit.orEmpty(), row.sourceColumn)
                        } else {
                            onSelect(kind, kind?.let(::defaultUnitFor), null)
                        }
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun UnitDropdown(
    row: CsvMappingRow,
    onSelect: (String?, String?, String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (row.unit == "lb") "lb" else "kg",
            onValueChange = {},
            readOnly = true,
            label = { Text("Source unit") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier =
                Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("kg", "lb").forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit) },
                    onClick = {
                        expanded = false
                        onSelect("weight", unit, null)
                    },
                )
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
