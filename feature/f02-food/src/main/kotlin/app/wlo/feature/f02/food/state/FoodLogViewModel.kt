package app.wlo.feature.f02.food.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.fold
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.FoodRepository
import app.wlo.core.data.NewCustomFood
import app.wlo.core.data.NewDiaryEntry
import app.wlo.core.data.ProfileRepository
import app.wlo.core.model.EntryVia
import app.wlo.core.model.FoodItem
import app.wlo.core.model.MealSlot
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Ladder intents (MVI-lite): one channel in, [FoodLogUiState] out. */
public sealed interface FoodLogEvent {
    public data class QueryChange(
        public val text: String,
    ) : FoodLogEvent

    /** The free-text note draft (F02 §3 rung 4, manual). */
    public data class HintChange(
        public val text: String,
    ) : FoodLogEvent

    public data class SlotChange(
        public val slot: MealSlot,
    ) : FoodLogEvent

    public data class PickHit(
        public val hit: FoodHitUi,
    ) : FoodLogEvent

    public data object DismissPortion : FoodLogEvent

    public data class QuantityChange(
        public val text: String,
    ) : FoodLogEvent

    public data class UnitChange(
        public val unit: String,
    ) : FoodLogEvent

    public data class PresetChange(
        public val index: Int,
    ) : FoodLogEvent

    public data object SavePortion : FoodLogEvent

    public data object OpenQuickAdd : FoodLogEvent

    public data class QuickAddChange(
        public val kcalText: String,
        public val name: String,
    ) : FoodLogEvent

    public data object SaveQuickAdd : FoodLogEvent

    public data object DismissQuickAdd : FoodLogEvent

    /** Opens a blank custom-food sheet; [food] pre-fills an edit of a custom row. */
    public data class OpenCustomFood(
        public val food: FoodItem? = null,
    ) : FoodLogEvent

    public data class CustomChange(
        public val draft: CustomFoodDraft,
    ) : FoodLogEvent

    public data object SaveCustomFood : FoodLogEvent

    public data object DismissCustom : FoodLogEvent

    public data object DismissNotice : FoodLogEvent

    /** Logs the free-text note as a `held` placeholder (0 kcal until an estimate lands). */
    public data class SaveTextHint(
        public val text: String,
    ) : FoodLogEvent
}

/**
 * The manual input ladder's state holder (F02 §3 rungs 4–5, R-U15). Search is
 * debounced and runs against the local FTS5 catalog (the 50 ms budget is the
 * repository's; the debounce only stops mid-word queries). Saves go straight
 * to the diary — save is live, editing optional (F02 §4).
 */
