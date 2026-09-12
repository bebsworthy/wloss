package app.wlo.core.documents

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The versioned Targets document — the contract every target-consuming feature
 * reads (FEATURES.md Appendix A, ratified WLO-0006; authority frozen by R-B2).
 * One active instance per profile; versions are immutable: every write creates
 * `vN+1`, "revert" is a new version copying an older one (no history rewrites).
 *
 * The envelope lives beside the payload in storage/export:
 * `{schemaVersion, version, parentVersion, createdAt, createdBy}` — see
 * [TargetsEnvelope] and [TargetsDocumentIO].
 */
@Serializable
public data class TargetsDocument(
    public val goal: Goal,
    public val energy: Energy,
    public val macros: Macros,
    public val fiber: FiberTarget = FiberTarget(),
    public val water: WaterTarget = WaterTarget(),
    public val workout: Workout = Workout(),
    public val surfaces: Surfaces = Surfaces(),
) {
    /** Budget for a given weekday (ISO day number 1=Mon…7=Sun) under the cadence. */
    public fun budgetForDay(isoDayNumber: Int): Double? =
        when (energy.cadence) {
            Cadence.DAILY -> energy.budgetKcal
            Cadence.WEEKLY -> energy.schedule.getOrNull(isoDayNumber - 1)
        }
}

/** A.1 `goal`: `targetDate` is advisory and always rendered as its implied pace (F01 §3). */
@Serializable
public data class Goal(
    public val targetWeightKg: Double,
    /** Pace in % bodyweight per week; hard-capped at ±1.0 (A.2 invariant 4). */
    public val pacePctPerWeek: Double,
    public val targetDate: String? = null,
)

@Serializable
public enum class Cadence {
    @SerialName("daily")
    DAILY,

    @SerialName("weekly")
    WEEKLY,
}

/** A.1 `energy`: cadence + budget | weekly budget + schedule[7] + floor. */
@Serializable
public data class Energy(
    public val cadence: Cadence = Cadence.DAILY,
    /** Daily budget when [cadence] is DAILY. */
    public val budgetKcal: Double? = null,
    /** Weekly budget when [cadence] is WEEKLY; schedule must sum exactly to it. */
    public val weeklyBudgetKcal: Double? = null,
    /** kcal per weekday (Mon–Sun), weekly cadence only. */
    public val schedule: List<Double> = emptyList(),
    /** Calorie floor: no version may budget below it (A.2 invariant 1). */
    public val floorKcal: Double,
    /**
     * Persistent acknowledgment for overriding the default floor (A.1).
     * The override can never weaken validation — the floor check always runs.
     */
    public val floorOverrideAcknowledged: Boolean = false,
)

/**
 * A.1 `macros` — one schema, no special cases (F01 §3): a preset or a custom
 * split, plus optional rings and eating-window rules for IF variants.
 * Percent splits must sum to 100 (± ε) when fully specified.
 */
@Serializable
public data class Macros(
    public val split: MacroSplit,
    public val proteinFloorG: Double? = null,
    /** Keto carb-limit ring (F01 §3). */
    public val carbCapG: Double? = null,
    public val eatingWindow: EatingWindow? = null,
)

@Serializable
public sealed interface MacroSplit {
    @Serializable
    @SerialName("preset")
    public data class Preset(
        /** balanced | high-protein | mediterranean | keto | low-carb | if-16-8 | if-5-2 | if-6-1 | custom */
        public val name: String,
    ) : MacroSplit

    @Serializable
    @SerialName("custom")
    public data class Custom(
        /** Grams take precedence over percent when both are set. */
        public val proteinG: Double? = null,
        public val proteinPct: Double? = null,
        public val carbPct: Double? = null,
        public val fatPct: Double? = null,
    ) : MacroSplit
}

/** IF timing rules (F01 §3); `windows` are clock hours, e.g. 12..20 for 16:8. */
@Serializable
public data class EatingWindow(
    public val pattern: String,
    public val startHour: Int? = null,
    public val endHour: Int? = null,
    /** 5:2 / 6:1 fast-day indices (ISO day numbers). */
    public val fastDays: List<Int> = emptyList(),
)

@Serializable
public data class FiberTarget(
    /** R-B3: exactly one number, default authored by F09 at plan creation. */
    public val targetG: Double = 25.0,
)

@Serializable
public data class WaterTarget(
    public val targetMl: Double = 2_000.0,
)

/** A.1 `workout` — context for F05, read-only there (R-B2 "context, never credit"). */
@Serializable
public data class Workout(
    public val cadencePerWeek: Int = 3,
)

/**
 * A.1 `surfaces` — which rings/timers/cards pin where (from the F01 template;
 * consumed by F02/F10). Open string set, deliberately unstructured: one schema.
 */
@Serializable
public data class Surfaces(
    public val pinned: List<String> = emptyList(),
)
