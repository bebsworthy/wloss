package app.wlo.core.network

import app.wlo.core.consent.ConsentChain
import app.wlo.core.consent.ConsentDecision
import app.wlo.core.consent.ConsentEntry
import app.wlo.core.consent.ConsentTimeSource
import app.wlo.core.consent.HashChain
import app.wlo.core.consent.InMemoryConsentLedger
import app.wlo.core.model.ConsentCapability
import app.wlo.core.ports.EgressPurpose
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Receipt chain integrity under the ConsentChain's own verifier engine
 * (:core:consent HashChain): consecutive seqs, predecessor links, canonical
 * hashes — and tamper-evidence, which is the point of F12 §3.6's ledger.
 */
class ReceiptChainTest {
    private suspend fun threeChain(): Pair<InMemoryEgressLedger, List<EgressReceipt>> {
        val ledger = InMemoryEgressLedger()
        ledger.append(
            PendingReceipt(
                EgressPurpose.ZOO_DOWNLOAD,
                "huggingface.co",
                "zoo/food-classifier/1",
                bytes = 9_835_830,
                outcome = EgressOutcome.OK,
                atEpochMs = 1_000,
            ),
        )
        ledger.append(
            PendingReceipt(
                EgressPurpose.OFF_LOOKUP,
                "world.openfoodfacts.org",
                "off/product/x",
                bytes = 512,
                outcome = EgressOutcome.OK,
                atEpochMs = 2_000,
            ),
        )
        ledger.append(
            PendingReceipt(
                EgressPurpose.FUTURE_CLOUD_CHAT,
                "api.example.com",
                "chat/ask",
                bytes = 0,
                outcome = EgressOutcome.DENIED,
                atEpochMs = 3_000,
            ),
        )
        return ledger to ledger.recent(10)
    }

    @Test
    fun chainedReceiptsVerify() {
        kotlinx.coroutines.test.runTest {
            val (ledger, receipts) = threeChain()
            assertTrue(ReceiptChain.verify(receipts), "a fresh ledger must verify")
            assertEquals(listOf(1L, 2L, 3L), receipts.map { it.seq }, "receipt seqs are 1-based")
            assertEquals(
                HashChain.GENESIS_PREV_HASH,
                receipts.first().prevHashHex,
                "the first receipt links to the genesis hash",
            )
            assertEquals(
                receipts.dropLast(1).map { it.hashHex },
                receipts.drop(1).map { it.prevHashHex },
                "every receipt embeds its predecessor's hash",
            )
            assertTrue(ledger.countByPurpose().getValue(EgressPurpose.ZOO_DOWNLOAD) == 1L)
            assertEquals(9_835_830L + 512, ledger.totalBytes())
        }
    }

    @Test
    fun tamperedBytesBreakTheChain() {
        kotlinx.coroutines.test.runTest {
            val (_, receipts) = threeChain()
            val tampered =
                receipts.mapIndexed { i, r ->
                    if (i == 1) r.copy(bytes = 1) else r
                }
            assertFalse(ReceiptChain.verify(tampered), "editing a byte count must be detectable")
        }
    }

    @Test
    fun removedOrReorderedReceiptsBreakTheChain() {
        kotlinx.coroutines.test.runTest {
            val (_, receipts) = threeChain()
            assertFalse(ReceiptChain.verify(receipts - receipts[1]), "deleting a middle receipt shifts seqs")
            assertFalse(ReceiptChain.verify(listOf(receipts[1], receipts[0], receipts[2])), "reordering shifts links")
        }
    }

    @Test
    fun denialAndCacheHitReceiptsVerifyLikeAnyOther() {
        kotlinx.coroutines.test.runTest {
            val ledger = InMemoryEgressLedger()
            ledger.append(
                PendingReceipt(
                    EgressPurpose.OFF_LOOKUP,
                    "world.openfoodfacts.org",
                    "off/hit",
                    bytes = 0,
                    outcome = EgressOutcome.CACHE_HIT,
                    atEpochMs = 10,
                ),
            )
            assertTrue(ReceiptChain.verify(ledger.recent(10)))
        }
    }

    @Test
    fun sharesTheConsentChainEngine() {
        // Same canonical-hash primitive as the consent ledger (F12 §3.6).
        assertEquals(
            MessageDigest
                .getInstance("SHA-256")
                .digest("abc".encodeToByteArray())
                .joinToString(separator = "") { "%02x".format(it) },
            HashChain.sha256Hex("abc"),
            "the pure-Kotlin sha256 must agree with the JVM's",
        )
        kotlinx.coroutines.test.runTest {
            val consentLedger = InMemoryConsentLedger(ConsentTimeSource { 42 })
            val entry: ConsentEntry = consentLedger.record(ConsentCapability.FOOD_PHOTO, ConsentDecision.GRANT)
            assertTrue(ConsentChain.verify(consentLedger.entries()), "consent chain untouched by the receipt work")
            assertEquals(entry.hashHex, consentLedger.entries().single().hashHex)
        }
    }
}
