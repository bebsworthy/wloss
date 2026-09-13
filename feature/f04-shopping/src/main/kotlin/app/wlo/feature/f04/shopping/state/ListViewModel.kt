package app.wlo.feature.f04.shopping.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.ListItem
import app.wlo.core.data.PantryRepository
import app.wlo.core.data.PlannerRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.RecipeRepository
import app.wlo.core.data.ReconciliationSummary
import app.wlo.core.data.ShoppingListRepository
import app.wlo.core.data.SweepItem
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.engines.ListExpansion
import app.wlo.core.model.Aisle
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.MeasureUnit
import app.wlo.core.model.Provenance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** List-surface intents (MVI-lite). */
public sealed interface ListEvent {
    /** Builds the list from the active plan (the week pre-selected). */
    public data object Generate : ListEvent

    /** The R-S5 one-time prompt's answer (remembered; then the build runs). */
    public data class AnswerPrompt(
        val enableDeduction: Boolean,
    ) : ListEvent

    /** The settings toggle (pantry screen shares it). */
    public data class SetDeduction(
        val enabled: Boolean,
    ) : ListEvent

    public data class Check(
        val itemId: String,
        val checked: Boolean,
    ) : ListEvent

    public data class Add(
        val name: String,
        val qty: Double,
        val unit: String,
    ) : ListEvent

    public data class Restore(
        val itemId: String,
    ) : ListEvent

    /** Moves every checked row into pantry stock (the sweep, F04 §2). */
    public data object SweepChecked : ListEvent

    public data class ImportCsv(
        val csv: String,
    ) : ListEvent

    public data object DismissBanner : ListEvent
}

