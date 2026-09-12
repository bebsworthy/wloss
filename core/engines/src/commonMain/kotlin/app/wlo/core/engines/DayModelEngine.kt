package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import kotlinx.serialization.Serializable

/**
 * F10 Adaptive Day Model — fixed-rules v1 (F10 §3): a rule-based state machine
 * over time-of-day plus day-completeness that reorders Hub cards and re-ranks
 * quick actions. Pure, deterministic, clock-INJECTED (D7: `minutesOfDay` is a
 * parameter — the caller reads the clock; golden-tested for a full-day
 * timeline).
 *
 * The Hub computes no science (F10 §1): this engine composes — it never sees
 * domain numbers, only completeness flags. Gut entries and silhouette
 * captures are structurally absent from the completeness inputs (R-U13:
 * irregular rhythms are normal, never a "miss").
 */
public object DayModelEngine {
    public const val VERSION: String = ConstantsRegistry.DAY_MODEL_VERSION

    /**
     * Resolves the day's phase, hero slot, ordered cards, and quick actions.
     * Fixed rules v1:
     *  - Phase: `< morningEnd` (default 10:30, user-set later) → MORNING;
     *    `< eveningStart` (19:00) → MIDDAY; `< nightStart` (22:00) → EVENING;
     *    else NIGHT.
     *  - Hero slot: check-in day promotes the check-in card (R-U17); else an
     *    unlogged morning leads with the weigh-in card (F10 §3); else the
     *    calorie ring.
     *  - Meals cards are content-rendered (R-D14): they only exist while an
     *    open planned slot exists; "plan tomorrow" renders evening-only for
     *    planners whose tomorrow plan is pending (R-D14).
     */
    public fun resolve(input: DayModelInput): DayModel {
        val phase = phaseOf(input)
        val weighInLeads = phase == DayPhase.MORNING && !input.weighInLogged

        val heroCard =
            when {
                input.checkInDueToday -> HubCard.CHECK_IN // R-U17
                weighInLeads -> HubCard.WEIGH_IN
                else -> HubCard.CALORIE_RING
            }

        val workoutPending = input.workoutDueToday && !input.workoutDone
        val mealsOpen = input.hasOpenPlannedMeal
        val trendCard = input.trendAvailable

        val cards: List<HubCardState> =
            when (phase) {
                DayPhase.MORNING ->
                    buildList {
                        if (weighInLeads) add(HubCard.WEIGH_IN.state(weighInState(input)))
                        if (trendCard) add(HubCard.TREND.state(CardState.OPEN))
                        add(HubCard.CALORIE_RING.state(CardState.OPEN))
                        if (mealsOpen) add(HubCard.MEALS_TODAY.state(CardState.OPEN))
                        if (workoutPending) add(HubCard.WORKOUT.state(CardState.OPEN))
                    }
                DayPhase.MIDDAY ->
                    buildList {
                        if (mealsOpen) add(HubCard.MEALS_TODAY.state(CardState.OPEN))
                        add(HubCard.CALORIE_RING.state(CardState.OPEN))
                        if (trendCard) add(HubCard.TREND.state(CardState.OPEN))
                        if (workoutPending) add(HubCard.WORKOUT.state(CardState.OPEN))
                    }
                DayPhase.EVENING ->
                    buildList {
                        add(HubCard.CLOSE_DAY.state(CardState.OPEN))
                        add(HubCard.CALORIE_RING.state(CardState.OPEN))
                        if (trendCard) add(HubCard.TREND.state(CardState.OPEN))
                        if (mealsOpen) add(HubCard.MEALS_TODAY.state(CardState.OPEN))
                        if (input.isPlanner && input.planTomorrowPending) {
                            add(HubCard.PLAN_TOMORROW.state(CardState.OPEN))
                        }
                    }
                DayPhase.NIGHT ->
                    buildList {
                        add(HubCard.RECAP.state(CardState.OPEN))
                        add(HubCard.CALORIE_RING.state(CardState.OPEN))
                    }
            }

        return DayModel(
            phase = phase,
            heroCard = heroCard,
            cards = cards,
            quickActions = quickActions(input, phase, weighInLeads, workoutPending),
        )
    }

    private fun phaseOf(input: DayModelInput): DayPhase =
        when {
            input.minutesOfDay < input.morningEndMinutes -> DayPhase.MORNING
            input.minutesOfDay < input.eveningStartMinutes -> DayPhase.MIDDAY
            input.minutesOfDay < input.nightStartMinutes -> DayPhase.EVENING
            else -> DayPhase.NIGHT
        }

    private fun weighInState(input: DayModelInput): CardState =
        if (input.weighInReminderMuted) CardState.MUTED_BY_CHOICE else CardState.OPEN

    /** Rail order is fixed (F10 §5); time-of-day re-ranks, never re-words. */
    private fun quickActions(
        input: DayModelInput,
        phase: DayPhase,
        weighInLeads: Boolean,
        workoutPending: Boolean,
    ): List<HubQuickAction> {
        val actions = mutableListOf<HubQuickAction>()
        if (weighInLeads) actions += HubQuickAction.WEIGH_IN
        actions += HubQuickAction.PHOTO_LOG
        if (workoutPending) actions += HubQuickAction.WORKOUT_START
        actions += HubQuickAction.POOP_LOG
        if (!weighInLeads && phase != DayPhase.NIGHT) actions += HubQuickAction.WEIGH_IN
        return actions
    }
}

