package app.wlo.core.model

/**
 * Versioned constants registry (R-A1, DRY anchor §2.5): the single owner of
 * physiological constants, engine parameters, and formula versions. F07's
 * "Algorithms" page renders from these values — they are never duplicated in
 * engines or copy. Bump [VERSION] whenever a value or formula version changes.
 *
 * v2 (M2, WLO-0024): adds the F07 Transparent-engine constants (R-A2/R-A3/R-A5/
 * R-A6, F07 §3). Values the specs name explicitly are marked with the ruling;
 * values the specs leave open carry a `DEFAULTED:` note in their KDoc — chosen
 * spec-sane, published here, and cheap to amend in one place.
 *
 * v3 (M3, WLO-0025): adds the F06 smoothing/outlier/body-fat formula versions
 * (R-A2, F06 §3), the F02 diary portion math, and the F10 Day Model fixed
 * rules (F10 §3).
 */
public object ConstantsRegistry {
    /** Registry schema/content version (R-A1). */
    public const val VERSION: Int = 3

    // --- Energy equivalence (R-A1) ---

    /** Energy equivalence of body-fat mass (R-A1: 7,700 kcal per kg). */
    public const val KCAL_PER_KG_FAT: Double = 7_700.0

    /** R-A1's imperial twin: 3,500 kcal per lb. */
    public const val KCAL_PER_LB_FAT: Double = 3_500.0

    /** Atwater factors used by F07/F01 energy math. */
    public const val KCAL_PER_G_PROTEIN: Double = 4.0
    public const val KCAL_PER_G_CARB: Double = 4.0
    public const val KCAL_PER_G_FAT: Double = 9.0

    // --- Engine parameters (F07 §3; rulings R-A2, R-A3, R-A5, R-A6) ---

    /** Trend-smoother weight (R-A2: EWMA α default 0.15, tuner visible). */
    public const val EWMA_ALPHA_DEFAULT: Double = 0.15

    /** v1 "Transparent" engine: closed-form TDEE on a rolling window (F07 §3). */
    public const val TDEE_WINDOW_DAYS: Int = 14

    /**
     * Calories burned per kg of body mass walked, used by the Activity term
     * (F07 §3: "steps → kcal via a published mass-scaled linear fit").
     * DEFAULTED (not fixed by spec): 0.00053 kcal/m/kg ≈ the mass-scaled
     * midpoint of the ACSM walking fit — v1 placeholder until F05/F13 steps
     * land; published here so the Algorithms page can already show it.
     */
    public const val KCAL_PER_STEP_PER_KG: Double = 0.00053

    /** Pace hard cap (F07 §3, A.2 invariant 4): ±1.0 % bodyweight per week. */
    public const val PACE_CAP_PCT_PER_WEEK: Double = 1.0

    /** Calorie floor defaults (F07 §3 / A.1: 1,200 F / 1,500 M). */
    public const val FLOOR_KCAL_FEMALE: Int = 1_200
    public const val FLOOR_KCAL_MALE: Int = 1_500

    /**
     * Floor for profiles with no recorded sex. DEFAULTED (not fixed by spec):
     * the higher (more protective) of the two defaults, 1,500 kcal.
     */
    public const val FLOOR_KCAL_UNDISCLOSED: Int = FLOOR_KCAL_MALE

    // --- Data-quality state machine (F07 §4, frozen thresholds) ---

    /** DEVELOPING until this many usable paired days exist in the window. */
    public const val QUALITY_MIN_USABLE_DAYS: Int = 10

    /** Trailing window the usable-day count is measured over (days). */
    public const val QUALITY_WINDOW_DAYS: Int = 21

    /** HELD trigger: this many trailing days with no intake record AND no status mark. */
    public const val HOLD_UNLOGGED_DAYS: Int = 3

    /** HELD trigger: weigh-in gap longer than this many days. */
    public const val HOLD_WEIGH_GAP_DAYS: Int = 4

    /** HELD trigger: intake residual beyond this many σ fails the outlier screen. */
    public const val HOLD_OUTLIER_SIGMA: Double = 3.0

    // --- Forecast (R-A5 mandatory cold start; R-A6 partition-free) ---

    /**
     * Cold-start band width (R-A5 "wide bands"): optimistic/pessimistic pace =
     * expected pace × [COLD_START_BAND_FAST_FACTOR] / × [COLD_START_BAND_SLOW_FACTOR].
     * DEFAULTED (not fixed by spec): ±50 % of the formula pace.
     */
    public const val COLD_START_BAND_FAST_FACTOR: Double = 1.5
    public const val COLD_START_BAND_SLOW_FACTOR: Double = 0.5

    /**
     * Measured-mode band percentiles from the trailing pace distribution
     * (F07 §3: 80th / 20th percentile, 28-day window).
     */
    public const val BAND_PACE_WINDOW_DAYS: Int = 28
    public const val BAND_PACE_FAST_PERCENTILE: Double = 0.80
    public const val BAND_PACE_SLOW_PERCENTILE: Double = 0.20

    /** Hard horizon cap for forecast integration (weeks) — forecasts never run forever. */
    public const val FORECAST_HORIZON_WEEKS: Int = 260

    /** Forecast integration step (days). */
    public const val FORECAST_STEP_DAYS: Int = 7

