package app.wlo.feature.f01.onboarding.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.TargetsRepository
import app.wlo.core.data.TargetsWriteError
import app.wlo.core.data.TargetsWriteOutcome
import app.wlo.core.data.TargetsWriters
import app.wlo.core.data.WeighInRepository
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.documents.Cadence
import app.wlo.core.documents.FiberTarget
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.TargetsRecord
import app.wlo.core.engines.EnergyDay
import app.wlo.core.engines.EnergyEngine
import app.wlo.core.engines.EngineState
import app.wlo.core.engines.ForecastEngine
import app.wlo.core.engines.ForecastMode
import app.wlo.core.engines.IntakeProjection
import app.wlo.core.engines.IntakeProjectionInput
import app.wlo.core.engines.MeasuredInput
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Profile
import app.wlo.core.model.Provenance
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafetyCopyPolicy
import app.wlo.feature.f01.onboarding.domain.IntakeEntryMode
import app.wlo.feature.f01.onboarding.domain.IntakeScenario
import app.wlo.feature.f01.onboarding.domain.IntakeSuggestion
import app.wlo.feature.f01.onboarding.domain.IntakeTargetDraft
import app.wlo.feature.f01.onboarding.domain.ProfileHealthContextStore
import app.wlo.feature.f01.onboarding.domain.WeightGoalPreview
import app.wlo.feature.f01.onboarding.domain.WeightGoalPreviewInput
import app.wlo.feature.f01.onboarding.domain.WeightGoalPreviewResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

public enum class SuggestionStatus { READY, HEALTH_REQUIRED, PROFILE_REQUIRED, UNAVAILABLE }

public enum class IntakeOrigin { SAVED, SUGGESTED, MANUAL }

public data class IntakeTargetState(
    val startDay: Long = 0,
    val scheduleEdited: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val profile: Profile? = null,
    val record: TargetsRecord? = null,
    val weight: Double? = null,
    val origin: IntakeOrigin = IntakeOrigin.SAVED,
    val maintenance: Double? = null,
    val maintenanceValue: DerivedValue<Double>? = null,
    val measured: Boolean = false,
    val mode: IntakeEntryMode = IntakeEntryMode.ADJUSTMENT,
    val text: String = "",
    val healthNeedsReview: Boolean = false,
    val answers: List<SafetyAnswer> = List(4) { SafetyAnswer.NOT_ANSWERED },
    val forecast: IntakeProjection? = null,
    val projectionNote: String = "Add profile details to estimate maintenance.",
    val suggested: Double? = null,
    val suggestionStatus: SuggestionStatus = SuggestionStatus.PROFILE_REQUIRED,
    val suggestionNote: String = "Complete your profile for a suggestion.",
    val weekly: Boolean = false,
    val weekendExtra: String = "200",
    val macrosEdited: Boolean = false,
    val protein: String = "30",
    val carbs: String = "40",
    val fat: String = "30",
    val fiber: String = "25",
) {
    val intake: Double?
        get() {
            val amount = text.toDoubleOrNull() ?: return null
            val value = if (mode == IntakeEntryMode.INTAKE) amount else maintenance?.plus(amount)
            return value?.takeIf { it.isFinite() }
        }
}

