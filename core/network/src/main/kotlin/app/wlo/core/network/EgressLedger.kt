package app.wlo.core.network

import app.wlo.core.consent.ConsentTimeSource
import app.wlo.core.consent.HashChain
import app.wlo.core.ports.EgressPurpose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The append-only egress receipt store (F12 §3.6). There is deliberately no
 * update/remove: history is evidence. Implementations hand out monotonically
 * increasing seqs (1-based) and chain each receipt to the previous hash, so
 * [ReceiptChain.verify] can prove nothing was edited after the fact. The query
 * side (recent/count-by-purpose/observe) feeds the debug egress monitor.
 */
public interface EgressLedger {
    /** Chains and persists one receipt; returns the sealed entry. */
    public suspend fun append(pending: PendingReceipt): EgressReceipt

    /** Most recent receipts, oldest first, capped at [limit]. */
    public suspend fun recent(limit: Long): List<EgressReceipt>

    /** Receipt count per purpose (wire names unmapped are ignored). */
    public suspend fun countByPurpose(): Map<EgressPurpose, Long>

    /** Total bytes that ever left (or were declared to leave) the device. */
    public suspend fun totalBytes(): Long

    /** Live receipt stream (ordered by seq) for the debug monitor. */
    public fun observe(): Flow<List<EgressReceipt>>
}

/**
 * In-memory append-only ledger — the chain semantics shared with the Room
 * implementation, usable in tests, previews and (later) the F12 receipt UI
 * before the vault exists. Backing list is only ever appended to.
 */
public class InMemoryEgressLedger : EgressLedger {
    private val mutex = Mutex()
    private val stateFlow = MutableStateFlow<List<EgressReceipt>>(emptyList())

    override suspend fun append(pending: PendingReceipt): EgressReceipt =
        mutex.withLock {
            val current = stateFlow.value
            val previous = current.lastOrNull()
            val unsigned =
                EgressReceipt(
                    seq = (current.size + 1).toLong(),
                    purpose = pending.purpose,
                    host = pending.host,
                    operation = pending.operation,
                    bytes = pending.bytes,
                    outcome = pending.outcome,
                    atEpochMs = pending.atEpochMs,
                    prevHashHex = previous?.hashHex ?: HashChain.GENESIS_PREV_HASH,
                    hashHex = "",
                )
            val sealed = unsigned.copy(hashHex = ReceiptChain.hashOf(unsigned))
            stateFlow.value = current + sealed
            sealed
        }

    override suspend fun recent(limit: Long): List<EgressReceipt> {
        val capped = limit.coerceAtLeast(0).toInt()
        return stateFlow.value.takeLast(capped)
    }

    override suspend fun countByPurpose(): Map<EgressPurpose, Long> =
        stateFlow.value
            .groupingBy { it.purpose }
            .eachCount()
            .mapValues { it.value.toLong() }

    override suspend fun totalBytes(): Long = stateFlow.value.sumOf { it.bytes }

    override fun observe(): Flow<List<EgressReceipt>> = stateFlow.asStateFlow()
}

/** Default time source for production ledgers/dispatcher wiring. */
public fun systemEpochMs(): ConsentTimeSource = ConsentTimeSource { System.currentTimeMillis() }