    // --- F06 smoothing + outlier guard (R-A2, F06 §3) ---

    /** Trailing-EWMA trend smoother (R-A2 default α; the tuner overrides it). */
    public const val EWMA_FORMULA_VERSION: String = "trend/ewma-v1"

    /** Zero-phase (forward-backward) EWMA — F06 §3 option 2; recent values revise. */
    public const val EWMA_ZERO_PHASE_FORMULA_VERSION: String = "trend/ewma-zero-phase-v1"

    /** Plain 7-day moving average — F06 §3 option 3. */
    public const val MA7_FORMULA_VERSION: String = "trend/ma7-v1"

    /** Window length of [MOVING_AVERAGE_7D] (days). */
    public const val MA7_WINDOW_DAYS: Int = 7

    /**
     * Weigh-in outlier guard width (F06 §4: "an entry ±3σ off recent
     * residual triggers a one-line confirm"). Flagged → held-adjacent
     * confirm, never dropped (R-B8: events stay verbatim).
     */
    public const val OUTLIER_SIGMA_WEIGHT: Double = 3.0

    /** Minimum trailing points before the σ estimate is trusted. */
    public const val OUTLIER_MIN_RECENT_POINTS: Int = 3

    /** Outlier-guard formula version (stamped into the confirm + attr payload). */
    public const val OUTLIER_FORMULA_VERSION: String = "weighin/outlier-3sigma-v1"

    // --- F06 body-fat method registry (F06 §3) ---

    /** US Navy tape method (Hodgdon & Beckett 1984 circumference equations). */
    public const val BODY_FAT_NAVY_FORMULA_VERSION: String = "bodyfat/navy-hodgdon-beckett-v1"

    /** RFM (Woolcott & Bergman 2012, PLoS One) — height/waist only. */
    public const val BODY_FAT_RFM_FORMULA_VERSION: String = "bodyfat/rfm-woolcott-bergman-v1"

    // --- F02 diary portion math (R-A4 v1 nutrient scope) ---

    /** Entry kcal/macros = per-100g × quantity/100 (portion-scale). */
    public const val DIARY_PORTION_FORMULA_VERSION: String = "diary/portion-scale-v1"

    /** Sanity rail (F02 §3/§8): per-100g energy density can never exceed pure fat. */
    public const val MAX_KCAL_PER_100G: Double = 9.0 * 100.0

    // --- F10 Adaptive Day Model, fixed rules v1 (F10 §3) ---

    /** Day Model rule-pack version (stamped into cards' provenance + tests). */
    public const val DAY_MODEL_VERSION: String = "daymodel/rules-v1"

    /** Morning ends (user-set later; v1 default) — F10 §3 "until user-set, default 10:30". */
    public const val DAY_MODEL_MORNING_END_MINUTES: Int = 10 * 60 + 30

    /** Evening starts — F10 §3 "from ~19:00". */
    public const val DAY_MODEL_EVENING_START_MINUTES: Int = 19 * 60

    /**
     * Night starts (F10 §3 "Night: minimal layout, recap only" names no hour).
     * DEFAULTED (not fixed by spec): 22:00 — after the quiet-hours ramp begins
     * (21:30 per R-U1) the Hub assumes wind-down.
     */
    public const val DAY_MODEL_NIGHT_START_MINUTES: Int = 22 * 60

    // --- Formula versions (stamped into Provenance.Derived + the decision ledger) ---

    /** Formula version stamped into [Provenance.Derived]. */
    public const val BMI_FORMULA_VERSION: String = "bmi/quetelet-v1"

    /** Mifflin-St Jeor BMR (F01 §3, F07 §3 BMR term). */
    public const val BMR_FORMULA_VERSION: String = "bmr/mifflin-st-jeor-v1"

    /** v1 Transparent engine version tag (F07 §3 "Algorithm versioning"). */
    public const val ENERGY_ENGINE_VERSION: String = "transparent-v1"

    /** Decelerating 3-band forecast model version (R-A5/R-A6). */
    public const val FORECAST_MODEL_VERSION: String = "forecast/decel-3band-v1"

    /** Closed-form TDEE solve version (Zolt-lineage formula, F07 §3). */
    public const val TDEE_FORMULA_VERSION: String = "tdee/closed-form-v1"

    /**
     * Mifflin-St Jeor activity multipliers (F01 §3 "Mifflin-St Jeor + activity
     * multiplier"). DEFAULTED to the published standard factors; keyed by
     * [ActivityLevel].
     */
    public fun activityMultiplier(level: ActivityLevel): Double =
        when (level) {
            ActivityLevel.SEDENTARY -> 1.2
            ActivityLevel.LIGHT -> 1.375
            ActivityLevel.MODERATE -> 1.55
            ActivityLevel.ACTIVE -> 1.725
            ActivityLevel.VERY_ACTIVE -> 1.9
        }

    /** Default calorie floor for a sex (F07 §3; [FLOOR_KCAL_UNDISCLOSED] when unknown/other). */
    public fun floorKcal(sex: Sex?): Int =
        when (sex) {
            Sex.FEMALE -> FLOOR_KCAL_FEMALE
            Sex.MALE -> FLOOR_KCAL_MALE
            Sex.OTHER, null -> FLOOR_KCAL_UNDISCLOSED
        }
}
