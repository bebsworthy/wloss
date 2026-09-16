package app.wlo.core.vault

import kotlinx.serialization.Serializable

/**
 * The versioned BACKUP DOCUMENT (F13 §3, ADR-004 house rules): this file is a
 * document like Targets/DietPlan — an explicit `schemaVersion`, additive
 * evolution only, and a single migration funnel ([BackupMigrations]).
 *
 * Row DTOs intentionally mirror the Room entity fields but live HERE: the
 * backup document version is decoupled from the Room schema version (Room
 * schema v6 ↔ backup schema v1 — the mapping is recorded in FORMAT.md), so a
 * restore of an old backup into a newer schema keeps working through the
 * document funnel without touching Room migrations.
 *
 * Excluded from the backup BY RULING:
 *  - `network_receipts` — device-local: receipts audit THIS install's egress;
 *    they are not user data and would only forge provenance on a new device.
 *  - `day_records` — a derived cache (A.3); the commit phase recomputes the
 *    projection for every restored day instead of trusting stale scalars.
 *  - `food_search` — the FTS5 mirror; rebuilt at commit from restored rows.
 *  - photo attachment BLOBS — excluded by default per R-U18 (explicit opt-in
 *    via [BackupOptions.includeVault]); the vault section below only ever
 *    appears when the user opts that bundle in.
 */
public object BackupSchema {
    /** The current backup document version (see [BackupMigrations] for history). */
    public const val SCHEMA_VERSION: Int = 1

    /** Section names — the manifest keys and the on-disk JSON keys. */
    public const val SECTION_PROFILES: String = "profiles"
    public const val SECTION_MEASUREMENTS: String = "measurements"
    public const val SECTION_DIARY: String = "diary"
    public const val SECTION_TARGETS: String = "targets"
    public const val SECTION_PROVENANCE: String = "provenance"
    public const val SECTION_CONSENT_LEDGER: String = "consent_ledger"
    public const val SECTION_FOOD_ITEMS: String = "food_items"
    public const val SECTION_RECIPES: String = "recipes"
    public const val SECTION_GROCERY: String = "grocery_items"
    public const val SECTION_PLANS: String = "plans"
    public const val SECTION_PLAN_SLOTS: String = "plan_slots"
    public const val SECTION_LIST: String = "list_items"
    public const val SECTION_PANTRY: String = "pantry_items"
    public const val SECTION_AISLE_CORRECTIONS: String = "aisle_corrections"
    public const val SECTION_HEALTH_CONNECT: String = "health_connect"
    public const val SECTION_SETTINGS: String = "settings"
    public const val SECTION_DOCUMENTS: String = "documents"
    public const val SECTION_VAULT: String = "vault_blobs"

    /** Canonical section order (FK-safe commit order + deterministic hashing). */
    public val SECTION_ORDER: List<String> =
        listOf(
            SECTION_PROFILES,
            SECTION_MEASUREMENTS,
            SECTION_DIARY,
            SECTION_TARGETS,
            SECTION_PROVENANCE,
            SECTION_CONSENT_LEDGER,
            SECTION_FOOD_ITEMS,
            SECTION_RECIPES,
            SECTION_GROCERY,
            SECTION_PLANS,
            SECTION_PLAN_SLOTS,
            SECTION_LIST,
            SECTION_PANTRY,
            SECTION_AISLE_CORRECTIONS,
            SECTION_HEALTH_CONNECT,
            SECTION_SETTINGS,
            SECTION_DOCUMENTS,
            SECTION_VAULT,
        )
}

/** One profile row (R-B9: every domain row carries profileId). */
@Serializable
public data class ProfileRow(
    val id: String,
    val sex: String? = null,
    val birthYear: Int?,
    val heightCm: Double?,
    val startWeightKg: Double?,
    val activityLevel: String,
    val unitPreference: String,
    val createdAtEpochMs: Long,
    val archivedAtEpochMs: Long? = null,
)

/** One measurement event with its EAV sidecar attrs (R-B8, F13 §3 EAV pattern). */
@Serializable
public data class MeasurementRow(
    val id: String,
    val profileId: String,
    val dayEpochDay: Long,
    val kind: String,
    val valueReal: Double,
    val unit: String,
    val source: String,
    val capturedAtEpochMs: Long,
    val note: String? = null,
    /** Fresh Start ledger (R-B7): hidden events stay in the store — never dropped. */
    val hiddenAtEpochMs: Long? = null,
    val hiddenReason: String? = null,
    val attrs: List<MeasurementAttrRow> = emptyList(),
)

@Serializable
public data class MeasurementAttrRow(
    val attr: String,
    val valueText: String? = null,
    val valueReal: Double? = null,
)

