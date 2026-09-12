package app.wlo.core.database

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

// Schema v3 — the real data spine (M2, WLO-0024). Layout per F13 §3 and
// FEATURES.md Appendix A, rulings R-B8 (event-level storage), R-B9
// (profileId partition-ready), R-U13/R-D10 (metric default).
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
    /** Wire name of [app.wlo.core.model.MeasurementKind]: weight/trend/intake/burn. */
    public val kind: String,
    public val valueReal: Double,
    public val unit: String,
    /** e.g. manual/scale/ocr/import/health-connect/engine. */
    public val source: String,
    public val capturedAtEpochMs: Long,
    public val note: String? = null,
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
 * the "how we got here" sheet.
 */
@Entity(
    tableName = "provenance",
    primaryKeys = ["profileId", "dayEpochDay", "scalar"],
    indices = [Index("profileId")],
)
public data class ProvenanceEntity(
    public val profileId: String,
    public val dayEpochDay: Long,
    /** Scalar name, e.g. "intakeKcal", "trendWeightKg", "budgetKcal". */
    public val scalar: String,
    /** Method label, e.g. "sum-of-events", "trend-event", "targets-projection". */
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
 * Food catalog stub (F13 §3 archive-don't-delete): items are archived — set
 * [archivedAtEpochMs] — never orphaned, never deleted. The DAO deliberately
 * exposes NO delete method (enforced in code, not convention). `profileId`
 * per R-B9 (partition-ready).
 */
@Entity(tableName = "food_items")
public data class FoodItemEntity(
    @PrimaryKey
    public val id: String,
    public val profileId: String,
    public val name: String,
    public val brand: String? = null,
    public val kcalPer100g: Double? = null,
    public val proteinGPer100g: Double? = null,
    public val carbGPer100g: Double? = null,
    public val fatGPer100g: Double? = null,
    public val fiberGPer100g: Double? = null,
    public val createdAtEpochMs: Long,
    public val archivedAtEpochMs: Long? = null,
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
