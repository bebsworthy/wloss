package app.wlo.core.documents

import app.wlo.core.model.ConstantsRegistry
import kotlinx.serialization.Serializable

/**
 * Deterministic template → Targets applier (F01 §3; the v1 deterministic
 * constraint→field path per R-S10 — no AI, no network). Pure: values in,
 * draft out; the caller (Studio) shows the draft as a diff and saves it
 * through a [TargetsWriter] — appliers never persist anything.
 */
public object DietTemplateApplier {
    /** Personal inputs the template cannot know (F01 §3 inputs + Studio state). */
    @Serializable
    public data class Context(
        public val sex: String? = null,
        public val birthYear: Int? = null,
        public val heightCm: Double? = null,
        public val currentWeightKg: Double? = null,
        public val goalWeightKg: Double,
        /** Pace in % bodyweight/week — the goal dial (±1.0 cap is validated, not clamped). */
        public val pacePctPerWeek: Double = 0.5,
        /** Formula-estimate TDEE (Mifflin-St Jeor × activity), computed by the caller. */
        public val formulaTdeeKcal: Double? = null,
        /** Deficit fraction of TDEE used when no explicit budget is given. */
        public val deficitFraction: Double = 0.2,
        /** Floor override acknowledgment — persisted, never weakens validation (A.1). */
        public val floorOverrideAcknowledged: Boolean = false,
    )

    /**
     * Builds the full Targets draft: goal + energy (budget from the formula
     * estimate or maintenance), macros/rings/windows from the template,
     * fiber/water/workout defaults, template surfaces. Drafts fail validation
     * loudly (sub-floor paces get the wall in the Studio, not a silent clamp).
     */
    public fun toTargetsDocument(
        template: DietTemplate,
        context: Context,
    ): TargetsDocument {
        val floor = defaultFloorKcal(context)
        val budget = budgetKcal(context)
        val energy =
            when (template.cadence) {
                Cadence.DAILY ->
                    Energy(
                        cadence = Cadence.DAILY,
                        budgetKcal = budget,
                        schedule = emptyList(),
                        floorKcal = floor.toDouble(),
                        floorOverrideAcknowledged = context.floorOverrideAcknowledged,
                    )

                Cadence.WEEKLY -> {
                    // Flat schedule that sums EXACTLY to the weekly budget
                    // (pinned total — F01's schedule bars drag from here).
                    val perDay = round1(budget)
                    val days = List(7) { perDay }
                    Energy(
                        cadence = Cadence.WEEKLY,
                        weeklyBudgetKcal = days.sum(),
                        schedule = days,
                        floorKcal = floor.toDouble(),
                        floorOverrideAcknowledged = context.floorOverrideAcknowledged,
                    )
                }
            }

        val split: MacroSplit =
            template.macroSplit?.let {
                MacroSplit.Custom(proteinPct = it.proteinPct, carbPct = it.carbPct, fatPct = it.fatPct)
            }
                ?: MacroSplit.Preset(template.macroPreset ?: "balanced")

        return TargetsDocument(
            goal =
                Goal(
                    targetWeightKg = context.goalWeightKg,
                    pacePctPerWeek = context.pacePctPerWeek,
                    targetDate = null,
                ),
            energy = energy,
            macros =
                Macros(
                    split = split,
                    proteinFloorG = template.proteinFloorG,
                    carbCapG = template.carbCapG,
                    eatingWindow = template.eatingWindow,
                ),
            fiber = FiberTarget(targetG = template.fiberTargetG),
            water = WaterTarget(targetMl = template.waterTargetMl),
            workout = Workout(),
            surfaces = Surfaces(pinned = template.surfaces),
        )
    }