/** One diary entry with its full revision chain (R-B8: history is never rewritten). */
@Serializable
public data class DiaryEntryRow(
    val id: String,
    val profileId: String,
    val dayEpochDay: Long,
    val mealSlot: String,
    val foodItemId: String? = null,
    val textHint: String? = null,
    val quantity: Double,
    val unit: String,
    val computedKcal: Double,
    val computedProteinG: Double? = null,
    val computedCarbG: Double? = null,
    val computedFatG: Double? = null,
    val computedFiberG: Double? = null,
    val enteredVia: String,
    val provenanceScalar: String,
    val revision: Int,
    val createdAtEpochMs: Long,
    val editedAtEpochMs: Long? = null,
    val archivedAtEpochMs: Long? = null,
    val hiddenAtEpochMs: Long? = null,
    val hiddenReason: String? = null,
    val revisions: List<DiaryRevisionRow> = emptyList(),
)

@Serializable
public data class DiaryRevisionRow(
    val id: String,
    val entryId: String,
    val revision: Int,
    val mealSlot: String,
    val foodItemId: String? = null,
    val textHint: String? = null,
    val quantity: Double,
    val unit: String,
    val computedKcal: Double,
    val computedProteinG: Double? = null,
    val computedCarbG: Double? = null,
    val computedFatG: Double? = null,
    val computedFiberG: Double? = null,
    val enteredVia: String,
    val editedAtEpochMs: Long,
)

/** One immutable Targets version (full DocumentEnvelope JSON, R-B2 two-writer rule). */
@Serializable
public data class TargetsVersionRow(
    val id: String,
    val profileId: String,
    val version: Int,
    val documentJson: String,
    val writtenBy: String,
    val createdAtEpochMs: Long,
    val supersededAtEpochMs: Long? = null,
)

/** One derived-number provenance row (F13 §3: the "how we got here" evidence). */
@Serializable
public data class ProvenanceRow(
    val profileId: String,
    val dayEpochDay: Long,
    val scalar: String,
    val method: String,
    val formulaVersion: String,
    val inputsHash: String,
    val computedAtEpochMs: Long,
)

/**
 * One consent-ledger entry (F12 §3.6). The chain RIDES THE BACKUP so the
 * post-restore audit can prove no entry was edited in flight — restored rows
 * append only when the chain links to the local head (see RestorePipeline).
 */
@Serializable
public data class ConsentLedgerRow(
    val seq: Long,
    val profileId: String,
    val atEpochMs: Long,
    val capability: String,
    val decision: String,
    val prevHashHex: String,
    val hashHex: String,
)

@Serializable
public data class FoodItemRow(
    val id: String,
    val profileId: String,
    val name: String,
    val brand: String? = null,
    val aliases: String? = null,
    val kcalPer100g: Double? = null,
    val proteinGPer100g: Double? = null,
    val carbGPer100g: Double? = null,
    val fatGPer100g: Double? = null,
    val fiberGPer100g: Double? = null,
    val servingPresetsJson: String? = null,
    val source: String = "custom",
    val macrosVerified: Boolean = false,
    val verifiedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long? = null,
    val archivedAtEpochMs: Long? = null,
)

@Serializable
public data class RecipeRow(
    val recipeId: String,
    val version: Int,
    val profileId: String,
    val name: String,
    val servingsBase: Double,
    val cuisine: String? = null,
    val slotsJson: String = "[]",
    val tagsJson: String = "[]",
    val fodmapTagsJson: String = "[]",
    val ingredientsJson: String = "[]",
    val stepsJson: String = "[]",
    val kcalPerServing: Double,
    val proteinGPerServing: Double,
    val carbGPerServing: Double,
    val fatGPerServing: Double,
    val fiberGPerServing: Double,
    val nutritionBasis: String = "estimated",
    val source: String = "manual",
    val license: String? = null,
    val rating: Int? = null,
    val lastPlannedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long? = null,
    val archivedAtEpochMs: Long? = null,
)

@Serializable
public data class GroceryItemRow(
    val id: String,
    val profileId: String,
    val name: String,
    val aisle: String,
    val defaultUnit: String,
    val densityGPerMl: Double? = null,
    val gramsPerPiece: Double? = null,
    val aliasesJson: String? = null,
    val createdAtEpochMs: Long,
    val archivedAtEpochMs: Long? = null,
)

@Serializable
public data class PlanVersionRow(
    val id: String,
    val profileId: String,
    val version: Int,
    val startDayEpochDay: Long,
    val endDayEpochDay: Long,
    val seed: Long,
    val settingsJson: String,
    val reportJson: String,
    val createdAtEpochMs: Long,
    val supersededAtEpochMs: Long? = null,
)

@Serializable
public data class PlanSlotRow(
    val id: String,
    val planId: String,
    val profileId: String,
    val dayEpochDay: Long,
    val mealSlot: String,
    val recipeId: String? = null,
    val recipeVersion: Int? = null,
    val recipeName: String? = null,
    val servings: Double,
    val state: String = "planned",
    val replacedByEntryId: String? = null,
    val successorSlotId: String? = null,
    val replacesSlotId: String? = null,
    val parentSlotId: String? = null,
    val isCookEvent: Boolean = false,
    val batchServings: Double? = null,
    val kcalPerServing: Double? = null,
    val proteinGPerServing: Double? = null,
    val carbGPerServing: Double? = null,
    val fatGPerServing: Double? = null,
    val fiberGPerServing: Double? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long? = null,
)

