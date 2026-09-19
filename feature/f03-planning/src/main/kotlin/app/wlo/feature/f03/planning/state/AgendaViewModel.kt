package app.wlo.feature.f03.planning.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.AppError
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.AgendaDraft
import app.wlo.core.data.AgendaFood
import app.wlo.core.data.AgendaItem
import app.wlo.core.data.DayView
import app.wlo.core.data.FoodRepository
import app.wlo.core.data.MealAgendaRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.RecipeRepository
import app.wlo.core.model.Recipe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import java.util.UUID

public data class AgendaState(
    val profile: String? = null,
    val selected: Long = 0,
    val today: Long = 0,
    val items: List<AgendaItem> = emptyList(),
    val days: List<DayView> = emptyList(),
    val foods: List<AgendaFood> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val undo: AgendaItem? = null,
    val draft: AgendaDraft? = null,
)

/** Date and draft survive recreation; repository owns all nutrition and writes. */
@OptIn(ExperimentalCoroutinesApi::class)
public class AgendaViewModel(
    private val saved: SavedStateHandle,
    private val clock: ClockPort,
    profiles: ProfileRepository,
    private val repository: MealAgendaRepository,
    private val recipes: RecipeRepository,
    foods: FoodRepository,
    focusDay: Long? = null,
    focusSlot: String? = null,
) : ViewModel() {
    private fun today(): Long = DayBoundary.epochDay(clock.now(), TimeZone.currentSystemDefault()).toLong()

    private val selected = saved.getStateFlow("agenda.day", focusDay ?: today())
    private val volatile =
        MutableStateFlow(
            AgendaState(
                draft =
                    saved.get<String>("agenda.draft")?.let {
                        runCatching { Json.decodeFromString<AgendaDraft>(it) }.getOrNull()
                    },
            ),
        )
    public val state: StateFlow<AgendaState> =
        combine(profiles.observeActive(), selected) { p, d -> p.getOrNull()?.id to d }
            .flatMapLatest { (profile, day) ->
                if (profile == null) {
                    flowOf(AgendaState(selected = day, today = today()))
                } else {
                    val start = weekStart(day)
                    viewModelScope.launch { recipes.ensureSeeded(profile, clock.now()) }
                    combine(
                        repository.observe(profile, start, start + 6),
                        repository.observeTargets(profile, start, start + 6),
                        recipes.observeLibrary(profile),
                        foods.observeActive(profile),
                        volatile,
                    ) { items, targets, library, catalog, vol ->
                        val draft = vol.draft?.takeIf { it.profile == profile }
                        vol.copy(
                            profile = profile,
                            selected = day,
                            today = today(),
                            items = items + draft?.items.orEmpty(),
                            days = targets,
                            foods =
                                catalog.getOrNull().orEmpty().map(AgendaFood::food) +
                                    library.getOrNull().orEmpty().map(AgendaFood::recipe),
                            recipes = library.getOrNull().orEmpty(),
                            draft = draft,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, AgendaState(selected = selected.value, today = today()))

    init {
        if (focusSlot != null && !saved.contains("agenda.focusHandled")) {
            viewModelScope.launch {
                val profile =
                    profiles
                        .observeActive()
                        .first()
                        .getOrNull()
                        ?.id
                if (profile != null) repository.slotDay(profile, focusSlot)?.let(::select)
                saved["agenda.focusHandled"] = true
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                volatile.value = volatile.value.copy(today = today())
            }
        }
    }

    public fun select(day: Long) {
        saved["agenda.day"] = day
    }

    public fun add(
        meal: String,
        food: AgendaFood,
        quantity: Double,
        eaten: Boolean,
        onSuccess: () -> Unit = {},
    ): Unit =
        write { p ->
            repository
                .add(
                    p,
                    AgendaItem(
                        UUID.randomUUID().toString(),
                        state.value.selected,
                        meal,
                        food,
                        quantity,
                        eaten && state.value.selected <= today(),
                    ),
                ).also { if (it is WloResult.Ok) onSuccess() }
        }

    public fun replace(
        item: AgendaItem,
        food: AgendaFood,
        quantity: Double,
        onSuccess: () -> Unit = {},
    ): Unit = write { p -> repository.replace(p, item, food, quantity).also { if (it is WloResult.Ok) onSuccess() } }

    public fun remove(item: AgendaItem): Unit =
        write { p ->
            repository.remove(p, item).also {
                if (it is WloResult.Ok) {
                    volatile.value =
                        volatile.value.copy(undo = item, message = "Food removed")
                }
            }
        }

    public fun undo() {
        val item =
            volatile.value.undo ?: return
        write { p ->
            repository.undoRemove(p, item).also {
                if (it is WloResult.Ok) {
                    volatile.value =
                        volatile.value.copy(undo = null, message = null)
                }
            }
        }
    }

    public fun dismissMessage() {
        volatile.value = volatile.value.copy(message = null, undo = null)
    }

    private var previewJob: Job? = null

    public fun cancelPreview() {
        previewJob?.cancel()
        previewJob = null
    }

    public fun preview(
        from: Long,
        to: Long,
        meals: Set<String>,
        weekdays: Boolean,
        onReady: () -> Unit,
    ) {
        val p = state.value.profile ?: return
        if (volatile.value.busy) return
        volatile.value = volatile.value.copy(busy = true)
        previewJob =
            viewModelScope.launch {
                try {
                    when (val result = repository.preview(p, from, to, meals, weekdays)) {
                        is WloResult.Ok -> {
                            if (result.value.items.isEmpty()) {
                                volatile.value =
                                    volatile.value.copy(message = "No suggestions for these empty meals")
                            } else {
                                setDraft(result.value)
                                onReady()
                            }
                        }
                        is WloResult.Err -> volatile.value = volatile.value.copy(message = userMessage(result.error))
                    }
                } finally {
                    volatile.value = volatile.value.copy(busy = false)
                }
            }
    }

    public fun discard() {
        setDraft(null)
    }

    public fun commit() {
        val draft =
            volatile.value.draft ?: return
        write { repository.commit(draft).also { if (it is WloResult.Ok) setDraft(null) } }
    }

    private fun setDraft(draft: AgendaDraft?) {
        saved["agenda.draft"] = draft?.let { Json.encodeToString(AgendaDraft.serializer(), it) }
        volatile.value =
            volatile.value.copy(draft = draft)
    }

    private fun write(block: suspend (String) -> WloResult<Unit>) {
        val p = state.value.profile ?: return
        if (volatile.value.busy) return
        volatile.value = volatile.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val result = block(p)
                if (result is WloResult.Err) {
                    volatile.value =
                        volatile.value.copy(message = userMessage(result.error))
                }
            } finally {
                volatile.value = volatile.value.copy(busy = false)
            }
        }
    }
}

public fun weekStart(day: Long): Long = day - LocalDate.fromEpochDays(day.toInt()).dayOfWeek.ordinal

private fun userMessage(error: AppError): String =
    when (error) {
        is AppError.InvalidInput -> error.detail
        is AppError.Storage ->
            (error.cause as? IllegalStateException)?.message?.takeUnless { it.contains("Storage(") }
                ?: "Could not save this change. Please try again."
        else -> "Could not load the meals. Please try again."
    }
