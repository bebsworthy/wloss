package app.wlo.feature.f02.food.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.fold
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.EditDiaryEntry
import app.wlo.core.data.FoodRepository
import app.wlo.core.data.NewDiaryEntry
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.RoomDiaryRepository
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.DiaryEntry
import app.wlo.core.model.EntryVia
import app.wlo.core.model.MealSlot
import app.wlo.core.model.Provenance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Diary intents (MVI-lite). */
public sealed interface DiaryEvent {
    /** Cycles the day-status marker: (none →) logged → skipped → fasted → logged. */
    public data object CycleStatus : DiaryEvent

    public data class EntryTap(
        public val entryId: String,
    ) : DiaryEvent

    public data object DismissEntry : DiaryEvent

    public data object BeginEdit : DiaryEvent

    public data class EditChange(
        public val text: String,
    ) : DiaryEvent

    public data object CancelEdit : DiaryEvent

    public data object SaveEdit : DiaryEvent

    public data object DeleteEntry : DiaryEvent

    public data object DismissNotice : DiaryEvent

    /** The one-tap water quick-add (F02 §3: drinks land like any other entry). */
    public data object WaterQuickAdd : DiaryEvent
}

/**
 * The diary day's state holder (R-B1/R-B8). Observes the day through the
 * [DiaryRepository]; edits append to the revision chain BEFORE the entry moves,
 * so the provenance sheet always shows the honest history.
 */