/**
 * The shopping-list state holder (F04 §3–4): aisle-grouped rows, the
 * reconciliation banner built from the counted diff (checks preserved — the
 * anti-Mealime contract), the R-S5 one-time prompt, the check-off loop and
 * the sweep into pantry stock. Numbers render through provenance components;
 * this holder only fetches and formats.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class ListViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val planner: PlannerRepository,
    private val list: ShoppingListRepository,
    private val pantry: PantryRepository,
    private val recipes: RecipeRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val volatile =
        MutableStateFlow(
            VolatileUi(
                banner = null,
                prompt = null,
                busy = false,
                notice = null,
            ),
        )
    private var profileId: String? = null

    /** Write tick: re-reads the once-queries (struck rows, name map). */
    private val refresh = MutableStateFlow(0)

    /** Renderable list state. */
    public val uiState: StateFlow<ListUiState> =
        combine(inputs(), volatile, ::render)
            .stateIn(viewModelScope, SharingStarted.Eagerly, ListUiState.LOADING)

    init {
        // The one-time R-S5 prompt renders when a build starts with pantry
        // stock present and the question was never answered.
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: ListEvent) {
        when (event) {
            ListEvent.Generate -> generate()
            is ListEvent.AnswerPrompt -> answerPrompt(event.enableDeduction)
            is ListEvent.SetDeduction -> setDeduction(event.enabled)
            is ListEvent.Check -> check(event.itemId, event.checked)
            is ListEvent.Add -> addItem(event.name, event.qty, event.unit)
            is ListEvent.Restore -> restore(event.itemId)
            ListEvent.SweepChecked -> sweepChecked()
            is ListEvent.ImportCsv -> importCsv(event.csv)
            ListEvent.DismissBanner -> volatile.value = volatile.value.copy(banner = null)
        }
    }

    // --- data plumbing -----------------------------------------------------------

    private fun inputs() =
        profiles.observeActive().flatMapLatest { profileResult ->
            val id = profileResult.getOrNull()?.id
            if (id == null) {
                flowOf(ListInputs(null, emptyList(), emptyList(), emptyMap(), null, false))
            } else {
                profileId = id
                // The struck rows and the name map are re-read on every write
                // tick (restore, sweep, import, generation).
                combine(
                    list.observeList(id),
                    refresh.flatMapLatest { flow { emit(list.struckThrough(id).getOrNull().orEmpty()) } },
                    refresh.flatMapLatest {
                        flow {
                            emit(
                                recipes
                                    .library(id)
                                    .getOrNull()
                                    .orEmpty()
                                    .associate { it.id to it.name },
                            )
                        }
                    },
                    planner.observeCurrentPlan(id),
                    settings.pantryDeductionEnabled,
                ) { rows, struck, names, plan, deduction ->
                    ListInputs(
                        id,
                        rows.getOrNull().orEmpty(),
                        struck,
                        names,
                        plan.getOrNull(),
                        deduction,
                    )
                }
            }
        }

    private fun render(
        inputs: ListInputs,
        vol: VolatileUi,
    ): ListUiState {
        val active = inputs.rows
        val pending = active.filterNot { it.checked }
        val checked = active.filter { it.checked }
        val removed = inputs.struck

        val grouped =
            pending
                .groupBy { it.aisle }
                .toSortedMap(compareBy { wire -> Aisle.fromWireName(wire)?.defaultOrder ?: 99 })
                .map { (aisle, rows) ->
                    AisleGroupUi(
                        aisleWord = aisleWord(aisle),
                        rows = rows.sortedBy { it.name.lowercase() }.map { it.toUi(inputs.recipeNames) },
                    )
                }

        val plan = inputs.plan
        val plannedKcal = plan?.slots?.sumOf { slot -> (slot.kcalPerServing ?: 0.0) * slot.servings } ?: 0.0
        val claim =
            if (plan != null && plannedKcal > 0.0) {
                DerivedValue(
                    plannedKcal,
                    Provenance.Derived(
                        formulaVersion = "plan-v${plan.version}",
                        inputs = listOf("slots=${plan.slots.size}"),
                    ),
                )
            } else {
                null
            }

        return ListUiState(
            profileId = inputs.profileId,
            groups = grouped,
            checked = checked.sortedByDescending { it.checkedAt }.map { it.toUi(inputs.recipeNames) },
            removed =
                removed
                    .sortedByDescending { it.archivedAt }
                    .map { it.toUi(inputs.recipeNames) },
            planClaim = claim,
            weekLabel =
                plan?.let {
                    weekLabel(it.startDayEpochDay, it.endDayEpochDay)
                },
            banner = vol.banner,
            prompt = vol.prompt,
            deductionEnabled = inputs.deductionEnabled,
            busy = vol.busy,
            notice = vol.notice,
        )
    }

    private fun ListItem.toUi(recipeNames: Map<String, String>): ListItemUi =
        ListItemUi(
            id = id,
            groceryItemId = groceryItemId,
            name = name,
            qtyLabel = qtyLabel(qty, unit),
            deltaLabel = deltaQty?.takeIf { it != 0.0 }?.let { "+${trimQty(it)}" },
            subLabel =
                when {
                    sources.isEmpty() -> "added by hand"
                    else -> "for: ${sources.size} planned line(s)" + firstSourceWord(sources, recipeNames)
                },
            aisleWord = aisleWord(aisle),
            checked = checked,
        )

    private fun firstSourceWord(
        sources: List<app.wlo.core.data.ListSourceLine>,
        recipeNames: Map<String, String>,
    ): String {
        val first = sources.firstOrNull() ?: return ""
        val recipeName = recipeNames[first.recipeId] ?: "a planned meal"
        val amount =
            if (MeasureUnit.fromWireName(first.unit) == MeasureUnit.COUNT) {
                "×${trimQty(first.qty)}"
            } else {
                "×${trimQty(first.qty)} ${first.unit}"
            }
        return " · $recipeName $amount"
    }

    // --- intents -----------------------------------------------------------------

    private fun generate() {
        val id = profileId ?: return
        viewModelScope.launch {
            volatile.value = volatile.value.copy(busy = true)
            val plan = planner.currentPlan(id).getOrNull()
            if (plan == null) {
                volatile.value =
                    volatile.value.copy(
                        busy = false,
                        notice = "no plan to build from yet — deal a week on the Plan segment first",
                    )
                return@launch
            }
            // R-S5: the one-time prompt fires on the first generation that could
            // deduct, and only while pantry stock exists to deduct from.
            val promptSeen = settings.pantryDeductionPromptSeen.getOnce()
            val stock = pantry.stock(id).getOrNull().orEmpty()
            if (!promptSeen && stock.isNotEmpty()) {
                volatile.value =
                    volatile.value.copy(
                        busy = false,
                        prompt =
                            DeductionPromptUi(
                                staplesWord = stock.count { it.isStaple }.let { "$it staple(s) marked" },
                            ),
                    )
                return@launch
            }
            runGeneration(
                id,
                plan.planId,
                plan.startDayEpochDay,
                plan.endDayEpochDay,
                settings.pantryDeductionEnabled.getOnce(),
            )
        }
    }

    private fun answerPrompt(enable: Boolean) {
        val id = profileId ?: return
        viewModelScope.launch {
            settings.setPantryDeductionEnabled(enable)
            volatile.value = volatile.value.copy(prompt = null, busy = true)
            val plan = planner.currentPlan(id).getOrNull() ?: return@launch
            runGeneration(id, plan.planId, plan.startDayEpochDay, plan.endDayEpochDay, enable)
        }
    }

    private suspend fun runGeneration(
        profileId: String,
        planId: String,
        from: Long,
        to: Long,
        deductionEnabled: Boolean,
    ) {
        val checksBefore =
            list
                .items(profileId)
                .getOrNull()
                .orEmpty()
                .count { it.checked }
        val summary =
            list.generateFromPlan(
                profileId = profileId,
                planId = planId,
                fromDay = from,
                toDay = to,
                at = clock.now(),
                deduction = ListExpansion.DeductionPolicy(enabled = deductionEnabled),
            )
        refresh.value += 1
        volatile.value =
            volatile.value.copy(
                busy = false,
                banner =
                    when (summary) {
                        is WloResult.Ok -> summaryUi(summary.value, checksBefore)
                        is WloResult.Err -> null
                    },
                notice =
                    when (summary) {
                        is WloResult.Err -> "the build didn't finish — your list is untouched"
                        is WloResult.Ok -> null
                    },
            )
    }

    private fun summaryUi(
        summary: ReconciliationSummary,
        checksBefore: Int,
    ): ReconciliationUi? =
        if (!summary.touched() && checksBefore == 0) {
            null
        } else {
            ReconciliationUi(
                added = summary.added,
                quantityChanged = summary.quantityChanged,
                removed = summary.removed,
                restored = summary.restored,
                checksKept = checksBefore,
            )
        }

    private fun setDeduction(enabled: Boolean) {
        viewModelScope.launch { settings.setPantryDeductionEnabled(enabled) }
    }

    private fun check(
        itemId: String,
        checked: Boolean,
    ) {
        viewModelScope.launch {
            val result = list.setChecked(itemId, checked, clock.now())
            if (result is WloResult.Err) {
                volatile.value = volatile.value.copy(notice = "that didn't save — nothing changed")
            }
        }
    }

    private fun addItem(
        name: String,
        qty: Double,
        unit: String,
    ) {
        val id = profileId ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val result = list.addItem(id, name, qty, unit, clock.now())
            if (result is WloResult.Err) {
                volatile.value = volatile.value.copy(notice = "couldn't add that — check the unit")
            }
        }
    }

    private fun restore(itemId: String) {
        viewModelScope.launch {
            list.restore(itemId, clock.now())
            refresh.value += 1
        }
    }

    private fun sweepChecked() {
        val id = profileId ?: return
        viewModelScope.launch {
            val checked =
                list
                    .items(id)
                    .getOrNull()
                    .orEmpty()
                    .filter { it.checked }
            if (checked.isEmpty()) {
                volatile.value =
                    volatile.value.copy(notice = "nothing checked yet — tick items as they land in the trolley")
                return@launch
            }
            val result =
                pantry.sweep(
                    id,
                    checked.map { SweepItem(it.groceryItemId, it.name, it.qty, it.unit) },
                    clock.now(),
                )
            when (result) {
                is WloResult.Ok -> {
                    checked.forEach { list.setChecked(it.id, false, clock.now()) }
                    refresh.value += 1
                    volatile.value = volatile.value.copy(notice = "swept ${result.value} item(s) into the pantry")
                }

                is WloResult.Err ->
                    volatile.value = volatile.value.copy(notice = "the sweep didn't finish — nothing moved")
            }
        }
    }

    private fun importCsv(csv: String) {
        val id = profileId ?: return
        viewModelScope.launch {
            val result = list.importCsv(id, csv, clock.now())
            volatile.value =
                volatile.value.copy(
                    notice =
                        when (result) {
                            is WloResult.Ok -> "imported ${result.value} row(s)"
                            is WloResult.Err -> "that CSV didn't parse — nothing was imported"
                        },
                )
        }
    }

    // --- exports (the share sheet + SAF writers call these) ----------------------

    /** The plain-text list (the share-sheet body). */
    public suspend fun exportText(): String? {
        val id = profileId ?: return null
        return list.exportText(id).getOrNull()
    }

    public suspend fun exportCsv(): String? {
        val id = profileId ?: return null
        return list.exportCsv(id).getOrNull()
    }

    public suspend fun exportJson(): String? {
        val id = profileId ?: return null
        return list.exportJson(id).getOrNull()
    }

    // --- small helpers -----------------------------------------------------------

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.getOnce(): T = first()

    private fun qtyLabel(
        qty: Double,
        unit: String,
    ): String =
        when (MeasureUnit.fromWireName(unit)) {
            MeasureUnit.COUNT -> "×${trimQty(qty)}"
            else -> "${trimQty(qty)} $unit"
        }

    private fun trimQty(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded == kotlin.math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    private fun aisleWord(wire: String): String =
        when (Aisle.fromWireName(wire)) {
            Aisle.PRODUCE -> "Produce"
            Aisle.BAKERY -> "Bakery"
            Aisle.DAIRY -> "Dairy"
            Aisle.MEAT_FISH -> "Meat & Fish"
            Aisle.FROZEN -> "Frozen"
            Aisle.PANTRY -> "Pantry"
            Aisle.HOUSEHOLD -> "Household"
            else -> "Other"
        }

    private fun weekLabel(
        from: Long,
        to: Long,
    ): String {
        val a = LocalDate.fromEpochDays(from.toInt())
        val b = LocalDate.fromEpochDays(to.toInt())
        val month = { date: LocalDate ->
            date.month.name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
                .take(3)
        }
        return if (a.month ==
            b.month
        ) {
            "${month(a)} ${a.dayOfMonth}–${b.dayOfMonth}"
        } else {
            "${month(a)} ${a.dayOfMonth} – ${month(b)} ${b.dayOfMonth}"
        }
    }

    private data class ListInputs(
        val profileId: String?,
        val rows: List<ListItem>,
        val struck: List<ListItem>,
        val recipeNames: Map<String, String>,
        val plan: app.wlo.core.data.PlanView?,
        val deductionEnabled: Boolean,
    )

    private data class VolatileUi(
        val banner: ReconciliationUi?,
        val prompt: DeductionPromptUi?,
        val busy: Boolean,
        val notice: String?,
    )
}
