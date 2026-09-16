package app.wlo.feature.f13.vault.ui

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.common.formatBytes
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.ports.HealthConnectAvailability
import app.wlo.feature.f13.vault.state.VaultDashboardViewModel

/**
 * The Data Vault dashboard (F13 §3): per-partition storage accounting with
 * one-tap reclaim, the backup posture (folder, auto toggle, last backup),
 * and the wizard entry points (backup · restore · export · import) plus the
 * data-movement controls. Everything on this surface is FLAG_SECURE (IA §6).
 */
@Composable
public fun VaultDashboardScreen(
    viewModel: VaultDashboardViewModel,
    onOpenBackup: () -> Unit,
    onOpenRestore: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenStorage: () -> Unit,
    section: String = "overview",
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deletePartition by remember { mutableStateOf<String?>(null) }
    deletePartition?.let { partition ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletePartition = null },
            title = { Text("Delete local attachments?") },
            text = {
                Text("All attachments in this storage category will be permanently deleted. This cannot be undone.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.reclaim(partition)
                    deletePartition = null
                }) { Text("Delete attachments") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deletePartition = null }) { Text("Cancel") }
            },
        )
    }
    val healthPermissions =
        remember {
            setOf(
                HealthPermission.getReadPermission(WeightRecord::class),
                HealthPermission.getReadPermission(BodyFatRecord::class),
            )
        }
    val healthPermissionLauncher =
        rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            viewModel.refreshHealthConnect()
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("f13-vault"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        if (section == "overview") {
            app.wlo.core.designsystem.WloSettingsGroup {
                WloListRow(
                    label = "Backup",
                    secondary =
                        when {
                            state.backup?.folderUri == null -> "Not set up"
                            state.lastBackupFile != null -> "Last backup: ${state.lastBackupFile}"
                            else -> "No backup yet"
                        },
                    chevron = true,
                    onClick = onOpenBackup,
                    modifier = Modifier.testTag("f13-open-backup"),
                )
                WloListRow(
                    label = "Restore backup",
                    secondary = "Recover data from a backup file",
                    chevron = true,
                    onClick = onOpenRestore,
                    modifier = Modifier.testTag("f13-open-restore"),
                )
            }
            app.wlo.core.designsystem.WloSettingsGroup {
                WloListRow(
                    label = "Export data",
                    secondary = "Save a copy to a file",
                    chevron = true,
                    onClick = onOpenExport,
                    modifier = Modifier.testTag("f13-open-export"),
                )
                WloListRow(
                    label = "Import data",
                    secondary = "Review a file before adding records",
                    chevron = true,
                    onClick = onOpenImport,
                    modifier = Modifier.testTag("f13-open-import"),
                )
            }
            app.wlo.core.designsystem.WloSettingsGroup {
                WloListRow(
                    label = "Storage",
                    secondary = "${formatBytes(state.totalVaultBytes)} of local attachments",
                    chevron = true,
                    onClick = onOpenStorage,
                    modifier = Modifier.testTag("f13-open-storage"),
                )
            }
        }
        // --- storage dashboard ------------------------------------------------
        if (section == "storage") {
            state.partitions.forEachIndexed { index, partition ->
                if (index > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (partition.partition == "photo") "Photos" else partition.partition,
                            style = wloType.statS,
                        )
                        Text(
                            text =
                                "${partition.count} local attachments",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                    Text(text = formatBytes(partition.bytes), style = wloType.statS)
                    if (partition.count > 0) {
                        WloSecondaryButton(
                            label = "Delete attachments",
                            onClick = { deletePartition = partition.partition },
                            enabled = partition.count > 0,
                        )
                    }
                }
            }
            if (state.partitions.isEmpty()) {
                Text(
                    text =
                        "No local attachments.",
                    style = wloType.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Local attachments: ${formatBytes(state.totalVaultBytes)}",
                style = wloType.receipt,
                modifier = Modifier.testTag("f13-storage-total"),
            )
        }

        if (section == "health") {
            val health = state.healthConnect
            val statusText =
                when {
                    health == null -> "Checking availability…"
                    health.availability == HealthConnectAvailability.UPDATE_REQUIRED ->
                        "Health Connect needs an update before WLO can import."
                    health.availability == HealthConnectAvailability.UNAVAILABLE ->
                        "Health Connect is not available on this device."
                    health.permissions.allGranted -> "Weight and body-fat access granted."
                    health.permissions.anyGranted -> "Partial access granted; only allowed metrics will sync."
                    else -> "Connect to import weight and body-fat records. Nothing is written back."
                }
            Text(statusText, style = wloType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            health?.lastLog?.let { log ->
                Text(
                    text =
                        "Last sync: ${log.outcome} · ${log.inserted} added · ${log.updated} updated · " +
                            "${log.deleted} deleted · ${log.skipped} unchanged" +
                            if (log.conflicts > 0) " · ${log.conflicts} conflicts" else "",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                    modifier = Modifier.testTag("f13-health-connect-log"),
                )
                log.detail?.let { Text(it, style = wloType.receipt, color = wloExtendedColors.textTertiary) }
            }
            if (health?.availability == HealthConnectAvailability.AVAILABLE) {
                if (health.permissions.anyGranted) {
                    WloSecondaryButton(
                        label = "Import now",
                        onClick = viewModel::syncHealthConnect,
                        enabled = !state.busy,
                        modifier = Modifier.testTag("f13-health-connect-sync"),
                    )
                }
                if (!health.permissions.allGranted) {
                    WloSecondaryButton(
                        label = if (health.permissions.anyGranted) "Review access" else "Choose access",
                        onClick = { healthPermissionLauncher.launch(healthPermissions) },
                        modifier = Modifier.testTag("f13-health-connect-permission"),
                    )
                }
            }
        }
    }
}
