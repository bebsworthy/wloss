@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package app.wlo.feature.f01.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.wlo.core.common.LengthUnit
import app.wlo.core.common.MassUnit
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloDeltaChip
import app.wlo.core.designsystem.WloForecastBands
import app.wlo.core.designsystem.WloForecastCard
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloHaptics
import app.wlo.core.designsystem.WloIconAction
import app.wlo.core.designsystem.WloMacroDot
import app.wlo.core.designsystem.WloScheduleBarChart
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSheet
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStat
import app.wlo.core.designsystem.WloStatDivider
import app.wlo.core.designsystem.WloStatRow
import app.wlo.core.designsystem.WloTag
import app.wlo.core.designsystem.WloTemplateCard
import app.wlo.core.designsystem.formatDay
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.documents.DietTemplate
import app.wlo.core.documents.DietTemplateApplier
import app.wlo.core.engines.ForecastBands
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Provenance
import app.wlo.core.model.Sex
import app.wlo.feature.f01.onboarding.domain.Milestones
import app.wlo.feature.f01.onboarding.state.OnboardingEvent
import app.wlo.feature.f01.onboarding.state.OnboardingStep
import app.wlo.feature.f01.onboarding.state.OnboardingUiState
import app.wlo.feature.f01.onboarding.state.OnboardingViewModel
import kotlin.math.roundToInt

/**
 * The eight wizard bodies (F01 §3 step map, flow-07 mocks). All derived
 * numbers render through designsystem provenance components (D6); copy passes
 * the tone audit — "later", never "incomplete"; nothing judges (DESIGN-SYSTEM §8).
 */

