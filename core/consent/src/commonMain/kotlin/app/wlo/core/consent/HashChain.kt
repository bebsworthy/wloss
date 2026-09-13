package app.wlo.core.consent

/**
 * The hash-chain mechanics shared by every append-only WLO ledger (F12 §3.6):
 * entries are numbered sequentially, each embeds its predecessor's hash, and a
 * canonical `field|field|…|prevHash` string is SHA-256'd into the entry hash.
 * [ConsentChain] verifies the consent ledger through this; `:core:network`'s
 * receipt chain verifies egress receipts through the same machinery, so "the
 * ConsentChain verifier" is literally the receipt verifier's engine.
 */
public object HashChain {
    /** Prev-hash of the first entry in any chain (all-zero, 64 hex chars). */
    public const val GENESIS_PREV_HASH: String = "0000000000000000000000000000000000000000000000000000000000000000"

    /** SHA-256 of the canonical string, lowercase hex. */
    public fun sha256Hex(canonical: String): String = Sha256.hex(Sha256.digest(canonical.encodeToByteArray()))

    /**
     * Sequential-chain verify for any entry shape: [seqOf] numbering must run
     * consecutively from [seqBase], each entry must embed the previous entry's
     * hash (or [GENESIS_PREV_HASH] at the start), and each entry's stored hash
     * must equal the SHA-256 of its canonical form.
     */
    public fun <T> verify(
        entries: List<T>,
        seqBase: Long,
        seqOf: (T) -> Long,
        prevHashOf: (T) -> String,
        canonicalOf: (T) -> String,
        hashOf: (T) -> String,
    ): Boolean {
        var expectedPrev = GENESIS_PREV_HASH
        entries.forEachIndexed { index, entry ->
            if (seqOf(entry) != index + seqBase) return false
            if (prevHashOf(entry) != expectedPrev) return false
            if (hashOf(entry) != sha256Hex(canonicalOf(entry))) return false
            expectedPrev = hashOf(entry)
        }
        return true
    }
}
