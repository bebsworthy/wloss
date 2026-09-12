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

    public fun observeRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<WloResult<List<MeasurementEvent>>>

    public suspend fun attrsOf(eventId: String): WloResult<List<MeasurementAttr>>
}