@Composable
internal fun WelcomeStep(state: OnboardingUiState) {
    var showImportNote by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
        Text(text = "WLO.", style = wloType.statL)
        Text(
            text =
                "Set a goal and watch an honest forecast bloom around it — " +
                    "a plan you own, versioned like a document.",
            style = wloType.titleL,
        )
        Text(
            text = "No account, no server, no ads — every number is computed and kept on this device.",
            style = wloType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            listOf("Free, no account", "Airplane-mode safe", "Open source").forEach { chip ->
                WloTag(text = chip)
            }
        }

        WloSecondaryButton(
            label = "I'm coming from another app",
            modifier = Modifier.testTag("onboarding-import"),
            onClick = { showImportNote = !showImportNote },
        )
        if (showImportNote) {
            Text(
                text =
                    "History import travels with the data vault — start now, " +
                        "bring it in any time. Nothing waits on it.",
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
        }

        WloCard(header = { WloCardHeader(title = "The next 7 steps") }) {
            StepHintRow("Goal & pace", "two numbers · one slider")
            StepHintRow("Forecast", "three bands · an honest range")
            StepHintRow("Diet template", "${state.templates.size} cards · one pick")
            StepHintRow("A few swipes", "foods, household, cooking")
            StepHintRow("Schedule · review", "both optional, both yours")
        }
        Text(
            text = "Takes about 3 minutes · every step skippable",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
    }
}

@Composable
internal fun GoalStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    haptics: WloHaptics,
) {
    val unit = MassUnit.DEFAULT.symbol
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN)) {
        WloCard(header = { WloCardHeader(title = "About you") }) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                SelectChip(
                    label = "female",
                    selected = state.sex == Sex.FEMALE,
                    modifier = Modifier.testTag("onboarding-sex-female"),
                    onClick = {
                        viewModel.onEvent(
                            OnboardingEvent.SetSex(if (state.sex == Sex.FEMALE) null else Sex.FEMALE),
                        )
                    },
                )
                SelectChip(
                    label = "male",
                    selected = state.sex == Sex.MALE,
                    modifier = Modifier.testTag("onboarding-sex-male"),
                    onClick = {
                        viewModel.onEvent(
                            OnboardingEvent.SetSex(if (state.sex == Sex.MALE) null else Sex.MALE),
                        )
                    },
                )
                SelectChip(
                    label = "other",
                    selected = state.sex == Sex.OTHER,
                    modifier = Modifier.testTag("onboarding-sex-other"),
                    onClick = {
                        viewModel.onEvent(
                            OnboardingEvent.SetSex(if (state.sex == Sex.OTHER) null else Sex.OTHER),
                        )
                    },
                )
                SelectChip(
                    label = "not shared",
                    selected = state.sex == null,
                    modifier = Modifier.testTag("onboarding-sex-unset"),
                    onClick = { viewModel.onEvent(OnboardingEvent.SetSex(null)) },
                )
            }
            StepperRow(
                label = "born in",
                value = state.birthYear.toString(),
                testTag = "birthyear",
                onMinus = { viewModel.onEvent(OnboardingEvent.SetBirthYear(state.birthYear - 1)) },
                onPlus = { viewModel.onEvent(OnboardingEvent.SetBirthYear(state.birthYear + 1)) },
            )
            StepperRow(
                label = "height",
                value = "${state.heightCm.roundToInt()} ${LengthUnit.DEFAULT.symbol}",
                testTag = "height",
                onMinus = { viewModel.onEvent(OnboardingEvent.SetHeightCm(state.heightCm - 1.0)) },
                onPlus = { viewModel.onEvent(OnboardingEvent.SetHeightCm(state.heightCm + 1.0)) },
            )
            Text(text = "A normal day moves…", style = wloType.label, color = wloExtendedColors.textTertiary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                ActivityChoice.entries.forEach { choice ->
                    SelectChip(
                        label = choice.label,
                        selected = state.activityLevel.wireName == choice.wire,
                        modifier = Modifier.testTag("onboarding-activity-${choice.wire}"),
                        onClick = { viewModel.onEvent(OnboardingEvent.SetActivity(choice.level)) },
                    )
                }
            }
        }

        WloCard(header = { WloCardHeader(title = "Goal") }) {
            StepperRow(
                label = "current weight",
                value = "${state.currentWeightKg} $unit",
                testTag = "current-weight",
                onMinus = { viewModel.onEvent(OnboardingEvent.SetCurrentWeightKg(state.currentWeightKg - 0.5)) },
                onPlus = { viewModel.onEvent(OnboardingEvent.SetCurrentWeightKg(state.currentWeightKg + 0.5)) },
            )
            StepperRow(
                label = "target weight",
                value = "${state.goalWeightKg} $unit",
                testTag = "goal-weight",
                onMinus = { viewModel.onEvent(OnboardingEvent.SetGoalWeightKg(state.goalWeightKg - 0.5)) },
                onPlus = { viewModel.onEvent(OnboardingEvent.SetGoalWeightKg(state.goalWeightKg + 0.5)) },
            )
            WloDeltaChip(
                value =
                    DerivedValue(
                        state.goalWeightKg - state.currentWeightKg,
                        Provenance.Derived(
                            formulaVersion = FORMULA_GOAL_DELTA,
                            inputs = listOf("current=${state.currentWeightKg}", "goal=${state.goalWeightKg}"),
                        ),
                    ),
                format = { MassUnit.DEFAULT.format(it) },
                context = "to goal",
            )
        }

        WloCard(header = { WloCardHeader(title = "Pace") }) {
            if (state.paceWallPctPerWeek > 0.0) {
                Slider(
                    value = state.pacePctPerWeek.toFloat(),
                    onValueChange = { raw ->
                        val snapped =
                            (raw.toDouble() / OnboardingUiState.PACE_STEP_PCT).roundToInt() *
                                OnboardingUiState.PACE_STEP_PCT
                        viewModel.onEvent(OnboardingEvent.SetPacePct(snapped))
                        haptics.perform(WloHaptic.SegmentFrequentTick)
                    },
                    valueRange = 0f..state.paceWallPctPerWeek.toFloat(),
                    steps =
                        (
                            (state.paceWallPctPerWeek / OnboardingUiState.PACE_STEP_PCT).roundToInt() -
                                1
                        ).coerceAtLeast(0),
                    modifier = Modifier.testTag("onboarding-pace-slider"),
                )
                WloStatRow(
                    label = "rate",
                    value =
                        DerivedValue(
                            state.pacePctPerWeek,
                            Provenance.Derived(
                                formulaVersion = FORMULA_GOAL_DELTA,
                                inputs = listOf("pacePctPerWeek=${state.pacePctPerWeek}"),
                            ),
                        ),
                    format = ::formatPctPerWeek,
                )
                if (state.budgetKcal != null) {
                    val kgPerWeek = state.pacePctPerWeek / 100.0 * state.currentWeightKg
                    WloStatRow(
                        label = "≈ per week",
                        value =
                            DerivedValue(
                                kgPerWeek,
                                Provenance.Derived(
                                    formulaVersion = FORMULA_GOAL_DELTA,
                                    inputs = listOf("pace=${state.pacePctPerWeek}", "weight=${state.currentWeightKg}"),
                                ),
                            ),
                        format = { MassUnit.DEFAULT.format(it) },
                    )
                }
            } else {
                Text(
                    text = "At these stats the plan stays at maintenance.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            }
            if (state.paceAtWall && state.paceWallPctPerWeek > 0.0) {
                WloBanner(
                    text =
                        "The wall — the fastest we'll suggest at your stats. " +
                            "The plan uses a sustainable pace; dates stay an estimate, not a promise.",
                    tone = WloBannerTone.Warning,
                )
            }
        }
    }
}

