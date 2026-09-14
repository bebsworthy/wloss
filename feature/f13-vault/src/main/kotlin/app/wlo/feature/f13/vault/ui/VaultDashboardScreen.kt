package app.wlo.feature.f13.vault.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.common.formatBytes
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloDialog
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloSwitchRow
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
        WloScreenTitle(title = "Data Vault", modifier = Modifier.testTag("f13-title"))
        Text(
            text =
                "Your data lives on this device — encrypted at rest, backed up where you say, " +
                    "exportable in formats that outlive the app.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- storage dashboard ------------------------------------------------
        WloCard(modifier = Modifier.fillMaxWidth().testTag("f13-storage")) {
            WloCardHeader(title = "Storage")
            state.partitions.forEachIndexed { index, partition ->
                if (index > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text = partition.partition, style = wloType.statS)
                        Text(
                            text =
                                "${partition.count} file(s) · encrypted with your key; " +
                                    "files carry scrambled names",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                    Text(text = formatBytes(partition.bytes), style = wloType.statS)
                    WloSecondaryButton(
                        label = "Reclaim",
                        onClick = { viewModel.reclaim(partition.partition) },
                        enabled = partition.count > 0,
                    )
                }
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
                text = "Total: ${formatBytes(state.totalVaultBytes)}",
                style = wloType.receipt,
                modifier = Modifier.testTag("f13-storage-total"),
            )
        }

        // --- backup posture ----------------------------------------------------
        WloCard(modifier = Modifier.fillMaxWidth().testTag("f13-backup-posture")) {
            WloCardHeader(title = "Backup")
            val backup = state.backup
            Text(
                text =
                    when {
                        backup?.folderUri != null && state.lastBackupFile != null ->
                            "Last backup: ${state.lastBackupFile} — rotation keeps the last 7"
                        backup?.folderUri != null -> "Folder chosen — no backup written yet."
                        else -> "No backup folder yet. Set it up once; backups then run on their own."
                    },
                style = wloType.body,
                modifier = Modifier.testTag("f13-last-backup"),
            )
            // Real precondition (WLO-0032): without a folder the row is
            // disabled and dimmed — the gate is visible, not silent.
            WloSwitchRow(
                label = "Automatic daily backup",
                checked = backup?.autoEnabled ?: false,
                onCheckedChange = viewModel::setAutoBackup,
                enabled = backup?.folderUri != null,
                secondary =
                    if (backup?.folderUri != null) {
                        "Runs quietly once a day while the folder is reachable."
                    } else {
                        "Available once a folder is chosen."
                    },
                modifier = Modifier.testTag("f13-auto-toggle"),
            )
            WloListRow(
                label = "Backup controls",
                secondary = "Folder, passphrase, back up now",
                chevron = true,
                onClick = onOpenBackup,
                modifier = Modifier.testTag("f13-open-backup"),
            )
        }

        // --- wizards -------------------------------------------------------------
        WloCard(modifier = Modifier.fillMaxWidth()) {
            WloCardHeader(title = "Move data")
            WloListRow(
                label = "Restore from a backup file",
                chevron = true,
                onClick = onOpenRestore,
                modifier = Modifier.testTag("f13-open-restore"),
            )
            HorizontalDivider()
            WloListRow(
                label = "Export",
                secondary = "JSON bundle or per-metric CSV",
                chevron = true,
                onClick = onOpenExport,
                modifier = Modifier.testTag("f13-open-export"),
            )
            HorizontalDivider()
            WloListRow(
                label = "Import",
                secondary = "Bundle or CSV with column mapping",
                chevron = true,
                onClick = onOpenImport,
                modifier = Modifier.testTag("f13-open-import"),
            )
        }

        // --- Fresh Start (R-B7) ---------------------------------------------------
        WloCard(modifier = Modifier.fillMaxWidth().testTag("f13-fresh-start-card")) {
            WloCardHeader(title = "Fresh Start")
            Text(
                text =
                    "Starting over after a relapse is a feature, not a failure. Fresh start hides " +
                        "your history — nothing is deleted — and walks you through onboarding again. " +
                        "Your backups keep everything.",
                style = wloType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.freshStartDone?.let { done ->
                Text(
                    text = "Hidden ${done.archivedDiaryEntries} diary entries. Setup runs again on next launch.",
                    style = wloType.receipt,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("f13-fresh-start-done"),
                )
            }
            WloSecondaryButton(
                label = "Hide history & start fresh…",
                onClick = { freshStartAsking = true },
                modifier = Modifier.testTag("f13-fresh-start"),
            )
        }
        Text(
            text = "Exports are versioned and documented. Secrets and keys never enter a backup or an export.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.padding(bottom = WloSpacing.SCREEN),
        )
    }

    if (freshStartAsking) {
        WloDialog(
            title = "Hide history & start fresh?",
            text =
                "Your ${state.freshStartPreview?.archivedDiaryEntries ?: 0} diary entries will be " +
                    "hidden — never deleted — and the active profile retires. Everything stays in " +
                    "your backups. This is reversible from a restore.",
            confirmLabel = "Hide & start fresh",
            onConfirm = {
                freshStartAsking = false
                viewModel.freshStart()
            },
            dismissLabel = "Keep everything",
            onDismiss = { freshStartAsking = false },
            destructive = true,
            modifier = Modifier.testTag("f13-fresh-start-confirm"),
        )
    }
}
