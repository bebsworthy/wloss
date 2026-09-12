package app.wlo.core.consent

import app.wlo.core.model.ConsentCapability

/**
 * Consent ledger entries (F12 §3.1, §3.6; R-C1). The ledger is APPEND-ONLY by
 * construction: [ConsentLedger] exposes record/observe only — there is no
 * update or delete API, and revocations are new entries that supersede older
 * grants. Every entry is hash-chained to its predecessor so the chain can be
 * verified after backup/restore.
 */
@kotlinx.serialization.Serializable
public data class ConsentEntry(
    public val seq: Long,
    public val atEpochMs: Long,
    public val capability: ConsentCapability,
    public val decision: ConsentDecision,
    /** Hash of the previous entry, or [GENESIS_PREV_HASH] for seq 0. */
    public val prevHashHex: String,
    /** Hash of (seq|at|capability|decision|prevHash). */
    public val hashHex: String,
) {
    public companion object {
        public const val GENESIS_PREV_HASH: String =
            "0000000000000000000000000000000000000000000000000000000000000000"
    }
}

@kotlinx.serialization.Serializable
public enum class ConsentDecision {
    @kotlinx.serialization.SerialName("grant")
    GRANT,

    @kotlinx.serialization.SerialName("revoke")
    REVOKE,
}
