package app.wlo.feature.f13.vault.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f13.vault.state.VaultDashboardViewModel

/**
 * The Data Vault dashboard (F13 §3): per-partition storage accounting with
 * one-tap reclaim, the backup posture (folder, auto toggle, last backup),
 * and the wizard entry points (backup · restore · export · import) plus the
 * Fresh Start row. Everything on this surface is FLAG_SECURE (IA §6).
 */
@Composable
public fun VaultDashboardScreen(
    viewModel: VaultDashboardViewModel,
    onOpenBackup: () -> Unit,
    onOpenRestore: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var freshStartAsking by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("f13-vault"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(
            text = "Data Vault",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("f13-title"),
        )
        Text(
            text =
                "Your data lives on this device — encrypted at rest, backed up where you say, " +
                    "exportable in formats that outlive the app.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- storage dashboard ------------------------------------------------
        Text(text = "Storage", style = wloType.title, modifier = Modifier.padding(top = WloSpacing.TIGHT))
        Surface(
            shape = WloShape.Card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().testTag("f13-storage"),
        ) {
            Column(
                Modifier.padding(WloSpacing.PAD_CARD),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                state.partitions.forEach { partition ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text = partition.partition, style = wloType.statS)
                            Text(
                                text = "${partition.count} file(s) · AES-GCM, opaque names",
                                style = wloType.receipt,
                                color = wloExtendedColors.textTertiary,
                            )
                        }
                        Text(
                            text = formatBytes(partition.bytes),
                            style = wloType.statS,
                            fontFamily = FontFamily.Monospace,
                        )
                        TextButton(
                            onClick = { viewModel.reclaim(partition.partition) },
                            enabled = partition.count > 0,
                        ) {
                            Text("Reclaim", style = wloType.label)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
                if (state.partitions.isEmpty()) {
                    Text(
                        text =
                            "No encrypted partitions in use yet. Captures that land here are invisible to " +
                                "the gallery and to cloud photo backup.",
                        style = wloType.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "total: ${formatBytes(state.totalVaultBytes)}",
                    style = wloType.label,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("f13-storage-total"),
                )
            }
        }

        // --- backup posture ----------------------------------------------------
        Text(text = "Backup", style = wloType.title, modifier = Modifier.padding(top = WloSpacing.TIGHT))
        Surface(
            shape = WloShape.Card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().testTag("f13-backup-posture"),
        ) {
            Column(
                Modifier.padding(WloSpacing.PAD_CARD),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                val backup = state.backup
                Text(
                    text =
                        when {
                            backup?.folderUri != null && state.lastBackupFile != null ->
                                "Last backup: ${state.lastBackupFile} (${backup.files.size} kept — rotation keeps 7)"
                            backup?.folderUri != null -> "Folder chosen — no backup written yet."
                            else -> "No backup folder yet. Set it up once; backups then run on their own."
                        },
                    style = wloType.body,
                    modifier = Modifier.testTag("f13-last-backup"),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text = "Automatic daily backup", style = wloType.statS)
                        Text(
                            text =
                                if (backup?.folderUri != null) {
                                    "Runs quietly once a day while the folder is reachable."
                                } else {
                                    "Available once a folder is chosen."
                                },
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                    Switch(
                        checked = backup?.autoEnabled ?: false,
                        onCheckedChange = viewModel::setAutoBackup,
                        enabled = backup?.folderUri != null,
                        colors =
                            SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedThumbColor = wloExtendedColors.surfaceSunken,
                            ),
                        modifier = Modifier.testTag("f13-auto-toggle"),
                    )
                }
                WizardRow(
                    tag = "f13-open-backup",
                    label = "Backup controls — folder, passphrase, backup now",
                    onClick = onOpenBackup,
                )
            }
        }

        // --- wizards -------------------------------------------------------------
        Text(text = "Move data", style = wloType.title, modifier = Modifier.padding(top = WloSpacing.TIGHT))
        Surface(
            shape = WloShape.Card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(WloSpacing.PAD_CARD)) {
                WizardRow(tag = "f13-open-restore", label = "Restore from a backup file", onClick = onOpenRestore)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                WizardRow(
                    tag = "f13-open-export",
                    label = "Export — JSON bundle or per-metric CSV",
                    onClick = onOpenExport,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                WizardRow(
                    tag = "f13-open-import",
                    label = "Import — bundle or CSV with column mapping",
                    onClick = onOpenImport,
                )
            }
        }

        // --- Fresh Start (R-B7) ---------------------------------------------------
        Surface(
            shape = WloShape.Card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().testTag("f13-fresh-start-card"),
        ) {
            Column(
                Modifier.padding(WloSpacing.PAD_CARD),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                Text(text = "Fresh Start", style = wloType.title)
                Text(
                    text =
                        "Starting over after a relapse is a feature, not a failure. Fresh Start HIDES " +
                            "your history — nothing is deleted — and walks you through onboarding again. " +
                            "Your backups keep everything.",
                    style = wloType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.freshStartDone?.let { done ->
                    Text(
                        text = "Hidden ${done.archivedDiaryEntries} diary entries. Onboarding re-arms on next launch.",
                        style = wloType.receipt,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("f13-fresh-start-done"),
                    )
                }
                TextButton(onClick = { freshStartAsking = true }, modifier = Modifier.testTag("f13-fresh-start")) {
                    Text("Hide history & start fresh…", style = wloType.label)
                }
            }
        }
        Text(
            text = "Exports are versioned and documented. Secrets and keys never enter a backup or an export.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
        )
    }

    if (freshStartAsking) {
        AlertDialog(
            onDismissRequest = { freshStartAsking = false },
            title = { Text("Hide history & start fresh?") },
            text = {
                Text(
                    text =
                        "Your ${state.freshStartPreview?.archivedDiaryEntries ?: 0} diary entries will be " +
                            "hidden — never deleted — and the active profile retires. Everything stays in " +
                            "your backups. This is reversible from a restore.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        freshStartAsking = false
                        viewModel.freshStart()
                    },
                    modifier = Modifier.testTag("f13-fresh-start-confirm"),
                ) { Text("Hide & start fresh") }
            },
            dismissButton = {
                TextButton(onClick = { freshStartAsking = false }) { Text("Keep everything") }
            },
        )
    }
}

@Composable
private fun WizardRow(
    tag: String,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .testTag(tag),
        onClick = onClick,
    ) {
        Text(text = label, style = wloType.statS, modifier = Modifier.padding(vertical = WloSpacing.CARD))
    }
}

internal fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
