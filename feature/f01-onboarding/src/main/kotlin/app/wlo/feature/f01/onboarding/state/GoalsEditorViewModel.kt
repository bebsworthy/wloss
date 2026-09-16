package app.wlo.feature.f01.onboarding.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.MassUnit
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.TargetsRepository
import app.wlo.core.data.TargetsWriteError
import app.wlo.core.data.TargetsWriteOutcome
import app.wlo.core.data.TargetsWriters
import app.wlo.core.data.WeighInRepository
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.documents.DocumentCodec
import app.wlo.core.documents.TargetsWriterId
import app.wlo.core.engines.EnergyDay
import app.wlo.core.engines.EnergyEngine
import app.wlo.core.engines.EngineState
import app.wlo.core.engines.ForecastEngine
import app.wlo.core.engines.GoalForecastResult
import app.wlo.core.engines.MeasuredInput
import app.wlo.core.model.Profile
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafetyCopy
import app.wlo.core.model.WeightGoalSafetyCopyPolicy
import app.wlo.core.model.WeightGoalSafetyInput
import app.wlo.feature.f01.onboarding.domain.GoalEditorSafety
import app.wlo.feature.f01.onboarding.domain.GoalsEditorDraft
import app.wlo.feature.f01.onboarding.domain.GoalsEditorDraftIO
import app.wlo.feature.f01.onboarding.domain.WeightGoalPreview
import app.wlo.feature.f01.onboarding.domain.WeightGoalPreviewInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Goals-editor intents (MVI-lite). */
public sealed interface GoalsEditorEvent {
    public data class GoalWeightChange(
        public val text: String,
    ) : GoalsEditorEvent

    public data class PaceChange(
        public val text: String,
    ) : GoalsEditorEvent

    public data class TargetDateChange(
        public val text: String,
    ) : GoalsEditorEvent

    public data class BudgetChange(
        public val text: String,
    ) : GoalsEditorEvent

    public data class ModeChange(
        public val mode: WeightGoalMode,
    ) : GoalsEditorEvent

    public data class SafetyChange(
        public val question: GoalSafetyQuestion,
        public val answer: SafetyAnswer,
    ) : GoalsEditorEvent

    /** Writes vN+1 through the STUDIO_F01 door (R-B2; wizard = v1 of the same editor). */
    public data object Save : GoalsEditorEvent

    /** Copies an older version forward — never a history rewrite (A.1). */
    public data class RevertTo(
        public val version: Int,
    ) : GoalsEditorEvent

    public data object DismissNotice : GoalsEditorEvent
}

public enum class GoalSafetyQuestion { PREGNANT, BREASTFEEDING, EATING_DISORDER, MEDICALLY_INFLUENCED }

/** One line of the versions ledger. */
public data class TargetsVersionUi(
    public val version: Int,
    public val writtenBy: TargetsWriterId,
    public val createdAtLabel: String,
    public val isCurrent: Boolean,
)

/** The goals editor's state (WLO-0035 W4: an edit path that is not the wizard). */
public data class GoalsEditorUi(
    public val loading: Boolean = true,
    public val hasTargets: Boolean = false,
    public val baseVersion: Int? = null,
    public val goalWeightText: String = "",
    public val massUnit: MassUnit = MassUnit.KILOGRAM,
    public val paceText: String = "",
    public val targetDateText: String = "",
    public val budgetText: String = "",
    public val mode: WeightGoalMode = WeightGoalMode.LOSS,
    public val pregnant: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    public val breastfeeding: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    public val eatingDisorderConcern: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    public val medicallyInfluencedWeight: SafetyAnswer = SafetyAnswer.NOT_ANSWERED,
    public val eligibility: WeightGoalEligibility =
        WeightGoalEligibility.Held(null, setOf(app.wlo.core.model.WeightGoalHoldReason.SCREENING_INCOMPLETE)),
    public val forecastEligibility: WeightGoalEligibility = eligibility,
    public val safetyCopy: WeightGoalSafetyCopy = WeightGoalSafetyCopyPolicy.forResult(eligibility),
    public val currentWeightKg: Double? = null,
    public val todayEpochDay: Long = 0,
    public val now: Instant = Instant.fromEpochMilliseconds(0),
    public val impliedPacePctPerWeek: Double? = null,
    public val targetDateValid: Boolean = true,
    public val forecast: GoalForecastResult? = null,
    public val forecastInputsMissing: Boolean = false,
    /** The last write's human diff ("budget 1900 → 1950 kcal"), newest first. */
    public val diff: List<String> = emptyList(),
    public val notice: String? = null,
    public val history: List<TargetsVersionUi> = emptyList(),
)

