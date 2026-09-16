package app.wlo.feature.f01.onboarding

import app.wlo.core.common.MassUnit
import app.wlo.core.documents.Energy
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafetyInput
import app.wlo.feature.f01.onboarding.domain.GoalSaveJournal
import app.wlo.feature.f01.onboarding.domain.GoalSaveJournalIO
import app.wlo.feature.f01.onboarding.domain.GoalsEditorDraft
import app.wlo.feature.f01.onboarding.domain.GoalsEditorDraftIO
import app.wlo.feature.f01.onboarding.state.restoredGoalWeightText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GoalsEditorDraftTest {
    @Test
    fun `imperial draft is converted exactly once when restored`() {
        assertEquals("160.0", restoredGoalWeightText("160", MassUnit.POUND, MassUnit.POUND))
        assertEquals("72.6", restoredGoalWeightText("160", MassUnit.POUND, MassUnit.KILOGRAM))
        assertEquals("160.1", restoredGoalWeightText("72.62", MassUnit.KILOGRAM, MassUnit.POUND))
    }

    @Test
    fun canonicalDraftRoundTripsAcrossProcessRecreation() {
        val draft =
            GoalsEditorDraft(
                profileId = "profile",
                baseVersion = 4,
                massUnit = MassUnit.KILOGRAM,
                targetWeightText = "72,5",
                paceText = "0.45",
                targetDate = "2027-01-03",
                budgetText = "1850",
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
    fun pendingGoalSaveJournalRetainsCommittedVersionAndSafetyMetadata() {
        val journal =
            GoalSaveJournal(
                operationId = "operation",
                profileId = "profile",
                baseVersion = null,
                document =
                    TargetsDocument(
                        goal = Goal(80.5, 0.5),
                        energy = Energy(budgetKcal = null, floorKcal = 1_200.0),
                        macros = Macros(MacroSplit.Preset("balanced")),
                    ),
                safetyInput =
                    WeightGoalSafetyInput(
                        ageYears = 36,
                        mode = WeightGoalMode.LOSS,
                        currentWeightKg = 90.0,
                        targetWeightKg = 80.5,
                    ),
                committedVersion = 1,
            )

        assertEquals(journal, GoalSaveJournalIO.decode(GoalSaveJournalIO.encode(journal)))
    }

    @Test
    fun canonicalTargetDoesNotDriftWhenDisplayUnitChanges() {
        val kg = 72.5
        val pounds = MassUnit.POUND.fromKilograms(kg)

        assertEquals(kg, MassUnit.POUND.toKilograms(pounds), 0.000_001)
    }
}
