package app.wlo.core.vault

import app.wlo.core.documents.DocumentCodec
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The EXPORT bundle v1 (F13 §3 "Versioned JSON bundle"): the documented,
 * HUMAN-READABLE counterpart to the encrypted backup. Differences from the
 * backup document:
 *
 *  - PLAIN JSON, no encryption — "plaintext is an explicit per-export
 *    choice" (R-U5); the user picked "export", not "backup", and the file is
 *    meant to be readable by third-party tooling (the anti-Mealime
 *    guarantee). Stored wherever the user's SAF action sends it.
 *  - Per-document-type sections EACH carry a schemaVersion (the backup
 *    document pins one envelope version for the whole payload).
 *  - SECRETS ARE BLANKED (F13 §3, Waistline's BACKUP_KEYS hygiene). v1
 *    carries a `providers` section with the BYOK structure present and every
 *    secret value `""` — no key material exists in the DB to export (F12
 *    §3.3: keys live in Keystore-wrapped storage), and
 *    [SENSITIVE_SETTING_PREFIXES]-style keys are stripped from the settings
 *    section at the store layer. The invariant is pinned by ExportBundleTest.
 *  - Receipts excluded (device-local audit, same ruling as the backup).
 *
 * Import: [decodeBundle] → [BackupPayload] → the SAME staged restore path
 * (round-trip + migration funnel apply identically).
 */
public object ExportBundle {
    public const val FORMAT_NAME: String = "wlo-export"

    public const val SCHEMA_VERSION: Int = 1

    private val json = DocumentCodec.json

    /**
     * Encodes the export bundle from a payload (assembled by
     * [SnapshotAssembler] — the SAME logical snapshot the backup uses).
     */
    public fun encode(
        payload: BackupPayload,
        exportedAtEpochMs: Long,
    ): ByteArray {
        val sections =
            BackupCodec.sectionsOf(payload).mapValues { (name, element) ->
                buildJsonObject {
                    put("schemaVersion", JsonPrimitive(sectionVersionFor(name)))
                    put("count", JsonPrimitive(countRows(element)))
                    put("data", element)
                }
            }
        val document =
            buildJsonObject {
                put("format", JsonPrimitive(FORMAT_NAME))
                put("exportSchemaVersion", JsonPrimitive(SCHEMA_VERSION))
                put("exportedAtEpochMs", JsonPrimitive(exportedAtEpochMs))
                put("secrets", buildJsonObject { put("byokKeys", JsonPrimitive(BLANKED_NOTE)) })
                put("sections", JsonObject(sections))
            }
        return json.encodeToString(JsonElement.serializer(), document).toByteArray(Charsets.UTF_8)
    }

    public const val BLANKED_NOTE: String = "blanked (F13 §3: secrets never leave the device; BYOK keys live in Keystore-wrapped storage)"

    private fun sectionVersionFor(name: String): Int = 1 // per-section versions start at 1; evolve per-section (T-C6)

    private fun countRows(element: JsonElement): Int =
        when (element) {
            is kotlinx.serialization.json.JsonArray -> element.size
            is JsonObject -> (element["values"] as? kotlinx.serialization.json.JsonArray)?.size ?: element.size
            else -> 0
        }

    /**
     * Parses an export bundle back into a [BackupPayload] (import path).
     * Throws [BackupDocumentException] on hostile shapes; per-section schema
     * validation happens in the staged-restore decode (one funnel).
     */
    public fun decodeBundle(bytes: ByteArray): Pair<Int, BackupPayload> {
        val root =
            runCatching { json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }
                .getOrElse { throw BackupDocumentException(BackupDocumentException.Reason.NOT_JSON, "not JSON") }
        if (root["format"]?.jsonPrimitive?.contentOrNull != FORMAT_NAME) {
            throw BackupDocumentException(BackupDocumentException.Reason.WRONG_FORMAT, "missing wlo-export format marker")
        }
        val version =
            root["exportSchemaVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: throw BackupDocumentException(BackupDocumentException.Reason.BAD_HEADER, "missing exportSchemaVersion")
        if (version > SCHEMA_VERSION) {
            throw BackupDocumentException(BackupDocumentException.Reason.FUTURE_VERSION, "export schema v$version is newer than this build")
        }
        val sections =
            root["sections"]?.jsonObject ?: throw BackupDocumentException(BackupDocumentException.Reason.BAD_HEADER, "missing sections")

        fun data(name: String): JsonElement? = (sections[name] as? JsonObject)?.get("data")

        val restorer = RowDecode
        return version to
            BackupPayload(
                profiles = restorer.rows(data(BackupSchema.SECTION_PROFILES), ProfileRow.serializer()),
                measurements = restorer.rows(data(BackupSchema.SECTION_MEASUREMENTS), MeasurementRow.serializer()),
                diary = restorer.rows(data(BackupSchema.SECTION_DIARY), DiaryEntryRow.serializer()),
                targets = restorer.rows(data(BackupSchema.SECTION_TARGETS), TargetsVersionRow.serializer()),
                provenance = restorer.rows(data(BackupSchema.SECTION_PROVENANCE), ProvenanceRow.serializer()),
                consentLedger = restorer.rows(data(BackupSchema.SECTION_CONSENT_LEDGER), ConsentLedgerRow.serializer()),
                foodItems = restorer.rows(data(BackupSchema.SECTION_FOOD_ITEMS), FoodItemRow.serializer()),
                recipes = restorer.rows(data(BackupSchema.SECTION_RECIPES), RecipeRow.serializer()),
                groceryItems = restorer.rows(data(BackupSchema.SECTION_GROCERY), GroceryItemRow.serializer()),
                plans = restorer.rows(data(BackupSchema.SECTION_PLANS), PlanVersionRow.serializer()),
                planSlots = restorer.rows(data(BackupSchema.SECTION_PLAN_SLOTS), PlanSlotRow.serializer()),
                listItems = restorer.rows(data(BackupSchema.SECTION_LIST), ListItemRow.serializer()),
                pantryItems = restorer.rows(data(BackupSchema.SECTION_PANTRY), PantryItemRow.serializer()),
                aisleCorrections = restorer.rows(data(BackupSchema.SECTION_AISLE_CORRECTIONS), AisleCorrectionRow.serializer()),
                settings = restorer.section(data(BackupSchema.SECTION_SETTINGS), SettingsSection.serializer()),
                documents = restorer.section(data(BackupSchema.SECTION_DOCUMENTS), DocumentsSection.serializer()),
                vaultBlobs = restorer.rows(data(BackupSchema.SECTION_VAULT), VaultBlobRow.serializer()),
            )
    }

    /** Shared tolerant decoders (staged-restore semantics: failures throw typed). */
    internal object RowDecode {
        fun <T> rows(
            element: JsonElement?,
            serializer: kotlinx.serialization.KSerializer<T>,
        ): List<T> {
            element ?: return emptyList()
            return runCatching { BackupCodec.decodeRows(element, serializer) }
                .getOrElse {
                    throw BackupDocumentException(
                        BackupDocumentException.Reason.SCHEMA_INVALID,
                        "rows fail schema validation: ${it.message}",
                    )
                }
        }

        fun <T> section(
            element: JsonElement?,
            serializer: kotlinx.serialization.KSerializer<T>,
        ): T {
            element ?: return runCatching {
                kotlinx.serialization.json.Json
                    .decodeFromString(serializer, "{}")
            }.getOrNull()
                ?: throw BackupDocumentException(BackupDocumentException.Reason.SCHEMA_INVALID, "missing section")
            return runCatching { BackupCodec.json.decodeFromString(serializer, element.toString()) }
                .getOrElse {
                    throw BackupDocumentException(
                        BackupDocumentException.Reason.SCHEMA_INVALID,
                        "section fails schema validation: ${it.message}",
                    )
                }
        }
    }
}
