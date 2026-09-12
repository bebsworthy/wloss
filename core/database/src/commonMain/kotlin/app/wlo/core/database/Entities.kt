package app.wlo.core.database

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Fts5
import androidx.room3.Index
import androidx.room3.PrimaryKey

// Schema v4 (M3, WLO-0025): F02 manual logging (realized food catalog + FTS5
// search + diary with a correction audit) on top of the M2 spine, plus the
// R-B7 Fresh Start ledger columns (hide-not-delete) on the event stores.
// Layout per F02/F13 §3, FEATURES.md rulings R-B1/R-B7/R-B8/R-B9.
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
