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
 * and in the F12 receipt-log UI ("nothing was edited after the fact").
 */
public object ConsentChain {
    /** True when every entry links to its predecessor with a valid hash. */
    public fun verify(entries: List<ConsentEntry>): Boolean {
        var expectedPrev = ConsentEntry.GENESIS_PREV_HASH
        entries.forEachIndexed { index, entry ->
            if (entry.seq != index.toLong()) return false
            if (entry.prevHashHex != expectedPrev) return false
            if (entry.hashHex != hashOf(entry)) return false
            expectedPrev = entry.hashHex
        }
        return true
    }

    public fun hashOf(entry: ConsentEntry): String {
        val canonical =
            "${entry.seq}|${entry.atEpochMs}|${entry.capability.wireName}|" +
                "${entry.decision.name}|${entry.prevHashHex}"
        return Sha256.hex(Sha256.digest(canonical.encodeToByteArray()))
    }
}
