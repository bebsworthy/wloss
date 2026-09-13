package app.wlo.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Fts5
import androidx.room3.Index
import androidx.room3.PrimaryKey

// Schema v6 (M5, WLO-0027 PART A): the F03/F04 planning surface on the spine —
// recipes (versioned), the canonical grocery catalog, plan_versions + plan_slots
// (the R-B1 state machine), list_items (delta-reconciled), pantry_items and
// aisle_corrections (the learned aisle loop). on top of schema v5.
// Layout per F03 §3, F04 §3, rulings R-B1/R-B9/R-S3/R-S8.
//
// House rule (ADR-003): every schema change lands with a migration +
// exported-schema test in the same PR — see [Migrations] and `MigrationTest`.
// Entities are dumb data rows; domain meaning (enums, provenance payloads)
// is applied in `:core:data` repositories.

/** One user profile (R-B9: single-profile v1, partition-ready; archive-don't-delete). */
@Entity(tableName = "profiles")
public data class ProfileEntity(
    @PrimaryKey
    public val id: String,
    /** Wire name of [app.wlo.core.model.Sex]; null = undisclosed. */
    public val sex: String?,
    public val birthYear: Int,
    public val heightCm: Double,
    public val startWeightKg: Double,
    /** Wire name of [app.wlo.core.model.ActivityLevel]. */
    public val activityLevel: String,
    /** "metric" default, "imperial" a user setting (R-D10). */
    public val unitPreference: String,
    public val createdAtEpochMs: Long,
    public val archivedAtEpochMs: Long? = null,
)

/**
 * One timestamped measurement event (R-B8): multiple weigh-ins per day are
 * kept verbatim — this table is APPEND-ONLY by discipline; no DAO exposes an
 * UPDATE or DELETE for measurement rows.
 *
 * v4 adds the Fresh Start ledger columns (R-B7, F06 §4 "hide-not-delete
 * everything before a chosen date; reversible"): a hidden event stays in the
 * store, exportable and reversible — reads treat NULL as visible.
 */
@Entity(
    tableName = "measurement_events",
    indices = [Index(value = ["profileId", "dayEpochDay"])],
)
public data class MeasurementEventEntity(
    @PrimaryKey
    public val id: String,
    public val profileId: String,
    /** Calendar day (epoch day, user's zone) — see :core:common DayBoundary. */
    public val dayEpochDay: Long,
    /** Wire name of [app.wlo.core.model.MeasurementKind]: weight/trend/intake/burn/… */
    public val kind: String,
    public val valueReal: Double,
    public val unit: String,
    /** e.g. manual/scale/ocr/import/health-connect/engine. */
    public val source: String,
    public val capturedAtEpochMs: Long,
    public val note: String? = null,
    /** Fresh Start ledger (R-B7): NULL = visible. */
    public val hiddenAtEpochMs: Long? = null,
    /** Fresh Start ledger (R-B7): why it was hidden (marker label). */
    public val hiddenReason: String? = null,
)

/** EAV sidecar for custom metrics (F13 §3, openScale pattern). */
@Entity(
    tableName = "measurement_event_attrs",
    primaryKeys = ["eventId", "attr"],
    foreignKeys = [
        ForeignKey(
            entity = MeasurementEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
        ),
    ],
    indices = [Index("eventId")],
)
public data class MeasurementEventAttrEntity(
    public val eventId: String,
    public val attr: String,
    public val valueText: String? = null,
    public val valueReal: Double? = null,
)

/**
 * Cached day scalars (Appendix A.3): WRITTEN ONLY BY THE PROJECTION PIPELINE
 * in `:core:data` (`DayProjector`) — features never write this table; they
 * read the day projection through `DayProjectionRepository`, the only door.
 */
