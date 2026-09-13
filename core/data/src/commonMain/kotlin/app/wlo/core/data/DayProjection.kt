package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.model.DerivedValue
import kotlinx.coroutines.flow.Flow

/**
 * The day projection (FEATURES.md Appendix A.3): `{date → kcal, macros g,
 * fiber g, water ml}` resolved from cadence + schedule + applied F07 deltas,
 * plus the day's measured scalars cached from events. THE ONLY DOOR to day
 * scalars — consumers never parse the Targets schedule or aggregate events
 * themselves; every scalar arrives provenance-chipped (§2.1 provenance rule).
 */
public interface DayProjectionRepository {
    public fun observeDay(
        profileId: String,
        day: Long,
    ): Flow<WloResult<DayView>>

    public fun observeRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<WloResult<List<DayView>>>

    public suspend fun day(
        profileId: String,
        day: Long,
    ): WloResult<DayView>

    public suspend fun range(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<DayView>>

    /**
     * Re-runs the projection pipeline over [fromDay]..[toDay] (called by the
     * background pipeline; append paths already trigger it for the day
     * touched). This is the ONLY writer of `day_records`, inside this module.
     */
    public suspend fun recompute(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<Unit>
}

/**
 * One day's rendering view. Every number is a [DerivedValue] (chip mandatory)
 * or null (absence is silent, R-D14); provenance for the targets part names
 * the Targets version it resolved from ("adaptive · check-in Sep 8").
 *
 * The planned side (R-B1) folds the F03 plan's slots for the day in at read
 * time: `planned*` scalars are the plan's claim (PLANNED + CONFIRMED slots;
 * swapped rows are retired, skipped/replaced contribute nothing), so the
 * planned-vs-logged distinction renders from one door and stays in lockstep
 * with any plan edit. Null = no plan for the day — a user who never plans
 * sees no plan surfaces (R-D14).
 */
public data class DayView(
    public val profileId: String,
    public val dayEpochDay: Long,
    // --- targets projection (A.3) ---
    public val budgetKcal: DerivedValue<Double>?,
    public val proteinG: DerivedValue<Double>?,
    public val carbG: DerivedValue<Double>?,
    public val fatG: DerivedValue<Double>?,
    public val fiberG: DerivedValue<Double>?,
    public val waterMl: DerivedValue<Double>?,
    // --- measured scalars cached from events (R-B8 derived views) ---
    public val trendWeightKg: DerivedValue<Double>?,
    public val intakeKcal: DerivedValue<Double>?,
    public val burnKcal: DerivedValue<Double>?,
    // --- planned scalars folded from the F03 plan (R-B1) ---
    public val plannedKcal: DerivedValue<Double>? = null,
    public val plannedProteinG: DerivedValue<Double>? = null,
    public val plannedCarbG: DerivedValue<Double>? = null,
    public val plannedFatG: DerivedValue<Double>? = null,
    public val plannedFiberG: DerivedValue<Double>? = null,
    /** Slots with a recipe in states planned + confirmed (the plan's claim). */
    public val plannedSlotCount: Int = 0,
    /** Of those, still open (planned) — "1 of 3 open" in the day header. */
    public val plannedOpenSlots: Int = 0,
)