@Composable
internal fun ForecastStep(
    state: OnboardingUiState,
    onExplain: () -> Unit,
) {
    val forecast = state.forecast
    if (forecast == null) {
        WloCard(header = { WloCardHeader(title = "Forecast") }) {
            Text(
                text =
                    "This plan starts at maintenance — set a target below your " +
                        "current weight and the forecast blooms here.",
                style = wloType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN)) {
        WloForecastCard(
            bands = toUiBands(state, forecast),
            goalWeight =
                DerivedValue(
                    state.goalWeightKg,
                    Provenance.Measured(at = state.now, instrument = "user-entered"),
                ),
            estimate = DerivedValue(forecast.tdeeEstimateKcal, forecast.provenance),
            formatWeight = { MassUnit.DEFAULT.format(it) },
            formatKcal = ::formatKcal,
            onExplain = onExplain,
        )
        val rates = forecast.expected.weeklyRatesKg
        if (rates.size >= 2) {
            WloCard(header = { WloCardHeader(title = "Why the curve bends") }) {
                WloStatRow(
                    label = "start of the path",
                    value =
                        DerivedValue(
                            rates.first(),
                            Provenance.Derived(
                                formulaVersion = forecast.modelVersion,
                                inputs = listOf("intake=${state.budgetKcal ?: "default"}"),
                            ),
                        ),
                    format = ::formatKgPerWeek,
                )
                WloStatDivider()
                WloStatRow(
                    label = "near the goal",
                    value =
                        DerivedValue(
                            rates.last(),
                            Provenance.Derived(
                                formulaVersion = forecast.modelVersion,
                                inputs = listOf("deceleration=bmr-falls-with-weight"),
                            ),
                        ),
                    format = ::formatKgPerWeek,
                )
                Text(
                    text = "The last kilos take longer — that's expected, not failure.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            }
        }
    }
}

@Composable
internal fun TemplateStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    haptics: WloHaptics,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
        state.templates.forEach { template ->
            WloTemplateCard(
                name = template.name,
                summary = template.summary,
                selected = template.id == state.selectedTemplateId,
                onClick = {
                    haptics.perform(WloHaptic.Tick)
                    viewModel.onEvent(OnboardingEvent.SelectTemplate(template.id))
                },
                modifier = Modifier.testTag("onboarding-template-${template.id}"),
                macroDots = macroDots(template),
                ruleChips = ruleChips(template),
            )
        }
        Text(
            text = "Templates are a starting point — edit everything.",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
    }
}

@Composable
internal fun AdaptStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    haptics: WloHaptics,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
        WloCard(header = { WloCardHeader(title = "Adjust in plain words") }) {
            Text(
                text = "Say what should change — what we can map lands in the plan; the rest is held, never guessed.",
                style = wloType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                listOf("~120 g protein", "no cooking wednesday", "household of 4").forEach { suggestion ->
                    SelectChip(
                        label = suggestion,
                        selected = false,
                        modifier = Modifier.testTag("onboarding-suggestion"),
                        onClick = { viewModel.onEvent(OnboardingEvent.AddConstraint(suggestion)) },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("onboarding-constraint-input"),
                    textStyle = wloType.body,
                    placeholder = {
                        Text(
                            "e.g. hate olives",
                            style = wloType.body,
                            color = wloExtendedColors.textTertiary,
                        )
                    },
                )
                WloButton(
                    label = "Add",
                    enabled = text.isNotBlank(),
                    modifier = Modifier.testTag("onboarding-constraint-add"),
                    onClick = {
                        haptics.perform(WloHaptic.Tick)
                        viewModel.onEvent(OnboardingEvent.AddConstraint(text))
                        text = ""
                    },
                )
            }
        }

        if (state.constraints.isNotEmpty()) {
            WloCard(header = { WloCardHeader(title = "Constraints") }) {
                state.constraints.forEachIndexed { index, constraint ->
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = WloSpacing.ROW_INTERACTIVE),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = constraint,
                            style = wloType.body,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        WloIconAction(
                            imageVector = WizardIcons.Close,
                            contentDescription = "Remove constraint",
                            onClick = { viewModel.onEvent(OnboardingEvent.RemoveConstraint(index)) },
                            modifier = Modifier.testTag("onboarding-constraint-remove-$index"),
                        )
                    }
                }
            }
        }

        WloCard(header = { WloCardHeader(title = "What landed in the plan") }) {
            if (state.constraints.isEmpty()) {
                Text(
                    text = "Nothing yet — the template stands as authored.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
            } else {
                ResolvedChips(state)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResolvedChips(state: OnboardingUiState) {
    val application = state.application
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        application.proteinGrams?.let { grams ->
            ProvenanceChip(
                value =
                    DerivedValue(
                        grams,
                        Provenance.Derived(
                            formulaVersion = FORMULA_CONSTRAINT,
                            inputs = state.constraints,
                        ),
                    ),
                format = { "$it g protein" },
            )
        }
        application.weekSchedule?.let { schedule ->
            ProvenanceChip(
                value =
                    DerivedValue(
                        "cycling week · ${schedule.map { it.roundToInt() }.distinct().joinToString("/")} kcal",
                        Provenance.Derived(
                            formulaVersion = FORMULA_CONSTRAINT,
                            inputs = state.constraints,
                        ),
                    ),
                format = { it },
            )
        }
        application.eatingWindow?.let { window ->
            ProvenanceChip(
                value =
                    DerivedValue(
                        "eating window ${window.pattern}",
                        Provenance.Derived(formulaVersion = FORMULA_CONSTRAINT, inputs = state.constraints),
                    ),
                format = { it },
            )
        }
        if (application.addedDislikes.isNotEmpty()) {
            ProvenanceChip(
                value =
                    DerivedValue(
                        "won't suggest ${application.addedDislikes.joinToString()}",
                        Provenance.Derived(formulaVersion = FORMULA_CONSTRAINT, inputs = state.constraints),
                    ),
                format = { it },
            )
        }
        if (application.addedExclusions.isNotEmpty()) {
            ProvenanceChip(
                value =
                    DerivedValue(
                        "excludes ${application.addedExclusions.joinToString()}",
                        Provenance.Derived(formulaVersion = FORMULA_CONSTRAINT, inputs = state.constraints),
                    ),
                format = { it },
            )
        }
        if (application.householdSize != null) {
            ProvenanceChip(
                value =
                    DerivedValue(
                        "cooking for ${application.householdSize}",
                        Provenance.Derived(formulaVersion = FORMULA_CONSTRAINT, inputs = state.constraints),
                    ),
                format = { it },
            )
        }
        application.unresolved.forEach { phrase ->
            ProvenanceChip(
                value =
                    DerivedValue(
                        phrase,
                        Provenance.Held(app.wlo.core.model.HoldReason.INSUFFICIENT_DATA),
                    ),
                format = { "held: “$it”" },
            )
        }
    }
}

@Composable
internal fun PreferencesStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    haptics: WloHaptics,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN)) {
        WloCard(header = { WloCardHeader(title = "Allergies — hard filters") }) {
            ChipGrid(
                items = ALLERGENS,
                selected = state.preferences.allergies.toSet(),
                tagPrefix = "onboarding-allergy",
                onToggle = { item, on ->
                    haptics.perform(WloHaptic.SegmentTick)
                    viewModel.onEvent(OnboardingEvent.SetQuizAllergy(item, on))
                },
            )
        }
        WloCard(header = { WloCardHeader(title = "Won't suggest") }) {
            ChipGrid(
                items = DISLIKES,
                selected = state.preferences.dislikes.toSet(),
                tagPrefix = "onboarding-dislike",
                onToggle = { item, on ->
                    haptics.perform(WloHaptic.SegmentTick)
                    viewModel.onEvent(OnboardingEvent.SetQuizDislike(item, on))
                },
            )
            Text(
                text = "Tune the essentials now.",
                style = wloType.caption,
                color = wloExtendedColors.textTertiary,
            )
        }
        WloCard(header = { WloCardHeader(title = "Household & kitchen") }) {
            Text(text = "Cooking for", style = wloType.label, color = wloExtendedColors.textTertiary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                for (size in 1..5) {
                    SelectChip(
                        label = if (size == 5) "5+" else size.toString(),
                        selected = state.preferences.householdSize == size,
                        modifier = Modifier.testTag("onboarding-household-$size"),
                        onClick = {
                            haptics.perform(WloHaptic.SegmentTick)
                            viewModel.onEvent(OnboardingEvent.SetHouseholdSize(size))
                        },
                    )
                }
            }
            ChoiceGrid(
                items = COOKING_FREQUENCY,
                selected = setOf(state.preferences.cookingFrequency),
                tagPrefix = "onboarding-cooking-freq",
                onToggle = { wire ->
                    haptics.perform(WloHaptic.SegmentTick)
                    viewModel.onEvent(OnboardingEvent.SetCookingFrequency(wire))
                },
            )
            ChoiceGrid(
                items = COOKING_SKILL,
                selected = setOf(state.preferences.cookingSkill),
                tagPrefix = "onboarding-cooking-skill",
                onToggle = { wire ->
                    haptics.perform(WloHaptic.SegmentTick)
                    viewModel.onEvent(OnboardingEvent.SetCookingSkill(wire))
                },
            )
            ChoiceGrid(
                items = BUDGET_BAND,
                selected = setOf(state.preferences.budgetBand),
                tagPrefix = "onboarding-budget",
                onToggle = { wire ->
                    haptics.perform(WloHaptic.SegmentTick)
                    viewModel.onEvent(OnboardingEvent.SetBudgetBand(wire))
                },
            )
        }
    }
}