@Entity(
    tableName = "day_records",
    primaryKeys = ["profileId", "dayEpochDay"],
    indices = [Index("profileId")],
)
public data class DayRecordEntity(
    public val profileId: String,
    public val dayEpochDay: Long,
    public val trendWeightKg: Double? = null,
    public val intakeKcal: Double? = null,
    public val burnKcal: Double? = null,
    public val computedAtEpochMs: Long,
)

/**
 * Provenance for every derived scalar (F13 §3: one row per derived number;
 * subject key = profile + day + scalar name). Backs [DerivedValue] chips and
 * the "how we got here" sheet. Diary entries link in via
 * `provenance.scalar = "diary/kcal/<entryId>"`.
 */
@Entity(
    tableName = "provenance",
    primaryKeys = ["profileId", "dayEpochDay", "scalar"],
    indices = [Index("profileId")],
)
public data class ProvenanceEntity(
    public val profileId: String,
    public val dayEpochDay: Long,
    /** Scalar name, e.g. "intakeKcal", "trendWeightKg", "budgetKcal", "diary/kcal/<id>". */
    public val scalar: String,
    /** Method label, e.g. "sum-of-events", "trend-event", "food-item-scale". */
    public val method: String,
    /** Formula/version string from the constants registry. */
    public val formulaVersion: String,
    /** Stable fingerprint of the inputs (see :core:engines InputsHash). */
    public val inputsHash: String,
    public val computedAtEpochMs: Long,
)

/**
 * Append-only consent ledger (F12 §3.6; schema only in M2 — consumers arrive
 * with M6). Every toggle change is a new row; the hash chain matches
 * :core:consent's [ConsentEntry] shape. `profileId` per R-B9 (partition-ready).
 */
@Entity(tableName = "consent_ledger")
public data class ConsentLedgerEntity(
    @PrimaryKey(autoGenerate = true)
    public val seq: Long = 0,
    public val profileId: String,
    public val capability: String,
    /** "grant" | "revoke". */
    public val decision: String,
    public val atEpochMs: Long,
    public val prevHashHex: String,
    public val hashHex: String,
)

/**
 * One egress receipt (F12 §3.6/§3.8; F13 §9): every NetworkDispatcher dispatch
 * — success OR failure OR denial — appends exactly one row. APPEND-ONLY by
 * discipline: no DAO exposes UPDATE or DELETE, and the prevHash/hash columns
 * chain rows so tampering is detectable (chain engine: :core:consent HashChain).
 * `seq` starts at 1 (Room AUTOINCREMENT); the first row links to the all-zero
 * genesis hash. Purpose/outcome are wire strings (:core:network owns the enums).
 */
@Entity(
    tableName = "network_receipts",
    indices = [Index("purpose"), Index("atEpochMs")],
)
public data class NetworkReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    public val seq: Long = 0,
    /** Wire name of [app.wlo.core.ports.EgressPurpose]. */
    public val purpose: String,
    public val host: String,
    public val operation: String,
    /** Bytes that left the device (0 for denials and cache hits). */
    public val bytes: Long,
    /** Wire name of the outcome: ok | denied | failed | cache-hit. */
    public val outcome: String,
    public val atEpochMs: Long,
    public val prevHashHex: String,
    public val hashHex: String,
)

/**
 * Food catalog (F13 §3 archive-don't-delete): items are archived — set
 * [archivedAtEpochMs] — never orphaned, never deleted. The DAO deliberately
 * exposes NO delete method (enforced in code, not convention). `profileId`
 * per R-B9 (partition-ready).
 *
 * v4 realizes the M2 stub per F02 §3/§5: searchable aliases, per-100g macros
 * with a label-verified flag, serving presets (JSON), and a source wire
 * (seed/custom/OFF/USDA/label-OCR). Fresh Start does NOT apply here — the
 * catalog is not user history; retirement is [archivedAtEpochMs].
 */
