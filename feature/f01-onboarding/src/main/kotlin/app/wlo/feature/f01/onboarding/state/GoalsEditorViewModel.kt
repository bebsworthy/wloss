package app.wlo.feature.f01.onboarding.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.getOrNull
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.TargetsRepository
import app.wlo.core.data.TargetsWriteError
import app.wlo.core.data.TargetsWriteOutcome
import app.wlo.core.data.TargetsWriters
import app.wlo.core.documents.TargetsWriterId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
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

    /** Writes vN+1 through the STUDIO_F01 door (R-B2; wizard = v1 of the same editor). */
    public data object Save : GoalsEditorEvent

    /** Copies an older version forward — never a history rewrite (A.1). */
    public data class RevertTo(
        public val version: Int,
    ) : GoalsEditorEvent

    public data object DismissNotice : GoalsEditorEvent
}

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
    public val goalWeightText: String = "",
    public val paceText: String = "",
    public val targetDateText: String = "",
    public val budgetText: String = "",
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
    private val clock: ClockPort,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private var profileId: String? = null

    private val state = MutableStateFlow(GoalsEditorUi())

    /** Renderable state. */
    public val uiState: StateFlow<GoalsEditorUi> = state

    init {
        viewModelScope.launch { reload() }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: GoalsEditorEvent) {
        when (event) {
            is GoalsEditorEvent.GoalWeightChange -> state.value = state.value.copy(goalWeightText = event.text)
            is GoalsEditorEvent.PaceChange -> state.value = state.value.copy(paceText = event.text)
            is GoalsEditorEvent.TargetDateChange -> state.value = state.value.copy(targetDateText = event.text)
            is GoalsEditorEvent.BudgetChange -> state.value = state.value.copy(budgetText = event.text)
            GoalsEditorEvent.DismissNotice -> state.value = state.value.copy(notice = null)
            GoalsEditorEvent.Save -> save()
            is GoalsEditorEvent.RevertTo ->
                viewModelScope.launch {
                    val id = profileId ?: return@launch
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
        val kg = current.goalWeightText.toDoubleOrNull()
        if (kg == null || kg <= 0.0) {
            state.value = state.value.copy(notice = "check the goal weight — a number in kg")
            return
        }
        val pace = current.paceText.toDoubleOrNull()
        if (pace == null || pace <= 0.0) {
            state.value = state.value.copy(notice = "check the pace — % of bodyweight per week")
            return
        }
        val date = current.targetDateText.trim().ifEmpty { null }
        date?.let {
            runCatching { Instant.parse("${it}T12:00:00Z") }.getOrNull() ?: run {
                state.value = state.value.copy(notice = "check the target date — YYYY-MM-DD")
                return
            }
        }
        val budget = current.budgetText.toDoubleOrNull()

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
                            pacePctPerWeek = pace,
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
        val id = profileId ?: profiles.active().getOrNull()?.id ?: return
        profileId = id
        val current = targets.current(id).getOrNull()
        if (current == null) {
            state.value = state.value.copy(loading = false, hasTargets = false)
            return
        }
        val document = current.document
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
        state.value =
            state.value.copy(
                loading = false,
                hasTargets = true,
                goalWeightText = trimNumber(document.goal.targetWeightKg),
                paceText = trimNumber(document.goal.pacePctPerWeek),
                targetDateText = document.goal.targetDate.orEmpty(),
                budgetText =
                    document.energy.budgetKcal
                        ?.let(::trimNumber)
                        .orEmpty(),
                history = history,
            )
    }

    private fun rejectionCopy(error: TargetsWriteError): String =
        when (error) {
            is TargetsWriteError.InvariantViolated ->
                "the plan's rules say no — " +
                    (error.violations.firstOrNull()?.let { violation -> violation.javaClass.simpleName } ?: "invariant violated")
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
