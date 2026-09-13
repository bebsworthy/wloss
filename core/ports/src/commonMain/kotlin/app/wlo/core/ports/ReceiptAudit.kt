package app.wlo.core.ports

import kotlinx.coroutines.flow.Flow

/**
 * One egress receipt, as the F12 receipt-log UI sees it (F12 §3.6). Wire names
 * ride along verbatim so the viewer renders storage truth (the same fields the
 * hash chain covers) without this module knowing the ledger implementation.
 */
public data class ReceiptEntryView(
    public val seq: Long,
    public val purposeWire: String,
    public val host: String,
    public val operation: String,
    public val bytes: Long,
    public val outcomeWire: String,
    public val atEpochMs: Long,
)

/**
 * The verdict of a hash-chain walk over the whole receipt ledger (F12 §3.6:
 * "tamper-evident — a nerd trust gimmick that is also a real audit guarantee").
 * [intact] true = consecutive seqs, every link + recomputed hash valid.
 * [brokenAtSeq] names the first receipt that fails, when one does.
 */
public data class ReceiptChainVerdict(
    public val intact: Boolean,
    public val checked: Int,
    public val brokenAtSeq: Long?,
) {
    public companion object {
        public fun intact(checked: Int): ReceiptChainVerdict = ReceiptChainVerdict(intact = true, checked = checked, brokenAtSeq = null)

        public fun broken(
            checked: Int,
            atSeq: Long?,
        ): ReceiptChainVerdict = ReceiptChainVerdict(intact = false, checked = checked, brokenAtSeq = atSeq)
    }
}

/**
 * The receipt ledger's READ side, as a port (D1: the append side —
 * [EgressPort]'s receipting and the :core:network ledger — is implementation;
 * the F12 receipt viewer consumes this abstraction). There is deliberately no
 * update/remove anywhere: history is evidence.
 */
public interface ReceiptAuditLog {
    /** Most recent receipts, oldest first, capped at [limit]. */
    public suspend fun recent(limit: Long): List<ReceiptEntryView>

    /** Receipt count per purpose (wire names). */
    public suspend fun countByPurpose(): Map<String, Long>

    /** Total receipted bytes (declared or actually sent). */
    public suspend fun totalBytes(): Long

    /** Live receipt stream (ordered by seq). */
    public fun observe(): Flow<List<ReceiptEntryView>>

    /** Verifies the full hash chain (recompute + link walk). */
    public suspend fun verifyChain(): ReceiptChainVerdict
}