@Entity(tableName = "food_items")
public data class FoodItemEntity(
    @PrimaryKey
    public val id: String,
    public val profileId: String,
    public val name: String,
    public val brand: String? = null,
    /** Free-text alternate names; mirrored into the FTS5 index (`food_search`). */
    public val aliases: String? = null,
    public val kcalPer100g: Double? = null,
    public val proteinGPer100g: Double? = null,
    public val carbGPer100g: Double? = null,
    public val fatGPer100g: Double? = null,
    public val fiberGPer100g: Double? = null,
    /** JSON array of {label, grams} ([app.wlo.core.model.ServingPreset]); codec in :core:data. */
    public val servingPresetsJson: String? = null,
    /** Wire name of [app.wlo.core.model.FoodSource]; DEFAULT 'custom' (v3 rows pre-date it). */
    @ColumnInfo(defaultValue = "custom")
    public val source: String = "custom",
    /** User/importer confirmed the macros against the physical label (F02 §5). */
    @ColumnInfo(defaultValue = "false")
    public val macrosVerified: Boolean = false,
    public val verifiedAtEpochMs: Long? = null,
    public val createdAtEpochMs: Long,
    public val updatedAtEpochMs: Long? = null,
    public val archivedAtEpochMs: Long? = null,
)

/**
 * FTS5 index over the searchable text of `food_items` (F02 §3 ladder rung 5;
 * ADR-003: `@Fts5` lands the food-search plan natively).
 *
 * Standalone CONTENTFUL pattern (not external-content): `food_items` has a
 * TEXT primary key, and SQLite external-content FTS5 requires an INTEGER
 * rowid alias to join on — so the index carries `foodId` as its own column
 * and is kept in sync by the `:core:data` write paths inside the same
 * transaction. Room does not manage sync triggers for this shape; there are
 * no raw-SQL writers of `food_items` outside [FoodRepository] (v1).
 */
@Fts5
@Entity(tableName = "food_search")
public data class FoodSearchEntity(
    public val name: String,
    public val brand: String? = null,
    public val aliases: String? = null,
    /** Join key back to [FoodItemEntity.id]. */
    public val foodId: String,
)

/**
 * One diary entry (R-B1: F02 owns the food diary; R-B8: event-level rows,
 * day-level rendering). Editing NEVER rewrites history: the prior state is
 * appended to `diary_entry_revisions` and [revision] bumps. "Delete" is an
 * archive; Fresh Start hides ([hiddenAtEpochMs]) — both reversible (R-B7).
 */
@Entity(
    tableName = "diary_entries",
    indices = [
        Index(value = ["profileId", "dayEpochDay"]),
        Index("foodItemId"),
    ],
)
public data class DiaryEntryEntity(
    @PrimaryKey
    public val id: String,
    public val profileId: String,
    public val dayEpochDay: Long,
    /** Wire name of [app.wlo.core.model.MealSlot]: breakfast/lunch/dinner/snack/drink. */
    public val mealSlot: String,
    public val foodItemId: String? = null,
    /** Free-text fallback path (R-U15). */
    public val textHint: String? = null,
    public val quantity: Double,
    /** "g" | "ml" | "serving" (v1 portion vocabulary). */
    public val unit: String,
    public val computedKcal: Double,
    public val computedProteinG: Double? = null,
    public val computedCarbG: Double? = null,
    public val computedFatG: Double? = null,
    public val computedFiberG: Double? = null,
    /** Wire name of [app.wlo.core.model.EntryVia]. */
    public val enteredVia: String,
    /** Provenance-table scalar key (`diary/kcal/<id>`). */
    public val provenanceScalar: String,
    /** Monotonic per-entry edit counter (revision chain pointer). */
    public val revision: Int,
    public val createdAtEpochMs: Long,
    public val editedAtEpochMs: Long? = null,
    /** Hide-not-delete "delete" (R-B7): NULL = visible in day views. */
    public val archivedAtEpochMs: Long? = null,
    /** Fresh Start ledger (R-B7): NULL = visible. */
    public val hiddenAtEpochMs: Long? = null,
    public val hiddenReason: String? = null,
)

