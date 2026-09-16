package app.wlo.feature.f01.onboarding.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloHaptics
import app.wlo.core.designsystem.WloMotion
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.rememberWloHaptics
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.model.WeightGoalEligibility
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
                    OnboardingStep.UNIT -> UnitStep(state, viewModel, haptics)
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

/** Slim segmented progress + the skip action (flow 07: "later", never "incomplete"). */
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
        // Segmented wizard progress as plain tinted boxes (WLO-0031): no
        // borders, no Surface. WloProgress is the wrong atom here — it is a
        // determinate task track with a caption, not a step-you-are-on
        // indicator. Colors are the tokens: current = primary, passed or
        // skipped = accentDim, ahead = outline.
        for (candidate in OnboardingStep.entries) {
            val segmentColor =
                when {
                    candidate == step -> MaterialTheme.colorScheme.primary
                    candidate.ordinal < step.ordinal || candidate in skipped -> wloExtendedColors.accentDim
                    else -> MaterialTheme.colorScheme.outline
                }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(color = segmentColor, shape = CircleShape),
            )
        }
        WloSecondaryButton(
            label = if (step == OnboardingStep.WELCOME) "Skip for now" else "Later",
            onClick = onSkip,
            modifier = Modifier.testTag("onboarding-skip"),
        )
    }
}

/** Back / Continue / Start rail pinned to the wizard footer (one-handed reach). */
@Composable
private fun FooterActions(
    state: OnboardingUiState,
    haptics: WloHaptics,
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
        state.error?.let { message ->
            WloBanner(text = message, tone = WloBannerTone.Warning)
        }
        if (atEnd) {
            WloBanner(
                text = "${state.goalSafetyCopy.title}. ${state.goalSafetyCopy.body}",
                tone =
                    if (state.goalEligibility is WeightGoalEligibility.Eligible) {
                        WloBannerTone.Info
                    } else {
                        WloBannerTone.Warning
                    },
                modifier = Modifier.testTag("onboarding-review-goal-safety"),
            )
            WloButton(
                label = if (state.finishing) "Writing the plan" else "Start",
                enabled = !state.finishing && state.goalEligibility is WeightGoalEligibility.Eligible,
                modifier = Modifier.fillMaxWidth().testTag("onboarding-start"),
                onClick = onStart,
            )
        } else {
            WloButton(
                label =
                    when (state.step) {
                        OnboardingStep.WELCOME -> "Set up my plan"
                        OnboardingStep.UNIT -> "Continue — set your goal"
                        OnboardingStep.GOAL -> "Continue — see the forecast"
                        OnboardingStep.FORECAST -> "Continue — pick a diet"
                        OnboardingStep.TEMPLATE -> "Continue"
                        OnboardingStep.ADAPT -> "Continue — a few swipes"
                        OnboardingStep.PREFERENCES -> "Continue — schedule"
                        else -> "Continue — review"
                    },
                enabled = true,
                modifier = Modifier.fillMaxWidth().testTag("onboarding-next"),
                onClick = onNext,
            )
        }
        if (!atStart) {
            WloSecondaryButton(
                label = "Back",
                modifier = Modifier.fillMaxWidth().testTag("onboarding-back"),
                onClick = {
                    haptics.perform(WloHaptic.Tick)
                    onBack()
                },
            )
        }
    }
}