/** Time-of-day phase (F10 §3). Boundaries are registry constants (v1 fixed rules). */
@Serializable
public enum class DayPhase(
    public val wireName: String,
) {
    @kotlinx.serialization.SerialName("morning")
    MORNING("morning"),

    @kotlinx.serialization.SerialName("midday")
    MIDDAY("midday"),

    @kotlinx.serialization.SerialName("evening")
    EVENING("evening"),

    @kotlinx.serialization.SerialName("night")
    NIGHT("night"),
    ;

    public companion object {
        public fun fromWireName(name: String): DayPhase? = entries.firstOrNull { it.wireName == name }
    }
}

/** The Hub cards the Day Model composes (F10 §5 surfaces). */
@Serializable
public enum class HubCard(
    public val wireName: String,
) {
    /** F07 check-in — promoted into the hero slot on check-in day (R-U17). */
    @kotlinx.serialization.SerialName("check_in")
    CHECK_IN("check_in"),

    @kotlinx.serialization.SerialName("weigh_in")
    WEIGH_IN("weigh_in"),

    @kotlinx.serialization.SerialName("trend")
    TREND("trend"),

    @kotlinx.serialization.SerialName("calorie_ring")
    CALORIE_RING("calorie_ring"),

    /** "Meals · today" — forward-looking only, content-rendered (R-D13/R-D14). */
    @kotlinx.serialization.SerialName("meals_today")
    MEALS_TODAY("meals_today"),

    @kotlinx.serialization.SerialName("workout")
    WORKOUT("workout"),

    @kotlinx.serialization.SerialName("close_day")
    CLOSE_DAY("close_day"),

    /** Evening "plan tomorrow" — planners only, plan-conditional (R-D14). */
    @kotlinx.serialization.SerialName("plan_tomorrow")
    PLAN_TOMORROW("plan_tomorrow"),

    /** Night recap (minimal layout). */
    @kotlinx.serialization.SerialName("recap")
    RECAP("recap"),
    ;

    public companion object {
        public fun fromWireName(name: String): HubCard? = entries.firstOrNull { it.wireName == name }
    }
}

/** Day-completeness card states (F10 §3): never a verdict, never a hue. */
@Serializable
public enum class CardState(
    public val wireName: String,
) {
    @kotlinx.serialization.SerialName("done")
    DONE("done"),

    @kotlinx.serialization.SerialName("open")
    OPEN("open"),

    @kotlinx.serialization.SerialName("muted-by-choice")
    MUTED_BY_CHOICE("muted-by-choice"),
    ;

    public companion object {
        public fun fromWireName(name: String): CardState? = entries.firstOrNull { it.wireName == name }
    }
}

/** The fixed quick-action rail (F10 §5); the Day Model only re-ranks it. */
@Serializable
public enum class HubQuickAction(
    public val wireName: String,
) {
    @kotlinx.serialization.SerialName("photo_log")
    PHOTO_LOG("photo_log"),

    @kotlinx.serialization.SerialName("weigh_in")
    WEIGH_IN("weigh_in"),

    @kotlinx.serialization.SerialName("poop_log")
    POOP_LOG("poop_log"),

    @kotlinx.serialization.SerialName("workout_start")
    WORKOUT_START("workout_start"),
    ;

    public companion object {
        public fun fromWireName(name: String): HubQuickAction? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * One ordered card with its completeness state. Rendering stays the caller's
 * job (content-rendered, R-D14) — the engine only decides existence, order,
 * and state.
 */
@Serializable
public data class HubCardState(
    public val card: HubCard,
    public val state: CardState,
)

private fun HubCard.state(state: CardState): HubCardState = HubCardState(this, state)

/** The engine's full output for one moment of one day. */
@Serializable
public data class DayModel(
    public val phase: DayPhase,
    public val heroCard: HubCard,
    /** Render order, first = hero-adjacent. Absence is silent (R-D14). */
    public val cards: List<HubCardState>,
    public val quickActions: List<HubQuickAction>,
)

/**
 * Everything the rules read. All completeness flags come from the owning
 * features (F06/F02/F03/F05); gut/silhouette inputs do not exist here (R-U13).
 */
@Serializable
public data class DayModelInput(
    /** Minutes since local midnight, 0..1439 (clock-injected by the caller). */
    public val minutesOfDay: Int,
    /** F10 §3: morning ends — user-set later; v1 default 10:30. */
    public val morningEndMinutes: Int = ConstantsRegistry.DAY_MODEL_MORNING_END_MINUTES,
    public val eveningStartMinutes: Int = ConstantsRegistry.DAY_MODEL_EVENING_START_MINUTES,
    public val nightStartMinutes: Int = ConstantsRegistry.DAY_MODEL_NIGHT_START_MINUTES,
    // --- day-completeness inputs (F10 §3) ---
    public val weighInLogged: Boolean = false,
    /** User muted the weigh-in nudge → the card shows `muted-by-choice`, never open. */
    public val weighInReminderMuted: Boolean = false,
    /** Sunday default: F07 check-in due — promotes into the hero slot (R-U17). */
    public val checkInDueToday: Boolean = false,
    /** An open (unresolved) planned meal slot exists — R-B1/R-D14. */
    public val hasOpenPlannedMeal: Boolean = false,
    public val workoutDueToday: Boolean = false,
    public val workoutDone: Boolean = false,
    /** F06 gating: ≥3 weigh-ins in the trailing window (F10 §4: no trend chip before). */
    public val trendAvailable: Boolean = false,
    /** R-D14: the "plan tomorrow" surface is plan-conditional and planner-only. */
    public val isPlanner: Boolean = false,
    public val planTomorrowPending: Boolean = false,
) {
    init {
        require(minutesOfDay in 0..(24 * 60 - 1)) { "minutesOfDay must be within [0, 1439]" }
        require(
            morningEndMinutes <= eveningStartMinutes &&
                eveningStartMinutes <= nightStartMinutes,
        ) { "phase boundaries must be non-decreasing" }
    }
}