@Composable
internal fun ScheduleStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    haptics: WloHaptics,
) {
    val flat = state.budgetKcal ?: DietTemplateApplier.DEFAULT_BUDGET_KCAL
    val schedule = state.schedule ?: List(7) { flat }
    val floor = ConstantsRegistry.floorKcal(state.sex).toDouble()
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
        WloCard(header = { WloCardHeader(title = "Week shape") }) {
            WloScheduleBarChart(
                scheduleKcal = schedule,
                flatKcal = flat,
                floorKcal = floor,
                pinnedWeeklyTotal =
                    DerivedValue(
                        schedule.sum(),
                        Provenance.Derived(
                            formulaVersion = FORMULA_SCHEDULE_PIN,
                            inputs = schedule.map { it.roundToInt().toString() },
                        ),
                    ),
                formatKcal = ::formatKcal,
                onScheduleChange = { next -> viewModel.onEvent(OnboardingEvent.SetSchedule(next)) },
                onDetent = { haptics.perform(WloHaptic.SegmentFrequentTick) },
                modifier = Modifier.testTag("onboarding-schedule-chart"),
            )
            Text(
                text = "Drag a bar — same week, different shape.",
                style = wloType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text =
                "Optional — flat by default (${formatKcal(flat)} × 7). " +
                    "“Later” keeps it flat; the studio re-shapes it any week.",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
    }
}

@Composable
internal fun ReviewStep(state: OnboardingUiState) {
    val template = state.selectedTemplate
    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN)) {
        WloCard(header = { WloCardHeader(title = "Plan preview") }) {
            WloStatRow(
                label = "budget",
                value =
                    DerivedValue(
                        state.budgetKcal ?: DietTemplateApplier.DEFAULT_BUDGET_KCAL,
                        Provenance.Estimated(
                            at = state.now,
                            method = "formula estimate (mifflin-st-jeor × activity − pace deficit)",
                        ),
                    ),
                format = ::formatKcal,
            )
            template?.let {
                WloStatDivider()
                WloStatRow(
                    label = "macros · ${it.name}",
                    value =
                        DerivedValue(
                            macroLine(it),
                            Provenance.Derived(formulaVersion = FORMULA_TEMPLATE, inputs = listOf(it.id)),
                        ),
                    format = { it },
                )
            }
            WloStatDivider()
            WloStatRow(
                label = "pace",
                value =
                    DerivedValue(
                        state.pacePctPerWeek,
                        Provenance.Derived(
                            formulaVersion = FORMULA_GOAL_DELTA,
                            inputs = listOf("pace=${state.pacePctPerWeek}"),
                        ),
                    ),
                format = { "$it %/wk" },
            )
            WloStatDivider()
            WloStatRow(
                label = "exclusions",
                value =
                    DerivedValue(
                        exclusionLine(state),
                        Provenance.Derived(
                            formulaVersion = FORMULA_TEMPLATE,
                            inputs = state.preferences.allergies + state.preferences.dislikes,
                        ),
                    ),
                format = { it },
            )
            WloStatDivider()
            WloStatRow(
                label = "week shape",
                value =
                    DerivedValue(
                        if (state.schedule == null) "flat" else "cycling",
                        Provenance.Derived(
                            formulaVersion = FORMULA_SCHEDULE_PIN,
                            inputs = listOf("touched=${state.schedule != null}"),
                        ),
                    ),
                format = { it },
            )
            if (OnboardingStep.ADAPT in state.skippedSteps) {
                WloStatDivider()
                WloStatRow(
                    label = "adjustments",
                    value =
                        DerivedValue(
                            "skipped — editable later",
                            Provenance.Held(app.wlo.core.model.HoldReason.INSUFFICIENT_DATA),
                        ),
                    format = { it },
                )
            }
        }

        if (state.milestones.isNotEmpty()) {
            WloCard(header = { WloCardHeader(title = "Milestones") }) {
                Text(
                    text = "Ranges, never a single promised date.",
                    style = wloType.caption,
                    color = wloExtendedColors.textTertiary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
                ) {
                    WloTag(text = "You are here")
                    Text(
                        text = MassUnit.DEFAULT.format(state.currentWeightKg),
                        style = wloType.receipt,
                        color = wloExtendedColors.textTertiary,
                    )
                }
                state.milestones.forEach { rung ->
                    RungRow(rung)
                }
            }
        }
    }
}

