package app.wlo.feature.f01.onboarding.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.DecimalInput
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
import app.wlo.core.documents.Energy
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.documents.TargetsWriterId
import app.wlo.core.engines.EnergyDay
import app.wlo.core.engines.EnergyEngine
import app.wlo.core.engines.EngineState
import app.wlo.core.engines.ForecastEngine
import app.wlo.core.engines.GoalForecastResult
import app.wlo.core.engines.MeasuredInput
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.Profile
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafetyCopy
import app.wlo.core.model.WeightGoalSafetyCopyPolicy
import app.wlo.core.model.WeightGoalSafetyInput
import app.wlo.feature.f01.onboarding.domain.GoalEditorSafety
import app.wlo.feature.f01.onboarding.domain.GoalSaveJournal
import app.wlo.feature.f01.onboarding.domain.GoalSaveJournalIO
import app.wlo.feature.f01.onboarding.domain.GoalsEditorDraft
import app.wlo.feature.f01.onboarding.domain.GoalsEditorDraftIO
import app.wlo.feature.f01.onboarding.domain.WeightFirstOnboardingStore
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
import kotlin.uuid.Uuid

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

    public data object ReloadCurrent : GoalsEditorEvent

    public data object ReviewDifferences : GoalsEditorEvent

    public data object DiscardDraft : GoalsEditorEvent
}

public enum class GoalSafetyQuestion { PREGNANT, BREASTFEEDING, EATING_DISORDER, MEDICALLY_INFLUENCED }

public enum class GoalFormState { LOADING, EDITING, SAVING, SAVE_ERROR, CONFLICT, SAVED }

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
    public val formState: GoalFormState = GoalFormState.LOADING,
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
    public val currentWeightIsStarting: Boolean = false,
    public val todayEpochDay: Long = 0,
    public val now: Instant = Instant.fromEpochMilliseconds(0),
    public val impliedPacePctPerWeek: Double? = null,
    public val targetDateValid: Boolean = true,
    public val forecast: GoalForecastResult? = null,
    public val forecastInputsMissing: Boolean = false,
    /** The last write's human diff ("budget 1900 → 1950 kcal"), newest first. */
    public val diff: List<String> = emptyList(),
    public val notice: String? = null,
    public val goalWeightError: String? = null,
    public val paceError: String? = null,
    public val dateError: String? = null,
    public val budgetError: String? = null,
    public val dirty: Boolean = false,
    public val history: List<TargetsVersionUi> = emptyList(),
)

/**
 * The goals editor (WLO-0035 W4 / R3): edits goal weight, pace, target date
 * and the daily budget, then writes a NEW Targets version through
 * [TargetsWriters.studio] — the same writer the wizard's first run uses, so
 * the ledger records what changed regardless of which surface wrote it.
 * Rejections are hard and spoken plainly; nothing is clamped (A.2).
 */