    /** Template + quiz → the F01-local DietPlan rules (food rules, household). */
    public fun toDietPlanDocument(
        template: DietTemplate,
        context: Context,
        preferences: PreferenceProfile,
        targets: TargetsDocument,
        version: Int,
        createdAtEpochMs: Long,
    ): DietPlanRecord =
        DietPlanRecord(
            version = version,
            createdAtEpochMs = createdAtEpochMs,
            document =
                DietPlanDocument(
                    name = template.name,
                    targets = targets,
                    foodRules =
                        FoodRules(
                            allergies = preferences.allergies,
                            exclusions = (preferences.exclusions + template.exclusions).distinct(),
                            dislikes = preferences.dislikes,
                            hints = template.hints,
                        ),
                    timingRules =
                        TimingRules(
                            eatingWindows = listOfNotNull(template.eatingWindow),
                            mealSlots = defaultMealSlots(),
                        ),
                    household =
                        Household(
                            size = preferences.householdSize,
                            defaultServings = preferences.householdSize,
                        ),
                ),
        )

    /** R-B3: F09 supplies the default (25–30 g) at plan creation. */
    public const val DEFAULT_FIBER_G: Double = 25.0

    public fun defaultFloorKcal(context: Context): Int {
        val sex =
            context.sex?.let { s ->
                app.wlo.core.model.Sex.entries
                    .firstOrNull { it.wireName == s }
            }
        return ConstantsRegistry.floorKcal(sex)
    }

    private fun budgetKcal(context: Context): Double {
        val tdee = context.formulaTdeeKcal
        return if (tdee != null) round1(tdee * (1.0 - context.deficitFraction)) else round1(DEFAULT_BUDGET_KCAL)
    }

    private fun defaultMealSlots(): List<String> = listOf("breakfast", "lunch", "dinner", "snack")

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

    /** Offline fallback budget when no formula inputs exist (held as ESTIMATED). */
    public const val DEFAULT_BUDGET_KCAL: Double = 2_000.0
}

/**
 * The deterministic constraint→field applier (F01 §3 on-device fallback for
 * common phrasings, R-S10: v1 ships THIS, never a cloud path). Recognized
 * families — everything else is returned UNRESOLVED and held, never guessed:
 *
 *  - "~120 g protein" / "120g protein"  → custom protein grams
 *  - "no cooking Wednesdays"            → weekly cadence, Wed trimmed and spread
 *  - "hate/no/allergic to X"            → dislikes / exclusions / allergies
 *  - "eating window 12-20" / "16:8"     → timing rule
 *  - "household of 4"                   → household size
 */
public object ConstraintApplier {
    @Serializable
    public data class Application(
        /** Template edits to show as a pending diff (apply/discard — never auto-commit). */
        public val proteinGrams: Double? = null,
        public val weekSchedule: List<Double>? = null,
        public val addedDislikes: List<String> = emptyList(),
        public val addedExclusions: List<String> = emptyList(),
        public val addedAllergies: List<String> = emptyList(),
        public val eatingWindow: EatingWindow? = null,
        public val householdSize: Int? = null,
        /** Phrasings this applier could not map — surfaced, held, never guessed. */
        public val unresolved: List<String> = emptyList(),
    )

    private val WEEKDAYS =
        listOf(
            "monday",
            "tuesday",
            "wednesday",
            "thursday",
            "friday",
            "saturday",
            "sunday",
        )

    /**
     * Applies constraints in order, each feeding the next (a schedule edit then
     * lands on top of an earlier schedule edit). [dailyBaseKcal] seeds the
     * schedule when the caller has not pinned one yet.
     */
    public fun apply(
        constraints: List<String>,
        dailyBaseKcal: Double = DietTemplateApplier.DEFAULT_BUDGET_KCAL,
    ): Application = constraints.fold(Application()) { acc, raw -> applyOne(normalize(raw), acc, dailyBaseKcal) }

    private fun applyOne(
        text: String,
        previous: Application,
        dailyBaseKcal: Double,
    ): Application {
        proteinGram(text)?.let { return previous.copy(proteinGrams = it) }
        weekdayPhrase(text)?.let { dayIndex ->
            val schedule = noCookingSchedule(previous, dayIndex, dailyBaseKcal)
            return previous.copy(weekSchedule = schedule)
        }
        foodRule(text)?.let { (bucket, item) ->
            return when (bucket) {
                "dislike" -> previous.copy(addedDislikes = previous.addedDislikes + item)
                "exclusion" -> previous.copy(addedExclusions = previous.addedExclusions + item)
                else -> previous.copy(addedAllergies = previous.addedAllergies + item)
            }
        }
        eatingWindow(text)?.let { return previous.copy(eatingWindow = it) }
        household(text)?.let { return previous.copy(householdSize = it) }
        return previous.copy(unresolved = previous.unresolved + text)
    }