/**
 * The goals editor (WLO-0035 W4 / R3): edits goal weight, pace, target date
 * and the daily budget, then writes a NEW Targets version through
 * [TargetsWriters.studio] — the same writer the wizard's first run uses, so
 * the ledger records what changed regardless of which surface wrote it.
 * Rejections are hard and spoken plainly; nothing is clamped (A.2).
 */
public class GoalsEditorViewModel(
    private val profiles: ProfileRepository,
    private val targets: TargetsRepository,
    private val writers: TargetsWriters,
    private val weighIns: WeighInRepository,
    private val dayProjection: DayProjectionRepository,
    private val settings: SettingsStore,
    private val documents: JsonDocumentStore,
    private val clock: ClockPort,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private var profileId: String? = null
    private var activeUnit: MassUnit = MassUnit.KILOGRAM
    private var profile: Profile? = null
    private var currentWeightKg: Double? = null
    private var engineState: EngineState = EngineState.Developing(0)
    private var measuredTdeeKcal: Double? = null

    private val state = MutableStateFlow(GoalsEditorUi())

    /** Renderable state. */
    public val uiState: StateFlow<GoalsEditorUi> = state

    init {
        viewModelScope.launch {
            settings.massUnit.collectLatest { unit ->
                activeUnit = unit
                reload()
            }
        }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: GoalsEditorEvent) {
        when (event) {
            is GoalsEditorEvent.GoalWeightChange -> updateSafety(state.value.copy(goalWeightText = event.text))
            is GoalsEditorEvent.PaceChange -> updateSafety(state.value.copy(paceText = event.text))
            is GoalsEditorEvent.TargetDateChange -> updateSafety(state.value.copy(targetDateText = event.text))
            is GoalsEditorEvent.BudgetChange -> updateSafety(state.value.copy(budgetText = event.text))
            is GoalsEditorEvent.ModeChange -> updateSafety(state.value.copy(mode = event.mode))
            is GoalsEditorEvent.SafetyChange ->
                updateSafety(
                    when (event.question) {
                        GoalSafetyQuestion.PREGNANT -> state.value.copy(pregnant = event.answer)
                        GoalSafetyQuestion.BREASTFEEDING -> state.value.copy(breastfeeding = event.answer)
                        GoalSafetyQuestion.EATING_DISORDER -> state.value.copy(eatingDisorderConcern = event.answer)
                        GoalSafetyQuestion.MEDICALLY_INFLUENCED ->
                            state.value.copy(medicallyInfluencedWeight = event.answer)
                    },
                )
            GoalsEditorEvent.DismissNotice -> state.value = state.value.copy(notice = null)
            GoalsEditorEvent.Save -> save()
            is GoalsEditorEvent.RevertTo ->
                viewModelScope.launch {
                    val id = profileId ?: return@launch
                    val candidate =
                        targets
                            .history(id)
                            .getOrNull()
                            .orEmpty()
                            .firstOrNull { it.version == event.version }
                    val eligibility =
                        candidate?.let {
                            eligibilityFor(
                                state.value,
                                it.document.goal.targetWeightKg,
                                it.document.goal.pacePctPerWeek,
                                it.document.energy.budgetKcal,
                            )
                        }
                    if (eligibility !is WeightGoalEligibility.Eligible) {
                        val copy = WeightGoalSafetyCopyPolicy.forResult(eligibility ?: state.value.eligibility)
                        state.value = state.value.copy(notice = "${copy.title}. ${copy.body}")
                        return@launch
                    }
                    val forecastEligibility = ForecastEngine.eligibilityForForecast(eligibility)
                    if (candidate.document.goal.targetDate != null &&
                        forecastEligibility !is WeightGoalEligibility.Eligible
                    ) {
                        val copy = WeightGoalSafetyCopyPolicy.forResult(forecastEligibility)
                        state.value = state.value.copy(notice = "${copy.title}. ${copy.body}")
                        return@launch
                    }
                    when (val outcome = writers.studio().revert(id, event.version)) {
                        is TargetsWriteOutcome.Written -> {
                            state.value =
                                state.value.copy(
                                    diff = outcome.diff,
                                    notice = "restored as v${outcome.record.version}",
                                )
                            reload()
                        }

                        is TargetsWriteOutcome.Rejected ->
                            state.value = state.value.copy(notice = rejectionCopy(outcome.error))
                    }
                }
        }
    }

    private fun save() {
        val id = profileId ?: return
        val current = state.value
        val displayWeight = current.goalWeightText.toDoubleOrNull()
        if (displayWeight == null || displayWeight <= 0.0) {
            state.value =
                state.value.copy(notice = "check the goal weight — a number in ${activeUnit.symbol}")
            return
        }
        val kg = activeUnit.toKilograms(displayWeight)
        val pace = current.impliedPacePctPerWeek ?: current.paceText.toDoubleOrNull()
        if (current.mode != WeightGoalMode.MAINTENANCE && (pace == null || pace <= 0.0)) {
            state.value = state.value.copy(notice = "check the pace — % of bodyweight per week")
            return
        }
        if (!current.targetDateValid) {
            state.value = state.value.copy(notice = "check the target date — use a future YYYY-MM-DD date")
            return
        }
        val date =
            current.targetDateText
                .trim()
                .ifEmpty { null }
                .takeIf { current.forecastEligibility is WeightGoalEligibility.Eligible }
        date?.let {
            runCatching { Instant.parse("${it}T12:00:00Z") }.getOrNull() ?: run {
                state.value = state.value.copy(notice = "check the target date — YYYY-MM-DD")
                return
            }
        }
        val budget = current.budgetText.toDoubleOrNull()
        val eligibility = current.eligibility
        if (eligibility !is WeightGoalEligibility.Eligible) {
            val copy = WeightGoalSafetyCopyPolicy.forResult(eligibility)
            state.value =
                current.copy(
                    eligibility = eligibility,
                    safetyCopy = copy,
                    notice = "${copy.title}. ${copy.body}",
                )
            return
        }

        viewModelScope.launch {
            val record = targets.current(id).getOrNull()
            if (record == null) {
                state.value = state.value.copy(notice = "no plan yet — finish the wizard first")
                return@launch
            }
            val document =
                record.document.copy(
                    goal =
                        record.document.goal.copy(
                            targetWeightKg = kg,
                            pacePctPerWeek = pace ?: 0.0,
                            targetDate = date,
                        ),
                    energy =
                        if (budget != null && record.document.energy.cadence == app.wlo.core.documents.Cadence.DAILY) {
                            record.document.energy.copy(budgetKcal = budget)
                        } else {
                            record.document.energy
                        },
                )
            when (val outcome = writers.studio().writeRevision(id, record.version, document)) {
                is TargetsWriteOutcome.Written -> {
                    val input = safetyInput(current, kg, pace, budget)
                    documents.writeText(
                        safetyKey(id),
                        DocumentCodec.json.encodeToString(WeightGoalSafetyInput.serializer(), input),
                    )
                    documents.remove(draftKey(id))
                    state.value =
                        state.value.copy(
                            diff = outcome.diff,
                            notice = "saved as v${outcome.record.version}",
                        )
                    reload()
                }

                is TargetsWriteOutcome.Rejected ->
                    state.value = state.value.copy(notice = rejectionCopy(outcome.error))
            }
        }
    }

    private suspend fun reload() {
        val activeProfile = profile ?: profiles.active().getOrNull() ?: return
        profile = activeProfile
        val id = profileId ?: activeProfile.id
        profileId = id
        currentWeightKg =
            weighIns
                .currentTrend(id, DayBoundary.epochDay(clock.now(), zone))
                .getOrNull()
                ?.current
                ?.value
                ?: activeProfile.startWeightKg
        val today = DayBoundary.epochDay(clock.now(), zone)
        val energyDays =
            dayProjection
                .range(
                    id,
                    today - app.wlo.core.model.ConstantsRegistry.QUALITY_WINDOW_DAYS + 1,
                    today,
                ).getOrNull()
                .orEmpty()
                .map { view ->
                    EnergyDay(
                        epochDay = view.dayEpochDay,
                        intakeKcal = view.intakeKcal?.value,
                        trendWeightKg = view.trendWeightKg?.value,
                    )
                }
        engineState = EnergyEngine.quality(energyDays, today)
        measuredTdeeKcal =
            if (engineState is EngineState.Updating) {
                val from = today - app.wlo.core.model.ConstantsRegistry.TDEE_WINDOW_DAYS + 1
                EnergyEngine.measuredTdee(energyDays.filter { it.epochDay >= from })?.tdeeKcal
            } else {
                null
            }
        val current = targets.current(id).getOrNull()
        if (current == null) {
            state.value = state.value.copy(loading = false, hasTargets = false)
            return
        }
        val document = current.document
        val draft =
            documents
                .readText(draftKey(id))
                ?.let(GoalsEditorDraftIO::decode)
                ?.takeIf { it.baseVersion == current.version }
        val attestation =
            documents.readText(safetyKey(id))?.let { text ->
                runCatching {
                    DocumentCodec.json.decodeFromString(WeightGoalSafetyInput.serializer(), text)
                }.getOrNull()
            }
        val mode = attestation?.mode ?: inferMode(activeProfile.startWeightKg, document.goal.targetWeightKg)
        val history =
            targets
                .history(id)
                .getOrNull()
                .orEmpty()
                .sortedByDescending { it.version }
                .map { record ->
                    TargetsVersionUi(
                        version = record.version,
                        writtenBy = record.createdBy,
                        createdAtLabel =
                            Instant
                                .fromEpochMilliseconds(record.createdAtEpochMs)
                                .toLocalDateTime(zone)
                                .date
                                .toString(),
                        isCurrent = record.version == current.version,
                    )
                }
        updateSafety(
            state.value.copy(
                loading = false,
                hasTargets = true,
                baseVersion = current.version,
                goalWeightText = activeUnit.formatNumber(draft?.targetWeightKg ?: document.goal.targetWeightKg),
                massUnit = activeUnit,
                paceText = trimNumber(draft?.pacePctPerWeek ?: document.goal.pacePctPerWeek),
                targetDateText = draft?.targetDate ?: document.goal.targetDate.orEmpty(),
                budgetText =
                    (draft?.budgetKcal ?: document.energy.budgetKcal)
                        ?.let(::trimNumber)
                        .orEmpty(),
                mode = draft?.mode ?: mode,
                pregnant = draft?.pregnant ?: attestation?.pregnant ?: SafetyAnswer.NOT_ANSWERED,
                breastfeeding = draft?.breastfeeding ?: attestation?.breastfeeding ?: SafetyAnswer.NOT_ANSWERED,
                eatingDisorderConcern =
                    draft?.eatingDisorderConcern ?: attestation?.eatingDisorderConcern ?: SafetyAnswer.NOT_ANSWERED,
                medicallyInfluencedWeight =
                    draft?.medicallyInfluencedWeight ?: attestation?.medicallyInfluencedWeight
                        ?: SafetyAnswer.NOT_ANSWERED,
                history = history,
            ),
        )
    }

    private fun updateSafety(updated: GoalsEditorUi) {
        val target = updated.goalWeightText.toDoubleOrNull()?.let(activeUnit::toKilograms)
        val pace = updated.paceText.toDoubleOrNull()
        val budget = updated.budgetText.toDoubleOrNull()
        val today = DayBoundary.epochDay(clock.now(), zone)
        val targetDay =
            updated.targetDateText
                .trim()
                .takeIf(String::isNotEmpty)
                ?.let { runCatching { LocalDate.parse(it).toEpochDays().toLong() }.getOrNull() }
        val active = profile
        val preview =
            WeightGoalPreview.evaluate(
                WeightGoalPreviewInput(
                    ageYears = active?.birthYear?.let { year -> clock.now().toLocalDateTime(zone).year - year },
                    currentWeightKg = currentWeightKg,
                    sex = active?.sex,
                    heightCm = active?.heightCm,
                    activityLevel = active?.activityLevel ?: app.wlo.core.model.ActivityLevel.SEDENTARY,
                    mode = updated.mode,
                    targetWeightKg = target,
                    requestedPacePctPerWeek = pace,
                    targetEpochDay = targetDay,
                    plannedDailyEnergyKcal = budget,
                    pregnant = updated.pregnant,
                    breastfeeding = updated.breastfeeding,
                    eatingDisorderConcern = updated.eatingDisorderConcern,
                    medicallyInfluencedWeight = updated.medicallyInfluencedWeight,
                    todayEpochDay = today,
                    now = clock.now(),
                    engineState = engineState,
                    measuredInput = measuredInput(active, target, budget, today),
                ),
            )
        state.value =
            updated.copy(
                eligibility = preview.eligibility,
                forecastEligibility = preview.forecastEligibility,
                safetyCopy = WeightGoalSafetyCopyPolicy.forResult(preview.eligibility),
                currentWeightKg = currentWeightKg,
                todayEpochDay = today,
                now = clock.now(),
                impliedPacePctPerWeek = preview.impliedPacePctPerWeek,
                targetDateValid =
                    updated.targetDateText.isBlank() || (targetDay != null && preview.targetDateValid),
                forecast = preview.forecast,
                forecastInputsMissing = preview.missingForecastInputs,
            )
        persistDraft(state.value)
    }

    private fun measuredInput(
        active: Profile?,
        target: Double?,
        budget: Double?,
        today: Long,
    ): MeasuredInput? {
        val tdee = measuredTdeeKcal ?: return null
        val birthYear = active?.birthYear ?: return null
        val height = active.heightCm ?: return null
        val start = currentWeightKg ?: return null
        val goal = target ?: return null
        val intake = budget ?: return null
        return MeasuredInput(
            sex = active.sex,
            ageYears = clock.now().toLocalDateTime(zone).year - birthYear,
            heightCm = height,
            startTrendKg = start,
            goalWeightKg = goal,
            intakeKcal = intake,
            measuredTdeeKcal = tdee,
            startEpochDay = today,
            startInstant = clock.now(),
        )
    }

    private fun persistDraft(ui: GoalsEditorUi) {
        val id = profileId ?: return
        val version = ui.baseVersion ?: return
        if (!ui.hasTargets || ui.loading) return
        val draft =
            GoalsEditorDraft(
                baseVersion = version,
                targetWeightKg = ui.goalWeightText.toDoubleOrNull()?.let(activeUnit::toKilograms),
                pacePctPerWeek = ui.paceText.toDoubleOrNull(),
                targetDate = ui.targetDateText,
                budgetKcal = ui.budgetText.toDoubleOrNull(),
                mode = ui.mode,
                pregnant = ui.pregnant,
                breastfeeding = ui.breastfeeding,
                eatingDisorderConcern = ui.eatingDisorderConcern,
                medicallyInfluencedWeight = ui.medicallyInfluencedWeight,
            )
        viewModelScope.launch { documents.writeText(draftKey(id), GoalsEditorDraftIO.encode(draft)) }
    }

    private fun eligibilityFor(
        ui: GoalsEditorUi,
        targetKg: Double?,
        pace: Double?,
        budget: Double?,
    ): WeightGoalEligibility =
        GoalEditorSafety.evaluate(
            ageYears = profile?.birthYear?.let { year -> clock.now().toLocalDateTime(zone).year - year },
            currentWeightKg = currentWeightKg,
            sex = profile?.sex,
            mode = ui.mode,
            targetWeightKg = targetKg,
            pacePctPerWeek = pace,
            plannedDailyEnergyKcal = budget,
            pregnant = ui.pregnant,
            breastfeeding = ui.breastfeeding,
            eatingDisorderConcern = ui.eatingDisorderConcern,
            medicallyInfluencedWeight = ui.medicallyInfluencedWeight,
        )

    private fun safetyInput(
        ui: GoalsEditorUi,
        targetKg: Double?,
        pace: Double?,
        budget: Double?,
    ): WeightGoalSafetyInput {
        val active = profile
        return GoalEditorSafety.input(
            ageYears = active?.birthYear?.let { year -> clock.now().toLocalDateTime(zone).year - year },
            currentWeightKg = currentWeightKg,
            sex = active?.sex,
            mode = ui.mode,
            targetWeightKg = targetKg,
            pacePctPerWeek = pace,
            plannedDailyEnergyKcal = budget,
            pregnant = ui.pregnant,
            breastfeeding = ui.breastfeeding,
            eatingDisorderConcern = ui.eatingDisorderConcern,
            medicallyInfluencedWeight = ui.medicallyInfluencedWeight,
        )
    }

    private fun inferMode(
        currentKg: Double?,
        targetKg: Double,
    ): WeightGoalMode =
        when {
            currentKg == null -> WeightGoalMode.LOSS
            targetKg < currentKg -> WeightGoalMode.LOSS
            targetKg > currentKg -> WeightGoalMode.GAIN
            else -> WeightGoalMode.MAINTENANCE
        }

    private fun safetyKey(profileId: String): String = "weight/goal-safety-v1/$profileId"

    private fun draftKey(profileId: String): String = "weight/goal-editor-draft-v1/$profileId"

    private fun rejectionCopy(error: TargetsWriteError): String =
        when (error) {
            is TargetsWriteError.InvariantViolated ->
                "the plan's rules say no — " +
                    (
                        error.violations.firstOrNull()?.let { violation -> violation.javaClass.simpleName }
                            ?: "invariant violated"
                    )
            is TargetsWriteError.FloorOverrideUnacknowledged ->
                "the floor is ${error.floorKcal.toInt()} kcal — lower budgets need the floor acknowledgment"
            is TargetsWriteError.EatBackRejected ->
                "a budget above measured expenditure would eat exercise back — not allowed"
            is TargetsWriteError.VersionConflict ->
                "that edit raced another save — review and retry"
            is TargetsWriteError.NoActiveTargets ->
                "no plan yet — finish the wizard first"
            is TargetsWriteError.StorageFailure ->
                "that didn't save — storage error"
        }

    private fun trimNumber(value: Double): String {
        val whole = value.toLong()
        return if (value == whole.toDouble()) "$whole" else "${format1(value)}"
    }

    private fun format1(value: Double): String {
        val tenths = (value * 10).toLong()
        return "${tenths / 10}.${tenths % 10}"
    }
}
