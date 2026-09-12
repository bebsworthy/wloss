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
        assertEquals(1, json["schemaVersion"]!!.toString().toInt())
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
    fun unknownSchemaVersionIsAMigrationFunnelError() {
        val json = """{"schemaVersion":99,"payload":{}}"""
        val error = assertFailsWith<IllegalStateException> { TargetsDocumentIO.decode(json) }
        assertTrue(error.message!!.contains("schemaVersion"))
    }
}
