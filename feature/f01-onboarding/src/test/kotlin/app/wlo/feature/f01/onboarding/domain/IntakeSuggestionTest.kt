package app.wlo.feature.f01.onboarding.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IntakeSuggestionTest {
    @Test
    fun lossCandidateRequiresGuidelinePopulationAndNeverScalesWithGoalDistance() {
        assertEquals(1900.0, IntakeSuggestion.candidate(2400.0, 90.0, 85.0, 175.0))
        assertEquals(1900.0, IntakeSuggestion.candidate(2400.0, 90.0, 60.0, 175.0))
        assertNull(IntakeSuggestion.candidate(2400.0, 65.0, 60.0, 175.0))
        assertNull(IntakeSuggestion.candidate(2400.0, 90.0, 80.0, null))
    }

    @Test
    fun maintenanceAndGainHaveSeparateStartingCandidates() {
        assertEquals(2400.0, IntakeSuggestion.candidate(2400.0, 70.0, 70.0, 175.0))
        assertEquals(2700.0, IntakeSuggestion.candidate(2400.0, 70.0, 75.0, 175.0))
        assertNull(IntakeSuggestion.candidate(Double.NaN, 70.0, 75.0, 175.0))
    }
}
