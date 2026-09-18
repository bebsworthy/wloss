package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.model.SafetyAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IntakeScreeningIOTest {
    @Test
    fun screeningRoundTripsWithoutTurningSkippedAnswersIntoNo() {
        val answers = listOf(SafetyAnswer.NO, SafetyAnswer.YES, SafetyAnswer.NOT_ANSWERED, SafetyAnswer.NO)
        assertEquals(answers, IntakeScreeningIO.decode(IntakeScreeningIO.encode(answers)))
        assertNull(IntakeScreeningIO.decode("broken"))
        assertNull(IntakeScreeningIO.decode(IntakeScreeningIO.encode(answers.take(2))))
        val future = IntakeScreeningIO.encode(answers).replace("\"schemaVersion\":1", "\"schemaVersion\":99")
        assertNull(IntakeScreeningIO.decode(future))
    }
}