@Suppress("LargeClass") // One cohesive versioned editor; journal, safety and forecast share one frozen draft.
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
    private var pendingCommittedJournal: GoalSaveJournal? = null

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
        if (state.value.formState == GoalFormState.SAVING) return
        when (event) {
            is GoalsEditorEvent.GoalWeightChange ->
                updateSafety(state.value.edited().copy(goalWeightText = event.text, goalWeightError = null))
            is GoalsEditorEvent.PaceChange ->
                updateSafety(state.value.edited().copy(paceText = event.text, paceError = null))
            is GoalsEditorEvent.TargetDateChange ->
                updateSafety(state.value.edited().copy(targetDateText = event.text, dateError = null))
            is GoalsEditorEvent.BudgetChange ->
                updateSafety(state.value.edited().copy(budgetText = event.text, budgetError = null))
            is GoalsEditorEvent.ModeChange -> updateSafety(state.value.edited().copy(mode = event.mode))
            is GoalsEditorEvent.SafetyChange ->
                updateSafety(
                    when (event.question) {
                        GoalSafetyQuestion.PREGNANT -> state.value.edited().copy(pregnant = event.answer)
                        GoalSafetyQuestion.BREASTFEEDING -> state.value.edited().copy(breastfeeding = event.answer)
                        GoalSafetyQuestion.EATING_DISORDER ->
                            state.value.edited().copy(eatingDisorderConcern = event.answer)
                        GoalSafetyQuestion.MEDICALLY_INFLUENCED ->
                            state.value.edited().copy(medicallyInfluencedWeight = event.answer)
                    },
                )
            GoalsEditorEvent.DismissNotice -> state.value = state.value.copy(notice = null)
            GoalsEditorEvent.Save -> save()
            GoalsEditorEvent.ReloadCurrent -> viewModelScope.launch { reload(ignoreDraft = true) }
            GoalsEditorEvent.ReviewDifferences ->
                viewModelScope.launch {
                    val id = profileId ?: return@launch
                    val current = targets.current(id).getOrNull() ?: return@launch
                    val differences =
                        listOf(
                            "Goal: current ${activeUnit.format(current.document.goal.targetWeightKg)}, " +
                                "draft ${state.value.goalWeightText} ${activeUnit.symbol}",
                            "Pace: current ${trimNumber(current.document.goal.pacePctPerWeek)}%, " +
                                "draft ${state.value.paceText}%",
                            "Budget: current ${current.document.energy.budgetKcal
                                ?.let(::trimNumber) ?: "held"}, " +
                                "draft ${state.value.budgetText.ifBlank { "held" }} kcal",
                        )
                    state.value =
                        state.value.copy(
                            baseVersion = current.version,
                            formState = GoalFormState.EDITING,
                            dirty = true,
                            diff = differences,
                            notice =
                                "Current v${current.version} and your draft are shown below. " +
                                    "Save only after review.",
                        )
                    persistDraft(state.value)
                }
            GoalsEditorEvent.DiscardDraft ->
                viewModelScope.launch {
                    profileId?.let { documents.remove(draftKey(it)) }
                    reload(ignoreDraft = true)
                }
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

    private fun GoalsEditorUi.edited(): GoalsEditorUi =
        copy(
            dirty = true,
            formState = GoalFormState.EDITING,
            notice = null,
        )

    private fun save() {
        val id = profileId ?: return
        pendingCommittedJournal?.let { journal ->
            state.value = state.value.copy(formState = GoalFormState.SAVING, notice = null)
            viewModelScope.launch { finishJournal(journal, emptyList()) }
            return
        }
        val current = state.value
        val displayWeight = DecimalInput.parse(current.goalWeightText)
        if (displayWeight == null || displayWeight <= 0.0) {
            state.value =
                current.copy(
                    goalWeightError = "Enter a positive number in ${activeUnit.symbol}.",
                    formState = GoalFormState.SAVE_ERROR,
                )
            return
        }
        val kg = activeUnit.toKilograms(displayWeight)
        val parsedPace = DecimalInput.parse(current.paceText)
        val pace = current.impliedPacePctPerWeek ?: parsedPace
        if (current.mode != WeightGoalMode.MAINTENANCE && (pace == null || pace <= 0.0)) {
            state.value =
                current.copy(
                    paceError = "Enter a positive % of bodyweight per week.",
                    formState = GoalFormState.SAVE_ERROR,
                )
            return
        }
        if (!current.targetDateValid) {
            state.value =
                current.copy(
                    dateError = "Choose a future date.",
                    formState = GoalFormState.SAVE_ERROR,
                )
            return
        }
        val date =
            current.targetDateText
                .trim()
                .ifEmpty { null }
                .takeIf { current.forecastEligibility is WeightGoalEligibility.Eligible }
        date?.let {
            runCatching { Instant.parse("${it}T12:00:00Z") }.getOrNull() ?: run {
                state.value =
                    current.copy(
                        dateError = "Choose a valid date.",
                        formState = GoalFormState.SAVE_ERROR,
                    )
                return
            }
        }
        val budget = current.budgetText.takeIf(String::isNotBlank)?.let(DecimalInput::parse)
        if (current.budgetText.isNotBlank() && budget == null) {
            state.value =
                current.copy(
                    budgetError = "Enter a finite daily calorie budget or leave this blank.",
                    formState = GoalFormState.SAVE_ERROR,
                )
            return
        }
        val eligibility = current.eligibility
        if (eligibility !is WeightGoalEligibility.Eligible) {
            val copy = WeightGoalSafetyCopyPolicy.forResult(eligibility)
            state.value =
                current.copy(
                    eligibility = eligibility,
                    safetyCopy = copy,
                    notice = "${copy.title}. ${copy.body}",
                    formState = GoalFormState.SAVE_ERROR,
                )
            return
        }

        viewModelScope.launch {
            state.value = current.copy(formState = GoalFormState.SAVING, notice = null)
            val record = targets.current(id).getOrNull()
            val document =
                record
                    ?.document
                    ?.copy(
                        goal =
                            record.document.goal.copy(
                                targetWeightKg = kg,
                                pacePctPerWeek = pace ?: 0.0,
                                targetDate = date,
                            ),
                        energy =
                            record.document.energy.copy(
                                budgetKcal = budget,
                            ),
                    )
                    ?: TargetsDocument(
                        goal =
                            Goal(
                                targetWeightKg = kg,
                                pacePctPerWeek = pace ?: 0.0,
                                targetDate = date,
                            ),
                        energy =
                            Energy(
                                budgetKcal = budget,
                                floorKcal = ConstantsRegistry.floorKcal(profile?.sex).toDouble(),
                            ),
                        macros = Macros(MacroSplit.Preset("balanced")),
                    )
            val journal =
                GoalSaveJournal(
                    operationId = Uuid.random().toString(),
                    profileId = id,
                    baseVersion = current.baseVersion,
                    document = document,
                    safetyInput = safetyInput(current, kg, pace, budget),
                )
            runCatching { documents.writeText(journalKey(id), GoalSaveJournalIO.encode(journal)) }
                .onFailure {
                    state.value =
                        current.copy(
                            formState = GoalFormState.SAVE_ERROR,
                            notice = "That didn't save. Your draft is still here.",
                        )
                    return@launch
                }
            val outcome =
                current.baseVersion?.let { writers.studio().writeRevision(id, it, document) }
                    ?: writers.studio().writeFirst(id, document)
            when (outcome) {
                is TargetsWriteOutcome.Written -> {
                    val committed = journal.copy(committedVersion = outcome.record.version)
                    pendingCommittedJournal = committed
                    runCatching { documents.writeText(journalKey(id), GoalSaveJournalIO.encode(committed)) }
                    finishJournal(committed, outcome.diff)
                }

                is TargetsWriteOutcome.Rejected -> {
                    val formState =
                        if (outcome.error is TargetsWriteError.VersionConflict) {
                            GoalFormState.CONFLICT
                        } else {
                            GoalFormState.SAVE_ERROR
                        }
                    state.value = current.copy(formState = formState, notice = rejectionCopy(outcome.error))
                }
            }
        }
    }

    private suspend fun reload(ignoreDraft: Boolean = false) {
        val activeProfile = profile ?: profiles.active().getOrNull() ?: return
        profile = activeProfile
        val id = profileId ?: activeProfile.id
        profileId = id
        val trendWeight =
            weighIns
                .currentTrend(id, DayBoundary.epochDay(clock.now(), zone))
                .getOrNull()
                ?.current
                ?.value
        currentWeightKg = trendWeight ?: activeProfile.startWeightKg
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
        val pending =
            documents
                .readText(journalKey(id))
                ?.let(GoalSaveJournalIO::decode)
                ?.takeIf { it.profileId == id }
        if (pending != null && current?.document == pending.document) {
            val committed = pending.copy(committedVersion = current.version)
            pendingCommittedJournal = committed
            finishJournal(committed, emptyList())
            return
        }
        val draft =
            if (ignoreDraft) {
                null
            } else {
                documents
                    .readText(draftKey(id))
                    ?.let(GoalsEditorDraftIO::decode)
                    ?.takeIf { it.profileId == id }
            }
        val draftConflict = draft != null && draft.baseVersion != current?.version
        if (current == null) {
            val intent =
                documents
                    .readText(WeightFirstOnboardingStore.GOAL_INTENT_KEY)
                    ?.let(WeightFirstOnboardingStore::decodeDraft)
            updateSafety(
                state.value.copy(
                    loading = false,
                    formState = if (draftConflict) GoalFormState.CONFLICT else GoalFormState.EDITING,
                    hasTargets = false,
                    baseVersion = null,
                    goalWeightText =
                        draft?.displayWeight(activeUnit)
                            ?: intent?.targetWeightKg?.let(activeUnit::formatNumber).orEmpty(),
                    massUnit = activeUnit,
                    paceText = draft?.paceText ?: intent?.pacePctPerWeek?.let(::trimNumber).orEmpty(),
                    targetDateText = draft?.targetDate.orEmpty(),
                    budgetText = draft?.budgetText.orEmpty(),
                    mode = draft?.mode ?: intent?.goalMode ?: WeightGoalMode.LOSS,
                    pregnant = draft?.pregnant ?: SafetyAnswer.NOT_ANSWERED,
                    breastfeeding = draft?.breastfeeding ?: SafetyAnswer.NOT_ANSWERED,
                    eatingDisorderConcern = draft?.eatingDisorderConcern ?: SafetyAnswer.NOT_ANSWERED,
                    medicallyInfluencedWeight = draft?.medicallyInfluencedWeight ?: SafetyAnswer.NOT_ANSWERED,
                    currentWeightIsStarting = trendWeight == null && activeProfile.startWeightKg != null,
                    dirty = draft != null,
                    notice = draftConflictNotice(draftConflict),
                ),
            )
            return
        }
        val document = current.document
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
                formState = if (draftConflict) GoalFormState.CONFLICT else GoalFormState.EDITING,
                hasTargets = true,
                baseVersion = current.version,
                goalWeightText =
                    draft?.displayWeight(activeUnit)
                        ?: activeUnit.formatNumber(document.goal.targetWeightKg),
                massUnit = activeUnit,
                paceText = draft?.paceText ?: trimNumber(document.goal.pacePctPerWeek),
                targetDateText = draft?.targetDate ?: document.goal.targetDate.orEmpty(),
                budgetText =
                    draft?.budgetText
                        ?: document.energy.budgetKcal
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
                currentWeightIsStarting = trendWeight == null && activeProfile.startWeightKg != null,
                dirty = draft != null,
                notice = draftConflictNotice(draftConflict),
                history = history,
            ),
        )
    }

    private fun updateSafety(updated: GoalsEditorUi) {
        val target = DecimalInput.parse(updated.goalWeightText)?.let(activeUnit::toKilograms)
        val pace = DecimalInput.parse(updated.paceText)
        val budget = updated.budgetText.takeIf(String::isNotBlank)?.let(DecimalInput::parse)
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
        if (ui.loading || !ui.dirty) return
        val draft =
            GoalsEditorDraft(
                profileId = id,
                baseVersion = ui.baseVersion,
                massUnit = activeUnit,
                targetWeightText = ui.goalWeightText,
                paceText = ui.paceText,
                targetDate = ui.targetDateText,
                budgetText = ui.budgetText,
                mode = ui.mode,
                pregnant = ui.pregnant,
                breastfeeding = ui.breastfeeding,
                eatingDisorderConcern = ui.eatingDisorderConcern,
                medicallyInfluencedWeight = ui.medicallyInfluencedWeight,
            )
        viewModelScope.launch { documents.writeText(draftKey(id), GoalsEditorDraftIO.encode(draft)) }
    }

    private fun GoalsEditorDraft.displayWeight(unit: MassUnit): String =
        restoredGoalWeightText(targetWeightText, massUnit, unit)

    private fun draftConflictNotice(hasConflict: Boolean): String? =
        if (hasConflict) "Your draft was based on a different version. Review differences." else null

    private suspend fun finishJournal(
        journal: GoalSaveJournal,
        diff: List<String>,
    ) {
        val completed =
            runCatching {
                documents.writeText(
                    safetyKey(journal.profileId),
                    DocumentCodec.json.encodeToString(
                        WeightGoalSafetyInput.serializer(),
                        journal.safetyInput,
                    ),
                )
                documents.remove(draftKey(journal.profileId))
                documents.remove(journalKey(journal.profileId))
            }.isSuccess
        if (!completed) {
            state.value =
                state.value.copy(
                    formState = GoalFormState.SAVE_ERROR,
                    notice = "Goal saved; finishing its safety record. Retry to reconcile.",
                )
            return
        }
        pendingCommittedJournal = null
        reload(ignoreDraft = true)
        state.value =
            state.value.copy(
                formState = GoalFormState.SAVED,
                dirty = false,
                diff = diff,
                notice = "Saved as v${journal.committedVersion}.",
            )
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

    private fun journalKey(profileId: String): String = "weight/goal-editor-save-v1/$profileId"

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

internal fun restoredGoalWeightText(
    text: String,
    storedUnit: MassUnit,
    displayUnit: MassUnit,
): String {
    val value = DecimalInput.parse(text) ?: return text
    return displayUnit.formatNumber(storedUnit.toKilograms(value))
}