@Composable
private fun RungRow(rung: Milestones.Rung) {
    Row(
        modifier = Modifier.fillMaxWidth().height(WloSpacing.ROW_MIN),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(
            text =
                if (rung.isGoal) {
                    "${MassUnit.DEFAULT.format(rung.weightKg)} — goal"
                } else {
                    MassUnit.DEFAULT.format(rung.weightKg)
                },
            style = wloType.statS,
            color = if (rung.isGoal) MaterialTheme.colorScheme.primary else Color.Unspecified,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
                rung.rangeEpochDays
                    ?.let { (fast, slow) -> "~ ${formatDay(fast)} – ${formatDay(slow)}" }
                    ?: "Beyond the horizon, for now.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )
    }
}

/** The forecast "how we got here" sheet (lean M2): versions + inputs + locality. */
@Composable
internal fun ForecastExplainerSheet(
    state: OnboardingUiState,
    onDismiss: () -> Unit,
) {
    val forecast = state.forecast ?: return
    WloSheet(onDismissRequest = onDismiss) {
        Text(text = "How we got here", style = wloType.titleL)
        WloStat(
            label = "estimated burn, today",
            value = DerivedValue(forecast.tdeeEstimateKcal, forecast.provenance),
            format = ::formatKcal,
        )
        WloCard {
            ExplainerRow("formula", forecast.modelVersion)
            ExplainerRow("burn formula", forecast.bmrVersion)
            ExplainerRow("energy rule", "${ConstantsRegistry.KCAL_PER_KG_FAT.toInt()} kcal per kg")
            ExplainerRow("start", MassUnit.DEFAULT.format(state.currentWeightKg))
            ExplainerRow("goal", MassUnit.DEFAULT.format(state.goalWeightKg))
            ExplainerRow("planned intake", formatKcal(state.budgetKcal ?: DietTemplateApplier.DEFAULT_BUDGET_KCAL))
            ExplainerRow("a normal day", state.activityLevel.wireName)
        }
        Text(
            text =
                "Computed on your device. The bands are wide because we know " +
                    "nothing about you yet — they sharpen as you log.",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
    }
}

@Composable
private fun ExplainerRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(WloSpacing.ROW_MIN),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = wloType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = wloType.receipt)
    }
}

