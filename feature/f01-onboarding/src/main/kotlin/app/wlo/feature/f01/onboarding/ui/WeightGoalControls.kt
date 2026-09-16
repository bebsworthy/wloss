package app.wlo.feature.f01.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.SafetyAnswer
import app.wlo.feature.f01.onboarding.state.GoalSafetyQuestion

/** Shared first-run/Settings safety controls; copy and answer semantics stay identical. */
@Composable
internal fun WeightGoalSafetyControls(
    pregnant: SafetyAnswer,
    breastfeeding: SafetyAnswer,
    eatingDisorderConcern: SafetyAnswer,
    medicallyInfluencedWeight: SafetyAnswer,
    onAnswer: (GoalSafetyQuestion, SafetyAnswer) -> Unit,
    testTagPrefix: String,
) {
    Text(
        text =
            "These answers only decide whether WLO's generic goal math applies. " +
                "They are not medical advice.",
        style = wloType.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SafetyAnswerControl(
        "Pregnant",
        pregnant,
        { onAnswer(GoalSafetyQuestion.PREGNANT, it) },
        "$testTagPrefix-pregnant",
    )
    SafetyAnswerControl(
        "Breastfeeding",
        breastfeeding,
        { onAnswer(GoalSafetyQuestion.BREASTFEEDING, it) },
        "$testTagPrefix-breastfeeding",
    )
    SafetyAnswerControl(
        "Concern about an eating disorder",
        eatingDisorderConcern,
        { onAnswer(GoalSafetyQuestion.EATING_DISORDER, it) },
        "$testTagPrefix-eating-disorder",
    )
    SafetyAnswerControl(
        "Medication or a condition is influencing weight",
        medicallyInfluencedWeight,
        { onAnswer(GoalSafetyQuestion.MEDICALLY_INFLUENCED, it) },
        "$testTagPrefix-medically-influenced",
    )
}

@Composable
private fun SafetyAnswerControl(
    label: String,
    answer: SafetyAnswer,
    onAnswer: (SafetyAnswer) -> Unit,
    testTagPrefix: String,
) {
    Text(text = label, style = wloType.body)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        SafetyAnswer.entries.forEach { option ->
            SelectChip(
                label =
                    when (option) {
                        SafetyAnswer.NOT_ANSWERED -> "Not answered"
                        SafetyAnswer.NO -> "No"
                        SafetyAnswer.YES -> "Yes"
                    },
                selected = answer == option,
                onClick = { onAnswer(option) },
                modifier = Modifier.testTag("$testTagPrefix-${option.name.lowercase()}"),
            )
        }
    }
}
