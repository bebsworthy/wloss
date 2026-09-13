package app.wlo.feature.f13.vault.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.ports.DataVaultPort
import app.wlo.core.ports.VaultBackupState
import app.wlo.core.ports.VaultFreshStartReport
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
    public val freshStartPreview: VaultFreshStartReport? = null,
    public val freshStartDone: VaultFreshStartReport? = null,
    public val busy: Boolean = false,
)

/**
 * The dashboard state holder: per-partition storage accounting with one-tap
 * reclaim (F13 §3 — the answer to photos-default-off, R-U14), the backup
 * posture, and the Fresh Start (R-B7) hide-not-delete entry.
 */
public class VaultDashboardViewModel(
    private val vault: DataVaultPort,
    private val settings: SettingsStore,
) : ViewModel() {
    private val usage = MutableStateFlow<List<VaultPartitionUsage>>(emptyList())
    private val backup = MutableStateFlow<VaultBackupState?>(null)
    private val freshPreview = MutableStateFlow<VaultFreshStartReport?>(null)
    private val freshDone = MutableStateFlow<VaultFreshStartReport?>(null)
    private val busy = MutableStateFlow(false)

    public val state: StateFlow<VaultDashboardUiState> =
        combine(usage, backup, freshPreview, freshDone, busy) { partitions, backupState, preview, done, busyNow ->
            VaultDashboardUiState(
                partitions = partitions,
                totalVaultBytes = partitions.sumOf { it.bytes },
                backup = backupState,
                lastBackupFile =
                    backupState
                        ?.files
                        ?.maxByOrNull { it.name }
                        ?.name,
                freshStartPreview = preview,
                freshStartDone = done,
                busy = busyNow,
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
            freshPreview.value = vault.freshStartPreview()
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
            settings.setBackupAutoEnabled(enabled)
            backup.value = vault.backupState()
        }
    }

    /**
     * Fresh Start (R-B7): hide-not-delete. Diary history is archived (every
     * row survives, reversible), the active profile retires, and the next
     * launch walks onboarding again. Backups keep everything.
     */
    public fun freshStart() {
        viewModelScope.launch {
            busy.value = true
            freshDone.value = vault.freshStartHide()
            refresh()
        }
    }
}
