package app.wlo.feature.f13.vault.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.ports.DataVaultPort
import app.wlo.core.ports.VaultFailure
import app.wlo.core.ports.VaultOperationException
import app.wlo.core.ports.VaultStagedRestoreReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The staged-restore wizard's steps (F13 §4 flow 1/3): pick a `.wlo` file →
 * passphrase → STAGED REPORT (counts, schema migration, warnings — nothing
 * applied) → explicit confirm → atomic apply → done. Any staging failure —
 * including a hostile or corrupt file — lands in [Step.Failed] with copy that
 * says what happened and what was NOT touched: the live data.
 */
public data class RestoreWizardUiState(
    public val step: Step = Step.Pick,
    public val fileName: String? = null,
    public val byteCount: Long = 0,
    public val passphraseError: Boolean = false,
    public val staged: VaultStagedRestoreReport? = null,
    public val commitInserted: Int = 0,
    public val commitSkipped: Int = 0,
    public val commitWarnings: List<String> = emptyList(),
    public val failure: VaultFailure? = null,
    public val failureDetail: String? = null,
) {
    public enum class Step {
        /** Explain + open the document picker. */
        Pick,

        /** Ask for the backup passphrase (encrypted by default, R-U5). */
        Passphrase,

        /** The staged validation report — read before anything happens. */
        Report,

        /** Explicit confirm — restore appends/reconciles, never deletes. */
        Confirm,

        /** Applying (one transaction). */
        Applying,

        /** Success with the inserted/skipped accounting. */
        Done,

        /** Clean failure: hostile/corrupt/wrong-passphrase; data untouched. */
        Failed,
    }
}

public class RestoreWizardViewModel(
    private val vault: DataVaultPort,
) : ViewModel() {
    private val stateFlow = MutableStateFlow(RestoreWizardUiState())

    public val state: StateFlow<RestoreWizardUiState> = stateFlow.asStateFlow()

    private var pendingBytes: ByteArray? = null

    /** The screen's document picker read the SAF stream; hand the bytes in. */
    public fun onFilePicked(
        fileName: String,
        bytes: ByteArray,
    ) {
        pendingBytes = bytes
        stateFlow.value =
            RestoreWizardUiState(
                step = RestoreWizardUiState.Step.Passphrase,
                fileName = fileName,
                byteCount = bytes.size.toLong(),
            )
    }

    public fun backToPick() {
        pendingBytes = null
        stateFlow.value = RestoreWizardUiState()
    }

    public fun submitPassphrase(passphrase: CharArray) {
        val bytes = pendingBytes ?: return
        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(passphraseError = false)
            try {
                val staged = vault.stageRestore(bytes, passphrase)
                stateFlow.value =
                    stateFlow.value.copy(
                        step = RestoreWizardUiState.Step.Report,
                        staged = staged,
                        failure = null,
                        failureDetail = null,
                    )
            } catch (wrong: VaultOperationException) {
                stateFlow.value =
                    stateFlow.value.copy(
                        step =
                            when (wrong.failure) {
                                VaultFailure.WRONG_PASSPHRASE -> RestoreWizardUiState.Step.Passphrase
                                else -> RestoreWizardUiState.Step.Failed
                            },
                        passphraseError = wrong.failure == VaultFailure.WRONG_PASSPHRASE,
                        failure = wrong.failure,
                        failureDetail = wrong.message,
                    )
            }
        }
    }

    public fun confirmCommit() {
        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(step = RestoreWizardUiState.Step.Applying)
            try {
                val commit = vault.commitStaged()
                stateFlow.value =
                    stateFlow.value.copy(
                        step = RestoreWizardUiState.Step.Done,
                        commitInserted = commit.inserted,
                        commitSkipped = commit.skipped,
                        commitWarnings = commit.warnings,
                    )
            } catch (failure: VaultOperationException) {
                stateFlow.value =
                    stateFlow.value.copy(
                        step = RestoreWizardUiState.Step.Failed,
                        failure = failure.failure,
                        failureDetail = failure.message,
                    )
            }
        }
    }

    public fun reset() {
        pendingBytes = null
        stateFlow.value = RestoreWizardUiState()
    }
}
