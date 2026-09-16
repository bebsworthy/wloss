package app.wlo.feature.f01.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.common.MassUnit
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalMode
import app.wlo.feature.f01.onboarding.domain.FirstWeightSource
import app.wlo.feature.f01.onboarding.domain.WeightFirstStep
import app.wlo.feature.f01.onboarding.state.WeightFirstOnboardingViewModel

@Composable
public fun WeightFirstOnboardingScreen(
    viewModel: WeightFirstOnboardingViewModel,
    onComplete: (FirstWeightSource) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (state.completed) onComplete(state.draft.weightSource)
    }
    if (state.loading) return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .testTag("weight-first-onboarding"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        WloScreenTitle(title = "Start with weight")
        Text("${state.draft.step.ordinal + 1} of ${WeightFirstStep.entries.size}")
        when (state.draft.step) {
            WeightFirstStep.WELCOME -> WelcomeStep()
            WeightFirstStep.UNIT -> UnitStep(state.draft.unit, viewModel::chooseUnit)
            WeightFirstStep.GOAL ->
                GoalStep(
                    mode = state.draft.goalMode,
                    unit = state.draft.unit ?: MassUnit.KILOGRAM,
                    target = state.targetText,
                    pace = state.paceText,
                    eligibility = state.eligibility,
                    onMode = viewModel::chooseGoal,
                    onTarget = viewModel::targetChanged,
                    onPace = viewModel::paceChanged,
                )
            WeightFirstStep.WEIGHT ->
                WeightStep(
                    source = state.draft.weightSource,
                    unit = state.draft.unit ?: MassUnit.KILOGRAM,
                    weight = state.weightText,
                    onSource = viewModel::chooseSource,
                    onWeight = viewModel::weightChanged,
                )
        }
        state.error?.let { WloBanner(it, WloBannerTone.Warning) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
            if (state.draft.step != WeightFirstStep.WELCOME) {
                OutlinedButton(onClick = viewModel::back, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            if (state.draft.step == WeightFirstStep.WEIGHT) {
                Button(onClick = viewModel::finish, modifier = Modifier.weight(1f).testTag("weight-first-finish")) {
                    Text("Open Weight")
                }
            } else {
                Button(
                    onClick = viewModel::next,
                    enabled = state.draft.step != WeightFirstStep.UNIT || state.draft.unit != null,
                    modifier = Modifier.weight(1f).testTag("weight-first-next"),
                ) { Text(if (state.draft.step == WeightFirstStep.GOAL) "Continue or skip" else "Continue") }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text("A private, local-first weight tracker. No account, ads, or judgment.")
    ListItem(
        headlineContent = { Text("Your data stays on this device") },
        supportingContent = { Text("You can export it whenever you want.") },
    )
    Text("WLO provides tracking and product estimates, not medical advice.")
}

@Composable
private fun UnitStep(
    unit: MassUnit?,
    onUnit: (MassUnit) -> Unit,
) {
    Text("Choose once. You can switch later in Settings.")
    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
        MassUnit.entries.forEach { option ->
            FilterChip(
                selected = unit == option,
                onClick = { onUnit(option) },
                label = { Text(if (option == MassUnit.KILOGRAM) "Kilograms (kg)" else "Pounds (lb)") },
                modifier = Modifier.testTag("weight-first-unit-${option.symbol}"),
            )
        }
    }
}

@Composable
private fun GoalStep(
    mode: WeightGoalMode?,
    unit: MassUnit,
    target: String,
    pace: String,
    eligibility: WeightGoalEligibility,
    onMode: (WeightGoalMode?) -> Unit,
    onTarget: (String) -> Unit,
    onPace: (String) -> Unit,
) {
    Text("A goal is optional. Tracking works without one.")
    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
        listOf(null, WeightGoalMode.LOSS, WeightGoalMode.MAINTENANCE, WeightGoalMode.GAIN).forEach { option ->
            FilterChip(
                selected = mode == option,
                onClick = { onMode(option) },
                label = { Text(option?.name?.lowercase()?.replaceFirstChar(Char::uppercase) ?: "No goal") },
            )
        }
    }
    if (mode != null) {
        OutlinedTextField(
            value = target,
            onValueChange = onTarget,
            label = { Text("Target (${unit.symbol})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("weight-first-target"),
        )
        if (mode != WeightGoalMode.MAINTENANCE) {
            OutlinedTextField(
                value = pace,
                onValueChange = onPace,
                label = { Text("Pace (% per week)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("weight-first-pace"),
            )
        }
        if (eligibility !is WeightGoalEligibility.Eligible) {
            Text("Goal dates stay held until the required profile and safety information is complete.")
        }
        if (mode == WeightGoalMode.GAIN) {
            Text("Gain goals are supported; a gain-date forecast is not available yet.")
        }
    }
}

@Composable
private fun WeightStep(
    source: FirstWeightSource,
    unit: MassUnit,
    weight: String,
    onSource: (FirstWeightSource) -> Unit,
    onWeight: (String) -> Unit,
) {
    Text("Add a first reading, import, or skip. No permission is requested until you use an import.")
    FirstWeightSource.entries.forEach { option ->
        val label =
            when (option) {
                FirstWeightSource.NONE -> "Skip for now"
                FirstWeightSource.MANUAL -> "Enter manually"
                FirstWeightSource.FILE_IMPORT -> "Import a file"
                FirstWeightSource.HEALTH_CONNECT -> "Health Connect (setup later)"
            }
        FilterChip(
            selected = source == option,
            onClick = { onSource(option) },
            label = { Text(label) },
            modifier = Modifier.testTag("weight-first-source-${option.name.lowercase()}"),
        )
    }
    if (source == FirstWeightSource.MANUAL) {
        OutlinedTextField(
            value = weight,
            onValueChange = onWeight,
            label = { Text("First weight (${unit.symbol})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("weight-first-weight"),
        )
    }
}
