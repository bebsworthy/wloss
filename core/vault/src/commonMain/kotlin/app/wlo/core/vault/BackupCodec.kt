package app.wlo.core.vault

import app.wlo.core.documents.DocumentCodec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/**
 * Encodes/decodes the versioned backup DOCUMENT that rides inside the
 * `.wlo` container (ADR-004 house rules apply verbatim: explicit
 * `schemaVersion`, additive fields, migration funnel, fixtures in CI).
 *
 * Wire shape (inside the encryption — see FORMAT.md for the full layout):
 *
 * ```json
 * {
 *   "format": "wlo-backup",
 *   "createdAtEpochMs": 0,
 *   "schemaVersion": 1,
 *   "manifest": {
 *     "sections": {
 *       "profiles": { "sha256": "…", "rows": 1, "schemaVersion": 1 }, …
 *     },
 *     "totalSha256": "…"
 *   },
 *   "sections": { "profiles": [ …rows… ], … }
 * }
 * ```
 *
 * Integrity: each section's canonical JSON re-serialization is hashed
 * (kotlinx `JsonElement` encoding is deterministic, so parse→re-encode is
 * byte-identical to what was written); `totalSha256` chains the sections in
 * [BackupSchema.SECTION_ORDER]. Staged restore verifies BOTH before decoding
 * any row — a manifest-mismatched file is rejected whole (F13 §4 flow 3:
 * "nothing was changed").
 */
public object BackupCodec {
    public const val FORMAT_NAME: String = "wlo-backup"

    internal val json = DocumentCodec.json

