package app.wlo.core.designsystem

import app.wlo.core.model.Provenance

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
)

/** True when the forecast's provenance is an estimate — drives the ESTIMATED stamp. */
public fun isEstimated(provenance: Provenance): Boolean = provenance is Provenance.Estimated
