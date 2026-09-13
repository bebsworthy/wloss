package app.wlo.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Backup document round-trip + fixtures (ADR-004 rule 4: every shipped
 * schema version decodes in CI; the funnel routes old versions forward and
 * rejects future versions closed).
 */
class BackupDocumentIOTest {
    private fun samplePayload(): BackupPayload =
        BackupPayload(
            profiles =
                listOf(
                    ProfileRow(
                        id = "p1",
                        sex = "female",
                        birthYear = 1994,
                        heightCm = 165.0,
                        startWeightKg = 84.2,
                        activityLevel = "sedentary",
                        unitPreference = "metric",
                        createdAtEpochMs = 1_760_000_000_000,
                    ),
                ),
            measurements =
                listOf(
                    MeasurementRow(
                        id = "m1",
                        profileId = "p1",
                        dayEpochDay = 20_000,
                        kind = "weight",
                        valueReal = 84.2,
                        unit = "kg",
                        source = "manual",
                        capturedAtEpochMs = 1_760_000_000_000,
                        attrs = listOf(MeasurementAttrRow(attr = "method", valueText = "navy")),
                    ),
                ),
            diary =
                listOf(
                    DiaryEntryRow(
                        id = "d1",
                        profileId = "p1",
                        dayEpochDay = 20_000,
                        mealSlot = "breakfast",
                        quantity = 120.0,
                        unit = "g",
                        computedKcal = 240.0,
                        enteredVia = "manual-search",
                        provenanceScalar = "diary/kcal/d1",
                        revision = 0,
                        createdAtEpochMs = 1_760_000_000_000,
                        revisions =
                            listOf(
                                DiaryRevisionRow(
                                    id = "d1-r0",
                                    entryId = "d1",
                                    revision = 0,
                                    mealSlot = "breakfast",
                                    quantity = 100.0,
                                    unit = "g",
                                    computedKcal = 200.0,
                                    enteredVia = "manual-search",
                                    editedAtEpochMs = 1_760_000_001_000,
                                ),
                            ),
                    ),
                ),
            consentLedger =
                listOf(
                    ConsentLedgerRow(
                        seq = 0,
                        profileId = "p1",
                        atEpochMs = 1_760_000_000_000,
                        capability = "food-photo",
                        decision = "grant",
                        prevHashHex = ConsentEntryWire.GENESIS,
                        hashHex = "aa".repeat(32),
                    ),
                ),
            settings = SettingsSection(values = mapOf("unit_system" to "KILOGRAM")),
        )

    @Test
    fun roundTrip_preservesEverySection() {
        val payload = samplePayload()
        val bytes = BackupCodec.encode(payload, createdAtEpochMs = 42)
        val verified = BackupCodec.decodeVerified(bytes)

        assertEquals(BackupSchema.SCHEMA_VERSION, verified.schemaVersionWritten)
        val staged = stagedOf(verified)
        assertEquals(payload.profiles, staged.payload.profiles)
        assertEquals(payload.measurements, staged.payload.measurements)
        assertEquals(payload.diary, staged.payload.diary)
        assertEquals(payload.consentLedger, staged.payload.consentLedger)
        assertEquals(payload.settings, staged.payload.settings)
        assertTrue(staged.sections.all { it.ok })
        assertEquals(BackupSchema.SECTION_ORDER.size, staged.sections.size)
    }

    @Test
    fun manifestMismatch_isRejected() {
        val bytes = BackupCodec.encode(samplePayload(), 42)
        val tampered = bytes.toString(Charsets.UTF_8).replace("84.2", "99.9").toByteArray()
        val failure =
            assertFailsWith<BackupDocumentException> { BackupCodec.decodeVerified(tampered) }
        assertEquals(BackupDocumentException.Reason.MANIFEST_MISMATCH, failure.reason)
    }

    @Test
    fun futureSchemaVersion_failsClosed() {
        val bytes =
            BackupCodec
                .encode(samplePayload(), 42)
                .toString(Charsets.UTF_8)
                .replace("\"schemaVersion\":1", "\"schemaVersion\":999")
                .toByteArray()
        val failure =
            assertFailsWith<BackupDocumentException> { BackupCodec.decodeVerified(bytes) }
        assertEquals(BackupDocumentException.Reason.FUTURE_VERSION, failure.reason)
    }

    @Test
    fun unknownFields_areTolerated_forwardCompat() {
        // A v1 file written by a NEWER app (still schemaVersion 1) may carry
        // extra fields — ADR-004: ignoreUnknownKeys means it still loads.
        val bytes = BackupCodec.encode(samplePayload(), 42)
        val withUnknown =
            bytes
                .toString(Charsets.UTF_8)
                .replace(
                    "\"schemaVersion\":1,\"manifest\"",
                    "\"schemaVersion\":1,\"futureField\":{\"x\":1},\"manifest\"",
                ).toByteArray()
        val verified = BackupCodec.decodeVerified(withUnknown)
        assertEquals(
            "p1",
            stagedOf(verified)
                .payload.profiles
                .single()
                .id,
        )
    }

    @Test
    fun shippedV1Fixture_decodes() {
        // resources/documents/backup-v1.json — the CI-pinned v1 fixture.
        val fixture =
            javaClass.classLoader
                ?.getResourceAsStream("documents/backup-v1.json")
                ?.use { it.readBytes() }
                ?: error("missing fixture documents/backup-v1.json")
        val verified = BackupCodec.decodeVerified(fixture)
        assertEquals(1, verified.schemaVersionWritten)
        val payload = stagedOf(verified).payload
        assertEquals("fixture-profile", payload.profiles.single().id)
        assertEquals(2, payload.measurements.size, "fixture carries two weigh-ins per R-B8")
        assertEquals("KILOGRAM", payload.settings.values["unit_system"])
    }

    @Test
    fun shippedFutureVersionFixture_isRejectedClosed() {
        val fixture =
            javaClass.classLoader
                ?.getResourceAsStream("documents/backup-v999-hostile.json")
                ?.use { it.readBytes() }
                ?: error("missing fixture documents/backup-v999-hostile.json")
        val failure =
            assertFailsWith<BackupDocumentException> { BackupCodec.decodeVerified(fixture) }
        assertEquals(BackupDocumentException.Reason.FUTURE_VERSION, failure.reason)
    }

    @Test
    fun funnel_identityHopsUntilV2() {
        val sections = kotlinx.serialization.json.JsonObject(BackupCodec.sectionsOf(BackupPayload()))
        assertEquals(sections, BackupMigrations.migrate(sections, 1, 1))
        assertEquals(sections, BackupMigrations.migrate(sections, 1, BackupSchema.SCHEMA_VERSION))
    }

    private fun stagedOf(verified: VerifiedDocument): StagedRestore {
        val payload = decodePayloadSections(verified.sections)
        return StagedRestore(
            schemaVersionWritten = verified.schemaVersionWritten,
            schemaVersionRead = verified.schemaVersionRead,
            sections =
                BackupSchema.SECTION_ORDER.map { name ->
                    val element = verified.sections[name] ?: kotlinx.serialization.json.JsonArray(emptyList())
                    SectionReport(
                        name = name,
                        rows = (element as? kotlinx.serialization.json.JsonArray)?.size ?: 0,
                        schemaVersion = verified.schemaVersionRead,
                        sha256 = BackupCodec.sha256(BackupCodec.canonical(element)),
                        ok = true,
                    )
                },
            payload = payload,
            warnings = emptyList(),
        )
    }
}

/** Local wire constant (avoids touching :core:consent from this test file). */
private object ConsentEntryWire {
    const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"
}
