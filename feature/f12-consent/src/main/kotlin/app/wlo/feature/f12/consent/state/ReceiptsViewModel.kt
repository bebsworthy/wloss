package app.wlo.feature.f12.consent.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.ports.ReceiptAuditLog
import app.wlo.core.ports.ReceiptChainVerdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One rendered receipt line (F12 §3.6: purpose/host/bytes/time/outcome). */
public data class ReceiptLine(
    public val seq: Long,
    public val purposeWire: String,
    public val host: String,
    public val operation: String,
    public val bytes: Long,
    public val outcomeWire: String,
    public val atEpochMs: Long,
)

public data class ReceiptsUiState(
    public val lines: List<ReceiptLine> = emptyList(),
    public val totalBytes: Long = 0,
    public val countByPurpose: Map<String, Long> = emptyMap(),
    /** null = not yet run; the verify action fills it. */
    public val chainVerdict: ReceiptChainVerdict? = null,
    public val verifying: Boolean = false,
)

/**
 * The receipt log (F12 §3.6): every departure's paperwork — summaries only,
 * never payloads — plus the "verify chain" action that walks the hash chain
 * and renders an intact/tampered verdict with an honest explanation.
 */
public class ReceiptsViewModel(
    private val audit: ReceiptAuditLog,
) : ViewModel() {
    private val verdict = MutableStateFlow<ReceiptChainVerdict?>(null)
    private val verifying = MutableStateFlow(false)

    public val state: StateFlow<ReceiptsUiState> =
        kotlinx.coroutines.flow
            .combine(
                audit.observe(),
                verdict,
                verifying,
            ) { receipts, chain, busy ->
                ReceiptsUiState(
                    lines =
                        receipts
                            .takeLast(RECENT_WINDOW)
                            .map { r ->
                                ReceiptLine(
                                    seq = r.seq,
                                    purposeWire = r.purposeWire,
                                    host = r.host,
                                    operation = r.operation,
                                    bytes = r.bytes,
                                    outcomeWire = r.outcomeWire,
                                    atEpochMs = r.atEpochMs,
                                )
                            },
                    totalBytes = receipts.sumOf { it.bytes },
                    countByPurpose =
                        receipts
                            .groupingBy { it.purposeWire }
                            .eachCount()
                            .mapValues { it.value.toLong() },
                    chainVerdict = chain,
                    verifying = busy,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptsUiState())

    /** The nerd-trust ritual: recompute + link-walk the whole chain. */
    public fun verifyChain() {
        viewModelScope.launch {
            verifying.value = true
            verdict.value = audit.verifyChain()
            verifying.value = false
        }
    }

    private companion object {
        /** The viewer renders the latest window; the chain verifies EVERYTHING. */
        const val RECENT_WINDOW: Int = 200
    }
}
