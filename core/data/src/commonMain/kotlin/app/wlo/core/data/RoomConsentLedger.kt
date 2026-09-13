package app.wlo.core.data

import app.wlo.core.consent.ConsentChain
import app.wlo.core.consent.ConsentDecision
import app.wlo.core.consent.ConsentEntry
import app.wlo.core.consent.ConsentLedger
import app.wlo.core.consent.ConsentTimeSource
import app.wlo.core.database.ConsentLedgerDao
import app.wlo.core.database.ConsentLedgerEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.model.ConsentCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The consent ledger over the `consent_ledger` table — the M6 PART B swap the
 * wipe-restore E2E anticipated ("PART B swaps the in-memory ledger for this
 * store"): grants and revocations SURVIVE restarts and ride every backup, so
 * the F12 consent matrix, its hash chain, and the restore reconciliation all
 * speak one history (F12 §3.1/§3.6, R-C1).
 *
 * Append-only by construction: [ConsentLedgerDao] exposes insert/read only —
 * a revocation is a new chained row, never an edit. Seqs continue the stored
 * chain head (0-based on an empty ledger, matching [ConsentChain]'s seq
 * contract); each row embeds the previous row's hash, and the stored hash is
 * recomputed over the canonical form before insert — the tamper evidence is
 * created here, not assumed.
 *
 * Restores (F13 staged restore) may append EXPLICIT-seq rows only when the
 * backup chain continues the local head — that discipline lives in
 * `:core:vault`'s RestoreCommitter; this class never rewrites seqs.
 */
public class RoomConsentLedger(
    db: WloDatabase,
    private val timeSource: ConsentTimeSource,
) : ConsentLedger {
    private val dao: ConsentLedgerDao = db.consentLedger()
    private val mutex = Mutex()

    override suspend fun record(
        capability: ConsentCapability,
        decision: ConsentDecision,
    ): ConsentEntry =
        mutex.withLock {
            val last = dao.last()
            val seq = (last?.seq ?: -1L) + 1L
            val prevHash = last?.hashHex ?: ConsentEntry.GENESIS_PREV_HASH
            val unsigned =
                ConsentEntry(
                    seq = seq,
                    atEpochMs = timeSource.nowEpochMs(),
                    capability = capability,
                    decision = decision,
                    prevHashHex = prevHash,
                    hashHex = "",
                )
            val sealed = unsigned.copy(hashHex = ConsentChain.hashOf(unsigned))
            dao.append(
                ConsentLedgerEntity(
                    seq = sealed.seq,
                    profileId = CONSENT_PROFILE_ID,
                    capability = sealed.capability.wireName,
                    decision = if (sealed.decision == ConsentDecision.GRANT) "grant" else "revoke",
                    atEpochMs = sealed.atEpochMs,
                    prevHashHex = sealed.prevHashHex,
                    hashHex = sealed.hashHex,
                ),
            )
            sealed
        }

    override suspend fun entries(): List<ConsentEntry> = dao.all().map { it.toEntry() }

    override fun observe(): Flow<List<ConsentEntry>> = dao.observeAll().map { rows -> rows.map { it.toEntry() } }

    private fun ConsentLedgerEntity.toEntry(): ConsentEntry =
        ConsentEntry(
            seq = seq,
            atEpochMs = atEpochMs,
            capability =
                ConsentCapability.entries.firstOrNull { it.wireName == capability }
                    ?: ConsentCapability.FOOD_PHOTO,
            decision = if (decision == "grant") ConsentDecision.GRANT else ConsentDecision.REVOKE,
            prevHashHex = prevHashHex,
            hashHex = hashHex,
        )

    public companion object {
        /** R-B9 single-profile v1: every consent row rides the default profile id. */
        public const val CONSENT_PROFILE_ID: String = "default"
    }
}

/**
 * The raw-row door already used by tests/seeding lives in RoomRepositories.kt
 * ([RoomConsentLedgerStore]); explicit-seq chain writes (the E2E seeder) keep
 * working through it.
 */
