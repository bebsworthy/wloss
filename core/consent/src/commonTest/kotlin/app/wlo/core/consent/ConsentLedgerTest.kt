package app.wlo.core.consent

import app.wlo.core.model.ConsentCapability
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Sha256Test {
    @Test
    fun nistVectors() {
        // FIPS 180-4 / NIST example vectors.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hexOf("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hexOf("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hexOf(""))
    }

    @Test
    fun longInputMatchesKnownDigest() {
        // "a" repeated one million times — known SHA-256 digest.
        val digest = Sha256.hex(Sha256.digest(CharArray(1_000_000) { 'a' }.concatToString().encodeToByteArray()))
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", digest)
    }

    private fun hexOf(text: String): String = Sha256.hex(Sha256.digest(text.encodeToByteArray()))
}

class ConsentLedgerTest {
    private var clockMs = 1_000L
    private val ledger = InMemoryConsentLedger { clockMs }
    private val gate = ReplayConsentGate(ledger)

    @Test
    fun emptyLedgerGrantsNothing() =
        runTest {
            assertFalse(gate.isGranted(ConsentCapability.FOOD_PHOTO))
            assertTrue(gate.currentGrants().isEmpty())
        }

    @Test
    fun recordGrantsThenRevokes_appendOnlyByConstruction() =
        runTest {
            val capability = ConsentCapability.INSIGHTS_CHAT
            ledger.record(capability, ConsentDecision.GRANT)
            assertTrue(gate.isGranted(capability))

            clockMs += 5_000
            ledger.record(capability, ConsentDecision.REVOKE)
            assertFalse(gate.isGranted(capability))

            val all = ledger.entries()
            // Append-only: both entries still exist, history preserved.
            assertEquals(2, all.size)
            assertEquals(ConsentDecision.GRANT, all[0].decision)
            assertEquals(ConsentDecision.REVOKE, all[1].decision)
        }

    @Test
    fun chainIsContiguousAndDetectsTampering() =
        runTest {
            val capabilities = ConsentCapability.entries
            capabilities.take(4).forEachIndexed { index, capability ->
                clockMs += 1_000
                ledger.record(
                    capability,
                    if (index % 2 == 0) ConsentDecision.GRANT else ConsentDecision.REVOKE,
                )
            }
            val entries = ledger.entries()
            assertTrue(ConsentChain.verify(entries), "fresh ledger must verify")

            val tampered = entries.toMutableList()
            // entries[1] is a REVOKE (index % 2 == 1) — flipping it must break the chain.
            tampered[1] = tampered[1].copy(decision = ConsentDecision.GRANT)
            assertFalse(ConsentChain.verify(tampered), "edited history must fail verification")

            val truncated = entries.dropLast(1)
            assertTrue(ConsentChain.verify(truncated), "prefix of a valid chain is still a valid chain")
        }

    @Test
    fun grantSetReflectsLatestDecisionPerCapability() =
        runTest {
            ledger.record(ConsentCapability.FOOD_PHOTO, ConsentDecision.GRANT)
            ledger.record(ConsentCapability.POOP_PHOTO, ConsentDecision.GRANT)

            assertEquals(
                setOf(ConsentCapability.FOOD_PHOTO, ConsentCapability.POOP_PHOTO),
                gate.currentGrants(),
            )
        }
}
