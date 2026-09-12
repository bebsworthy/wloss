package app.wlo.core.consent

import app.wlo.core.model.ConsentCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory append-only ledger. Records chain each entry to the previous hash;
 * there is no mutation path by construction (the backing list is only ever
 * appended to).
 */
public class InMemoryConsentLedger(
    private val timeSource: ConsentTimeSource,
) : ConsentLedger {
    private val mutex = Mutex()
    private val stateFlow = MutableStateFlow<List<ConsentEntry>>(emptyList())

    override suspend fun record(
        capability: ConsentCapability,
        decision: ConsentDecision,
    ): ConsentEntry =
        mutex.withLock {
            val current = stateFlow.value
            val previous = current.lastOrNull()
            val unsigned =
                ConsentEntry(
                    seq = current.size.toLong(),
                    atEpochMs = timeSource.nowEpochMs(),
                    capability = capability,
                    decision = decision,
                    prevHashHex = previous?.hashHex ?: ConsentEntry.GENESIS_PREV_HASH,
                    hashHex = "",
                )
            val sealed = unsigned.copy(hashHex = ConsentChain.hashOf(unsigned))
            stateFlow.value = current + sealed
            sealed
        }

    override suspend fun entries(): List<ConsentEntry> = stateFlow.value

    override fun observe(): Flow<List<ConsentEntry>> = stateFlow.asStateFlow()
}

/**
 * Gate replaying the ledger: latest entry per capability wins; empty ledger ⇒
 * nothing granted ("no consent, no capability" by default).
 */
public class ReplayConsentGate(
    private val ledger: ConsentLedger,
) : ConsentGate {
    override suspend fun isGranted(capability: ConsentCapability): Boolean =
        latestDecision(ledger.entries())[capability] == ConsentDecision.GRANT

    override suspend fun currentGrants(): Set<ConsentCapability> =
        latestDecision(ledger.entries())
            .filterValues { it == ConsentDecision.GRANT }
            .keys

    /** Live grant set, e.g. for the F12 §3.4 consent-matrix UI. */
    public fun observeGrants(): Flow<Set<ConsentCapability>> =
        ledger.observe().map { entries ->
            latestDecision(entries).filterValues { it == ConsentDecision.GRANT }.keys
        }

    private fun latestDecision(entries: List<ConsentEntry>): Map<ConsentCapability, ConsentDecision> {
        val latest = mutableMapOf<ConsentCapability, ConsentDecision>()
        entries.forEach { entry -> latest[entry.capability] = entry.decision }
        return latest
    }
}
