package app.wlo.feature.f13.vault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloBadge
import app.wlo.core.designsystem.WloBadgeTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardAccent
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloProgress
import app.wlo.core.designsystem.WloScreenTitle
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
        WloScreenTitle(title = "Import", modifier = Modifier.testTag("f13-import-title"))
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
                    WloButton(
                        label = "Validate rows",
                        onClick = viewModel::stage,
                        enabled = state.mapping.any { it.targetKind != null },
                        modifier = Modifier.testTag("f13-import-stage"),
                    )
                }

            ImportUiState.Step.Report -> {
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
                        text =
                            "${staged?.stagedRows ?: 0} row(s) parsed and ready" +
                                (if (state.isBundle) " (bundle import)" else ""),
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
                        text = "Weak rows are skipped with reasons — never guessed, never fatal.",
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                    WloSecondaryButton(
                        label = "Back",
                        onClick = viewModel::reset,
                        modifier = Modifier.testTag("f13-import-back"),
                    )
                    WloButton(
                        label = "Apply ${state.staged?.stagedRows ?: 0} rows",
                        onClick = viewModel::commit,
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

            ImportUiState.Step.Failed ->
                WloCard(
                    accent = WloCardAccent.Warning,
                    modifier = Modifier.fillMaxWidth().testTag("f13-import-failed"),
                ) {
                    WloCardHeader(
                        title = "Import stopped",
                        provenance = { WloBadge(text = "Failed", tone = WloBadgeTone.Held) },
                    )
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
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        WloSecondaryButton(
                            label = "Try another file",
                            onClick = viewModel::reset,
                            modifier = Modifier.testTag("f13-import-retry"),
                        )
                        WloSecondaryButton(label = "Done", onClick = onDone)
                    }
                }
        }

        HorizontalDivider()
        WloButton(label = "Close", onClick = onDone)
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
    targets: List<Pair<String?, String>>,
    onSelect: (String?, String?, String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = WloSpacing.TIGHT).testTag("f13-map-${row.sourceColumn}")) {
        Text(text = row.sourceColumn, style = wloType.statS)
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
                        // Custom metrics have no known unit yet — a blank unit
                        // stores the metric bare (no unit suffix in its name).
                        "custom" -> onSelect("custom", null, row.sourceColumn)
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
