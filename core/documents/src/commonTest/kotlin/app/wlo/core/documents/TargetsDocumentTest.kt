package app.wlo.core.documents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Appendix A.2 invariants — table-driven; rejections are hard, never clamps. */
class TargetsDocumentTest {
    private fun document(
        cadence: Cadence = Cadence.DAILY,
        budgetKcal: Double? = 1_900.0,
        weeklyBudgetKcal: Double? = null,
        schedule: List<Double> = emptyList(),
        floorKcal: Double = 1_200.0,
        pacePctPerWeek: Double = 0.5,
    ): TargetsDocument =
        TargetsDocument(
            goal = Goal(targetWeightKg = 78.0, pacePctPerWeek = pacePctPerWeek),
            energy =
                Energy(
                    cadence = cadence,
                    budgetKcal = budgetKcal,
                    weeklyBudgetKcal = weeklyBudgetKcal,
                    schedule = schedule,
                    floorKcal = floorKcal,
                ),
            macros = Macros(split = MacroSplit.Preset("balanced")),
        )

    @Test
    fun floor_dailyBudgetBelowFloorIsRejected() {
        val violations = TargetsInvariants.validate(document(budgetKcal = 1_100.0, floorKcal = 1_200.0))
        val violation = assertIs<TargetsViolation.FloorViolated>(violations.single())
        assertEquals(1_200.0, violation.floorKcal)
        assertEquals(1_100.0, violation.attemptedKcal)
    }

    @Test
    fun floor_budgetAtFloorPasses_neverClamped() {
        assertTrue(TargetsInvariants.isValid(document(budgetKcal = 1_200.0, floorKcal = 1_200.0)))
    }

    @Test
    fun floor_weeklyAverageAndEveryDayChecked() {
        val schedule = List(6) { 1_800.0 } + listOf(1_000.0) // one sub-floor day
        val violations = TargetsInvariants.validate(document(cadence = Cadence.WEEKLY, schedule = schedule, floorKcal = 1_200.0))
        val violation = assertIs<TargetsViolation.FloorViolated>(violations.single())
        assertEquals(6, violation.dayIndex)
    }

    @Test
    fun paceCap_rejectsAboveOnePercent() {
        val violations = TargetsInvariants.validate(document(pacePctPerWeek = 1.2))
        assertIs<TargetsViolation.PaceCapViolated>(violations.single())
        assertFalse(TargetsInvariants.isValid(document(pacePctPerWeek = -1.2)), "negative pace is capped too")
        assertTrue(TargetsInvariants.isValid(document(pacePctPerWeek = 1.0)))
        assertTrue(TargetsInvariants.isValid(document(pacePctPerWeek = -1.0)))
    }

    @Test
    fun scheduleSum_weeklyMustSumExactly() {
        val schedule = List(5) { 1_800.0 } + List(2) { 2_400.0 } // = 13_800
        val valid = document(cadence = Cadence.WEEKLY, weeklyBudgetKcal = 13_800.0, schedule = schedule, floorKcal = 1_200.0)
        assertTrue(TargetsInvariants.isValid(valid), "5×1800 + 2×2400 = 13800 exactly")

        val off = document(cadence = Cadence.WEEKLY, weeklyBudgetKcal = 13_900.0, schedule = schedule, floorKcal = 1_200.0)
        val violations = TargetsInvariants.validate(off)
        val mismatch = assertIs<TargetsViolation.ScheduleSumMismatch>(violations.single())
        assertEquals(13_800.0, mismatch.actualKcal)
    }

    @Test
    fun scheduleShape_weeklyNeedsSevenEntries() {
        val violations =
            TargetsInvariants.validate(
                document(cadence = Cadence.WEEKLY, weeklyBudgetKcal = 9_000.0, schedule = List(5) { 1_800.0 }),
            )
        assertIs<TargetsViolation.ScheduleShapeInvalid>(violations.single())
    }

    @Test
    fun macros_customPercentSplitMustSumTo100() {
        val bad =
            document().copy(macros = Macros(split = MacroSplit.Custom(proteinPct = 30.0, carbPct = 30.0, fatPct = 30.0)))
        assertIs<TargetsViolation.MacroSplitInvalid>(TargetsInvariants.validate(bad).single())
    }

    @Test
    fun goalTargetWeightMustBePositive() {
        val bad = document().copy(goal = Goal(targetWeightKg = -1.0, pacePctPerWeek = 0.5))
        assertIs<TargetsViolation.InvalidGoal>(TargetsInvariants.validate(bad).single())
    }

    @Test
    fun budgetForDay_resolvesCadence() {
        val weekly = document(cadence = Cadence.WEEKLY, weeklyBudgetKcal = 13_800.0, schedule = List(5) { 1_800.0 } + List(2) { 2_400.0 })
        assertEquals(1_800.0, weekly.budgetForDay(1)) // Monday
        assertEquals(2_400.0, weekly.budgetForDay(7)) // Sunday
        val daily = document(budgetKcal = 1_900.0)
        assertEquals(1_900.0, daily.budgetForDay(3))
    }
}
