package app.wlo.core.designsystem

/**
 * The neutral band model behind the forecast cone (DESIGN-SYSTEM.md §6): three
 * integrated trajectories + per-band finish days, decoupled from
 * `:core:engines` — the caller maps engine output 1:1.
 */
public data class WloForecastBands(
    public val startWeightKg: Double,
    public val goalWeightKg: Double,
    public val startEpochDay: Long,
    /** Fastest (steepest) integrated path, one point per engine step. */
    public val optimisticKg: List<Double>,
    public val expectedKg: List<Double>,
    /** Slowest path. */
    public val pessimisticKg: List<Double>,
    public val optimisticFinishEpochDay: Long?,
    public val expectedFinishEpochDay: Long?,
    public val pessimisticFinishEpochDay: Long?,
    /** False while evidence is developing: render the outer range, never a central date. */
    public val pointDateEligible: Boolean = true,
    /**
     * The expected band's starting weekly rate (kg/week), engine sign
     * (positive = losing) — powers the arrival row's pace fact when the goal
     * is not reached inside the horizon. Null when the caller has none.
     */
    public val expectedPaceKgPerWeek: Double? = null,
)
