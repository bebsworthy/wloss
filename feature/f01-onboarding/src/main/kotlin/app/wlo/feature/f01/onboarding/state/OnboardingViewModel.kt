package app.wlo.feature.f01.onboarding.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.data.NewProfile
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.documents.ConstraintApplier
import app.wlo.core.documents.DietTemplate
import app.wlo.core.documents.DietTemplateApplier
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.engines.ForecastEngine
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.Sex
import app.wlo.feature.f01.onboarding.domain.FinishOnboarding
import app.wlo.feature.f01.onboarding.domain.Milestones
import app.wlo.feature.f01.onboarding.domain.OnboardingDraft
import app.wlo.feature.f01.onboarding.domain.OnboardingDraftIO
import app.wlo.feature.f01.onboarding.domain.TemplateLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

/** MVI-lite intents (one channel in, state out; effects ride state). */
public sealed interface OnboardingEvent {
    public data object Next : OnboardingEvent

    public data object Back : OnboardingEvent

    /** "Later" — advances WITHOUT marking the step done; its fields ship estimated. */
    public data object Skip : OnboardingEvent

    public data class SetSex(
        public val sex: Sex?,
    ) : OnboardingEvent

    public data class SetBirthYear(
        public val year: Int,
    ) : OnboardingEvent

    public data class SetHeightCm(
        public val heightCm: Double,
    ) : OnboardingEvent

    public data class SetCurrentWeightKg(
        public val weightKg: Double,
    ) : OnboardingEvent

    public data class SetGoalWeightKg(
        public val weightKg: Double,
    ) : OnboardingEvent

    public data class SetPacePct(
        public val pacePctPerWeek: Double,
    ) : OnboardingEvent

    public data class SetActivity(
        public val level: ActivityLevel,
    ) : OnboardingEvent

    public data class SelectTemplate(
        public val templateId: String,
    ) : OnboardingEvent

    public data class AddConstraint(
        public val text: String,
    ) : OnboardingEvent

    public data class RemoveConstraint(
        public val index: Int,
    ) : OnboardingEvent

    public data class SetQuizAllergy(
        public val item: String,
        public val on: Boolean,
    ) : OnboardingEvent

    public data class SetQuizDislike(
        public val item: String,
        public val on: Boolean,
    ) : OnboardingEvent

    public data class SetHouseholdSize(
        public val size: Int,
    ) : OnboardingEvent

    public data class SetCookingFrequency(
        public val value: String,
    ) : OnboardingEvent

    public data class SetCookingSkill(
        public val value: String,
    ) : OnboardingEvent

    public data class SetBudgetBand(
        public val value: String,
    ) : OnboardingEvent

    public data class SetSchedule(
        public val schedule: List<Double>,
    ) : OnboardingEvent

    public data object Start : OnboardingEvent

    public data object DismissError : OnboardingEvent
}

/**
 * The F01 wizard state holder (MVI-lite, ARCHITECTURE §2.4): one
 * [StateFlow] + [onEvent]; the draft persists on every state change so a kill
 * mid-flow resumes exactly where the user stood (acceptance: never corrupt —
 * a corrupt draft decodes to null and restarts cleanly at step one).
 */
