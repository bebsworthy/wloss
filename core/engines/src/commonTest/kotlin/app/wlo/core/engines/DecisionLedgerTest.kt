package app.wlo.core.engines

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The decision ledger (F07 §3 [v1]) must be deterministic and hash-stable. */
class DecisionLedgerTest {
    @Serializable
    private data class Inputs(
        val windowDays: Int,
        val avgIntakeKcal: Double,
        val weeklyTrendChangeKg: Double,
    )

    @Serializable
    private data class Output(
        val tdeeKcal: Double,
        val decision: String,
    )

    @Test
    fun recordCapturesVersionedInputsAndOutput() {
        val decision =
            DecisionLedger.record(
                decisionId = "checkin-2026-W37",
                atEpochMs = 1_788_482_400_000,
                engineVersion = EnergyEngine.ENGINE_VERSION,
                formulaVersion = EnergyEngine.TDEE_FORMULA_VERSION,
                inputs = Inputs(14, 1900.0, -0.45),
                output = Output(2410.0, "apply"),
                summary = "tdee 2410 · apply +50",
            )
        assertEquals(EnergyEngine.ENGINE_VERSION, decision.engineVersion)
        assertEquals(EnergyEngine.TDEE_FORMULA_VERSION, decision.formulaVersion)
        assertTrue(decision.inputSnapshotJson.contains("\"avgIntakeKcal\":1900.0"))
        assertTrue(decision.outputJson.contains("\"decision\":\"apply\""))
        assertEquals(16, decision.inputsHashHex.length)
    }

    @Test
    fun equalInputsProduceEqualHashes_differingInputsDoNot() {
        val a =
            DecisionLedger.record(
                decisionId = "d1",
                atEpochMs = 1,
                engineVersion = "transparent-v1",
                formulaVersion = "tdee/closed-form-v1",
                inputs = Inputs(14, 1900.0, -0.45),
                output = Output(2410.0, "apply"),
                summary = "a",
            )
        val b =
            DecisionLedger.record(
                decisionId = "d2",
                atEpochMs = 99_999,
                engineVersion = "transparent-v1",
                formulaVersion = "tdee/closed-form-v1",
                inputs = Inputs(14, 1900.0, -0.45),
                output = Output(2410.0, "apply"),
                summary = "b",
            )
        assertEquals(a.inputsHashHex, b.inputsHashHex, "same inputs ⇒ same fingerprint")

        val c =
            DecisionLedger.record(
                decisionId = "d3",
                atEpochMs = 1,
                engineVersion = "transparent-v1",
                formulaVersion = "tdee/closed-form-v1",
                inputs = Inputs(14, 1900.5, -0.45),
                output = Output(2410.0, "apply"),
                summary = "c",
            )
        assertNotEquals(a.inputsHashHex, c.inputsHashHex, "a moved input must move the fingerprint")
    }

    @Test
    fun hashIsPlatformStableHex() {
        // FNV-1a 64 test vectors ("a" = af63dc4c8601ec8c, "" = offset basis
        // cbf29ce484222325) — freezes the implementation against silent drift.
        assertEquals("af63dc4c8601ec8c", InputsHash.fnv1a64("a"))
        assertEquals("cbf29ce484222325", InputsHash.fnv1a64(""))
    }
}
