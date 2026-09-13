package app.wlo.app.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.ai.ZooManager
import app.wlo.core.network.EgressLedger
import app.wlo.core.network.EgressReceipt
import app.wlo.core.ports.EgressPurpose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The debug egress monitor's state (F12 §3.8): it reads the PERSISTED receipt
 * ledger (schema-v5 `network_receipts`) and the zoo's real on-disk bytes —
 * never an in-memory counter. "What left my phone?" is answered from the same
 * rows the dispatcher wrote.
 */
public class EgressMonitorViewModel(
    ledger: EgressLedger,
    zoo: ZooManager,
) : ViewModel() {
    public val state: StateFlow<Model> =
        combine(
            ledger.observe(),
            // Re-read true disk usage whenever a zoo state flips.
            zoo.observeStates().map { zoo.storageBytes() },
        ) { receipts, zooStorageBytes ->
            Model(
                receipts = receipts,
                countByPurpose = receipts.groupingBy { it.purpose }.eachCount(),
                totalBytes = receipts.sumOf { it.bytes },
                zooStorageBytes = zooStorageBytes,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Model())

    public data class Model(
        public val receipts: List<EgressReceipt> = emptyList(),
        public val countByPurpose: Map<EgressPurpose, Int> = emptyMap(),
        public val totalBytes: Long = 0,
        public val zooStorageBytes: Long = 0,
    )
}
