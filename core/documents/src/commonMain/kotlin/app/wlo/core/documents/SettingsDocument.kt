package app.wlo.core.documents

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.serializer

/**
 * Example versioned document: user settings. Schema history:
 *
 * - **v1** — `unitSystem` ("metric"), `darkTheme`.
 * - **v2** — adds `weekStartDay` (default 1 = Monday). v1 payloads normalize
 *   via [SettingsDocumentV1ToV2] before decode (ADR-004 rule 2: new fields get
 *   defaults, old versions normalize through transforming serializers).
 *
 * The same pattern scales to Targets/DietPlan/export bundles; each document
 * keeps its fixtures in CI round-trip + old-version tests.
 */
@Serializable
public data class SettingsDocument(
    public val unitSystem: String = UNIT_METRIC,
    public val darkTheme: Boolean = true,
    public val weekStartDay: Int = WEEK_START_MONDAY,
) {
    public companion object {
        public const val SCHEMA_VERSION: Int = 2
        public const val UNIT_METRIC: String = "metric"
        public const val WEEK_START_MONDAY: Int = 1
    }
}

/**
 * Normalizes schema-v1 payloads into the v2 shape by injecting the default for
 * the field v1 predates. Decode-only; encoding always emits the current shape.
 */
public object SettingsDocumentV1ToV2 :
    JsonTransformingSerializer<SettingsDocument>(SettingsDocument.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element !is JsonObject || "weekStartDay" in element) return element
        return JsonObject(element + ("weekStartDay" to JsonPrimitive(SettingsDocument.WEEK_START_MONDAY)))
    }
}

/**
 * Single funnel for SettingsDocument storage: encode always writes the current
 * schema version; decode routes older versions through their migrations.
 */
public object SettingsDocumentIO {
    public fun encode(document: SettingsDocument): String =
        DocumentCodec.json.encodeToString(
            serializer = serializer<DocumentEnvelope<SettingsDocument>>(),
            value = DocumentEnvelope(SettingsDocument.SCHEMA_VERSION, document),
        )

    public fun decode(text: String): SettingsDocument {
        val envelope =
            DocumentCodec.json.decodeFromString(
                deserializer = serializer<DocumentEnvelope<kotlinx.serialization.json.JsonObject>>(),
                string = text,
            )
        val payloadSerializer =
            when (val version = envelope.schemaVersion) {
                1 -> SettingsDocumentV1ToV2
                SettingsDocument.SCHEMA_VERSION -> SettingsDocument.serializer()
                else -> error("unknown settings schemaVersion: $version (migration funnel owns this, not callers)")
            }
        return DocumentCodec.json.decodeFromJsonElement(payloadSerializer, envelope.payload)
    }
}
