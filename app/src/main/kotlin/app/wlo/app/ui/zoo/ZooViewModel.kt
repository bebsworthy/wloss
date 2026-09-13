package app.wlo.app.ui.zoo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.ai.ZooManager
import app.wlo.core.ai.ZooModel
import app.wlo.core.ai.ZooModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The model-zoo manager's state holder (F12 §3.2, R-S14): per-model cards
 * with size BEFORE download, license + training-data provenance, live states
 * (downloading progress included), storage accounting, download/reclaim.
 */
public class ZooViewModel(
    private val zoo: ZooManager,
) : ViewModel() {
    public data class ModelCard(
        public val model: ZooModel,
        public val state: ZooModelState,
    )

    public data class ZooState(
        public val cards: List<ModelCard> = emptyList(),
        public val storageBytes: Long = 0L,
        public val busyModelId: String? = null,
        public val notice: String? = null,
    )

    private val state = MutableStateFlow(ZooState())
    public val uiState: StateFlow<ZooState> = state

    init {
        viewModelScope.launch {
            zoo.observeStates().collectLatest { states ->
                state.value =
                    state.value.copy(
                        cards =
                            zoo.catalog().map { model ->
                                ModelCard(model, states[model.id] ?: ZooModelState.NotDownloaded)
                            },
                    )
                state.value = state.value.copy(storageBytes = zoo.storageBytes())
            }
        }
        viewModelScope.launch { refreshStorage() }
    }

    public fun onEvent(event: ZooEvent) {
        when (event) {
            is ZooEvent.Download -> download(event.modelId)
            is ZooEvent.Reclaim -> reclaim(event.modelId)
            ZooEvent.DismissNotice -> state.value = state.value.copy(notice = null)
        }
    }

    private fun download(modelId: String) {
        if (state.value.busyModelId != null) return
        state.value = state.value.copy(busyModelId = modelId)
        viewModelScope.launch {
            val outcome = zoo.ensureAvailable(modelId)
            state.value =
                state.value.copy(
                    busyModelId = null,
                    storageBytes = zoo.storageBytes(),
                    notice =
                        outcome.fold(
                            onSuccess = { handle ->
                                "${handle.modelId} verified (${handle.sizeBytes} B, sha256 ok) — ready offline"
                            },
                            onFailure = { "download didn't complete: ${it.message} — try again when online" },
                        ),
                )
        }
    }

    private fun reclaim(modelId: String) {
        if (state.value.busyModelId != null) return
        state.value = state.value.copy(busyModelId = modelId)
        viewModelScope.launch {
            zoo.reclaim(modelId)
            state.value =
                state.value.copy(
                    busyModelId = null,
                    storageBytes = zoo.storageBytes(),
                    notice = "$modelId reclaimed — the space is back; it downloads again on first use",
                )
        }
    }

    private suspend fun refreshStorage() {
        state.value = state.value.copy(storageBytes = zoo.storageBytes())
    }
}

public sealed interface ZooEvent {
    public data class Download(
        public val modelId: String,
    ) : ZooEvent

    public data class Reclaim(
        public val modelId: String,
    ) : ZooEvent

    public data object DismissNotice : ZooEvent
}
