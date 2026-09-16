package app.wlo.feature.f13.vault.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.WloResult
import app.wlo.core.data.ProfileRepository
import app.wlo.core.ports.BackupRequest
import app.wlo.core.ports.BackupScheduler
import app.wlo.core.ports.DataVaultPort
import app.wlo.core.ports.HealthConnectSyncPort
import app.wlo.core.ports.HealthConnectSyncStatus
import app.wlo.core.ports.VaultBackupState
import app.wlo.core.ports.VaultPartitionUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The vault dashboard (F13 §3 storage dashboard + backup posture at a glance). */
public data class VaultDashboardUiState(
    public val partitions: List<VaultPartitionUsage> = emptyList(),
    public val totalVaultBytes: Long = 0,
    public val backup: VaultBackupState? = null,
    public val lastBackupFile: String? = null,
    public val busy: Boolean = false,
    public val healthConnect: HealthConnectSyncStatus? = null,
)

/**
 * The dashboard state holder: per-partition storage accounting with one-tap
 * reclaim (F13 §3 — the answer to photos-default-off, R-U14), the backup
 * posture. Fresh Start is deliberately absent until R-B7 has a reversible implementation.
 */
public class VaultDashboardViewModel(
    private val vault: DataVaultPort,
    private val scheduler: BackupScheduler,
    private val profiles: ProfileRepository,
    private val healthConnectSync: HealthConnectSyncPort,
) : ViewModel() {
    private val usage = MutableStateFlow<List<VaultPartitionUsage>>(emptyList())
    private val backup = MutableStateFlow<VaultBackupState?>(null)
    private val busy = MutableStateFlow(false)
    private val healthConnect = MutableStateFlow<HealthConnectSyncStatus?>(null)
    private val activity = combine(busy, healthConnect) { busyNow, hc -> busyNow to hc }

    public val state: StateFlow<VaultDashboardUiState> =
        combine(
            usage,
            backup,
            activity,
        ) { partitions, backupState, activityNow ->
            val (busyNow, hc) = activityNow
            VaultDashboardUiState(
                partitions = partitions,
                totalVaultBytes = partitions.sumOf { it.bytes },
                backup = backupState,
                lastBackupFile =
                    backupState
                        ?.files
                        ?.maxByOrNull { it.name }
                        ?.name,
                busy = busyNow,
                healthConnect = hc,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultDashboardUiState())

    init {
        refresh()
    }

    public fun refresh() {
        viewModelScope.launch {
            busy.value = true
            usage.value = vault.storageUsage()
            backup.value = vault.backupState()
            healthConnect.value = healthConnectSync.status()
            busy.value = false
        }
    }

    public fun refreshHealthConnect() {
        viewModelScope.launch { healthConnect.value = healthConnectSync.status() }
    }

    public fun syncHealthConnect() {
        viewModelScope.launch {
            busy.value = true
            when (val active = profiles.active()) {
                is WloResult.Ok -> active.value?.let { healthConnectSync.syncNow(it.id) }
                is WloResult.Err -> Unit
            }
            healthConnect.value = healthConnectSync.status()
            busy.value = false
        }
    }

    /** Per-category purge: blobs + the partition key (nothing left to decrypt). */
    public fun reclaim(partition: String) {
        viewModelScope.launch {
            busy.value = true
            vault.reclaimPartition(partition)
            usage.value = vault.storageUsage()
            busy.value = false
        }
    }

    public fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            val current = vault.backupState()
            scheduler.setEnabled(
                enabled = enabled,
                request = current.folderUri?.let { BackupRequest(destinationUri = it, includeVault = false) },
            )
            backup.value = vault.backupState()
        }
    }
}
