package app.wlo.feature.f01.onboarding.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.MassUnit
import app.wlo.core.common.getOrNull
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalMode
import app.wlo.feature.f01.onboarding.domain.FinishWeightFirstOnboarding
import app.wlo.feature.f01.onboarding.domain.FirstWeightSource
import app.wlo.feature.f01.onboarding.domain.WeightFirstDraft
import app.wlo.feature.f01.onboarding.domain.WeightFirstOnboardingStore
import app.wlo.feature.f01.onboarding.domain.WeightFirstStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

public data class WeightFirstUiState(
    val loading: Boolean = true,
    val draft: WeightFirstDraft = WeightFirstDraft(),
    val targetText: String = "",
    val paceText: String = "",
    val weightText: String = "",
    val error: String? = null,
    val completed: Boolean = false,
) {
    val eligibility: WeightGoalEligibility get() = draft.eligibility()
}

public class WeightFirstOnboardingViewModel(
    private val store: WeightFirstOnboardingStore,
    private val finisher: FinishWeightFirstOnboarding,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WeightFirstUiState())
    public val state: StateFlow<WeightFirstUiState> = mutableState

    init {
        viewModelScope.launch {
            val draft = store.read()
            mutableState.value =
                WeightFirstUiState(
                    loading = false,
                    draft = draft,
                    targetText = draft.targetWeightKg?.let(draft.unit.orKg()::formatNumber).orEmpty(),
                    paceText = draft.pacePctPerWeek?.toString().orEmpty(),
                    weightText = draft.firstWeightKg?.let(draft.unit.orKg()::formatNumber).orEmpty(),
                )
        }
    }

    public fun next(): Unit = update { it.copy(step = WeightFirstStep.entries[(it.step.ordinal + 1).coerceAtMost(3)]) }

    public fun back(): Unit = update { it.copy(step = WeightFirstStep.entries[(it.step.ordinal - 1).coerceAtLeast(0)]) }

    public fun chooseUnit(unit: MassUnit): Unit = update { it.copy(unit = unit) }

    public fun chooseGoal(mode: WeightGoalMode?): Unit = update { it.copy(goalMode = mode) }

    public fun chooseSource(source: FirstWeightSource): Unit = update { it.copy(weightSource = source) }

    public fun targetChanged(text: String) {
        mutableState.value = mutableState.value.copy(targetText = text, error = null)
        persistNumeric()
    }

    public fun paceChanged(text: String) {
        mutableState.value = mutableState.value.copy(paceText = text, error = null)
        persistNumeric()
    }

    public fun weightChanged(text: String) {
        mutableState.value = mutableState.value.copy(weightText = text, error = null)
        persistNumeric()
    }

    public fun finish() {
        val parsed = parsedDraft() ?: return
        viewModelScope.launch {
            val result = finisher(parsed)
            mutableState.value =
                if (result.getOrNull() != null) {
                    mutableState.value.copy(draft = parsed, completed = true)
                } else {
                    mutableState.value.copy(error = "Setup did not save. Nothing was discarded; try again.")
                }
        }
    }

    private fun persistNumeric() {
        val parsed = parsedDraft(showErrors = false) ?: return
        update { parsed }
    }

    private fun parsedDraft(showErrors: Boolean = true): WeightFirstDraft? {
        val state = mutableState.value
        val unit = state.draft.unit ?: return state.draft

        fun mass(text: String): Double? =
            text
                .trim()
                .takeIf(String::isNotEmpty)
                ?.toDoubleOrNull()
                ?.let(unit::toKilograms)
        val weight = mass(state.weightText)
        val target = mass(state.targetText)
        val pace =
            state.paceText
                .trim()
                .takeIf(String::isNotEmpty)
                ?.toDoubleOrNull()
        val invalid =
            (state.weightText.isNotBlank() && (weight == null || weight !in 30.0..300.0)) ||
                (state.targetText.isNotBlank() && (target == null || target !in 30.0..300.0)) ||
                (state.paceText.isNotBlank() && (pace == null || pace < 0.0))
        if (invalid) {
            if (showErrors) mutableState.value = state.copy(error = "Check the entered weight and pace values.")
            return null
        }
        return state.draft.copy(firstWeightKg = weight, targetWeightKg = target, pacePctPerWeek = pace)
    }

    private fun update(transform: (WeightFirstDraft) -> WeightFirstDraft) {
        val draft = transform(mutableState.value.draft)
        mutableState.value = mutableState.value.copy(draft = draft, error = null)
        viewModelScope.launch { store.write(draft) }
    }
}

private fun MassUnit?.orKg(): MassUnit = this ?: MassUnit.KILOGRAM