    private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())

    // --- encode --------------------------------------------------------------

    public fun encode(
        payload: BackupPayload,
        createdAtEpochMs: Long,
    ): ByteArray {
        val sections = sectionsOf(payload)
        val manifest = manifestFor(sections, BackupSchema.SCHEMA_VERSION)
        val document =
            buildJsonObject {
                put("format", JsonPrimitive(FORMAT_NAME))
                put("createdAtEpochMs", JsonPrimitive(createdAtEpochMs))
                put("schemaVersion", JsonPrimitive(BackupSchema.SCHEMA_VERSION))
                put("manifest", manifest)
                put("sections", JsonObject(sections))
            }
        return json.encodeToString(JsonElement.serializer(), document).toByteArray(Charsets.UTF_8)
    }

    /** Section name → canonical JSON element (deterministic section order). */
    public fun sectionsOf(payload: BackupPayload): LinkedHashMap<String, JsonElement> {
        val sections = LinkedHashMap<String, JsonElement>()

        fun put(
            name: String,
            element: JsonElement,
        ) {
            sections[name] = element
        }

        put(BackupSchema.SECTION_PROFILES, json.encodeToJsonElement(ListSerializer(ProfileRow.serializer()), payload.profiles))
        put(BackupSchema.SECTION_MEASUREMENTS, json.encodeToJsonElement(ListSerializer(MeasurementRow.serializer()), payload.measurements))
        put(BackupSchema.SECTION_DIARY, json.encodeToJsonElement(ListSerializer(DiaryEntryRow.serializer()), payload.diary))
        put(BackupSchema.SECTION_TARGETS, json.encodeToJsonElement(ListSerializer(TargetsVersionRow.serializer()), payload.targets))
        put(BackupSchema.SECTION_PROVENANCE, json.encodeToJsonElement(ListSerializer(ProvenanceRow.serializer()), payload.provenance))
        put(
            BackupSchema.SECTION_CONSENT_LEDGER,
            json.encodeToJsonElement(ListSerializer(ConsentLedgerRow.serializer()), payload.consentLedger),
        )
        put(BackupSchema.SECTION_FOOD_ITEMS, json.encodeToJsonElement(ListSerializer(FoodItemRow.serializer()), payload.foodItems))
        put(BackupSchema.SECTION_RECIPES, json.encodeToJsonElement(ListSerializer(RecipeRow.serializer()), payload.recipes))
        put(BackupSchema.SECTION_GROCERY, json.encodeToJsonElement(ListSerializer(GroceryItemRow.serializer()), payload.groceryItems))
        put(BackupSchema.SECTION_PLANS, json.encodeToJsonElement(ListSerializer(PlanVersionRow.serializer()), payload.plans))
        put(BackupSchema.SECTION_PLAN_SLOTS, json.encodeToJsonElement(ListSerializer(PlanSlotRow.serializer()), payload.planSlots))
        put(BackupSchema.SECTION_LIST, json.encodeToJsonElement(ListSerializer(ListItemRow.serializer()), payload.listItems))
        put(BackupSchema.SECTION_PANTRY, json.encodeToJsonElement(ListSerializer(PantryItemRow.serializer()), payload.pantryItems))
        put(
            BackupSchema.SECTION_AISLE_CORRECTIONS,
            json.encodeToJsonElement(ListSerializer(AisleCorrectionRow.serializer()), payload.aisleCorrections),
        )
        put(BackupSchema.SECTION_SETTINGS, json.encodeToJsonElement(SettingsSection.serializer(), payload.settings))
        put(BackupSchema.SECTION_DOCUMENTS, json.encodeToJsonElement(DocumentsSection.serializer(), payload.documents))
        put(BackupSchema.SECTION_VAULT, json.encodeToJsonElement(ListSerializer(VaultBlobRow.serializer()), payload.vaultBlobs))
        return sections
    }

    // --- manifest ------------------------------------------------------------

    public fun manifestFor(
        sections: Map<String, JsonElement>,
        sectionSchemaVersion: Int,
    ): JsonObject {
        val entries =
            sections.mapValues { (_, element) ->
                buildJsonObject {
                    put("sha256", JsonPrimitive(sha256(canonical(element))))
                    put("rows", JsonPrimitive(rowsOf(element)))
                    put("schemaVersion", JsonPrimitive(sectionSchemaVersion))
                }
            }
        return buildJsonObject {
            put("sections", JsonObject(entries))
            put("totalSha256", JsonPrimitive(totalSha256(sections)))
        }
    }

    /** sha256 over the concatenation of every section's canonical bytes, in order. */
    public fun totalSha256(sections: Map<String, JsonElement>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (name in BackupSchema.SECTION_ORDER) {
            sections[name]?.let { digest.update(canonical(it)) }
        }
        return digest.digest().toHex()
    }

    public fun canonical(element: JsonElement): ByteArray =
        json.encodeToString(JsonElement.serializer(), element).toByteArray(Charsets.UTF_8)

    private fun rowsOf(element: JsonElement): Int =
        when (element) {
            is kotlinx.serialization.json.JsonArray -> element.size
            is JsonObject -> element["values"]?.let { v -> (v as? kotlinx.serialization.json.JsonArray)?.size } ?: element.size
            else -> 0
        }

    // --- decode --------------------------------------------------------------

    /**
     * Parses, verifies the manifest, and migrates to the current schema
     * version. Structural failures throw [BackupDocumentException]; a wrong
     * `format` marker is hostile-file evidence.
     */
    public fun decodeVerified(documentBytes: ByteArray): VerifiedDocument {
        val text = documentBytes.toString(Charsets.UTF_8)
        val root =
            runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse { throw BackupDocumentException(BackupDocumentException.Reason.NOT_JSON, "not a JSON document") }
        if (root["format"]?.jsonPrimitive?.contentOrNull != FORMAT_NAME) {
            throw BackupDocumentException(BackupDocumentException.Reason.WRONG_FORMAT, "missing wlo-backup format marker")
        }
        val schemaVersion =
            root["schemaVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: throw BackupDocumentException(BackupDocumentException.Reason.BAD_HEADER, "missing schemaVersion")
        if (schemaVersion > BackupSchema.SCHEMA_VERSION) {
            throw BackupDocumentException(
                BackupDocumentException.Reason.FUTURE_VERSION,
                "backup schema v$schemaVersion is newer than this build (v${BackupSchema.SCHEMA_VERSION})",
            )
        }
        val manifestElement = root["manifest"] as? JsonObject
        val sectionsElement = root["sections"] as? JsonObject
        if (manifestElement == null || sectionsElement == null) {
            throw BackupDocumentException(BackupDocumentException.Reason.BAD_HEADER, "missing manifest or sections")
        }
        // Manifest verification runs against the ORIGINAL (pre-migration)
        // sections; the migration funnel only runs on an intact file.
        verifyManifest(manifestElement, sectionsElement)
        val migrated = BackupMigrations.migrate(sectionsElement, schemaVersion, BackupSchema.SCHEMA_VERSION)
        return VerifiedDocument(
            schemaVersionWritten = schemaVersion,
            schemaVersionRead = BackupSchema.SCHEMA_VERSION,
            manifest = manifestElement,
            sections = migrated,
        )
    }

    private fun verifyManifest(
        manifest: JsonObject,
        sections: JsonObject,
    ) {
        val total = manifest["totalSha256"]?.jsonPrimitive?.contentOrNull
        val expectedTotal =
            runCatching { totalSha256(sections) }.getOrElse {
                throw BackupDocumentException(BackupDocumentException.Reason.MANIFEST_MISMATCH, "sections not canonical")
            }
        if (total != expectedTotal) {
            throw BackupDocumentException(
                BackupDocumentException.Reason.MANIFEST_MISMATCH,
                "totalSha256 mismatch (expected $expectedTotal, manifest says $total)",
            )
        }
        val declared = manifest["sections"] as? JsonObject ?: return
        for ((name, entry) in declared) {
            val section =
                sections[name]
                    ?: throw BackupDocumentException(
                        BackupDocumentException.Reason.MANIFEST_MISMATCH,
                        "manifest names missing section $name",
                    )
            val want = (entry as? JsonObject)?.get("sha256")?.jsonPrimitive?.contentOrNull ?: continue
            val got = runCatching { sha256(canonical(section)) }.getOrDefault("")
            if (want != got) {
                throw BackupDocumentException(
                    BackupDocumentException.Reason.MANIFEST_MISMATCH,
                    "section $name sha256 mismatch",
                )
            }
        }
    }

    /** Typed row decoders (post-migration, current-schema sections). */
    public fun <T> decodeRows(
        section: JsonElement,
        serializer: KSerializer<T>,
    ): List<T> = json.decodeFromString(ListSerializer(serializer), section.toString())

    public fun decodeStringMap(section: JsonElement): Map<String, String> = json.decodeFromString(stringMapSerializer, section.toString())

    public fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

/** A verified, migrated document ready for typed row decoding + staging. */
public data class VerifiedDocument(
    public val schemaVersionWritten: Int,
    public val schemaVersionRead: Int,
    public val manifest: JsonObject,
    public val sections: JsonObject,
)

/** Typed document failures (container vs document are distinct hostile-file classes). */
public class BackupDocumentException(
    public val reason: Reason,
    message: String,
) : Exception(message) {
    public enum class Reason {
        NOT_JSON,
        WRONG_FORMAT,
        BAD_HEADER,
        FUTURE_VERSION,
        MANIFEST_MISMATCH,
        SCHEMA_INVALID,
        REFERENCE_BROKEN,
    }
}