public class DiaryViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    initialDay: Long?,
    initialEntryId: String? = null,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private val today: Long = DayBoundary.epochDay(clock.now(), zone)
    private val day: MutableStateFlow<Long> = MutableStateFlow(initialDay ?: today)

    /** Session-scoped day-status markers (see [DayStatusUi]). */
    private val statuses = MutableStateFlow<Map<Long, DayStatusUi>>(emptyMap())
    private val openEntryId = MutableStateFlow<String?>(initialEntryId)
    private val editDraft = MutableStateFlow<Pair<Boolean, String>?>(null)
    private val notice = MutableStateFlow<NoticeUi?>(null)

    /** Renderable state. */
    @OptIn(ExperimentalCoroutinesApi::class)
    public val uiState: StateFlow<DiaryUiState> =
        combine(
            day.flatMapLatest { d ->
                val profileId = profiles.activeSync() ?: return@flatMapLatest flowOf(null)
                diary.observeDay(profileId, d).map { result -> result.getOrNull() }
            },
            combine(statuses, openEntryId, editDraft, notice, ::Merge4),
            day,
        ) { dayDiary, session, d -> Triple(dayDiary, session, d) }
            .map { (dayDiary, session, d) -> render(dayDiary, session, d) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, DiaryUiState.loading(initialDay ?: today))

    /** MVI-lite intent entry point. */
    public fun onEvent(event: DiaryEvent) {
        when (event) {
            DiaryEvent.CycleStatus -> {
                val current = statuses.value[day.value]
                statuses.value =
                    statuses.value +
                    (
                        day.value to
                            when (current) {
                                null -> DayStatusUi.LOGGED
                                DayStatusUi.LOGGED -> DayStatusUi.SKIPPED
                                DayStatusUi.SKIPPED -> DayStatusUi.FASTED
                                DayStatusUi.FASTED -> DayStatusUi.LOGGED
                            }
                    )
            }

            is DiaryEvent.EntryTap -> {
                openEntryId.value = event.entryId
                editDraft.value = null
            }

            DiaryEvent.DismissEntry -> openEntryId.value = null
            DiaryEvent.BeginEdit ->
                editDraft.value =
                    true to (
                        uiState.value.openEntry
                            ?.entry
                            ?.let { entryQuantityText(it) } ?: ""
                    )
            is DiaryEvent.EditChange -> editDraft.value = (editDraft.value?.first ?: true) to event.text
            DiaryEvent.CancelEdit -> editDraft.value = null
            DiaryEvent.SaveEdit -> saveEdit()
            DiaryEvent.DeleteEntry -> deleteEntry()
            DiaryEvent.DismissNotice -> notice.value = null
            DiaryEvent.WaterQuickAdd -> waterQuickAdd()
        }
    }

    private suspend fun render(
        dayDiary: app.wlo.core.data.DayDiary?,
        session: Merge4,
        dayEpochDay: Long,
    ): DiaryUiState {
        if (dayDiary == null) return DiaryUiState.loading(dayEpochDay)
        // Resolve catalog names once per render (rows carry the food's name,
        // not its storage id).
        val names = HashMap<String, String>()
        for (entry in dayDiary.entries) {
            val foodId = entry.foodItemId
            if (foodId != null && foodId !in names) {
                names[foodId] = foods.byId(foodId).fold(onOk = { it?.name }, onErr = { null }) ?: foodId
            }
        }
        val slotSections =
            dayDiary.slots.map { (slot, entries) ->
                SlotUi(
                    slot = slot,
                    entries = entries.map { entry -> entry.toRow(entry.foodItemId?.let(names::get)) },
                    kcal = if (entries.any { it.kcal == null }) null else entries.sumOf { it.kcal!! },
                )
            }
        val totals = dayDiary.totals
        val detail = openEntryDetail(dayDiary, session)
        return DiaryUiState(
            dayEpochDay = dayEpochDay,
            dayLabel = dayLabel(dayEpochDay),
            slots = slotSections,
            totals = totals.kcal?.let { DerivedValue(it, dayProvenance(dayDiary.entries)) },
            macroLine = macroLine(totals.proteinG, totals.carbG, totals.fatG, totals.fiberG),
            dayStatus = session.statuses[dayEpochDay] ?: DayStatusUi.LOGGED.takeIf { dayDiary.entries.isNotEmpty() },
            openEntry = detail,
            notice = session.notice,
        )
    }

    private suspend fun openEntryDetail(
        dayDiary: app.wlo.core.data.DayDiary,
        session: Merge4,
    ): EntryDetailUi? {
        val id = session.openEntryId ?: return null
        val entry = dayDiary.entries.firstOrNull { it.id == id } ?: return null
        val revisions =
            when (val result = diary.revisionsOf(id)) {
                is WloResult.Ok -> result.value.map { rev -> RevisionUi(rev, rev.toDerived()) }
                is WloResult.Err -> emptyList()
            }
        val foodName =
            entry.foodItemId?.let { foodId -> foods.byId(foodId).fold(onOk = { it?.name }, onErr = { null }) }
        val row = entry.toRow(foodName)
        return EntryDetailUi(
            entry = row,
            rows =
                buildList {
                    add("entry" to row.title)
                    add("logged" to timestamp(entry.createdAt))
                    entry.editedAt?.let { add("last edit" to timestamp(it)) }
                    add("method" to methodWord(entry.enteredVia))
                    add("formula" to RoomDiaryRepository.PORTION_FORMULA_VERSION)
                    add("portion" to row.subtitle)
                    foodName?.let { add("food" to it) }
                },
            revisions = revisions,
            editing = session.edit?.first ?: false,
            editText = session.edit?.second ?: "",
        )
    }

    private fun saveEdit() {
        val detail = uiState.value.openEntry ?: return
        val draft = editDraft.value ?: return
        val value = draft.second.toDoubleOrNull() ?: return
        val entry = detail.entry
        viewModelScope.launch {
            val profileId = profiles.activeSync() ?: return@launch
            val dayDiary = diary.day(profileId, day.value).getOrNull() ?: return@launch
            val current = dayDiary.entries.firstOrNull { it.id == entry.id } ?: return@launch
            val edit =
                if (current.kcalOnlyQuickAdd) {
                    EditDiaryEntry(
                        mealSlot = current.mealSlot,
                        foodItemId = null,
                        textHint = current.textHint,
                        quantity = value,
                        kcalOnly = value,
                    )
                } else {
                    EditDiaryEntry(
                        mealSlot = current.mealSlot,
                        foodItemId = current.foodItemId,
                        textHint = current.textHint,
                        quantity = value,
                        unit = current.unit,
                        servingGrams = current.servingGrams(current.foodItemId),
                    )
                }
            when (diary.editEntry(current.id, edit, clock.now())) {
                is WloResult.Ok -> {
                    editDraft.value = null
                    notice.value = NoticeUi("corrected — the prior version stays in the history")
                }

                is WloResult.Err -> notice.value = NoticeUi("that didn't save — nothing changed", NoticeState.RAIL)
            }
        }
    }

    private fun deleteEntry() {
        val id =
            uiState.value.openEntry
                ?.entry
                ?.id ?: return
        viewModelScope.launch {
            when (diary.archiveEntry(id, clock.now())) {
                is WloResult.Ok -> {
                    openEntryId.value = null
                    notice.value = NoticeUi("removed — restorable from history")
                }

                is WloResult.Err -> notice.value = NoticeUi("that didn't save — nothing changed", NoticeState.RAIL)
            }
        }
    }

    private fun waterQuickAdd() {
        viewModelScope.launch {
            val profileId = profiles.activeSync() ?: return@launch
            diary.logEntry(
                entry =
                    NewDiaryEntry(
                        profileId = profileId,
                        dayEpochDay = day.value,
                        mealSlot = MealSlot.DRINK,
                        textHint = "water",
                        quantity = WATER_ML,
                        unit = "g",
                        kcalOnly = 0.0,
                        enteredVia = EntryVia.QUICK_ADD,
                    ),
                at = clock.now(),
            )
        }
    }

    // --- rendering helpers (pure) ---

    private fun DiaryEntry.toRow(foodName: String? = null): EntryRowUi {
        val name =
            foodName
                ?: textHint?.takeIf { it.isNotBlank() }
                ?: "quick add"
        val subtitle =
            when {
                kcalOnlyQuickAdd -> "%,d kcal".format(kcal?.toInt())
                unit == "serving" ->
                    "${FoodLogViewModel.formatQuantity(quantity)} serving"
                else -> "${FoodLogViewModel.formatQuantity(quantity)} $unit"
            }
        return EntryRowUi(
            id = id,
            title = name,
            subtitle = subtitle,
            kcal = toDerived(),
            isDrink = mealSlot == MealSlot.DRINK,
            revision = revision,
            edited = editedAt != null,
        )
    }

    /** The D6-typed kcal chip: derived for portion math, measured for quick adds, held for notes. */
    private fun DiaryEntry.toDerived(): DerivedValue<Double>? =
        kcal?.let { value ->
            DerivedValue(
                value,
                Provenance.Derived(RoomDiaryRepository.PORTION_FORMULA_VERSION, listOf("entry=$id", "portion=$quantity $unit")),
            )
        }

    private fun app.wlo.core.model.DiaryRevision.toDerived(): DerivedValue<Double>? =
        kcal?.let { value ->
            DerivedValue(value, Provenance.Derived(RoomDiaryRepository.PORTION_FORMULA_VERSION, listOf("portion=$quantity $unit")))
        }

    private fun dayProvenance(entries: List<DiaryEntry>): Provenance =
        Provenance.Derived(
            formulaVersion = RoomDiaryRepository.PORTION_FORMULA_VERSION,
            inputs = listOf("entries=${entries.size}"),
        )

    private fun entryQuantityText(row: EntryRowUi): String =
        row.subtitle
            .substringBefore(" ")
            .filter { it.isDigit() || it == '.' }

    private suspend fun DiaryEntry.servingGrams(foodId: String?): Double? =
        foodId?.let { id ->
            foods.byId(id).fold(onOk = { food -> food?.servingPresets?.firstOrNull()?.grams }, onErr = { null })
        }

    private val DiaryEntry.kcalOnlyQuickAdd: Boolean
        get() = enteredVia == EntryVia.QUICK_ADD && foodItemId == null

    private fun dayLabel(epochDay: Long): String {
        val date = LocalDate.fromEpochDays(epochDay.toInt())
        return when (epochDay) {
            today -> "today"
            today - 1 -> "yesterday"
            today + 1 -> "tomorrow"
            else -> "${date.dayOfWeek.name.lowercase().replaceFirstChar {
                it.uppercase()
            }} ${date.dayOfMonth} ${date.month.name.lowercase().take(
                3,
            )}"
        }
    }

    private fun timestamp(instant: Instant): String {
        val local = instant.toLocalDateTime(zone)
        val h = local.hour.toString().padStart(2, '0')
        val m = local.minute.toString().padStart(2, '0')
        return "$h:$m"
    }

    private fun methodWord(via: EntryVia): String =
        when (via) {
            EntryVia.MANUAL_SEARCH -> "catalog search"
            EntryVia.MANUAL_CUSTOM -> "your catalog entry"
            EntryVia.TEXT_HINT -> "text note (estimate pending)"
            EntryVia.PHOTO -> "photo"
            EntryVia.VOICE -> "voice"
            EntryVia.RELOG -> "re-log"
            EntryVia.QUICK_ADD -> "calories-only quick add"
            EntryVia.PLAN -> "planned meal"
        }

    private fun macroLine(
        protein: Double?,
        carb: Double?,
        fat: Double?,
        fiber: Double?,
    ): String =
        listOfNotNull(
            protein?.let { "${it.toInt()} g P" },
            carb?.let { "${it.toInt()} g C" },
            fat?.let { "${it.toInt()} g F" },
            fiber?.let { "${it.toInt()} g fiber" },
        ).joinToString(" · ")

    /** Session state bundle for the combine pipeline. */
    private data class Merge4(
        val statuses: Map<Long, DayStatusUi>,
        val openEntryId: String?,
        val edit: Pair<Boolean, String>?,
        val notice: NoticeUi?,
    )

    private companion object {
        const val WATER_ML: Double = 500.0
    }
}

/** Active-profile convenience for suspend contexts inside the VM. */
private suspend fun ProfileRepository.activeSync(): String? = active().fold(onOk = { it?.id }, onErr = { null })