/**
 * Append-only correction audit (R-B8 + F02 §3 "ground-truth pair"): the prior
 * version of an entry, written by the edit path before the entry row updates.
 * No DAO exposes UPDATE or DELETE — the chain is the honest history.
 */
@Entity(
    tableName = "diary_entry_revisions",
    indices = [Index("entryId")],
)
public data class DiaryEntryRevisionEntity(
    @PrimaryKey
    public val id: String,
    public val entryId: String,
    /** The revision number this row SNAPSHOT (the entry's revision before the edit). */
    public val revision: Int,
    public val mealSlot: String,
    public val foodItemId: String? = null,
    public val textHint: String? = null,
    public val quantity: Double,
    public val unit: String,
    public val computedKcal: Double,
    public val computedProteinG: Double? = null,
    public val computedCarbG: Double? = null,
    public val computedFatG: Double? = null,
    public val computedFiberG: Double? = null,
    public val enteredVia: String,
    /** When this version was superseded by the next edit. */
    public val editedAtEpochMs: Long,
)

/**
 * Versioned Targets rows (Appendix A.1): immutable versions, revert = new
 * version copying an old one. `writtenBy` enforces the two-writer rule
 * (R-B2): 'F01_STUDIO' | 'F07_APPLY' — the sealed write path in :core:data
 * is the only producer.
 */
@Entity(
    tableName = "targets_versions",
    indices = [Index(value = ["profileId", "version"], unique = true)],
)
public data class TargetsVersionEntity(
    @PrimaryKey
    public val id: String,
    public val profileId: String,
    public val version: Int,
    /** Full [DocumentEnvelope]-shaped JSON of the version (codec: :core:documents). */
    public val documentJson: String,
    public val writtenBy: String,
    public val createdAtEpochMs: Long,
    public val supersededAtEpochMs: Long? = null,
)

// --- v6: F03 meal planning + F04 shopping list & pantry (WLO-0027 PART A) ----

/**
 * One recipe VERSION (F03 §3; R-S3 content rules): versions are immutable
 * rows keyed (recipeId, version); an edit writes vN+1. Ingredients/steps/tags
 * ride as JSON columns (same pattern as `servingPresetsJson`) — codec in
 * :core:data against :core:model's canonical types. Per-serving macros are
 * stored size-normalized so plan rollups never scale by assumption.
 */
@Entity(
    tableName = "recipes",
    primaryKeys = ["recipeId", "version"],
    indices = [Index("recipeId"), Index(value = ["profileId", "archivedAtEpochMs"])],
)
public data class RecipeEntity(
    public val recipeId: String,
    public val version: Int,
    public val profileId: String,
    public val name: String,
    public val servingsBase: Double,
    public val cuisine: String? = null,
    /** JSON array of meal-slot wire names ("breakfast"…). */
    public val slotsJson: String = "[]",
    /** JSON array of diet/facet tags (open vocabulary, e.g. "vegetarian", "batch"). */
    public val tagsJson: String = "[]",
    /** JSON array of R-S8 FODMAP tags (subset of the shipped six). */
    public val fodmapTagsJson: String = "[]",
    /** JSON array of {groceryItemId, name, qty, unit, optional} canonical lines. */
    public val ingredientsJson: String = "[]",
    /** JSON array of step strings (plain text in v1, F03 §3). */
    public val stepsJson: String = "[]",
    public val kcalPerServing: Double,
    public val proteinGPerServing: Double,
    public val carbGPerServing: Double,
    public val fatGPerServing: Double,
    public val fiberGPerServing: Double,
    /** Wire name of [app.wlo.core.model.NutritionBasis]: label | db | estimated. */
    public val nutritionBasis: String = "estimated",
    /** Wire name of [app.wlo.core.model.RecipeSource]: seed | manual | import | ai-draft. */
    public val source: String = "manual",
    /** R-S3: "CC0" for shipped seeds; null for user rows. */
    public val license: String? = null,
    public val rating: Int? = null,
    public val lastPlannedAtEpochMs: Long? = null,
    public val createdAtEpochMs: Long,
    public val updatedAtEpochMs: Long? = null,
    /** Archive-don't-delete (F13 §3). */
    public val archivedAtEpochMs: Long? = null,
)

