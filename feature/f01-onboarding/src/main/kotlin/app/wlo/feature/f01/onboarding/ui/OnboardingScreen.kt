package app.wlo.feature.f01.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloMotion
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.rememberWloHaptics
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f01.onboarding.state.OnboardingEvent
import app.wlo.feature.f01.onboarding.state.OnboardingStep
import app.wlo.feature.f01.onboarding.state.OnboardingUiState
import app.wlo.feature.f01.onboarding.state.OnboardingViewModel

/**
 * The F01 first-run wizard (F01 §3 step map, flow 07): eight slim steps, every
 * one skippable ("later", never "incomplete"), zero accounts, zero network,
 * zero permission asks. Step transitions ride [WloMotion]; transitions and
 * constraint resolutions carry kind haptics (DESIGN-SYSTEM §4–§5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    val state: OnboardingUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberWloHaptics()
    var explainForecast by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        StepHeader(
            step = state.step,
            skipped = state.skippedSteps,
            onSkip = {
                haptics.perform(WloHaptic.Tick)
                viewModel.onEvent(OnboardingEvent.Skip)
            },
        )

        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val enter = fadeIn(tween(WloMotion.DURATION_MEDIUM_MS, easing = WloMotion.EasingEnter))
                val exit = fadeOut(tween(WloMotion.DURATION_SHORT_MS, easing = WloMotion.EasingExit))
                enter togetherWith exit
            },
            label = "onboarding-step",
            modifier = Modifier.weight(1f),
        ) { step ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = WloSpacing.SCREEN)
                        .testTag("onboarding-step-$step"),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
            ) {
                Spacer(Modifier.height(WloSpacing.TIGHT))
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeStep(state)
                    OnboardingStep.GOAL -> GoalStep(state, viewModel, haptics)
                    OnboardingStep.FORECAST ->
                        ForecastStep(
                            state = state,
                            onExplain = { explainForecast = true },
                        )
                    OnboardingStep.TEMPLATE -> TemplateStep(state, viewModel, haptics)
                    OnboardingStep.ADAPT -> AdaptStep(state, viewModel, haptics)
                    OnboardingStep.PREFERENCES -> PreferencesStep(state, viewModel, haptics)
                    OnboardingStep.SCHEDULE -> ScheduleStep(state, viewModel, haptics)
                    OnboardingStep.REVIEW -> ReviewStep(state)
                }
                Spacer(Modifier.height(WloSpacing.TIGHT))
            }
        }

        FooterActions(
            state = state,
            haptics = haptics,
            onBack = { viewModel.onEvent(OnboardingEvent.Back) },
            onNext = {
                haptics.perform(WloHaptic.Settle)
                viewModel.onEvent(OnboardingEvent.Next)
            },
            onStart = {
                haptics.perform(WloHaptic.DoubleTick)
                viewModel.onEvent(OnboardingEvent.Start)
            },
        )
    }

    if (explainForecast && state.forecast != null) {
        ForecastExplainerSheet(
            state = state,
            onDismiss = { explainForecast = false },
        )
    }
}

/** Slim segmented progress + the skip link (flow 07: "later", never "incomplete"). */
@Composable
private fun StepHeader(
    step: OnboardingStep,
    skipped: Set<OnboardingStep>,
    onSkip: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WloSpacing.SCREEN, vertical = WloSpacing.CARD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        for (candidate in OnboardingStep.entries) {
            val weight = 1f
            Surface(
                modifier = Modifier.weight(weight).height(3.dp),
                shape = CircleShape,
                color =
                    when {
                        candidate == step -> MaterialTheme.colorScheme.primary
                        candidate.ordinal < step.ordinal || candidate in skipped -> wloExtendedColors.accentDim
                        else -> MaterialTheme.colorScheme.outline
                    },
                contentColor = MaterialTheme.colorScheme.primary,
            ) {}
        }
        Spacer(Modifier.padding(horizontal = WloSpacing.TIGHT))
        TextButton(onClick = onSkip, modifier = Modifier.testTag("onboarding-skip")) {
            Text(
                text = if (step == OnboardingStep.WELCOME) "skip for now" else "Later",
                style = wloType.label,
            )
        }
    }
}

/** Back / Continue / Start rail pinned to the wizard footer (one-handed reach). */
@Composable
private fun FooterActions(
    state: OnboardingUiState,
    haptics: app.wlo.core.designsystem.WloHaptics,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onStart: () -> Unit,
) {
    val atStart = state.step == OnboardingStep.WELCOME
    val atEnd = state.step == OnboardingStep.REVIEW
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WloSpacing.SCREEN, vertical = WloSpacing.CARD),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        if (state.error != null) {
            ErrorCard(state.error)
        }
        if (atEnd) {
            WloPrimaryButton(
                label = if (state.finishing) "Writing the plan" else "Start — write Plan v1",
                enabled = !state.finishing,
                modifier = Modifier.testTag("onboarding-start"),
                onClick = onStart,
            )
        } else {
            WloPrimaryButton(
                label =
                    when (state.step) {
                        OnboardingStep.WELCOME -> "Set up my plan"
                        OnboardingStep.GOAL -> "Continue — see the forecast"
                        OnboardingStep.FORECAST -> "Continue — pick a diet"
                        OnboardingStep.TEMPLATE -> "Continue"
                        OnboardingStep.ADAPT -> "Continue — a few swipes"
                        OnboardingStep.PREFERENCES -> "Continue — schedule"
                        else -> "Continue — review"
                    },
                enabled = true,
                modifier = Modifier.testTag("onboarding-next"),
                onClick = onNext,
            )
        }
        if (!atStart) {
            WloGhostButton(
                label = "Back",
                modifier = Modifier.testTag("onboarding-back"),
                onClick = {
                    haptics.perform(WloHaptic.Tick)
                    onBack()
                },
            )
        }
    }
}

/** Honest failure surface (amber — no red exists; D8 copy mapped in the state holder). */
@Composable
private fun ErrorCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = WloShape.Chip,
        color = wloExtendedColors.held.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, wloExtendedColors.held.copy(alpha = 0.5f)),
    ) {
        Text(
            text = message,
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            modifier = Modifier.padding(horizontal = WloSpacing.CARD, vertical = WloSpacing.TIGHT),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun WloPrimaryButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .fillMaxWidth()
                .height(WloSpacing.TOUCH_PRIMARY),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        shape = WloShape.Chip,
    ) {
        Text(text = label, style = wloType.title)
    }
}

@Composable
internal fun WloGhostButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .height(WloSpacing.ROW_INTERACTIVE),
        shape = WloShape.Chip,
    ) {
        Text(text = label, style = wloType.body)
    }
}