/** Transactional food-intake draft. Clinical eligibility gates previews, never manual saves. */
public class IntakeTargetViewModel(
    private val profiles: ProfileRepository,
    private val targets: TargetsRepository,
    private val writers: TargetsWriters,
    private val weighIns: WeighInRepository,
    private val days: DayProjectionRepository,
    private val documents: JsonDocumentStore,
    private val clock: ClockPort,
) : ViewModel() {
    private val state = MutableStateFlow(IntakeTargetState())
    public val uiState: StateFlow<IntakeTargetState> = state
    private var quality: EngineState = EngineState.Developing(0)
    private var measuredTdee: Double? = null
    private var edited = false
    private var reloadPending = false
    private var dragHorizon: Int? = null
    private var loadingJob: kotlinx.coroutines.Job? = null
    private val zone = TimeZone.currentSystemDefault()
    private val today: Long get() = DayBoundary.epochDay(clock.now(), zone)

    init {
        reload()
        viewModelScope.launch {
            profiles.observeActive().distinctUntilChanged().collectLatest { result ->
                val profile = result.getOrNull() ?: return@collectLatest
                documents.observeText(ProfileHealthContextStore.key(profile.id)).collect {
                    reload()
                }
            }
        }
    }

    public fun reload() {
        if (state.value.saving) return
        if (loadingJob?.isActive == true) {
            reloadPending = true
            return
        }
        loadingJob =
            viewModelScope.launch {
                try {
                    val profile = profiles.active().required() ?: error("Set up a profile first.")
                    val record = targets.current(profile.id).required() ?: error("Set a weight goal first.")
                    val weight =
                        weighIns
                            .currentTrend(profile.id, today)
                            .required()
                            ?.current
                            ?.value ?: profile.startWeightKg
                    val energyDays =
                        days
                            .range(profile.id, today - ConstantsRegistry.QUALITY_WINDOW_DAYS + 1, today)
                            .required()
                            .map {
                                EnergyDay(
                                    epochDay = it.dayEpochDay,
                                    intakeKcal = it.intakeKcal?.value,
                                    trendWeightKg = it.trendWeightKg?.value,
                                )
                            }
                    quality = EnergyEngine.quality(energyDays, today)
                    measuredTdee =
                        if (quality is EngineState.Updating) {
                            EnergyEngine
                                .measuredTdee(
                                    energyDays.filter { it.epochDay >= today - ConstantsRegistry.TDEE_WINDOW_DAYS + 1 },
                                )?.tdeeKcal
                        } else {
                            null
                        }
                    val age = profile.ageAtYear(clock.now().toLocalDateTime(zone).year)
                    val hasBodyInputs = profile.heightCm != null && weight != null
                    val rawMaintenance =
                        measuredTdee ?: if (age != null && age >= 18 && hasBodyInputs) {
                            ForecastEngine.bmrMifflinStJeor(profile.sex, weight!!, profile.heightCm!!, age) *
                                ConstantsRegistry.activityMultiplier(profile.activityLevel)
                        } else {
                            null
                        }
                    val maintenance =
                        rawMaintenance?.takeUnless { quality is EngineState.Held }?.let {
                            kotlin.math
                                .round(
                                    it,
                                )
                        }
                    val health = ProfileHealthContextStore(documents).read(profile.id)
                    val old = state.value
                    if (edited &&
                        old.profile?.id != profile.id
                    ) {
                        error("The active profile changed. Reopen this editor.")
                    }
                    val answers = health.answers
                    val energy = record.document.energy
                    val budget =
                        if (energy.cadence ==
                            Cadence.WEEKLY
                        ) {
                            energy.weeklyBudgetKcal?.div(7)
                        } else {
                            energy.budgetKcal
                        }
                    val canonical = if (edited) old.intake else budget
                    val mode = if (maintenance == null) IntakeEntryMode.INTAKE else old.mode
                    val split = record.document.macros.split as? MacroSplit.Custom
                    recalculate(
                        old.copy(
                            startDay = today,
                            loading = false,
                            error = null,
                            profile = profile,
                            record = if (edited) old.record else record,
                            weight = weight,
                            maintenance = maintenance,
                            maintenanceValue =
                                maintenance?.let {
                                    DerivedValue(
                                        it,
                                        if (measuredTdee != null) {
                                            Provenance.Derived(
                                                EnergyEngine.TDEE_FORMULA_VERSION,
                                                listOf("logged intake", "weight trend"),
                                            )
                                        } else {
                                            Provenance.Estimated(clock.now(), ConstantsRegistry.BMR_FORMULA_VERSION)
                                        },
                                    )
                                },
                            measured = measuredTdee != null,
                            mode = mode,
                            answers = answers,
                            healthNeedsReview = health.unreadable || health.conflicts.isNotEmpty(),
                            text =
                                if (edited &&
                                    canonical == null
                                ) {
                                    old.text
                                } else {
                                    canonical?.let {
                                        number(
                                            if (mode ==
                                                IntakeEntryMode.INTAKE
                                            ) {
                                                it
                                            } else {
                                                it - maintenance!!
                                            },
                                        )
                                    }
                                        ?: ""
                                },
                            weekly = if (edited) old.weekly else energy.cadence == Cadence.WEEKLY,
                            weekendExtra =
                                if (edited || energy.schedule.size != 7 || budget == null) {
                                    old.weekendExtra
                                } else {
                                    number((energy.schedule[5] + energy.schedule[6]) / 2 - budget)
                                },
                            protein = if (edited) old.protein else number(split?.proteinPct ?: 30.0),
                            carbs = if (edited) old.carbs else number(split?.carbPct ?: 40.0),
                            fat = if (edited) old.fat else number(split?.fatPct ?: 30.0),
                            fiber = if (edited) old.fiber else number(record.document.fiber.targetG),
                        ),
                    )
                    if (old.loading && !edited && budget == null) useSuggestion()
                } catch (e: CancellationException) {
                    throw e
                } catch (expected: Exception) {
                    state.value =
                        state.value.copy(loading = false, error = expected.message ?: "Could not load intake. Retry.")
                } finally {
                    completeReload()
                }
            }
    }

    private fun completeReload() {
        loadingJob = null
        if (reloadPending) {
            reloadPending = false
            reload()
        }
    }

    public fun edit(
        preserveOrigin: Boolean = false,
        transform: (IntakeTargetState) -> IntakeTargetState,
    ) {
        if (state.value.saving) return
        val before = state.value
        edited = true
        val changed = transform(before)
        recalculate(
            changed.copy(
                error = null,
                origin = if (!preserveOrigin && changed.text != before.text) IntakeOrigin.MANUAL else before.origin,
            ),
        )
    }

    public fun switchMode(mode: IntakeEntryMode) {
        val current = state.value
        if (mode == current.mode || (mode == IntakeEntryMode.ADJUSTMENT && current.maintenance == null)) return
        val intake = current.intake
        if (intake == null) {
            edit(preserveOrigin = true) { it.copy(mode = mode, text = "") }
            return
        }
        edit(preserveOrigin = true) {
            it.copy(
                mode = mode,
                text =
                    number(
                        if (mode ==
                            IntakeEntryMode.INTAKE
                        ) {
                            intake
                        } else {
                            intake - it.maintenance!!
                        },
                    ),
            )
        }
    }

    public fun slide(delta: Float) {
        val current = state.value
        val maintenance = current.maintenance ?: return
        if (dragHorizon == null) dragHorizon = current.forecast?.weeks
        val amount = kotlin.math.round(delta / 25.0) * 25.0
        edit { it.copy(text = number(if (it.mode == IntakeEntryMode.INTAKE) maintenance + amount else amount)) }
    }

    public fun finishSliding() {
        dragHorizon = null
        recalculate()
    }

    public fun useSuggestion() {
        if (state.value.saving) return
        val s = state.value
        val intake = s.suggested ?: return
        edited = true
        recalculate(
            s.copy(
                origin = IntakeOrigin.SUGGESTED,
                text =
                    number(
                        if (s.mode ==
                            IntakeEntryMode.INTAKE
                        ) {
                            intake
                        } else {
                            intake - s.maintenance!!
                        },
                    ),
            ),
        )
    }

    private fun recalculate(s: IntakeTargetState = state.value) {
        val p = s.profile
        val weight = s.weight
        val goal =
            s.record
                ?.document
                ?.goal
                ?.targetWeightKg
        val maintenance = s.maintenance
        val age = p?.ageAtYear(clock.now().toLocalDateTime(zone).year)
        val scenario =
            IntakeScenario.evaluate(
                scenarioInput(s),
                s.answers,
                quality is EngineState.Held,
                goal,
                dragHorizon,
            )
        val evaluated =
            s.copy(
                forecast = if (s.healthNeedsReview) null else scenario.projection,
                projectionNote =
                    if (s.healthNeedsReview) {
                        "Review health context in Profile."
                    } else {
                        scenario.note
                    },
                suggested = null,
                suggestionStatus = SuggestionStatus.PROFILE_REQUIRED,
                suggestionNote = "Complete your profile for a suggestion.",
            )
        if (s.healthNeedsReview || s.answers.any { it == SafetyAnswer.NOT_ANSWERED }) {
            state.value =
                evaluated.copy(
                    suggestionStatus = SuggestionStatus.HEALTH_REQUIRED,
                    suggestionNote =
                        if (s.healthNeedsReview) {
                            "Review health context for a suggestion."
                        } else {
                            "Complete health context for a suggestion."
                        },
                )
            return
        }
        if (p == null || weight == null) {
            state.value = evaluated
            return
        }
        if (goal == null || maintenance == null) {
            state.value = evaluated
            return
        }
        val mode =
            when {
                goal < weight -> WeightGoalMode.LOSS
                goal > weight -> WeightGoalMode.GAIN
                else -> WeightGoalMode.MAINTENANCE
            }

        fun preview(intake: Double): WeightGoalPreviewResult =
            WeightGoalPreview.evaluate(
                WeightGoalPreviewInput(
                    ageYears = age,
                    sex = p.sex,
                    heightCm = p.heightCm,
                    activityLevel = p.activityLevel,
                    currentWeightKg = weight,
                    targetWeightKg = goal,
                    mode = mode,
                    requestedPacePctPerWeek = abs(maintenance - intake) * 7 / 7700 / weight * 100,
                    targetEpochDay = null,
                    plannedDailyEnergyKcal = intake,
                    pregnant = s.answers[0],
                    breastfeeding = s.answers[1],
                    eatingDisorderConcern = s.answers[2],
                    medicallyInfluencedWeight = s.answers[3],
                    todayEpochDay = today,
                    now = clock.now(),
                    engineState = quality,
                    measuredInput =
                        if (age != null && p.heightCm != null && measuredTdee != null) {
                            MeasuredInput(
                                p.sex,
                                age,
                                p.heightCm!!,
                                weight,
                                goal,
                                intake,
                                measuredTdee,
                                startEpochDay = today,
                                startInstant = clock.now(),
                            )
                        } else {
                            null
                        },
                ),
            )
        val candidate = IntakeSuggestion.candidate(maintenance, weight, goal, p.heightCm)

        fun scheduleSupported(intake: Double): Boolean {
            val energy = energyFor(s, intake) ?: return false
            return energy.cadence != Cadence.WEEKLY || energy.schedule.all { it >= energy.floorKcal }
        }
        val eligibility = candidate?.let { preview(it).eligibility }
        val suggested =
            candidate?.takeIf {
                it > 0 && scheduleSupported(it) && eligibility?.allowsGoalMath == true
            }
        val note =
            when {
                suggested != null -> "Suggested intake"
                candidate == null -> "No standard suggestion for this weight goal and profile."
                eligibility is WeightGoalEligibility.Held -> WeightGoalSafetyCopyPolicy.forResult(eligibility).body
                eligibility is WeightGoalEligibility.Unsupported ->
                    WeightGoalSafetyCopyPolicy
                        .forResult(
                            eligibility,
                        ).body
                !scheduleSupported(candidate) -> "This schedule puts a day below the recommendation range."
                else -> "No suggestion is available for these inputs."
            }
        state.value =
            evaluated.copy(
                suggested = suggested,
                suggestionStatus = if (suggested != null) SuggestionStatus.READY else SuggestionStatus.UNAVAILABLE,
                suggestionNote = note,
            )
    }

    private fun scenarioInput(s: IntakeTargetState): IntakeProjectionInput? {
        val profile = s.profile ?: return null
        val age = profile.ageAtYear(clock.now().toLocalDateTime(zone).year) ?: return null
        val height = profile.heightCm ?: return null
        val weight = s.weight ?: return null
        val maintenance = s.maintenance ?: return null
        return IntakeProjectionInput(
            profile.sex,
            age,
            height,
            weight,
            maintenance,
            s.intake ?: Double.NaN,
            if (s.measured) ForecastMode.MEASURED else ForecastMode.COLD_START,
            clock.now(),
        )
    }

    private fun energyFor(
        s: IntakeTargetState,
        intake: Double,
    ): app.wlo.core.documents.Energy? {
        val draft = IntakeTargetDraft(intake, s.maintenance)
        val oldEnergy = s.record?.document?.energy ?: return null
        return if (s.weekly && !s.scheduleEdited && oldEnergy.cadence == Cadence.WEEKLY) {
            val delta = intake - (oldEnergy.weeklyBudgetKcal ?: 0.0) / 7
            oldEnergy
                .copy(
                    weeklyBudgetKcal = intake * 7,
                    schedule = oldEnergy.schedule.map { it + delta },
                    manuallyEntered = true,
                ).takeIf { it.schedule.all { day -> day.isFinite() && day >= 0 } }
        } else if (s.weekly) {
            s.weekendExtra.toDoubleOrNull()?.let { draft.weeklyEnergy(oldEnergy, it) }
        } else {
            draft.dailyEnergy(oldEnergy)
        }
    }

    public fun save() {
        val s = state.value
        if (s.saving || s.loading || s.saved) return
        val p = s.profile ?: return
        val record = s.record ?: return
        val intake = s.intake?.takeIf { it.isFinite() && it >= 0 }
        val fiber = s.fiber.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        val percentages = listOf(s.protein, s.carbs, s.fat).map { it.toDoubleOrNull() }
        val invalidMacros = percentages.any { it == null || !it.isFinite() || it < 0 }
        val invalidSum = abs(percentages.filterNotNull().sum() - 100) > 0.01
        val macrosInvalid = s.macrosEdited && (invalidMacros || invalidSum)
        if (intake == null || fiber == null || macrosInvalid) {
            state.value = s.copy(error = "Enter valid amounts; macro percentages must total 100%.")
            return
        }
        val energy = energyFor(s, intake)
        if (energy == null) {
            state.value = s.copy(error = "Enter a weekly allocation with non-negative daily amounts.")
            return
        }
        state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                if (profiles.active().required()?.id != p.id) error("The active profile changed. Reopen this editor.")
                val macros =
                    if (s.macrosEdited) {
                        record.document.macros.copy(
                            split =
                                MacroSplit.Custom(
                                    proteinPct = percentages[0],
                                    carbPct = percentages[1],
                                    fatPct = percentages[2],
                                ),
                        )
                    } else {
                        record.document.macros
                    }
                when (
                    val outcome =
                        writers.studio().writeManualIntake(
                            p.id,
                            record.version,
                            energy,
                            macros,
                            FiberTarget(fiber),
                        )
                ) {
                    is TargetsWriteOutcome.Written -> state.value = state.value.copy(saving = false, saved = true)
                    is TargetsWriteOutcome.Rejected ->
                        state.value =
                            state.value.copy(
                                saving = false,
                                error =
                                    if (outcome.error is TargetsWriteError.VersionConflict) {
                                        "Targets changed elsewhere. Close and reopen before saving."
                                    } else {
                                        "Could not save these targets. Your draft is still here."
                                    },
                            )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (expected: Exception) {
                state.value =
                    state.value.copy(saving = false, error = "Could not save. Your draft is still here; retry.")
            }
        }
    }
}

private fun <T> WloResult<T>.required(): T =
    when (this) {
        is WloResult.Ok -> value
        is WloResult.Err -> error("Could not read saved data. Retry.")
    }

private fun number(value: Double): String =
    if (value ==
        value.toLong().toDouble()
    ) {
        value.toLong().toString()
    } else {
        value.toString()
    }
