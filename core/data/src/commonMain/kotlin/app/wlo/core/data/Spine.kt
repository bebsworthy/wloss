package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.MeasurementAttr
import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.Profile
import app.wlo.core.model.Sex
import app.wlo.core.model.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// The data spine's public doors (ARCHITECTURE §2.2 ":core:data",
// §2.4 "Repositories + projections"). Features depend on these interfaces
// only — never on `:core:database`. All fallible calls return [WloResult]
// with sealed [AppError]s; Flows emit [WloResult] values so a storage failure
// upstream is a VALUE, never a thrown exception across a boundary (D8).

/** Onboarding input for the (single, R-B9) profile — everything optional except defaults. */
@Serializable
public data class NewProfile(
    public val sex: Sex? = null,
    public val birthYear: Int,
    public val heightCm: Double,
    public val startWeightKg: Double,
    public val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    /** Metric default (R-D10). */
    public val unitPreference: UnitSystem = UnitSystem.METRIC,
)

public interface ProfileRepository {
    /** The active (non-archived) profile; null before onboarding writes one. */
    public fun observeActive(): Flow<WloResult<Profile?>>

    public suspend fun active(): WloResult<Profile?>

    public suspend fun byId(profileId: String): WloResult<Profile?>

    /** Creates the default profile (id assigned by the store, archive-don't-delete). */
    public suspend fun create(
        profile: NewProfile,
        at: Instant,
    ): WloResult<Profile>

    /** Fresh Start's reversible hide-not-delete (F01 §3, R-B7) lives with F01; this is the store-level retirement. */
    public suspend fun archive(
        profileId: String,
        at: Instant,
    ): WloResult<Unit>

    /** Unit setting (R-D10) — writes the profile row and the app-level settings store. */
    public suspend fun setUnitPreference(
        profileId: String,
        unit: UnitSystem,
    ): WloResult<Unit>

    /**
     * Corrects the profile FACTS collected at onboarding (sex, birth year,
     * height, activity level — WLO-0035 W4). A mistyped height must never
     * permanently skew RFM/Navy/BMR math. Not a goal edit: goals live in the
     * versioned Targets store (R-B2); this only rewrites the measured facts.
     */
    public suspend fun updateFacts(
        profileId: String,
        sex: Sex?,
        birthYear: Int,
        heightCm: Double,
        activityLevel: ActivityLevel,
    ): WloResult<Unit>
}

/** Append input (R-B8: events are append-only). Unit derives from [kind]
 * unless [unitOverride] names a per-metric unit (F06 §3 custom metrics). */
@Serializable
public data class NewMeasurement(
    public val profileId: String,
    public val dayEpochDay: Long,
    public val kind: MeasurementKind,
    public val valueReal: Double,
    public val source: String,
    public val capturedAt: Instant,
    public val note: String? = null,
    /** Per-metric unit (custom metrics, F06 §3 EAV); null = kind's default. */
    public val unitOverride: String? = null,
)

public interface MeasurementRepository {
    /**
     * Appends one timestamped event verbatim (multiple weigh-ins per day are
     * normal data) and refreshes the affected day's projection scalars.
     */
    public suspend fun append(event: NewMeasurement): WloResult<MeasurementEvent>

    /** Attaches custom-metric attributes (EAV sidecar) to an existing event. */
    public suspend fun attachAttrs(
        eventId: String,
        attrs: List<MeasurementAttr>,
    ): WloResult<Unit>

    public suspend fun range(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<MeasurementEvent>>

    /**
     * One kind's events in a day range, oldest first — the bounded reads the
     * history card and the logbook feed are built on (WLO-0055): windows, not
     * whole-table sweeps.
     */
    public suspend fun rangeOfKind(
        profileId: String,
        kind: MeasurementKind,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<MeasurementEvent>>

    /**
     * How many events of one kind sit in a day range — the logbook footer's
     * "N more" stays a stated fact, not a guess (WLO-0055).
     */
    public suspend fun countOfKind(
        profileId: String,
        kind: MeasurementKind,
        fromDay: Long,
        toDay: Long,
    ): WloResult<Int>

    /** One event by id (the weigh-in door snapshots before delete). */
    public suspend fun byId(eventId: String): WloResult<MeasurementEvent?>

    public fun observeRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<WloResult<List<MeasurementEvent>>>

    /**
     * Hard-deletes one event and its EAV sidecar, then refreshes the touched
     * day's projection. R-B8 amendment (WLO-0035): a user-initiated delete is
     * an explicit act, not silent collapsing — the verbatim rule governs
     * ingestion and automatic processing, not the user's own corrections.
     * Undo is the caller's business (in-memory snapshot, no trash table).
     */
    public suspend fun delete(eventId: String): WloResult<Unit>

    /**
     * Removes every TREND scalar of a day and refreshes that day's projection.
     * The weigh-in door calls it when a day's last weigh-in is gone — a stale
     * trend scalar would keep poisoning the Hub hero and exports.
     */
    public suspend fun deleteTrendScalars(
        profileId: String,
        day: Long,
    ): WloResult<Unit>

    public suspend fun attrsOf(eventId: String): WloResult<List<MeasurementAttr>>

    /** Every sidecar row of a profile's events in the day range (logbook flags, EAV). */
    public suspend fun attrsInRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<MeasurementAttr>>
}
