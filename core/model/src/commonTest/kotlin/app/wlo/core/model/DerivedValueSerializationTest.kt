package app.wlo.core.model

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DerivedValueSerializationTest {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun derivedProvenanceRoundTripsWithPinnedDiscriminator() {
        val value = DerivedValue(24.2, Provenance.Derived("bmi/quetelet-v1", listOf("weightKg", "heightCm")))
        val encoded = json.encodeToString(DerivedValue.serializer(Double.serializer()), value)
        assertTrue("bmi/quetelet-v1" in encoded, "formula version must survive")
        assertEquals(value, json.decodeFromString(DerivedValue.serializer(Double.serializer()), encoded))
    }

    @Test
    fun provenanceDiscriminatorsArePinnedNotClassNames() {
        val measured =
            json.encodeToString(
                Provenance.serializer(),
                Provenance.Measured(kotlinx.datetime.Instant.fromEpochMilliseconds(1_700_000_000_000), "scale-a1"),
            )
        assertTrue("\"measured\"" in measured, "discriminator must be the pinned string, got: $measured")
    }

    @Test
    fun allConsentCapabilitiesUseFrozenWireNames() {
        val expected =
            setOf(
                "food-photo",
                "voice-input",
                "meal-planning",
                "silhouette",
                "poop-photo",
                "insights-chat",
            )
        assertEquals(expected, ConsentCapability.entries.map { it.wireName }.toSet())
    }
}