/**
 * The canonical grocery catalog (F04 §3 data model's `canonicalItem`): the
 * list/pantry/plan ingredient vocabulary, profile-scoped per R-B9 (the seed
 * loader copies the shipped catalog into each profile at first use). Rows are
 * archive-don't-delete like the food catalog.
 */
@Entity(
    tableName = "grocery_items",
    indices = [Index("profileId")],
)
public data class GroceryItemEntity(
    @PrimaryKey
    public val id: String,
    public val profileId: String,
    public val name: String,
    /** Shipped aisle tag ([app.wlo.core.model.Aisle.wireName]). */
    public val aisle: String,
    /** [app.wlo.core.model.MeasureUnit.wireName] — new list/pantry rows default to it. */
    public val defaultUnit: String,
    /** g per ml for pourables; a display/sweep hint, never a consolidation input. */
    public val densityGPerMl: Double? = null,
    /** g per piece for count items; nutrition/estimation hint, never a consolidation input. */
    public val gramsPerPiece: Double? = null,
    /** JSON array of alias strings. */
    public val aliasesJson: String? = null,
    public val createdAtEpochMs: Long,
    public val archivedAtEpochMs: Long? = null,
)

/**
 * One plan generation (F03): header row for an immutable slot set; one active
 * plan per profile ([supersededAtEpochMs] NULL). The generation settings and
 * the "why this plan" report ride along as JSON — the plan stays re-runnable
 * and explainable (F03 §8 visible reasoning).
 */
@Entity(
    tableName = "plan_versions",
    indices = [Index(value = ["profileId", "version"], unique = true)],
)
public data class PlanVersionEntity(
    @PrimaryKey
    public val id: String,
    public val profileId: String,
    public val version: Int,
    public val startDayEpochDay: Long,
    public val endDayEpochDay: Long,
    /** The variety seed the deal ran with (re-runnable). */
    public val seed: Long,
    /** JSON [app.wlo.core.engines.PlannerEngine.Settings]. */
    public val settingsJson: String,
    /** JSON [app.wlo.core.engines.PlannerEngine.Report] ("why this plan"). */
    public val reportJson: String,
    public val createdAtEpochMs: Long,
    public val supersededAtEpochMs: Long? = null,
)

/**
 * One planned meal slot (R-B1): F03's state machine rows projecting into the
 * day record. Slots are append-only records — a swap retires the old row
 * (`state = 'swapped'`, successor link) and inserts a fresh `planned` row;
 * `replaced` links the F02 diary entry that owns the nutrition. Per-serving
 * macros are denormalized so the day projection never joins a recipe version
 * that may not exist anymore.
 */
@Entity(
    tableName = "plan_slots",
    indices = [Index("planId"), Index(value = ["profileId", "dayEpochDay"]), Index("recipeId")],
)
public data class PlanSlotEntity(
    @PrimaryKey
    public val id: String,
    public val planId: String,
    public val profileId: String,
    public val dayEpochDay: Long,
    /** Wire name of [app.wlo.core.model.MealSlot]. */
    public val mealSlot: String,
    public val recipeId: String? = null,
    public val recipeVersion: Int? = null,
    public val recipeName: String? = null,
    public val servings: Double,
    /** Wire name of [app.wlo.core.model.PlannedSlotState]. */
    public val state: String = "planned",
    /** R-B1 `replaced`: the F02 diary entry that now owns this meal's nutrition. */
    public val replacedByEntryId: String? = null,
    public val successorSlotId: String? = null,
    public val replacesSlotId: String? = null,
    /** Leftovers: non-null when eating from a cook event's batch. */
    public val parentSlotId: String? = null,
    public val isCookEvent: Boolean = false,
    public val batchServings: Double? = null,
    public val kcalPerServing: Double? = null,
    public val proteinGPerServing: Double? = null,
    public val carbGPerServing: Double? = null,
    public val fatGPerServing: Double? = null,
    public val fiberGPerServing: Double? = null,
    public val createdAtEpochMs: Long,
    public val updatedAtEpochMs: Long? = null,
)

