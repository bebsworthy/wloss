package app.wlo.core.documents

import kotlinx.serialization.json.Json

/**
 * Serialization house rules for versioned documents (ADR-004, F13 §3):
 *
 * 1. Every document carries an explicit `schemaVersion` envelope.
 * 2. New fields always get defaults; unknown keys are ignored on decode;
 *    renames normalize via `@JsonNames` or transforming serializers.
 * 3. Polymorphic document ASTs use sealed hierarchies with PINNED
 *    `classDiscriminator` values — never class-name defaults. The pinned
 *    discriminator key is `kind` (see [json]); provenance entries in
 *    `:core:model` pin their discriminators with `@SerialName` ("measured",
 *    "estimated", "derived", "held").
 * 4. Round-trip + old-version fixtures decode in CI (see SettingsDocumentTest
 *    and the fixtures under src/commonTest/resources/documents/).
 *
 * This object is the ONE Json configuration for document storage/export —
 * ad-hoc Json instances elsewhere are a review finding.
 */
public object DocumentCodec {
    public const val CLASS_DISCRIMINATOR: String = "kind"

    public val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            classDiscriminator = CLASS_DISCRIMINATOR
        }
}
