package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.engines.IntakeProjection
import app.wlo.core.engines.IntakeProjectionEngine
import app.wlo.core.engines.IntakeProjectionInput
import app.wlo.core.model.SafetyAnswer

/** Population/data applicability remains separate from calorie-floor, pace and goal-plan recommendation rules. */
public object IntakeScenario {
    public fun evaluate(
        input: IntakeProjectionInput?,
        answers: List<SafetyAnswer>,
        held: Boolean,
        targetKg: Double? = null,
        horizonOverride: Int? = null,
    ): IntakeScenarioResult =
        when {
            held -> IntakeScenarioResult(null, "Maintenance held · more complete data needed.")
            input == null -> IntakeScenarioResult(null, "Add profile details to estimate a projection.")
            input.ageYears < 18 -> IntakeScenarioResult(null, "This projection model is for adults.")
            answers.any { it == SafetyAnswer.YES } ->
                IntakeScenarioResult(null, "Projection unavailable for the health context recorded in Profile.")
            !input.intakeKcal.isFinite() || input.intakeKcal < 0 ->
                IntakeScenarioResult(null, "Enter a valid daily intake to preview.")
            else -> {
                val projection = IntakeProjectionEngine.project(input, targetKg, horizonOverride)
                IntakeScenarioResult(
                    projection,
                    if (projection ==
                        null
                    ) {
                        "This input is outside the projection model."
                    } else {
                        ""
                    },
                )
            }
        }
}

public data class IntakeScenarioResult(
    val projection: IntakeProjection?,
    val note: String,
)
