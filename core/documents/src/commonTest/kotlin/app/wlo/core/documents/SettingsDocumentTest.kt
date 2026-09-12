package app.wlo.core.documents

import app.wlo.core.testing.GoldenFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR-004 rule 4: round-trip + old-version fixtures decode in CI. Every
 * document lands with the same pair of tests; this is the worked example.
 */
class SettingsDocumentTest {
    @Test
    fun roundTripsThroughEnvelopeAtCurrentVersion() {
        val document =
            SettingsDocument(
                unitSystem = "imperial",
                darkTheme = false,
                weekStartDay = 7,
            )
        val encoded = SettingsDocumentIO.encode(document)
        val decoded = SettingsDocumentIO.decode(encoded)

        assertEquals(document, decoded)
        assertTrue(
            "\"schemaVersion\":2" in encoded.replace(" ", ""),
            "encoding must stamp the current schema version, got: $encoded",
        )
    }

    @Test
    fun decodesV1FixtureThroughMigrationFunnel() {
        val v1Json = GoldenFixtures.load("documents/settings_v1.json").replace("\n", "")
        val document = SettingsDocumentIO.decode(v1Json)

        // Preserved fields survive; the v2-only field gets its house default.
        assertEquals("metric", document.unitSystem)
        assertEquals(true, document.darkTheme)
        assertEquals(SettingsDocument.WEEK_START_MONDAY, document.weekStartDay)
    }

    @Test
    fun defaultsHoldForMinimalPayload() {
        val decoded =
            SettingsDocumentIO.decode(
                """{"schemaVersion":2,"payload":{"unitSystem":"metric"}}""",
            )
        assertEquals(SettingsDocument(darkTheme = true, weekStartDay = 1, unitSystem = "metric"), decoded)
    }

    @Test
    fun unknownKeysAreIgnoredOnDecode() {
        val decoded =
            SettingsDocumentIO.decode(
                """{"schemaVersion":2,"payload":{"unitSystem":"metric","futureField":42}}""",
            )
        assertEquals("metric", decoded.unitSystem)
    }
}