public class FoodLogViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    initialQuickAdd: Boolean = false,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()

    private val state =
        MutableStateFlow(
            FoodLogUiState(
                slot = defaultSlot(clock.now().toLocalDateTime(zone).time),
                quickAdd = initialQuickAdd.takeIf { it }?.let { QuickAddDraft("", "") },
            ),
        )

    /** Debounced query source — UI writes here, searches run off it. */
    private val queryFlow = MutableStateFlow("")

    /** The renderable state. */
    public val uiState: StateFlow<FoodLogUiState> = state

    init {
        observeQueries()
    }

    /** Mid-word queries are debounced; each settled query runs one FTS search. */
    @OptIn(FlowPreview::class)
    private fun observeQueries() {
        viewModelScope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { query -> runSearch(query) }
        }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: FoodLogEvent) {
        when (event) {
            is FoodLogEvent.QueryChange -> {
                state.value = state.value.copy(query = event.text, searching = event.text.isNotBlank())
                queryFlow.value = event.text
            }

            is FoodLogEvent.HintChange -> state.value = state.value.copy(hintText = event.text)
            is FoodLogEvent.SlotChange -> state.value = state.value.copy(slot = event.slot)
            is FoodLogEvent.PickHit ->
                state.value = state.value.copy(selection = PortionSelection.forFood(event.hit.food))
            FoodLogEvent.DismissPortion -> state.value = state.value.copy(selection = null)
            is FoodLogEvent.QuantityChange ->
                state.value =
                    state.value.selection?.let { current ->
                        state.value.copy(
                            selection = current.copy(quantityText = event.text, chosenPresetIndex = null),
                        )
                    } ?: state.value
            is FoodLogEvent.UnitChange ->
                state.value =
                    state.value.selection?.let { current ->
                        state.value.copy(selection = current.copy(unit = event.unit))
                    } ?: state.value
            is FoodLogEvent.PresetChange ->
                state.value =
                    state.value.selection?.let { current ->
                        if (event.index !in current.presets.indices) return
                        state.value.copy(
                            selection =
                                current.copy(
                                    chosenPresetIndex = event.index,
                                    quantityText = "1",
                                    unit = PortionSelection.UNIT_SERVING,
                                ),
                        )
                    } ?: state.value
            FoodLogEvent.SavePortion -> savePortion()
            FoodLogEvent.OpenQuickAdd -> state.value = state.value.copy(quickAdd = QuickAddDraft("", ""))
            is FoodLogEvent.QuickAddChange ->
                state.value =
                    state.value.quickAdd?.let { current ->
                        val next = current.copy(kcalText = event.kcalText, name = event.name)
                        state.value.copy(quickAdd = next)
                    } ?: state.value
            FoodLogEvent.SaveQuickAdd -> saveQuickAdd()
            FoodLogEvent.DismissQuickAdd -> state.value = state.value.copy(quickAdd = null)
            is FoodLogEvent.OpenCustomFood ->
                state.value =
                    state.value.copy(
                        customDraft =
                            event.food?.let { food ->
                                CustomFoodDraft(
                                    editId = food.id.takeIf { food.source.wireName == "custom" },
                                    name = food.name,
                                    brand = food.brand ?: "",
                                    kcalText = food.kcalPer100g?.let(::formatQuantity)?.removeSuffix(".0") ?: "",
                                    proteinText = food.proteinGPer100g?.let(::formatQuantity)?.removeSuffix(".0") ?: "",
                                    carbText = food.carbGPer100g?.let(::formatQuantity)?.removeSuffix(".0") ?: "",
                                    fatText = food.fatGPer100g?.let(::formatQuantity)?.removeSuffix(".0") ?: "",
                                    fiberText = food.fiberGPer100g?.let(::formatQuantity)?.removeSuffix(".0") ?: "",
                                    macrosVerified = food.macrosVerified,
                                )
                            } ?: CustomFoodDraft(),
                    )
            is FoodLogEvent.CustomChange -> state.value = state.value.copy(customDraft = event.draft)
            FoodLogEvent.SaveCustomFood -> saveCustomFood()
            FoodLogEvent.DismissCustom -> state.value = state.value.copy(customDraft = null)
            FoodLogEvent.DismissNotice -> state.value = state.value.copy(notice = null)
            is FoodLogEvent.SaveTextHint -> saveTextHint(event.text)
        }
    }

    private suspend fun runSearch(query: String) {
        val profileId = activeProfileId() ?: return
        if (query.isBlank()) {
            state.value = state.value.copy(results = emptyList(), searching = false)
            return
        }
        val hits =
            when (val result = foods.search(profileId, query)) {
                is WloResult.Ok -> result.value.map(FoodHitUi::from)
                is WloResult.Err -> emptyList()
            }
        state.value = state.value.copy(results = hits, searching = false)
    }

    private fun savePortion() {
        val selection = state.value.selection ?: return
        val grams = selection.grams
        if (grams <= 0.0) return
        viewModelScope.launch {
            val profileId = activeProfileId() ?: return@launch
            val via =
                if (selection.food.source.wireName == "custom") EntryVia.MANUAL_CUSTOM else EntryVia.MANUAL_SEARCH
            val outcome =
                diary.logEntry(
                    entry =
                        NewDiaryEntry(
                            profileId = profileId,
                            dayEpochDay = DayBoundary.epochDay(clock.now(), zone),
                            mealSlot = state.value.slot,
                            foodItemId = selection.food.id,
                            quantity = selection.quantityText.toDoubleOrNull() ?: grams,
                            unit = selection.unit,
                            servingGrams =
                                selection.presets.getOrNull(selection.chosenPresetIndex ?: 0)?.grams,
                            enteredVia = via,
                        ),
                    at = clock.now(),
                )
            when (outcome) {
                is WloResult.Ok ->
                    state.value =
                        state.value.copy(
                            selection = null,
                            notice = NoticeUi("saved to the diary"),
                        )
                is WloResult.Err ->
                    state.value = state.value.copy(notice = NoticeUi(outcome.error.userCopy(), NoticeState.RAIL))
            }
        }
    }

    private fun saveQuickAdd() {
        val draft = state.value.quickAdd ?: return
        val kcal = draft.kcalText.toDoubleOrNull() ?: return
        viewModelScope.launch {
            val profileId = activeProfileId() ?: return@launch
            val outcome =
                diary.logEntry(
                    entry =
                        NewDiaryEntry(
                            profileId = profileId,
                            dayEpochDay = DayBoundary.epochDay(clock.now(), zone),
                            mealSlot = state.value.slot,
                            textHint = draft.name.takeIf { it.isNotBlank() },
                            quantity = 0.0,
                            unit = "g",
                            kcalOnly = kcal,
                            enteredVia = EntryVia.QUICK_ADD,
                        ),
                    at = clock.now(),
                )
            when (outcome) {
                is WloResult.Ok ->
                    state.value = state.value.copy(quickAdd = null, notice = NoticeUi("saved to the diary"))
                is WloResult.Err ->
                    state.value = state.value.copy(notice = NoticeUi(outcome.error.userCopy(), NoticeState.RAIL))
            }
        }
    }

    private fun saveTextHint(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val profileId = activeProfileId() ?: return@launch
            diary.logEntry(
                entry =
                    NewDiaryEntry(
                        profileId = profileId,
                        dayEpochDay = DayBoundary.epochDay(clock.now(), zone),
                        mealSlot = state.value.slot,
                        textHint = text.trim(),
                        quantity = 0.0,
                        unit = "g",
                        enteredVia = EntryVia.TEXT_HINT,
                    ),
                at = clock.now(),
            )
            state.value =
                state.value.copy(notice = NoticeUi("noted — it waits in the diary until an estimate lands"))
        }
    }

    private fun saveCustomFood() {
        val draft = state.value.customDraft ?: return
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            val profileId = activeProfileId() ?: return@launch
            val food =
                NewCustomFood(
                    profileId = profileId,
                    name = draft.name.trim(),
                    brand = draft.brand.trim().takeIf { it.isNotEmpty() },
                    kcalPer100g = draft.kcalText.toDoubleOrNull(),
                    proteinGPer100g = draft.proteinText.toDoubleOrNull(),
                    carbGPer100g = draft.carbText.toDoubleOrNull(),
                    fatGPer100g = draft.fatText.toDoubleOrNull(),
                    fiberGPer100g = draft.fiberText.toDoubleOrNull(),
                    macrosVerified = draft.macrosVerified,
                )
            val outcome =
                if (draft.editId ==
                    null
                ) {
                    foods.createCustomFood(food, clock.now())
                } else {
                    foods.updateCustomFood(draft.editId, food, clock.now())
                }
            when (outcome) {
                is WloResult.Ok ->
                    state.value =
                        state.value.copy(
                            customDraft = null,
                            notice = NoticeUi("${food.name} is in your catalog"),
                            query = "",
                        )
                is WloResult.Err ->
                    state.value = state.value.copy(notice = NoticeUi(outcome.error.userCopy(), NoticeState.RAIL))
            }
        }
    }

    private suspend fun activeProfileId(): String? = profiles.active().fold(onOk = { it?.id }, onErr = { null })

    /** The rail rejection, in kind copy (F02 §6: mistakes are data, never sin). */
    private fun app.wlo.core.common.AppError.userCopy(): String =
        when (this) {
            is app.wlo.core.common.AppError.InvalidInput ->
                if (detail.contains("energy-density rail")) {
                    "Nothing is denser than pure fat — 900 kcal per 100 g is the physical ceiling. " +
                        "Check the label once more; a value above that is almost always a typo."
                } else {
                    detail
                }
            else -> "that didn't save — nothing changed"
        }

    public companion object {
        private const val SEARCH_DEBOUNCE_MS: Long = 150L

        /** Default meal slot from the wall clock (chips override). */
        public fun defaultSlot(time: LocalTime): MealSlot =
            when {
                time < LocalTime(10, 30) -> MealSlot.BREAKFAST
                time < LocalTime(15, 0) -> MealSlot.LUNCH
                time < LocalTime(20, 0) -> MealSlot.DINNER
                else -> MealSlot.SNACK
            }

        /** "55.0" → "55", "55.25" → "55.25" — label-friendly quantities. */
        public fun formatQuantity(value: Double): String {
            val rounded = (value * 100).toLong() / 100.0
            return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
        }
    }
}
