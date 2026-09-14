package app.wlo.feature.f10.hub.ui

/**
 * Cross-feature exits from the Hub; :app maps each onto the owning feature's
 * route contract (D2: no feature→feature dependency).
 */
public data class HubActions(
    val onOpenDiary: () -> Unit = {},
    val onOpenCapture: () -> Unit = {},
    val onQuickAddKcal: () -> Unit = {},
    val onLogWeight: () -> Unit = {},
    val onOpenWeight: () -> Unit = {},
    val onGutLog: () -> Unit = {},
    val onWorkout: () -> Unit = {},
    // F03 (M5): today's plan card and the evening "plan tomorrow" card.
    val onOpenPlan: () -> Unit = {},
    val onPlanTomorrow: () -> Unit = {},
    // WLO-0033 wave 2: the meals row opens the planner focused on the slot
    // (wlo://log/planned?slot= → f03/plan/focus).
    val onOpenMealSlot: (String) -> Unit = {},
    // Settings (IA §1: top-right on the Hub header; M6 PART B wires it).
    val onOpenSettings: () -> Unit = {},
)
