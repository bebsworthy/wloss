package app.wlo.feature.f13.vault.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.ports.BackupScheduler
import app.wlo.core.ports.DataVaultPort
import app.wlo.core.ports.VaultBackupFileInfo
import app.wlo.core.ports.VaultBackupOutcome
import app.wlo.core.ports.VaultOperationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The backup controls' state (F13 §3: SAF folder chosen once, encrypted
 * by default R-U5, write-new-then-rotate keep 7, auto-backup daily).
 */
public data class BackupControlsUiState(
    public val folderUri: String? = null,
    public val autoEnabled: Boolean = false,
    public val autoKeyConfigured: Boolean = false,
    public val files: List<VaultBackupFileInfo> = emptyList(),
    public val lastBackupFile: String? = null,
    /** A completed manual run, rendered once ("the vault glyph morphs to a check"). */
    public val lastOutcome: VaultBackupOutcome? = null,
    public val failure: String? = null,
    public val running: Boolean = false,
    public val passphraseSet: Boolean = false,
)

public class BackupControlsViewModel(
    private val vault: DataVaultPort,
    private val settings: SettingsStore,
    private val scheduler: BackupScheduler,
) : ViewModel() {
    private val folder = MutableStateFlow<String?>(null)
    private val files = MutableStateFlow<List<VaultBackupFileInfo>>(emptyList())
    private val autoKey = MutableStateFlow(false)
    private val outcome = MutableStateFlow<VaultBackupOutcome?>(null)
    private val failure = MutableStateFlow<String?>(null)
    private val running = MutableStateFlow(false)
    private val passphraseSet = MutableStateFlow(false)
    private val autoEnabled = MutableStateFlow(false)

    private data class BackupCoreState(
        val folderUri: String?,
        val files: List<VaultBackupFileInfo>,
        val autoKey: Boolean,
        val last: VaultBackupOutcome?,
        val failure: String?,
    )

    public val state: StateFlow<BackupControlsUiState> =
        combine(
            combine(folder, files, autoKey, outcome, failure) { folderValue, listed, key, last, error ->
                BackupCoreState(folderValue, listed, key, last, error)
            },
            combine(running, passphraseSet, autoEnabled) { busy, passSet, auto -> Triple(busy, passSet, auto) },
        ) { core, (busy, passSet, auto) ->
            BackupControlsUiState(
                folderUri = core.folderUri,
                autoEnabled = auto,
                autoKeyConfigured = core.autoKey,
                files = core.files,
                lastBackupFile = core.last?.fileName ?: core.files.maxByOrNull { it.name }?.name,
                lastOutcome = core.last,
                failure = core.failure,
                running = busy,
                passphraseSet = passSet,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupControlsUiState())

    init {
        refresh()
        // The folder state mirrors the PERSISTED setting, not just this
        // screen's launcher: the activity-level SAF plumbing (the E2E seeder
        // path) persists the grant too, and the controls must reflect it.
        viewModelScope.launch {
            settings.backupFolderUri.collect { uri -> folder.value = uri }
        }
    }

    public fun refresh() {
        viewModelScope.launch {
            folder.value = settings.backupFolderUriOnce()
            autoEnabled.value = settings.backupAutoEnabled.first()
            autoKey.value = vault.autoBackupKeyConfigured()
            passphraseSet.value = autoKey.value
            files.value = vault.backupState().files
        }
    }

    /** Called by the screen after the SAF ACTION_OPEN_DOCUMENT_TREE pick lands. */
    public fun onFolderPicked(treeUri: String) {
        viewModelScope.launch {
            settings.setBackupFolderUri(treeUri)
            folder.value = treeUri
            files.value = vault.backupState().files
        }
    }

    /** Arms the auto-backup key (device-bound wrap; passphrase never stored). */
    public fun setPassphrase(passphrase: CharArray) {
        viewModelScope.launch {
            failure.value = null
            if (passphrase.size < MIN_PASSPHRASE) {
                failure.value = "Use at least 8 characters — this passphrase is the recovery path of record."
                return@launch
            }
            vault.configureAutoBackupKey(passphrase)
            passphraseSet.value = true
            autoKey.value = true
            autoEnabled.value = true
            settings.setBackupAutoEnabled(true)
            schedule()
        }
    }

    /** Manual run: user-typed passphrase (or the stored key when armed). */
    public fun backupNow(passphrase: CharArray?) {
        viewModelScope.launch {
            val destination = folder.value
            if (destination == null) {
                failure.value = "Choose a backup folder first — your data stays yours."
                return@launch
            }
            running.value = true
            failure.value = null
            try {
                val result = vault.backupNow(passphrase)
                outcome.value = result
                files.value = vault.backupState().files
            } catch (failureEx: VaultOperationException) {
                failure.value = failureEx.message
            } finally {
                running.value = false
            }
        }
    }

    public fun setAuto(enabled: Boolean) {
        viewModelScope.launch {
            settings.setBackupAutoEnabled(enabled)
            autoEnabled.value = enabled
            if (enabled) schedule()
        }
    }

    private fun schedule() {
        val destination = folder.value ?: return
        scheduler.schedule(
            app.wlo.core.ports
                .BackupRequest(destinationUri = destination, includeVault = false),
        )
    }

    private companion object {
        const val MIN_PASSPHRASE: Int = 8
    }
}
