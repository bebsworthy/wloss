package app.wlo.feature.f06.weight.state

import app.wlo.core.common.DecimalInput
import app.wlo.core.common.MassUnit
import app.wlo.core.common.WloResult
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.TargetsRepository
import app.wlo.core.data.TargetsWriteError
import app.wlo.core.data.TargetsWriteOutcome
import app.wlo.core.documents.Energy
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.documents.TargetsRecord
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Provided

public data class GoalTargetDraft(
    public val text: String = "",
    public val originalText: String = "",
    public val unit: MassUnit = MassUnit.KILOGRAM,
    public val loading: Boolean = true,
    public val saving: Boolean = false,
    public val error: String? = null,
) {
    public val kilograms: Double?
        get() =
            DecimalInput
                .parse(text)
                ?.takeIf { it > 0.0 }
                ?.let(unit::toKilograms)
                ?.takeIf(Double::isFinite)
    public val changed: Boolean
        get() = DecimalInput.parse(text) != DecimalInput.parse(originalText)
}

/** Target-only transaction through the existing Studio door; never edits calorie plans or screening. */
public class GoalTargetEditor(
    private val profiles: ProfileRepository,
    private val targets: TargetsRepository,
    @Provided private val write: suspend (String, Int?, TargetsDocument) -> TargetsWriteOutcome,
) {
    private val state = MutableStateFlow<GoalTargetDraft?>(null)
    public val uiState: StateFlow<GoalTargetDraft?> = state
    private var profile: Profile? = null
    private var base: TargetsRecord? = null
    private var generation: Long = 0

    public suspend fun open(unit: MassUnit) {
        if (state.value?.saving == true) return
        val request = ++generation
        state.value = GoalTargetDraft(unit = unit)
        try {
            val person = (profiles.active() as? WloResult.Ok)?.value ?: error("No active profile")
            val result = targets.current(person.id)
            val record =
                when (result) {
                    is WloResult.Ok -> result.value
                    is WloResult.Err -> error("Read failed")
                }
            if (state.value == null || request != generation) return
            profile = person
            base = record
            val text =
                record
                    ?.document
                    ?.goal
                    ?.targetWeightKg
                    ?.let(unit::formatNumber)
                    .orEmpty()
            state.value = GoalTargetDraft(text, text, unit, loading = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (request == generation) {
                state.value?.let { state.value = it.copy(error = "Couldn't load your goal. Close and try again.") }
            }
        }
    }

    public fun edit(text: String) {
        state.value?.takeUnless { it.saving || it.loading }?.let { state.value = it.copy(text = text, error = null) }
    }

    public fun dismiss() {
        if (state.value?.saving != true) {
            generation++
            state.value = null
        }
    }

    /** True only after a committed save; conflicts retain the draft and require reopening. */
    public suspend fun save(): Boolean {
        val draft = state.value ?: return false
        if (draft.loading || draft.saving) return false
        val kg = draft.kilograms ?: return false
        if (!draft.changed) {
            dismiss()
            return false
        }
        val person = profile ?: return false
        state.value = draft.copy(saving = true, error = null)
        val document = targetDocument(base?.document, kg, ConstantsRegistry.floorKcal(person.sex).toDouble())
        try {
            when (val result = write(person.id, base?.version, document)) {
                is TargetsWriteOutcome.Written -> {
                    state.value = null
                    return true
                }
                is TargetsWriteOutcome.Rejected -> {
                    val message =
                        if (result.error is TargetsWriteError.VersionConflict) {
                            "Your goal changed elsewhere. Close and reopen to review the latest value."
                        } else {
                            "Couldn't save your goal. Your change is still here; try again."
                        }
                    state.value = draft.copy(error = message)
                }
            }
        } catch (cancelled: CancellationException) {
            state.value = draft
            throw cancelled
        } catch (_: Exception) {
            state.value = draft.copy(error = "Couldn't save your goal. Your change is still here; try again.")
        }
        return false
    }
}

/** Legacy planning fields remain untouched; a first reference target requests no pace or date. */
internal fun targetDocument(
    old: TargetsDocument?,
    kg: Double,
    floor: Double,
): TargetsDocument =
    old?.copy(goal = old.goal.copy(targetWeightKg = kg))
        ?: TargetsDocument(
            Goal(kg, pacePctPerWeek = 0.0),
            Energy(floorKcal = floor),
            Macros(MacroSplit.Preset("balanced")),
        )
