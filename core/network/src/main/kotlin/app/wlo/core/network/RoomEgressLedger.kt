package app.wlo.core.network

import app.wlo.core.consent.HashChain
import app.wlo.core.database.NetworkReceiptEntity
import app.wlo.core.database.PurposeCount
import app.wlo.core.database.WloDatabase
import app.wlo.core.ports.EgressPurpose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room-backed receipt ledger over the `network_receipts` table (schema v5).
 * Chain state lives in the table itself (last row's hash), guarded by a mutex:
 * receipts are appended under a single lock so seqs and links stay tight.
 * The DAO exposes no update/delete — the store is append-only by construction.
 */
public class RoomEgressLedger(
    private val db: WloDatabase,
) : EgressLedger {
    private val mutex = Mutex()
    private val dao = db.networkReceipts()

    override suspend fun append(pending: PendingReceipt): EgressReceipt =
        mutex.withLock {
            val last = dao.last()
            val seq = (last?.seq ?: 0L) + 1L
            val prevHash = last?.hashHex ?: GENESIS_HASH
            val unsigned =
                NetworkReceiptEntity(
                    seq = seq,
                    purpose = pending.purpose.wireName,
                    host = pending.host,
                    operation = pending.operation,
                    bytes = pending.bytes,
                    outcome = pending.outcome.wireName,
                    atEpochMs = pending.atEpochMs,
                    prevHashHex = prevHash,
                    hashHex = "",
                )
            val sealed =
                unsigned.copy(
                    hashHex =
                        ReceiptChain
                            .canonicalOf(
                                seq = seq,
                                atEpochMs = unsigned.atEpochMs,
                                purposeWire = unsigned.purpose,
                                host = unsigned.host,
                                operation = unsigned.operation,
                                bytes = unsigned.bytes,
                                outcomeWire = unsigned.outcome,
                                prevHashHex = unsigned.prevHashHex,
                            ).let(HashChain::sha256Hex),
                )
            dao.append(sealed)
            sealed.toReceipt()
        }

    override suspend fun recent(limit: Long): List<EgressReceipt> = dao.recent(limit).map { it.toReceipt() }

    override suspend fun countByPurpose(): Map<EgressPurpose, Long> =
        dao
            .countByPurpose()
            .mapNotNull { row: PurposeCount ->
                purposeFromWire(row.purpose)?.let { it to row.count }
            }.toMap()

    override suspend fun totalBytes(): Long = dao.totalBytes()

    override fun observe(): Flow<List<EgressReceipt>> = dao.observeAll().map { rows -> rows.map { it.toReceipt() } }

    public companion object {
        /** All-zero genesis prev-hash (chain base; seqs start at 1). */
        public const val GENESIS_HASH: String =
            "0000000000000000000000000000000000000000000000000000000000000000"

        internal fun purposeFromWire(wireName: String) = EgressPurpose.entries.firstOrNull { it.wireName == wireName }

        internal fun outcomeFromWire(wireName: String): EgressOutcome =
            EgressOutcome.entries.firstOrNull { it.wireName == wireName } ?: EgressOutcome.FAILED

        internal fun NetworkReceiptEntity.toReceipt(): EgressReceipt =
            EgressReceipt(
                seq = seq,
                purpose = purposeFromWire(purpose) ?: EgressPurpose.OFF_LOOKUP,
                host = host,
                operation = operation,
                bytes = bytes,
                outcome = outcomeFromWire(outcome),
                atEpochMs = atEpochMs,
                prevHashHex = prevHashHex,
                hashHex = hashHex,
            )
    }
}
