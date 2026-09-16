package app.wlo.feature.f01.onboarding

import app.wlo.core.common.MassUnit
import app.wlo.core.documents.Cadence
import app.wlo.core.documents.ConstraintApplier
import app.wlo.core.documents.Energy
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.engines.ForecastBand
import app.wlo.core.engines.MilestoneLadder
import app.wlo.feature.f01.onboarding.domain.FinishOnboarding
import app.wlo.feature.f01.onboarding.domain.OnboardingDraft
import app.wlo.feature.f01.onboarding.domain.OnboardingDraftIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pure wizard-domain math: milestones, constraints merge, draft codec. */
class OnboardingDomainTest {
    private fun band(rates: List<Double>): ForecastBand =
        ForecastBand(finishEpochDay = null, weeklyRatesKg = rates, trajectoryKg = List(rates.size) { i -> 80.0 - i })

    @Test
    fun milestoneLadderPrefersThreeKgSteps_whenTheyYieldFourToEightRungs() {
        // 12 kg journey: 3 kg steps → 4 rungs (inside the 4–8 window).
        val rungs =
            MilestoneLadder.loss(
                journeyStartKg = 80.0,
                currentTrendKg = 80.0,
                goalKg = 68.0,
                optimistic = band(List(40) { 1.0 }),
                pessimistic = band(List(40) { 1.0 }),
                forecastStartEpochDay = 20_700,
            )
        assertEquals(4, rungs.size)
        assertTrue(rungs.last().isGoal)
        assertEquals(68.0, rungs.last().weightKg, 1e-9)
    }

    @Test
    fun milestoneRungsBeyondTheHorizonCarryNoDate() {
        // A trajectory that never reaches the rungs → hidden projections.
        val short = band(List(3) { 0.1 })
        val rungs =
            MilestoneLadder.loss(
                journeyStartKg = 80.0,
                currentTrendKg = 80.0,
                goalKg = 68.0,
                optimistic = short,
                pessimistic = short,
                forecastStartEpochDay = 20_700,
            )
        assertTrue(rungs.isNotEmpty())
        rungs.forEach { rung -> assertNull(rung.rangeEpochDays, "rung ${rung.weightKg} must not fake a date") }
    }

    @Test
    fun draftCodecRoundTripsAndRefusesGarbage() {
        val draft =
            OnboardingDraft(
                step = "FORECAST",
                massUnit = MassUnit.POUND.name,
                sex = "female",
                goalWeightKg = 74.0,
                constraints = listOf("hate olives"),
            )
        val decoded = OnboardingDraftIO.decode(OnboardingDraftIO.encode(draft))
        assertEquals(draft, decoded)
        assertEquals(MassUnit.POUND.name, decoded?.massUnit)
        assertNull(OnboardingDraftIO.decode("not json at all"))
        assertNull(OnboardingDraftIO.decode("{\"schemaVersion\":99,\"payload\":{}}"))
    }

    @Test
    fun applyConstraintsMergesProteinAndSwitchesToPinnedWeeklySchedule() {
        val draft =
            TargetsDocument(
                goal = Goal(targetWeightKg = 74.0, pacePctPerWeek = 0.5),
                energy = Energy(cadence = Cadence.DAILY, budgetKcal = 1_900.0, floorKcal = 1_500.0),
                macros = Macros(split = MacrosSplitPreset),
            )
        val application =
            ConstraintApplier.Application(
                proteinGrams = 120.0,
                weekSchedule = List(7) { 1_800.0 },
            )
        val merged = FinishOnboarding.applyConstraints(draft, application)

        assertEquals(Cadence.WEEKLY, merged.energy.cadence)
        assertEquals(7, merged.energy.schedule.size)
        val weekly = assertNotNull(merged.energy.weeklyBudgetKcal)
        assertEquals(merged.energy.schedule.sum(), weekly, 1e-6)
        val split = merged.macros.split as MacroSplit.Custom
        assertEquals(120.0, split.proteinG ?: 0.0, 1e-9)
    }

    private companion object {
        val MacrosSplitPreset: MacroSplit = MacroSplit.Preset("balanced")
    }
}