/**
 * One shopping-list row (F04 §3 `ListItem`, hardened): the stable id is the
 * anchor of delta reconciliation — plan edits update rows in place; checks
 * ride the row and survive every change. `archivedAtEpochMs` is the
 * struck-through, recoverable removal; nothing is ever hard-deleted silently.
 */
@Entity(
    tableName = "list_items",
    indices = [Index(value = ["profileId", "listId"]), Index("groceryItemId")],
)
public data class ListItemEntity(
    @PrimaryKey
    public val id: String,
    public val profileId: String,
    /** Default list "shopping" (renameable, never deletable — Paprika's anchor rule). */
    public val listId: String = "shopping",
    public val groceryItemId: String,
    /** Denormalized display name (the canonical name at last write). */
    public val name: String,
    public val qty: Double,
    /** [app.wlo.core.model.MeasureUnit.wireName]. */
    public val unit: String,
    /** [app.wlo.core.model.Aisle.wireName] (corrections already applied at write). */
    public val aisle: String,
    /** "pending" | "checked". */
    public val state: String = "pending",
    public val checkedAtEpochMs: Long? = null,
    /** The "+2" chip payload from the last reconciliation; null = no pending delta. */
    public val deltaQty: Double? = null,
    /** JSON array of {recipeId, slotId, dayEpochDay, qty, unit} provenance lines. */
    public val sourcesJson: String = "[]",
    public val createdAtEpochMs: Long,
    public val updatedAtEpochMs: Long? = null,
    public val archivedAtEpochMs: Long? = null,
)

/**
 * One pantry row (F04 §3 `PantryItem`): stock levels, expiry, staple flag and
 * the out-of-stock marker that puts an item on the next generated list.
 * Purchase history is a counter + timestamp pair in v1 (the cadence stat is
 * [purchaseCount] over [addedAtEpochMs]..[lastPurchasedAtEpochMs]).
 */
@Entity(
    tableName = "pantry_items",
    indices = [Index("profileId"), Index("groceryItemId")],
)
public data class PantryItemEntity(
    @PrimaryKey
    public val id: String,
    public val profileId: String,
    public val groceryItemId: String,
    public val name: String,
    public val qty: Double,
    public val unit: String,
    /** Calendar day (epoch day) of expiry; null = unknown (never guessed). */
    public val expiryEpochDay: Long? = null,
    public val addedAtEpochMs: Long,
    public val lastPurchasedAtEpochMs: Long? = null,
    public val purchaseCount: Int = 0,
    /** Staples auto-participate in list generation when deduction is enabled (R-S5). */
    public val isStaple: Boolean = false,
    public val outOfStock: Boolean = false,
    public val updatedAtEpochMs: Long? = null,
    public val archivedAtEpochMs: Long? = null,
)

/**
 * Learned aisle assignment (F04 §3 "teach-the-system loop"): one row per
 * (profile, item) — every user reassignment wins forever over the shipped
 * tag and the keyword guesser.
 */
@Entity(
    tableName = "aisle_corrections",
    primaryKeys = ["profileId", "groceryItemId"],
)
public data class AisleCorrectionEntity(
    public val profileId: String,
    public val groceryItemId: String,
    /** [app.wlo.core.model.Aisle.wireName]. */
    public val aisle: String,
    public val updatedAtEpochMs: Long,
)
