package app.wlo.core.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The bundled zoo manifest (R-S14, ADR-004 house rules): a VERSIONED document
 * describing every model the zoo can install — first entry per ADR-007. The
 * manifest ships as an `:app` asset; the document shape lives here so any
 * consumer (UI size disclosure, PART B analyzers) parses with the same rules:
 * explicit `schemaVersion` envelope, defaults for new fields,
 * `ignoreUnknownKeys`, required fields strict (id/url/hash/size/license/
 * provenance have no defaults — a manifest without them does not parse).
 */
@Serializable
public data class ZooManifest(
    public val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    public val revision: String = "",
    public val models: List<ZooModel> = emptyList(),
) {
    /** The model with [id], or null (unknown ids never download). */
    public fun byId(id: String): ZooModel? = models.firstOrNull { it.id == id }

    public companion object {
        public const val CURRENT_SCHEMA_VERSION: Int = 1

        private val wire: Json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        /**
         * Strict-enough decode: type- and required-field strict, unknown keys
         * tolerated; a manifest written by a NEWER schema version is refused
         * (old versions forward-fill via defaults per ADR-004 rule 2).
         */
        public fun fromJson(text: String): ZooManifest {
            val document = wire.decodeFromString<ZooManifest>(text)
            check(document.schemaVersion <= CURRENT_SCHEMA_VERSION) {
                "zoo manifest schema ${document.schemaVersion} is newer than supported " +
                    "($CURRENT_SCHEMA_VERSION) — update the app first"
            }
            return document
        }
    }
}

/**
 * One zoo model card entry (R-S12/R-S14): everything the size-disclosure sheet
 * and the user-visible model card render — including named license + training
 * provenance — plus the hash-pinned URL and the exact byte size.
 */
@Serializable
public data class ZooModel(
    /** Stable id, `family/version`, e.g. `food-classifier/1`. */
    public val id: String,
    /** Wire name of the F12 consent category this model serves on-device. */
    public val purpose: String,
    /** Hash-pinned public URL (R-S14: no WLO backend, no mirror prerequisite). */
    public val url: String,
    /** Published sha256 of the artifact — verified before install. */
    public val sha256Hex: String,
    /** Exact artifact size; disclosed to the user BEFORE download. */
    public val sizeBytes: Long,
    public val license: String,
    /** Training-data provenance, user-visible (R-S12: datasets named). */
    public val provenance: String,
    public val format: String = "onnx",
)
