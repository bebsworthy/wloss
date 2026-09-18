package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.documents.Cadence
import app.wlo.core.documents.Energy

public enum class IntakeEntryMode { ADJUSTMENT, INTAKE }

/**
 * One canonical intake under both editor modes (WLO-0126). Maintenance is
 * an estimate supplied by the caller, never guessed here. Changing modes
 * or refreshing that estimate cannot change the user's eating budget.
 */
public data class IntakeTargetDraft(
    public val intakeKcal: Double,
    public val maintenanceKcal: Double?,
    public val mode: IntakeEntryMode = IntakeEntryMode.INTAKE,
) {
    public val adjustmentKcal: Double?
        get() = maintenanceKcal?.let { intakeKcal - it }

    public val displayedKcal: Double?
        get() = if (mode == IntakeEntryMode.INTAKE) intakeKcal else adjustmentKcal

    public fun switchMode(mode: IntakeEntryMode): IntakeTargetDraft =
        if (mode == IntakeEntryMode.ADJUSTMENT && maintenanceKcal == null) this else copy(mode = mode)

    /** Null means invalid numeric input, not a clinical or goal-direction rejection. */
    public fun enter(value: Double): IntakeTargetDraft? {
        val intake =
            when (mode) {
                IntakeEntryMode.INTAKE -> value
                IntakeEntryMode.ADJUSTMENT -> (maintenanceKcal ?: return null) + value
            }
        return if (intake.isFinite() && intake >= 0.0) copy(intakeKcal = intake) else null
    }

    /** Slider deltas use 25 kcal steps around maintenance in either mode. */
    public fun slide(adjustmentKcal: Double): IntakeTargetDraft? {
        val maintenance = maintenanceKcal ?: return null
        val stepped = kotlin.math.round(adjustmentKcal / 25.0) * 25.0
        val intake = maintenance + stepped
        return if (intake.isFinite() && intake >= 0.0) copy(intakeKcal = intake) else null
    }

    /** Explicit flat daily allocation. Other target fields remain the writer's responsibility. */
    public fun dailyEnergy(previous: Energy): Energy =
        previous.copy(
            cadence = Cadence.DAILY,
            budgetKcal = intakeKcal,
            weeklyBudgetKcal = null,
            schedule = emptyList(),
            manuallyEntered = true,
        )

    /** Weekend adjustment is balanced across weekdays; total remains seven daily budgets. */
    public fun weeklyEnergy(
        previous: Energy,
        weekendExtraKcal: Double,
    ): Energy? {
        val weekday = intakeKcal - weekendExtraKcal * 2.0 / 5.0
        val weekend = intakeKcal + weekendExtraKcal
        val total = intakeKcal * 7.0
        if (listOf(weekday, weekend, total).any { !it.isFinite() || it < 0.0 }) return null
        return previous.copy(
            cadence = Cadence.WEEKLY,
            budgetKcal = null,
            weeklyBudgetKcal = total,
            schedule = List(5) { weekday } + List(2) { weekend },
            manuallyEntered = true,
        )
    }
}
