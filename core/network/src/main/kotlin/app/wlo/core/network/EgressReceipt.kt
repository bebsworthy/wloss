package app.wlo.core.network

import app.wlo.core.consent.HashChain
import app.wlo.core.ports.EgressPurpose

/** How a dispatched call ended — receipted verbatim (F12 §3.6). */
public enum class EgressOutcome(
    public val wireName: String,
) {
    /** The call ran; [EgressReceipt.bytes] left the device (or were declared). */
    OK("ok"),

    /** Enforcement refused the call — nothing was sent, bytes are 0. */
    DENIED("denied"),

    /** The call was allowed but failed mid-flight (network, HTTP status, cap). */
    FAILED("failed"),

    /** Served from the dispatcher's cache-first store (R-C4); nothing was sent. */
    CACHE_HIT("cache-hit"),
}

/**
 * One hash-chained egress receipt (F12 §3.6): purpose, host, bytes, at,
 * outcome — written for EVERY dispatch, success OR failure. Append-only by
 * construction: ledgers expose append/read only.
 */
public data class EgressReceipt(
    public val seq: Long,
    public val purpose: EgressPurpose,
    public val host: String,
    public val operation: String,
    public val bytes: Long,
    public val outcome: EgressOutcome,
    public val atEpochMs: Long,
    /** Hash of the previous receipt, or the genesis hash at [SEQ_BASE]. */
    public val prevHashHex: String,
    /** Hash of the canonical form ([ReceiptChain.canonical]). */
    public val hashHex: String,
)

/** A receipt before the ledger assigns seq + chain hashes. */
public data class PendingReceipt(
    public val purpose: EgressPurpose,
    public val host: String,
    public val operation: String,
    public val bytes: Long,
    public val outcome: EgressOutcome,
    public val atEpochMs: Long,
)

/**
 * The receipt chain, verified with the same engine as the consent ledger
 * (:core:consent [HashChain] — "the ConsentChain verifier"). Seqs are 1-based
 * (Room AUTOINCREMENT base); the first receipt links to the genesis hash.
 */
public object ReceiptChain {
    public const val SEQ_BASE: Long = 1L

    /** Canonical chain string over raw wire fields (storage-level truth). */
    public fun canonicalOf(
        seq: Long,
        atEpochMs: Long,
        purposeWire: String,
        host: String,
        operation: String,
        bytes: Long,
        outcomeWire: String,
        prevHashHex: String,
    ): String = "$seq|$atEpochMs|$purposeWire|$host|$operation|$bytes|$outcomeWire|$prevHashHex"

    public fun canonical(receipt: EgressReceipt): String =
        canonicalOf(
            seq = receipt.seq,
            atEpochMs = receipt.atEpochMs,
            purposeWire = receipt.purpose.wireName,
            host = receipt.host,
            operation = receipt.operation,
            bytes = receipt.bytes,
            outcomeWire = receipt.outcome.wireName,
            prevHashHex = receipt.prevHashHex,
        )

    public fun hashOf(receipt: EgressReceipt): String = HashChain.sha256Hex(canonical(receipt))

    /** True when the chain is intact: consecutive seqs, valid links + hashes. */
    public fun verify(receipts: List<EgressReceipt>): Boolean =
        HashChain.verify(
            entries = receipts,
            seqBase = SEQ_BASE,
            seqOf = { it.seq },
            prevHashOf = { it.prevHashHex },
            canonicalOf = ::canonical,
            hashOf = { it.hashHex },
        )
}
