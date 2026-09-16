package app.wlo.feature.f01.onboarding.state

import app.wlo.core.common.MassUnit
import app.wlo.core.documents.ConstraintApplier
import app.wlo.core.documents.DietTemplate
import app.wlo.core.documents.PreferenceProfile
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.engines.ForecastBands
import app.wlo.core.engines.GoalForecastResult
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.Sex
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalHoldReason
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafetyCopy
import app.wlo.core.model.WeightGoalSafetyCopyPolicy
import app.wlo.feature.f01.onboarding.domain.Milestones

/**
 * The wizard steps (F01 §3 step map + flow 07): every step is skippable —
 * "later", never "incomplete" (F01 §6).
 */
public enum class OnboardingStep {
    WELCOME,
    UNIT,
    GOAL,
    FORECAST,
    TEMPLATE,
    ADAPT,
    PREFERENCES,
    SCHEDULE,
    REVIEW,
    ;

    public val index: Int
        get() = ordinal

    public companion object {
        public val COUNT: Int = entries.size

        public fun fromNameOrNull(name: String?): OnboardingStep? = entries.firstOrNull { it.name == name }
    }
}

/**
 * One renderable wizard state. Input fields carry their values directly
 * (user-entered numbers, not derived); every DERIVED number the UI shows —
 * budget, forecast, milestones — travels through the domain types
 * ([ForecastBands], [TargetsDocument], [Milestones.Rung]) and renders via
 * provenance-chip components only (D6).
 */
public data class OnboardingUiState(
    public val step: OnboardingStep = OnboardingStep.WELCOME,
    /** Steps the user skipped with "Later" (their fields ship chip-estimated). */
    public val skippedSteps: Set<OnboardingStep> = emptySet(),
    public val restoreAttempted: Boolean = false,
    /** Global display/input unit. Domain values below remain canonical kg. */
    public val massUnit: MassUnit = MassUnit.KILOGRAM,
    // --- template gallery ---
    public val templates: List<DietTemplate> = emptyList(),
    public val selectedTemplateId: String? = null,
    // --- about you / goal (F01 §3 inputs; zero fields mandatory) ---
    public val sex: Sex? = null,
    public val birthYear: Int = DEFAULT_BIRTH_YEAR,
    public val heightCm: Double = DEFAULT_HEIGHT_CM,
    public val currentWeightKg: Double = DEFAULT_WEIGHT_KG,
    public val goalWeightKg: Double = DEFAULT_GOAL_KG,
    public val pacePctPerWeek: Double = DEFAULT_PACE_PCT,
    public val pregnant: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    public val breastfeeding: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    public val eatingDisorderConcern: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    public val medicallyInfluencedWeight: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    public val goalEligibility: WeightGoalEligibility =
        WeightGoalEligibility.Held(WeightGoalMode.LOSS, setOf(WeightGoalHoldReason.SCREENING_INCOMPLETE)),
    public val goalSafetyCopy: WeightGoalSafetyCopy = WeightGoalSafetyCopyPolicy.forResult(goalEligibility),
    public val activityLevel: ActivityLevel = ActivityLevel.SEDENTARY,
    /** The state holder's clock anchor (VM-owned time; provenance "at" stamps). */
    public val now: kotlinx.datetime.Instant = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
    public val nowEpochDay: Long = 0,
    public val formulaTdeeKcal: Double? = null,
    public val budgetKcal: Double? = null,
    public val paceWallPctPerWeek: Double = MAX_PACE_PCT,
    public val forecast: ForecastBands? = null,
    /** Shared WLO-0074 quality/safety result; [forecast] is a render compatibility view. */
    public val forecastResult: GoalForecastResult? = null,
    public val draftTargets: TargetsDocument? = null,
    public val milestones: List<Milestones.Rung> = emptyList(),
    // --- adapt (deterministic constraint applier) ---
    public val constraints: List<String> = emptyList(),
    public val application: ConstraintApplier.Application = ConstraintApplier.Application(),
    // --- preference quiz (R-S6 8-card core) ---
    public val preferences: PreferenceProfile = PreferenceProfile(),
    // --- schedule (null = flat, untouched) ---
    public val schedule: List<Double>? = null,
    // --- start ---
    public val finishing: Boolean = false,
    public val error: String? = null,
) {
    public val selectedTemplate: DietTemplate?
        get() = templates.firstOrNull { it.id == selectedTemplateId }

    public val paceAtWall: Boolean
        get() = pacePctPerWeek >= paceWallPctPerWeek - PACE_EPSILON

    public companion object {
        /** Wizard defaults (metric, R-D10; every field user-editable). */
        public const val DEFAULT_BIRTH_YEAR: Int = 1990
        public const val DEFAULT_HEIGHT_CM: Double = 172.0
        public const val DEFAULT_WEIGHT_KG: Double = 82.0
        public const val DEFAULT_GOAL_KG: Double = 74.0
        public const val DEFAULT_PACE_PCT: Double = 0.5

        /** Pace cap (A.2 #4) + the epsilon for "at the wall". */
        public const val MAX_PACE_PCT: Double = 1.0
        public const val PACE_EPSILON: Double = 1e-6

        /** Pace slider detent (the ticks under the slider). */
        public const val PACE_STEP_PCT: Double = 0.05
    }
}