public class OnboardingViewModel(
    private val library: TemplateLibrary,
    private val finisher: FinishOnboarding,
    private val documents: JsonDocumentStore,
    private val clock: ClockPort,
) : ViewModel() {
    /** The shipped library, loaded lazily at holder construction. */
    private val shipped: List<DietTemplate> =
        runCatching { library.load() }.getOrElse { emptyList() }

    private val _uiState: MutableStateFlow<OnboardingUiState> =
        MutableStateFlow(
            OnboardingUiState(
                templates = shipped,
                selectedTemplateId = shipped.firstOrNull { it.isDefault }?.id,
            ),
        )

    /** Renderable wizard state. */
    public val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        restoreDraft()
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.Next -> moveBy(1)
            OnboardingEvent.Back -> moveBy(-1)
            OnboardingEvent.Skip -> skip()
            is OnboardingEvent.SetSex -> update { it.copy(sex = event.sex) }
            is OnboardingEvent.SetBirthYear -> update { it.copy(birthYear = event.year.coerceIn(1930, 2010)) }
            is OnboardingEvent.SetHeightCm -> update { it.copy(heightCm = event.heightCm.coerceIn(120.0, 220.0)) }
            is OnboardingEvent.SetCurrentWeightKg ->
                update { state ->
                    state.copy(
                        currentWeightKg = event.weightKg.coerceIn(30.0, 300.0).round1(),
                    )
                }
            is OnboardingEvent.SetGoalWeightKg ->
                update { state ->
                    state.copy(goalWeightKg = event.weightKg.coerceIn(30.0, 300.0).round1())
                }
            is OnboardingEvent.SetPacePct ->
                update { state ->
                    // Detent snapping must not strand the user just under the
                    // wall: anything within half a step of the cap IS the cap.
                    val snapped =
                        (event.pacePctPerWeek / OnboardingUiState.PACE_STEP_PCT).roundToInt() *
                            OnboardingUiState.PACE_STEP_PCT
                    val atWall =
                        event.pacePctPerWeek >= state.paceWallPctPerWeek - OnboardingUiState.PACE_STEP_PCT / 2
                    val pace =
                        if (atWall) state.paceWallPctPerWeek else snapped.coerceAtMost(state.paceWallPctPerWeek)
                    state.copy(pacePctPerWeek = pace.round2())
                }
            is OnboardingEvent.SetActivity -> update { it.copy(activityLevel = event.level) }
            is OnboardingEvent.SelectTemplate -> update { it.copy(selectedTemplateId = event.templateId) }
            is OnboardingEvent.AddConstraint -> {
                val text = event.text.trim()
                if (text.isNotEmpty()) {
                    update { it.copy(constraints = it.constraints + text) }
                }
            }
            is OnboardingEvent.RemoveConstraint ->
                update { state ->
                    state.copy(constraints = state.constraints.filterIndexed { index, _ -> index != event.index })
                }
            is OnboardingEvent.SetQuizAllergy ->
                update { state ->
                    state.copy(
                        preferences =
                            state.preferences.copy(
                                allergies = toggle(state.preferences.allergies, event.item, event.on),
                            ),
                    )
                }
            is OnboardingEvent.SetQuizDislike ->
                update { state ->
                    state.copy(
                        preferences =
                            state.preferences.copy(
                                dislikes = toggle(state.preferences.dislikes, event.item, event.on),
                            ),
                    )
                }
            is OnboardingEvent.SetHouseholdSize ->
                update { it.copy(preferences = it.preferences.copy(householdSize = event.size.coerceIn(1, 6))) }
            is OnboardingEvent.SetCookingFrequency ->
                update { it.copy(preferences = it.preferences.copy(cookingFrequency = event.value)) }
            is OnboardingEvent.SetCookingSkill ->
                update { it.copy(preferences = it.preferences.copy(cookingSkill = event.value)) }
            is OnboardingEvent.SetBudgetBand ->
                update { it.copy(preferences = it.preferences.copy(budgetBand = event.value)) }
            is OnboardingEvent.SetSchedule -> update { it.copy(schedule = event.schedule) }
            OnboardingEvent.Start -> start()
            OnboardingEvent.DismissError -> update { it.copy(error = null) }
        }
    }

    private fun moveBy(delta: Int) {
        update { state ->
            val nextIndex = (state.step.ordinal + delta).coerceIn(0, OnboardingStep.COUNT - 1)
            state.copy(step = OnboardingStep.entries[nextIndex])
        }
    }

    private fun skip() {
        update { state ->
            val nextIndex = (state.step.ordinal + 1).coerceAtMost(OnboardingStep.COUNT - 1)
            state.copy(
                step = OnboardingStep.entries[nextIndex],
                skippedSteps = state.skippedSteps + state.step,
            )
        }
    }

    private fun update(reducer: (OnboardingUiState) -> OnboardingUiState) {
        val next = recompute(reducer(_uiState.value))
        _uiState.value = next
        persistDraft(next)
    }

    private fun start() {
        val state = _uiState.value
        if (state.finishing) return
        val template = state.selectedTemplate ?: state.templates.firstOrNull { it.isDefault } ?: return
        _uiState.value = state.copy(finishing = true, error = null)
        viewModelScope.launch {
            val zone = TimeZone.currentSystemDefault()
            val outcome =
                finisher(
                    profile =
                        NewProfile(
                            sex = state.sex,
                            birthYear = state.birthYear,
                            heightCm = state.heightCm,
                            startWeightKg = state.currentWeightKg,
                            activityLevel = state.activityLevel,
                        ),
                    template = template,
                    draftTargets = state.draftTargets ?: draftFor(state, template),
                    draftWeightKg = state.currentWeightKg,
                    timeZone = zone,
                )
            when (outcome) {
                is app.wlo.core.common.WloResult.Ok -> {
                    // Gate flips in :app (profile + complete flag); wizard content yields to the Hub.
                    _uiState.value = _uiState.value.copy(finishing = false, error = null)
                }

                is app.wlo.core.common.WloResult.Err ->
                    _uiState.value =
                        _uiState.value.copy(finishing = false, error = errorCopy(outcome.error))
            }
        }
    }

    // --- derived computation (the only place wizard math lives) -------------

    private fun recompute(state: OnboardingUiState): OnboardingUiState {
        val template = state.selectedTemplate ?: state.templates.firstOrNull { it.isDefault }
        val currentYear = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        val age = (currentYear - state.birthYear).coerceAtLeast(0)

        val tdee =
            state.currentWeightKg
                .takeIf { it > 0.0 }
                ?.let { weight ->
                    ForecastEngine.bmrMifflinStJeor(state.sex, weight, state.heightCm, age) *
                        ConstantsRegistry.activityMultiplier(state.activityLevel)
                }
        val floor = ConstantsRegistry.floorKcal(state.sex).toDouble()

        // The wall in CODE (F01 §4 fallback b): the fastest pace whose budget
        // stays above the calorie floor — the slider physically cannot pass it.
        val wall =
            tdee
                ?.takeIf { state.currentWeightKg > 0.0 }
                ?.let { t ->
                    val kcalPerWeekPerPct = state.currentWeightKg * ConstantsRegistry.KCAL_PER_KG_FAT / 7.0
                    ((t - floor) * 100.0 / kcalPerWeekPerPct)
                }?.coerceIn(0.0, OnboardingUiState.MAX_PACE_PCT)
                ?: OnboardingUiState.MAX_PACE_PCT

        val pace = state.pacePctPerWeek.coerceAtMost(wall)
        val budget =
            tdee?.let { t ->
                t - pace / 100.0 * state.currentWeightKg * ConstantsRegistry.KCAL_PER_KG_FAT / 7.0
            }

        val forecast =
            if (state.currentWeightKg > 0.0 && state.goalWeightKg < state.currentWeightKg) {
                ForecastEngine.coldStart(
                    input =
                        ForecastEngineColdStart.input(
                            sex = state.sex,
                            ageYears = age,
                            heightCm = state.heightCm,
                            startTrendKg = state.currentWeightKg,
                            goalWeightKg = state.goalWeightKg,
                            activityLevel = state.activityLevel,
                            intakeKcal = budget ?: DietTemplateApplier.DEFAULT_BUDGET_KCAL,
                            startEpochDay = clockEpochDay(),
                            startInstant = clock.now(),
                        ),
                )
            } else {
                null
            }

        val application =
            ConstraintApplier.apply(
                constraints = state.constraints,
                dailyBaseKcal = budget ?: DietTemplateApplier.DEFAULT_BUDGET_KCAL,
            )

        val draftTargets =
            template?.let {
                applyConstraintsTo(draftFor(state.copy(pacePctPerWeek = pace), it, tdee), application)
            }

        val milestones =
            forecast?.let { bands ->
                Milestones.ladder(
                    from = state.currentWeightKg,
                    to = state.goalWeightKg,
                    optimistic = bands.optimistic,
                    pessimistic = bands.pessimistic,
                    startEpochDay = clockEpochDay(),
                )
            } ?: emptyList()

        return state.copy(
            now = clock.now(),
            nowEpochDay = clockEpochDay(),
            pacePctPerWeek = pace,
            paceWallPctPerWeek = wall,
            formulaTdeeKcal = tdee,
            budgetKcal = budget,
            forecast = forecast,
            draftTargets = draftTargets,
            milestones = milestones,
            application = application,
        )
    }

    private fun draftFor(
        state: OnboardingUiState,
        template: DietTemplate,
        tdee: Double? = state.formulaTdeeKcal,
    ): TargetsDocument =
        DietTemplateApplier.toTargetsDocument(
            template = template,
            context =
                FinishOnboarding.applierContext(
                    sex = state.sex,
                    birthYear = state.birthYear,
                    heightCm = state.heightCm,
                    currentWeightKg = state.currentWeightKg.takeIf { it > 0.0 },
                    goalWeightKg = state.goalWeightKg,
                    pacePctPerWeek = state.pacePctPerWeek,
                    formulaTdeeKcal = tdee,
                ),
        )

    private fun applyConstraintsTo(
        draft: TargetsDocument,
        application: ConstraintApplier.Application,
    ): TargetsDocument = FinishOnboarding.applyConstraints(draft, application)

    private fun clockEpochDay(): Long =
        app.wlo.core.common.DayBoundary
            .epochDay(clock.now(), TimeZone.currentSystemDefault())

    // --- draft persistence ---------------------------------------------------

    private fun restoreDraft() {
        viewModelScope.launch {
            val draft = documents.readText(FinishOnboarding.DRAFT_KEY)?.let(OnboardingDraftIO::decode)
            _uiState.value =
                if (draft != null) {
                    recompute(fromDraft(draft))
                } else {
                    recompute(_uiState.value).copy(restoreAttempted = true)
                }
        }
    }

    private fun persistDraft(state: OnboardingUiState) {
        // Never persist before the restore attempt resolved, or a stale empty
        // draft could overwrite a good one on a slow first read.
        if (!state.restoreAttempted) return
        viewModelScope.launch {
            documents.writeText(FinishOnboarding.DRAFT_KEY, OnboardingDraftIO.encode(toDraft(state)))
        }
    }

    private fun toDraft(state: OnboardingUiState): OnboardingDraft =
        OnboardingDraft(
            step = state.step.name,
            sex = state.sex?.wireName,
            birthYear = state.birthYear,
            heightCm = state.heightCm,
            currentWeightKg = state.currentWeightKg,
            goalWeightKg = state.goalWeightKg,
            pacePctPerWeek = state.pacePctPerWeek,
            activityLevel = state.activityLevel.wireName,
            templateId = state.selectedTemplateId,
            constraints = state.constraints,
            preferences = state.preferences,
            schedule = state.schedule,
            startedAtEpochMs = clock.now().toEpochMilliseconds(),
        )

    private fun fromDraft(draft: OnboardingDraft): OnboardingUiState =
        OnboardingUiState(
            step = OnboardingStep.fromNameOrNull(draft.step) ?: OnboardingStep.WELCOME,
            restoreAttempted = true,
            templates = shipped,
            selectedTemplateId = draft.templateId ?: shipped.firstOrNull { it.isDefault }?.id,
            sex = Sex.fromWireName(draft.sex),
            birthYear = draft.birthYear ?: OnboardingUiState.DEFAULT_BIRTH_YEAR,
            heightCm = draft.heightCm ?: OnboardingUiState.DEFAULT_HEIGHT_CM,
            currentWeightKg = draft.currentWeightKg ?: OnboardingUiState.DEFAULT_WEIGHT_KG,
            goalWeightKg = draft.goalWeightKg ?: OnboardingUiState.DEFAULT_GOAL_KG,
            pacePctPerWeek = draft.pacePctPerWeek ?: OnboardingUiState.DEFAULT_PACE_PCT,
            activityLevel =
                draft.activityLevel
                    ?.let { ActivityLevel.fromWireName(it) }
                    ?: ActivityLevel.SEDENTARY,
            constraints = draft.constraints,
            preferences = draft.preferences,
            schedule = draft.schedule,
        )

    /** Copy mapping lives here and only here (ARCHITECTURE §2.4 error handling). */
    private fun errorCopy(error: app.wlo.core.common.AppError): String =
        when (error) {
            is app.wlo.core.common.AppError.InvalidInput ->
                "The plan asks for a slower pace to stay above the calorie floor — nudge the pace and start again."
            else -> "Nothing was written — the draft is safe here; try Start once more."
        }

    private fun toggle(
        items: List<String>,
        item: String,
        on: Boolean,
    ): List<String> = if (on) (items + item).distinct() else items - item

    private fun Double.round1(): Double = kotlin.math.round(this * 10.0) / 10.0

    private fun Double.round2(): Double = kotlin.math.round(this * 100.0) / 100.0
}

/** Local shim so the cold-start input construction reads cleanly above. */
private object ForecastEngineColdStart {
    fun input(
        sex: Sex?,
        ageYears: Int,
        heightCm: Double,
        startTrendKg: Double,
        goalWeightKg: Double,
        activityLevel: ActivityLevel,
        intakeKcal: Double,
        startEpochDay: Long,
        startInstant: kotlinx.datetime.Instant,
    ): app.wlo.core.engines.ColdStartInput =
        app.wlo.core.engines.ColdStartInput(
            sex = sex,
            ageYears = ageYears,
            heightCm = heightCm,
            startTrendKg = startTrendKg,
            goalWeightKg = goalWeightKg,
            activityLevel = activityLevel,
            intakeKcal = intakeKcal,
            startEpochDay = startEpochDay,
            startInstant = startInstant,
        )
}
