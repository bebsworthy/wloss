package app.wlo.core.consent

import app.wlo.core.model.ConsentCapability
import kotlinx.coroutines.flow.Flow

/**
 * Append-only consent ledger (F12 §3.6). There is deliberately no
 * update/remove: history is evidence. Implementations must hand out monotonically
 * increasing [ConsentEntry.seq] and chain each entry to the previous hash.
 */
public interface ConsentLedger {
    /** Appends a grant or revoke. Never mutates or removes earlier entries. */
    public suspend fun record(
        capability: ConsentCapability,
        decision: ConsentDecision,
    ): ConsentEntry

    /** Full ledger snapshot, ordered by [ConsentEntry.seq]. */
    public suspend fun entries(): List<ConsentEntry>

    /** Reactive ledger stream, ordered by [ConsentEntry.seq]. */
    public fun observe(): Flow<List<ConsentEntry>>
}

/**
 * Current grant state, replayed from the ledger: the latest entry per
 * capability wins. The gate is the only door AI capabilities open (C3/F12).
 */
public interface ConsentGate {
    public suspend fun isGranted(capability: ConsentCapability): Boolean

    public suspend fun currentGrants(): Set<ConsentCapability>
}

/**
 * Hash-chain verifier for consent/receipt entries — used after backup/restore
 * and in the F12 receipt-log UI ("nothing was edited after the fact"). Delegates
 * to [HashChain], the chain engine shared with the egress receipt ledger.
 */
public object ConsentChain {
    /** True when every entry links to its predecessor with a valid hash. */
    public fun verify(entries: List<ConsentEntry>): Boolean =
        HashChain.verify(
            entries = entries,
            seqBase = 0L,
            seqOf = ConsentEntry::seq,
            prevHashOf = ConsentEntry::prevHashHex,
            canonicalOf = ::canonicalOf,
            hashOf = ConsentEntry::hashHex,
        )

    public fun hashOf(entry: ConsentEntry): String = HashChain.sha256Hex(canonicalOf(entry))

    private fun canonicalOf(entry: ConsentEntry): String =
        "${entry.seq}|${entry.atEpochMs}|${entry.capability.wireName}|" +
            "${entry.decision.name}|${entry.prevHashHex}"
}