@Serializable
public data class ListItemRow(
    val id: String,
    val profileId: String,
    val listId: String = "shopping",
    val groceryItemId: String,
    val name: String,
    val qty: Double,
    val unit: String,
    val aisle: String,
    val state: String = "pending",
    val checkedAtEpochMs: Long? = null,
    val deltaQty: Double? = null,
    val sourcesJson: String = "[]",
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long? = null,
    val archivedAtEpochMs: Long? = null,
)

@Serializable
public data class PantryItemRow(
    val id: String,
    val profileId: String,
    val groceryItemId: String,
    val name: String,
    val qty: Double,
    val unit: String,
    val expiryEpochDay: Long? = null,
    val addedAtEpochMs: Long,
    val lastPurchasedAtEpochMs: Long? = null,
    val purchaseCount: Int = 0,
    val isStaple: Boolean = false,
    val outOfStock: Boolean = false,
    val updatedAtEpochMs: Long? = null,
    val archivedAtEpochMs: Long? = null,
)

@Serializable
public data class AisleCorrectionRow(
    val profileId: String,
    val groceryItemId: String,
    val aisle: String,
    val updatedAtEpochMs: Long,
)

/**
 * One vault blob when the user opted this bundle into attachments (R-U18:
 * EXCLUDED BY DEFAULT). Blobs ride re-encrypted (the whole payload sits inside
 * the passphrase envelope); restore re-wraps them into the partition store.
 */
@Serializable
public data class VaultBlobRow(
    val partition: String,
    val blobId: String,
    val logicalName: String,
    val contentType: String,
    val sizeBytes: Long,
    val createdAtEpochMs: Long,
    val dataBase64: String,
)

/** App-level settings (known keys only; sensitive prefixes never enter — see SettingsStore). */
@Serializable
public data class SettingsSection(
    val values: Map<String, String> = emptyMap(),
)

/** Raw JSON documents (DataStore documents: drafts, correction cache). */
@Serializable
public data class DocumentsSection(
    val values: Map<String, String> = emptyMap(),
)

/** Health Connect provenance, replay cursor, and user-visible sync receipts. */
@Serializable
public data class HealthConnectSection(
    val records: List<HealthConnectRecordRow> = emptyList(),
    val syncStates: List<HealthConnectSyncStateRow> = emptyList(),
    val logs: List<HealthConnectLogRow> = emptyList(),
)

@Serializable
public data class HealthConnectRecordRow(
    val recordId: String,
    val profileId: String,
    val measurementEventId: String,
    val dataOriginPackage: String,
    val clientRecordId: String? = null,
    val clientRecordVersion: Long? = null,
    val recordingMethod: Int,
    val lastModifiedAtEpochMs: Long,
    val capturedAtEpochMs: Long,
    val zoneOffsetSeconds: Int? = null,
    val metric: String,
    val canonicalValue: Double,
)

@Serializable
public data class HealthConnectSyncStateRow(
    val profileId: String,
    val metric: String,
    val changeToken: String,
    val lastSyncAtEpochMs: Long,
)

@Serializable
public data class HealthConnectLogRow(
    val id: String,
    val profileId: String,
    val atEpochMs: Long,
    val outcome: String,
    val inserted: Int,
    val updated: Int,
    val deleted: Int,
    val skipped: Int,
    val conflicts: Int,
    val retryable: Boolean,
    val detail: String? = null,
)

/**
 * The logical payload: every restored/restored-able section, typed. Section
 * names + shapes are pinned by [BackupSchema]; evolution is additive (ADR-004).
 */
@Serializable
public data class BackupPayload(
    val profiles: List<ProfileRow> = emptyList(),
    val measurements: List<MeasurementRow> = emptyList(),
    val diary: List<DiaryEntryRow> = emptyList(),
    val targets: List<TargetsVersionRow> = emptyList(),
    val provenance: List<ProvenanceRow> = emptyList(),
    val consentLedger: List<ConsentLedgerRow> = emptyList(),
    val foodItems: List<FoodItemRow> = emptyList(),
    val recipes: List<RecipeRow> = emptyList(),
    val groceryItems: List<GroceryItemRow> = emptyList(),
    val plans: List<PlanVersionRow> = emptyList(),
    val planSlots: List<PlanSlotRow> = emptyList(),
    val listItems: List<ListItemRow> = emptyList(),
    val pantryItems: List<PantryItemRow> = emptyList(),
    val aisleCorrections: List<AisleCorrectionRow> = emptyList(),
    val healthConnect: HealthConnectSection = HealthConnectSection(),
    val settings: SettingsSection = SettingsSection(),
    val documents: DocumentsSection = DocumentsSection(),
    /** Present only when the bundle opted into attachments (R-U18). */
    val vaultBlobs: List<VaultBlobRow> = emptyList(),
)

/** Backup options at assembly time. */
public data class BackupOptions(
    /**
     * R-U18: photo attachments are EXCLUDED by default; `true` includes the
     * vault blobs (explicit per-bundle opt-in, the only path they ever ride).
     */
    val includeVault: Boolean = false,
)
