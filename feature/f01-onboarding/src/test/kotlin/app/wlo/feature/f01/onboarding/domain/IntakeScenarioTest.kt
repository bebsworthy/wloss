package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.engines.ForecastMode
import app.wlo.core.engines.IntakeProjectionInput
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.Sex
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntakeScenarioTest {
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
    private val screened = List(4) { SafetyAnswer.NO }

    @Test fun recommendationFloorAndDirectionDoNotSuppressScenarios() {
        for (kcal in listOf(900.0, 1700.0, 2200.0, 2700.0)) {
            assertNotNull(IntakeScenario.evaluate(input.copy(intakeKcal = kcal), screened, false).projection)
        }
    }

    @Test fun unansweredHealthContextDoesNotHideExploration() {
        assertNotNull(IntakeScenario.evaluate(input, List(4) { SafetyAnswer.NOT_ANSWERED }, false).projection)
    }

    @Test fun missingInvalidAndHeldDataExplainUnavailableState() {
        val cases =
            listOf(
                IntakeScenario.evaluate(null, screened, false),
                IntakeScenario.evaluate(input.copy(intakeKcal = Double.NaN), screened, false),
                IntakeScenario.evaluate(input, screened, true),
                IntakeScenario.evaluate(input, listOf(SafetyAnswer.YES) + screened.drop(1), false),
            )
        cases.forEach {
            assertNull(it.projection)
            assertTrue(it.note.isNotBlank())
        }
    }
}
