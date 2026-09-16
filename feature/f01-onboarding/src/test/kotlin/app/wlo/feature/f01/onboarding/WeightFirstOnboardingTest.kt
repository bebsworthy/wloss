package app.wlo.feature.f01.onboarding

import app.wlo.core.common.MassUnit
import app.wlo.core.documents.DocumentCodec
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalHoldReason
import app.wlo.core.model.WeightGoalMode
import app.wlo.feature.f01.onboarding.domain.FirstWeightSource
import app.wlo.feature.f01.onboarding.domain.WeightFirstDraft
import app.wlo.feature.f01.onboarding.domain.WeightFirstOnboardingStore
import app.wlo.feature.f01.onboarding.domain.WeightFirstStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WeightFirstOnboardingTest {
    @Test
    fun `draft round trip preserves resumable step unit and optional path`() {
        val draft =
            WeightFirstDraft(
                step = WeightFirstStep.WEIGHT,
                unit = MassUnit.POUND,
                goalMode = WeightGoalMode.GAIN,
                targetWeightKg = 80.0,
                pacePctPerWeek = 0.25,
                weightSource = FirstWeightSource.FILE_IMPORT,
            )

        val encoded = DocumentCodec.json.encodeToString(WeightFirstDraft.serializer(), draft)

        assertEquals(draft, WeightFirstOnboardingStore.decodeDraft(encoded))
    }

    @Test
    fun `optional goal remains held without invented profile or screening facts`() {
        val result =
            WeightFirstDraft(
                unit = MassUnit.KILOGRAM,
                goalMode = WeightGoalMode.LOSS,
                targetWeightKg = 70.0,
                pacePctPerWeek = 0.5,
            ).eligibility()

        val held = assertIs<WeightGoalEligibility.Held>(result)
        assertTrue(WeightGoalHoldReason.CURRENT_WEIGHT_REQUIRED in held.reasons)
        assertTrue(WeightGoalHoldReason.SCREENING_INCOMPLETE in held.reasons)
    }
}