// --- wizard glyphs ----------------------------------------------------------
// Hand-built marks, the WloIcons idiom from :core:designsystem (no icon-font
// dependency): painted black so Icon(tint) recolors them. f01 needs only the
// stepper and remove marks, which WloIcons does not carry yet.

private object WizardIcons {
    /** Paint source for all glyph paths; `Icon(tint = ...)` recolors at render. */
    private val Black: SolidColor = SolidColor(Color.Black)

    /** Stepper decrease. */
    val Minus: ImageVector =
        mark("WloMinus") {
            path(
                stroke = Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }

    /** Stepper increase. */
    val Plus: ImageVector =
        mark("WloPlus") {
            path(
                stroke = Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }

    /** Remove a constraint. */
    val Close: ImageVector =
        mark("WloClose") {
            path(
                stroke = Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }

    private inline fun mark(
        name: String,
        builder: ImageVector.Builder.() -> ImageVector.Builder,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).builder()
            .build()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceGrid(
    items: List<QuizChoice>,
    selected: Set<String>,
    tagPrefix: String,
    onToggle: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        items.forEach { choice ->
            SelectChip(
                label = choice.label,
                selected = choice.wire in selected,
                modifier = Modifier.testTag("$tagPrefix-${choice.wire}"),
                onClick = { onToggle(choice.wire) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGrid(
    items: List<String>,
    selected: Set<String>,
    tagPrefix: String,
    onToggle: (String, Boolean) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        items.forEach { item ->
            SelectChip(
                label = item,
                selected = item in selected,
                modifier = Modifier.testTag("$tagPrefix-$item"),
                onClick = { onToggle(item, item !in selected) },
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    testTag: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = WloSpacing.ROW_INTERACTIVE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(
            text = label,
            style = wloType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = wloType.statS, modifier = Modifier.testTag("onboarding-$testTag"))
        WloIconAction(
            imageVector = WizardIcons.Minus,
            contentDescription = "Decrease",
            onClick = onMinus,
            modifier = Modifier.testTag("onboarding-$testTag-minus"),
        )
        WloIconAction(
            imageVector = WizardIcons.Plus,
            contentDescription = "Increase",
            onClick = onPlus,
            modifier = Modifier.testTag("onboarding-$testTag-plus"),
        )
    }
}

@Composable
private fun StepHintRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(WloSpacing.ROW_MIN),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = wloType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = wloType.caption, color = wloExtendedColors.textTertiary)
    }
}

// --- pure helpers -----------------------------------------------------------

private fun toUiBands(
    state: OnboardingUiState,
    forecast: ForecastBands,
): WloForecastBands =
    WloForecastBands(
        startWeightKg = state.currentWeightKg,
        goalWeightKg = state.goalWeightKg,
        startEpochDay = state.nowEpochDay,
        optimisticKg = forecast.optimistic.trajectoryKg,
        expectedKg = forecast.expected.trajectoryKg,
        pessimisticKg = forecast.pessimistic.trajectoryKg,
        optimisticFinishEpochDay = forecast.optimistic.finishEpochDay,
        expectedFinishEpochDay = forecast.expected.finishEpochDay,
        pessimisticFinishEpochDay = forecast.pessimistic.finishEpochDay,
    )

@Composable
private fun macroDots(template: DietTemplate): List<WloMacroDot> {
    val colors = wloExtendedColors.series
    val split = template.macroSplit ?: return emptyList()
    return listOf(
        WloMacroDot("P ${split.proteinPct.roundToInt()} %", colors[3]),
        WloMacroDot("C ${split.carbPct.roundToInt()} %", colors[0]),
        WloMacroDot("F ${split.fatPct.roundToInt()} %", colors[1]),
    )
}

private fun ruleChips(template: DietTemplate): List<String> {
    val chips = mutableListOf<String>()
    template.carbCapG?.let { chips += "carbs ≤ ${it.roundToInt()} g" }
    template.proteinFloorG?.let { chips += "protein floor ${it.roundToInt()} g" }
    template.eatingWindow?.let { chips += "window ${it.pattern}" }
    if (template.cadence == app.wlo.core.documents.Cadence.WEEKLY) chips += "weekly cadence"
    return chips
}

private fun macroLine(template: DietTemplate): String {
    val split = template.macroSplit
    return if (split != null) {
        "${split.proteinPct.roundToInt()}P / ${split.carbPct.roundToInt()}C / ${split.fatPct.roundToInt()}F"
    } else {
        template.macroPreset ?: "custom"
    }
}

private fun exclusionLine(state: OnboardingUiState): String {
    val items = state.preferences.allergies + state.preferences.dislikes + state.application.addedExclusions
    return if (items.isEmpty()) "none yet" else items.distinct().joinToString(" · ")
}

internal fun formatKcal(value: Double): String = "%,d kcal".format(value.roundToInt())

internal fun formatKgPerWeek(value: Double): String = "%.2f kg/wk".format(value)

internal fun formatPctPerWeek(value: Double): String = "%.2f %%/wk".format(value)

internal enum class ActivityChoice(
    val label: String,
    val wire: String,
    val level: ActivityLevel,
) {
    SEDENTARY("mostly seated", "sedentary", ActivityLevel.SEDENTARY),
    LIGHT("light days", "light", ActivityLevel.LIGHT),
    MODERATE("on my feet", "moderate", ActivityLevel.MODERATE),
    ACTIVE("regularly active", "active", ActivityLevel.ACTIVE),
    VERY_ACTIVE("physical work or sport daily", "very-active", ActivityLevel.VERY_ACTIVE),
}

private val ALLERGENS: List<String> =
    listOf("gluten", "shellfish", "fish", "dairy", "peanut", "tree nut", "soy", "sesame", "mustard")

private val DISLIKES: List<String> =
    listOf("olives", "cottage cheese", "mushrooms", "cilantro", "liver", "tofu")

/** Quiz choice with its user label + the wire value written to [PreferenceProfile]. */
private data class QuizChoice(
    val label: String,
    val wire: String,
)

private val COOKING_FREQUENCY: List<QuizChoice> =
    listOf(
        QuizChoice("never", "never"),
        QuizChoice("a few a week", "few-week"),
        QuizChoice("most days", "most-days"),
        QuizChoice("daily", "daily"),
    )

private val COOKING_SKILL: List<QuizChoice> =
    listOf(
        QuizChoice("beginner", "beginner"),
        QuizChoice("home cook", "home-cook"),
        QuizChoice("confident", "confident"),
        QuizChoice("advanced", "advanced"),
    )

private val BUDGET_BAND: List<QuizChoice> =
    listOf(
        QuizChoice("tight", "tight"),
        QuizChoice("moderate", "moderate"),
        QuizChoice("relaxed", "relaxed"),
    )

private const val FORMULA_GOAL_DELTA: String = "f01/goal-draft-v1"
private const val FORMULA_TEMPLATE: String = "f01/template-v1"
private const val FORMULA_CONSTRAINT: String = "constraints/deterministic-v1"
private const val FORMULA_SCHEDULE_PIN: String = "schedule/pinned-total-v1"
