package app.wlo.core.engines

import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.Sex
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntakeProjectionEngineTest {
    private val input =
        IntakeProjectionInput(
            Sex.MALE,
            40,
            180.0,
            80.0,
            2200.0,
            2200.0,
            ForecastMode.COLD_START,
            Instant.parse("2026-09-18T12:00:00Z"),
        )

    @Test fun goalHorizonIncludesNineEighteenAndThirtySixMonthArrivals() {
        val loss = input.copy(startWeightKg = 120.0, maintenanceKcal = 2600.0, intakeKcal = 2100.0)
        for (weeks in listOf(39, 78, 156)) {
            val reference = assertNotNull(IntakeProjectionEngine.project(loss, horizonOverride = weeks))
            val target = reference.upperKg.last()
            val result = assertNotNull(IntakeProjectionEngine.project(loss, target))
            val (early, late) = result.crossingWeeks(target)
            assertNotNull(early)
            assertNotNull(late)
            assertTrue(late >= weeks - 0.001)
            assertTrue(result.weeks > weeks)
            assertEquals(reference.expectedKg, result.expectedKg.take(weeks + 1))
        }
    }

    @Test fun unreachableGoalsAreNotConfusedWithShortPreviewCutoffs() {
        val loss = input.copy(startWeightKg = 120.0, maintenanceKcal = 2600.0, intakeKcal = 2100.0)
        val result = assertNotNull(IntakeProjectionEngine.project(loss, 60.0))
        assertEquals(70.0, result.equilibriumKg)
        assertEquals(null to null, result.crossingWeeks(60.0))
        assertTrue(result.expectedKg.last() > 70.0)
        val frozen = assertNotNull(IntakeProjectionEngine.project(loss, 75.0, horizonOverride = 26))
        assertEquals(26, frozen.weeks)
        assertTrue(assertNotNull(frozen.crossingWeeks(75.0).second) > 26)
        val reframed = assertNotNull(IntakeProjectionEngine.project(loss, 75.0))
        assertTrue(reframed.weeks > frozen.weeks)
        assertNotNull(reframed.crossingWeeks(75.0).second)
    }

    @Test fun allNineGoalAndAdjustmentCombinationsKeepFullPaths() {
        for (goal in listOf(74.0, 80.0, 86.0)) {
            for (delta in listOf(-500.0, 0.0, 500.0)) {
                val result = assertNotNull(IntakeProjectionEngine.project(input.copy(intakeKcal = 2200 + delta)))
                assertEquals(27, result.expectedKg.size)
                assertEquals(27, result.lowerKg.size)
                assertEquals(27, result.upperKg.size)
                assertEquals(80.0, result.expectedKg.first())
                val end = result.expectedKg.last()
                assertTrue(if (delta == 0.0) end == 80.0 else (end - 80) * delta > 0)
                val (early, late) = result.crossingWeeks(goal)
                if ((goal - 80) * delta <= 0) {
                    assertNull(early)
                    assertNull(late)
                } else {
                    assertNotNull(early)
                }
                result.expectedKg.indices.forEach { i ->
                    assertTrue(result.lowerKg[i] <= result.expectedKg[i] && result.expectedKg[i] <= result.upperKg[i])
                }
            }
        }
    }

    @Test fun targetAnnotationDoesNotTruncateOrAlterProjection() {
        val result = assertNotNull(IntakeProjectionEngine.project(input.copy(intakeKcal = 1700.0)))
        val before = result.expectedKg.toList()
        val (early, late) = result.crossingWeeks(79.5)
        assertNotNull(early)
        assertNotNull(late)
        assertTrue(early < late && early > 0)
        assertTrue(result.expectedKg.last() < 79.5)
        result.crossingWeeks(81.0)
        assertEquals(before, result.expectedKg)
    }

    @Test fun maintenanceAndBothSidesOfZeroAreContinuous() {
        for (mode in ForecastMode.entries) {
            val at = assertNotNull(IntakeProjectionEngine.project(input.copy(mode = mode)))
            assertTrue(at.expectedKg.all { it == 80.0 })
            for (delta in listOf(-0.001, 0.001)) {
                val near = assertNotNull(IntakeProjectionEngine.project(input.copy(intakeKcal = 2200 + delta, mode = mode)))
                assertTrue(abs(near.expectedKg.last() - 80) < 0.001)
                assertTrue((near.expectedKg.last() - 80) * delta > 0)
            }
        }
    }

    @Test fun scenariosReuseExistingProductionDynamicsInBothDirections() {
        val cold =
            ColdStartInput(
                Sex.MALE,
                40,
                180.0,
                80.0,
                74.0,
                ActivityLevel.SEDENTARY,
                1700.0,
                0,
                input.at,
            )
        val maintenance =
            ForecastEngine.bmrMifflinStJeor(cold.sex, 80.0, 180.0, 40) *
                ConstantsRegistry.activityMultiplier(cold.activityLevel)
        for (gain in listOf(false, true)) {
            val intake = maintenance + if (gain) 300.0 else -500.0
            val legacy = ForecastEngine.coldStart(cold.copy(goalWeightKg = if (gain) 86.0 else 74.0, intakeKcal = intake))
            val scenario = assertNotNull(IntakeProjectionEngine.project(input.copy(maintenanceKcal = maintenance, intakeKcal = intake)))
            legacy.expected.trajectoryKg.take(5).forEachIndexed { index, kg ->
                assertEquals(kg, scenario.expectedKg[index + 1], 1e-9)
            }
        }
    }

    @Test fun floorIsNotAChartGateButInvalidOrNonPhysicalInputsAre() {
        assertNotNull(IntakeProjectionEngine.project(input.copy(intakeKcal = 900.0)))
        assertNotNull(IntakeProjectionEngine.project(input.copy(intakeKcal = 0.0)))
        for (bad in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(IntakeProjectionEngine.project(input.copy(intakeKcal = bad)))
        }
        assertNull(IntakeProjectionEngine.project(input.copy(maintenanceKcal = 100000.0, intakeKcal = 0.0)))
        assertNull(IntakeProjectionEngine.project(input.copy(ageYears = 17)))
    }
}
