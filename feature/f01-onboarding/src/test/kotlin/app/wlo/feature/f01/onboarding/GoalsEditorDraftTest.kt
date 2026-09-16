package app.wlo.feature.f01.onboarding

import app.wlo.core.common.MassUnit
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.WeightGoalMode
import app.wlo.feature.f01.onboarding.domain.GoalsEditorDraft
import app.wlo.feature.f01.onboarding.domain.GoalsEditorDraftIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoalsEditorDraftTest {
    @Test
    fun canonicalDraftRoundTripsAcrossProcessRecreation() {
        val draft =
            GoalsEditorDraft(
                baseVersion = 4,
                targetWeightKg = 72.5,
                pacePctPerWeek = 0.45,
                targetDate = "2027-01-03",
                budgetKcal = 1_850.0,
                mode = WeightGoalMode.LOSS,
                pregnant = SafetyAnswer.NO,
                breastfeeding = SafetyAnswer.NO,
                eatingDisorderConcern = SafetyAnswer.NO,
                medicallyInfluencedWeight = SafetyAnswer.NO,
            )

        assertEquals(draft, GoalsEditorDraftIO.decode(GoalsEditorDraftIO.encode(draft)))
    }

    @Test
    fun corruptDraftIsIgnored() {
        assertNull(GoalsEditorDraftIO.decode("not json"))
    }

    @Test
    fun canonicalTargetDoesNotDriftWhenDisplayUnitChanges() {
        val kg = 72.5
        val pounds = MassUnit.POUND.fromKilograms(kg)

        assertEquals(kg, MassUnit.POUND.toKilograms(pounds), 0.000_001)
    }
}
