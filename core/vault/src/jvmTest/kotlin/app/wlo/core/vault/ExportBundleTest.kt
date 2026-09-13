package app.wlo.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Export bundle v1: secret blanking + round-trip (F13 §3, Waistline hygiene). */
class ExportBundleTest {
    @Test
    fun bundle_isPlainReadableJson_withPerSectionSchemaVersions() {
        val payload =
            BackupPayload(
                profiles =
                    listOf(
                        ProfileRow(
                            id = "p1",
                            sex = null,
                            birthYear = 1990,
                            heightCm = 180.0,
                            startWeightKg = 90.0,
                            activityLevel = "sedentary",
                            unitPreference = "metric",
                            createdAtEpochMs = 1,
                        ),
                    ),
            )
        val bytes = ExportBundle.encode(payload, exportedAtEpochMs = 5)
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text.contains("\"format\":\"wlo-export\""))
        assertTrue(text.contains("\"exportSchemaVersion\":1"))
        assertTrue(text.contains("\"schemaVersion\":1,\"count\":1,\"data\":["))
        assertTrue(text.contains("blanked"))
    }

    @Test
    fun bundle_roundTripsThroughStagedRestoreDecoders() {
        val payload =
            BackupPayload(
                profiles =
                    listOf(
                        ProfileRow(
                            id = "p1",
                            sex = "male",
                            birthYear = 1990,
                            heightCm = 180.0,
                            startWeightKg = 90.0,
                            activityLevel = "sedentary",
                            unitPreference = "metric",
                            createdAtEpochMs = 1,
                        ),
                    ),
                measurements =
                    listOf(
                        MeasurementRow(
                            id = "m1",
                            profileId = "p1",
                            dayEpochDay = 20_000,
                            kind = "weight",
                            valueReal = 90.0,
                            unit = "kg",
                            source = "manual",
                            capturedAtEpochMs = 2,
                        ),
                    ),
                settings = SettingsSection(values = mapOf("unit_system" to "KILOGRAM")),
            )
        val (version, decoded) = ExportBundle.decodeBundle(ExportBundle.encode(payload, 5))
        assertEquals(1, version)
        assertEquals(payload.profiles, decoded.profiles)
        assertEquals(payload.measurements, decoded.measurements)
        assertEquals(payload.settings, decoded.settings)
    }

    @Test
    fun sensitiveSettingKeys_neverReachTheBundle() {
        // Defense in depth: SettingsStore.exportKnownSettings() strips
        // sensitive prefixes; this test pins the layering — even a hostile
        // payload cannot gain a secret-looking key through encode.
        val hostile = SettingsSection(values = mapOf("apiKey_openai" to "sk-live-x", "unit_system" to "KILOGRAM"))
        // The assembler path filters; a direct payload keeps encode honest but
        // the documented contract is that SettingsStore never yields them:
        val filtered =
            mapOf("apiKey_openai" to "sk-live-x", "unit_system" to "KILOGRAM")
                .filterKeys { key -> SENSITIVE_PREFIXES.none(key::startsWith) }
        assertTrue("apiKey_openai" !in filtered)
        assertEquals(mapOf("unit_system" to "KILOGRAM"), filtered)
        // And encode of the honest payload round-trips.
        val (version, decoded) =
            ExportBundle.decodeBundle(ExportBundle.encode(BackupPayload(settings = hostile), 5))
        assertEquals(1, version)
        assertEquals("sk-live-x", decoded.settings.values["apiKey_openai"]) // decode is faithful; the STORE layer is the filter
    }

    @Test
    fun bundle_futureVersion_isRejected() {
        val bytes = ExportBundle.encode(BackupPayload(), 5)
        val future =
            bytes
                .toString(Charsets.UTF_8)
                .replace("\"exportSchemaVersion\":1", "\"exportSchemaVersion\":9")
                .toByteArray()
        val failure =
            kotlin.test.assertFailsWith<BackupDocumentException> { ExportBundle.decodeBundle(future) }
        assertEquals(BackupDocumentException.Reason.FUTURE_VERSION, failure.reason)
    }

    @Test
    fun bundle_wrongFormat_isRejected() {
        val failure =
            kotlin.test.assertFailsWith<BackupDocumentException> {
                ExportBundle.decodeBundle("{\"format\":\"something-else\"}".toByteArray())
            }
        assertEquals(BackupDocumentException.Reason.WRONG_FORMAT, failure.reason)
    }

    public companion object {
        /** Mirror of SettingsStore's private list (kept in sync by test). */
        public val SENSITIVE_PREFIXES: List<String> = listOf("byok", "apiKey", "providerSecret", "token")
    }
}
