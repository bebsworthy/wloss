package app.wlo.core.documents

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Round-trip + envelope house rules for the Targets funnel (ADR-004 rule 4:
 * old-version fixtures decode in CI — v1 is the genesis version, and the
 * unknown-schemaVersion branch is the seam future migrations plug into).
 */
class TargetsDocumentIOTest {
    private fun record(): TargetsRecord =
        TargetsRecord(
            version = 1,
            parentVersion = null,
            createdAtEpochMs = 1_788_482_400_000,
            createdBy = TargetsWriterId.STUDIO_F01,
            document =
                TargetsDocument(
                    goal = Goal(targetWeightKg = 78.0, pacePctPerWeek = 0.5),
                    energy =
                        Energy(
                            cadence = Cadence.WEEKLY,
                            weeklyBudgetKcal = 13_800.0,
                            schedule = List(5) { 1_800.0 } + List(2) { 2_400.0 },
                            floorKcal = 1_500.0,
                        ),
                    macros =
                        Macros(
                            split = MacroSplit.Custom(proteinPct = 30.0, carbPct = 40.0, fatPct = 30.0),
                            carbCapG = null,
                            proteinFloorG = 120.0,
                        ),
                ),
        )

    @Test
    fun roundTrip_preservesEverything() {
        val text = TargetsDocumentIO.encode(record())
        val decoded = TargetsDocumentIO.decode(text)
        assertEquals(record(), decoded)
    }

    @Test
    fun envelopeCarriesSchemaVersionAndPinnedWriter() {
        val json = Json.parseToJsonElement(TargetsDocumentIO.encode(record())).jsonObject
        assertEquals(2, json["schemaVersion"]!!.toString().toInt())
        val recordJson = json["payload"]!!.jsonObject
        assertEquals("\"studio@F01\"", recordJson["createdBy"]!!.toString())
    }

    @Test
    fun decode_refusesDocumentsViolatingInvariants() {
        val invalid =
            record().copy(
                document = record().document.copy(goal = record().document.goal.copy(pacePctPerWeek = 3.0)),
            )
        val text = DocumentCodec.json.encodeToString(TargetsRecord.serializer(), invalid)
        // Raw decode through the funnel must refuse (no invariant-breaking row).
        assertFailsWith<IllegalArgumentException> { TargetsDocumentIO.decode(text) }
    }

    @Test
    fun oldDocumentsNeverAcquireManualProvenance() {
        val current = TargetsDocumentIO.encode(record())
        val legacy = current.replace("\"schemaVersion\":2", "\"schemaVersion\":1")
        assertEquals(record(), TargetsDocumentIO.decode(legacy))
        val manual =
            record().copy(
                document =
                    record().document.copy(
                        energy = Energy(budgetKcal = 500.0, floorKcal = 1500.0, manuallyEntered = true),
                    ),
            )
        val encoded = TargetsDocumentIO.encode(manual)
        assertEquals(manual, TargetsDocumentIO.decode(encoded))
        assertFailsWith<IllegalArgumentException> {
            TargetsDocumentIO.decode(encoded.replace("\"schemaVersion\":2", "\"schemaVersion\":1"))
        }
    }

    @Test
    fun unknownSchemaVersionIsAMigrationFunnelError() {
        val json = """{"schemaVersion":99,"payload":{}}"""
        val error = assertFailsWith<IllegalStateException> { TargetsDocumentIO.decode(json) }
        assertTrue(error.message!!.contains("schemaVersion"))
    }
}
