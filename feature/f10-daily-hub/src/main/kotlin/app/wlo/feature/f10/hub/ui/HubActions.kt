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
)