    // --- phrase families (kept deliberately small, table-tested) ----------

    internal fun proteinGram(text: String): Double? {
        val match =
            Regex("(?:~?\\s*)?(\\d{2,4})\\s*(?:g|grams?)\\s+protein|protein\\s*(?:of\\s*)?~?(\\d{2,4})\\s*(?:g|grams?)")
                .find(text) ?: return null
        val value = (match.groupValues[1].ifEmpty { match.groupValues[2] }).toDoubleOrNull() ?: return null
        return value.takeIf { it in 20.0..400.0 }
    }

    internal fun weekdayPhrase(text: String): Int? {
        if (!text.contains("no cooking") && !text.contains("don't cook") && !text.contains("cant cook")) return null
        return WEEKDAYS.indexOfFirst { text.contains(it) }.takeIf { it >= 0 }
    }

    internal fun noCookingSchedule(
        previous: Application,
        dayIndex: Int,
        dailyBaseKcal: Double,
    ): List<Double> {
        // Deterministic: trim the cook-day to 70% and spread the difference
        // evenly across the other six days (pinned weekly total — F01's bar
        // chart shows the compensation live; the math stays inspectable).
        val start = previous.weekSchedule ?: List(7) { dailyBaseKcal }
        val trimmed = start[dayIndex] * 0.7
        val spread = (start[dayIndex] - trimmed) / 6.0
        return start.mapIndexed { index, kcal ->
            when (index) {
                dayIndex -> round1(trimmed)
                else -> round1(kcal + spread)
            }
        }
    }

    internal fun foodRule(text: String): Pair<String, String>? {
        val noMatch = Regex("no\\s+([a-z][a-z \\-]{1,30})$").find(text)
        val hateMatch = Regex("(?:hate|hates|don't like|dislike)\\s+([a-z][a-z \\-]{1,30})$").find(text)
        val allergyMatch = Regex("allergic to\\s+([a-z][a-z \\-]{1,30})$").find(text)
        return when {
            allergyMatch != null -> "allergy" to allergyMatch.groupValues[1].trim()
            hateMatch != null -> "dislike" to hateMatch.groupValues[1].trim()
            noMatch != null -> "exclusion" to noMatch.groupValues[1].trim()
            else -> null
        }
    }

    internal fun eatingWindow(text: String): EatingWindow? {
        val range = Regex("eating window\\s*(\\d{1,2})\\s*[-–to]+\\s*(\\d{1,2})").find(text)
        if (range != null) {
            val start = range.groupValues[1].toIntOrNull()
            val end = range.groupValues[2].toIntOrNull()
            if (start != null && end != null && start in 0..23 && end in 1..24 && start != end) {
                return EatingWindow(pattern = "window", startHour = start, endHour = end)
            }
        }
        val pattern = Regex("(\\d{1,2}):(\\d{1,2})\\s*(?:fasting|window|if)?").find(text)
        if (pattern != null && (text.contains("fast") || text.contains("window") || text.contains("if"))) {
            val fastH = pattern.groupValues[1].toIntOrNull()
            val eatH = pattern.groupValues[2].toIntOrNull()
            if (fastH != null && eatH != null && fastH + eatH == 24) {
                // 16:8 from midnight-anchored math: eat window ends at 24 − fast + start(12h default)
                return EatingWindow(pattern = "$fastH:$eatH", startHour = 12, endHour = (12 + eatH) % 24)
            }
        }
        return null
    }

    internal fun household(text: String): Int? {
        val match = Regex("household of\\s*(\\d)").find(text) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it in 1..9 }
    }

    internal fun normalize(raw: String): String = raw.trim().lowercase()

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0
}
