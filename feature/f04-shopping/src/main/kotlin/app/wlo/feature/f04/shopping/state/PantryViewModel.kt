package app.wlo.feature.f04.shopping.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.GroceryRepository
import app.wlo.core.data.NewPantryItem
import app.wlo.core.data.PantryRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.model.MeasureUnit
import app.wlo.core.ports.OffRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Pantry-surface intents (MVI-lite). */
public sealed interface PantryEvent {
    /** Stock-take / check-in: adds (or resets) one item's stock. */
    public data class Upsert(
        val name: String,
        val qty: Double,
        val unit: String,
        val expiryEpochDay: Long?,
        val staple: Boolean,
    ) : PantryEvent

    /**
     * The barcode check-in path (the M4 capture stack's port): resolves the
     * code through the food-DB door, then opens the qty prompt with the match.
     */
    public data class CheckInBarcode(
        val barcode: String,
    ) : PantryEvent

    /** "mark used" — the manual deduction (always allowed; unit-honest). */
    public data class Deduct(
        val groceryItemId: String,
        val qty: Double,
    ) : PantryEvent

    public data class SetStaple(
        val groceryItemId: String,
        val staple: Boolean,
    ) : PantryEvent

    public data class SetOutOfStock(
        val groceryItemId: String,
        val outOfStock: Boolean,
    ) : PantryEvent

    public data class ToggleDeduction(
        val enabled: Boolean,
    ) : PantryEvent

    public data class Query(
        val query: String,
    ) : PantryEvent
}

/**
 * The pantry state holder (F04 §3): stock with the use-soon / running-low
 * bands, the typed check-in (barcode match via the shared OFF door, typed
 * name fallback — the camera is never required), the manual "mark used"
 * deduction, and the R-S5 toggle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class PantryViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val pantry: PantryRepository,
    private val groceries: GroceryRepository,
    private val settings: SettingsStore,
    private val off: OffRepository,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private var profileId: String? = null
    private val query = MutableStateFlow("")
    private val notice = MutableStateFlow<String?>(null)

    /** The barcode check-in match awaiting its qty prompt (null = none). */
    public val checkInMatch: MutableStateFlow<CheckInMatch?> = MutableStateFlow(null)

    /** One barcode match: the resolved name + the unit it counts in. */
    public data class CheckInMatch(
        val name: String,
        val source: String,
    )

    /** Renderable pantry state. */
    public val uiState: StateFlow<PantryUiState> =
        combine(inputs(), query, notice, settings.pantryDeductionEnabled, ::render)
            .stateIn(viewModelScope, SharingStarted.Eagerly, PantryUiState.LOADING)

    /** MVI-lite intent entry point. */
    public fun onEvent(event: PantryEvent) {
        when (event) {
            is PantryEvent.Upsert -> upsert(event.name, event.qty, event.unit, event.expiryEpochDay, event.staple)
            is PantryEvent.CheckInBarcode -> checkInBarcode(event.barcode)
            is PantryEvent.Deduct -> deduct(event.groceryItemId, event.qty)
            is PantryEvent.SetStaple -> setStaple(event.groceryItemId, event.staple)
            is PantryEvent.SetOutOfStock -> setOutOfStock(event.groceryItemId, event.outOfStock)
            is PantryEvent.ToggleDeduction ->
                viewModelScope.launch { settings.setPantryDeductionEnabled(event.enabled) }
            is PantryEvent.Query -> query.value = event.query
        }
    }

    // --- data plumbing -----------------------------------------------------------

    private fun inputs() =
        profiles.observeActive().flatMapLatest { profileResult ->
            val id = profileResult.getOrNull()?.id
            if (id == null) {
                flowOf(PantryInputs(emptyList(), emptyList()))
            } else {
                profileId = id
                combine(
                    pantry.observeStock(id),
                    groceries.observeAll(id),
                ) { stockResult, catalogResult ->
                    PantryInputs(stockResult.getOrNull().orEmpty(), catalogResult.getOrNull().orEmpty())
                }
            }
        }

    private data class PantryInputs(
        val stock: List<app.wlo.core.data.PantryStockItem>,
        val catalog: List<app.wlo.core.model.GroceryItem>,
    )

    private fun render(
        inputs: PantryInputs,
        queryText: String,
        noticeText: String?,
        deduction: Boolean,
    ): PantryUiState {
        val today = today()
        val rows =
            inputs.stock
                .sortedBy { it.name.lowercase() }
                .map { item -> item.toUi(today) }
        val trimmed = queryText.trim()
        val suggestions =
            if (trimmed.length < SUGGESTION_MIN_CHARS) {
                emptyList()
            } else {
                inputs.catalog
                    .filter { it.name.contains(trimmed, ignoreCase = true) }
                    .take(SUGGESTION_LIMIT)
                    .map { it.name }
            }
        return PantryUiState(
            profileId = profileId,
            useSoon = rows.filter { it.useSoon },
            low = rows.filter { it.low && !it.useSoon },
            stock = rows.filterNot { it.useSoon || (it.low && !it.useSoon) },
            deductionEnabled = deduction,
            suggestions = suggestions,
            notice = noticeText,
        )
    }

    private fun app.wlo.core.data.PantryStockItem.toUi(today: Long): PantryRowUi {
        val expiry = expiryEpochDay
        val useSoon = expiry != null && expiry <= today + PantryRepository.EXPIRING_SOON_DAYS
        return PantryRowUi(
            groceryItemId = groceryItemId,
            name = name,
            qtyLabel = qtyLabel(qty, unit),
            staple = isStaple,
            outOfStock = outOfStock,
            expiryWord =
                expiry?.let {
                    when (val days = it - today) {
                        0L -> "expires today"
                        1L -> "expires in 1 d"
                        in 2..13 -> "expires in $days d"
                        else -> "exp ${LocalDate.fromEpochDays(it.toInt()).dayOfMonth} ${monthWord(it)}"
                    }
                },
            useSoon = useSoon,
            low = outOfStock || qty <= 0.0,
        )
    }

    // --- intents -----------------------------------------------------------------

    private fun upsert(
        name: String,
        qty: Double,
        unit: String,
        expiryEpochDay: Long?,
        staple: Boolean,
    ) {
        val id = profileId ?: return
        if (name.isBlank() || qty < 0.0) return
        viewModelScope.launch {
            val grocery =
                groceries.ensure(id, name, unit, clock.now()).getOrNull() ?: run {
                    notice.value = "couldn't file that item — check the unit"
                    return@launch
                }
            val result =
                pantry.upsert(
                    NewPantryItem(
                        profileId = id,
                        groceryItemId = grocery.id,
                        name = grocery.name,
                        qty = qty,
                        unit = unit,
                        expiryEpochDay = expiryEpochDay,
                        isStaple = staple,
                    ),
                    clock.now(),
                )
            notice.value =
                when (result) {
                    is WloResult.Ok -> null
                    is WloResult.Err -> "the stock-take didn't save — nothing changed"
                }
        }
    }

    private fun checkInBarcode(barcode: String) {
        val id = profileId ?: return
        val code = barcode.trim()
        if (code.isEmpty()) return
        viewModelScope.launch {
            when (val lookup = off.lookup(code)) {
                is app.wlo.core.ports.OffLookupResult.Hit ->
                    checkInMatch.value =
                        CheckInMatch(
                            name = lookup.product.name,
                            source =
                                "matched $code · " +
                                    (if (lookup.servedFromCache) "cached lookup" else "food-db lookup"),
                        )

                is app.wlo.core.ports.OffLookupResult.Miss ->
                    notice.value =
                        when (lookup.reason) {
                            app.wlo.core.ports.MissReason.DISABLED ->
                                "the food-db lookup is switched off — type the name and it files the same way"
                            else ->
                                "no product matched $code — type the name below and it files the same way"
                        }
            }
        }
    }

    private fun deduct(
        groceryItemId: String,
        qty: Double,
    ) {
        val id = profileId ?: return
        if (qty <= 0.0) return
        viewModelScope.launch {
            val stock = pantry.byGroceryItem(id, groceryItemId).getOrNull()
            if (stock == null) {
                notice.value = "nothing in stock for that item"
                return@launch
            }
            val result = pantry.deduct(id, groceryItemId, qty, stock.unit, clock.now())
            notice.value =
                when (val updated = result) {
                    is WloResult.Ok -> {
                        val left = trimQty(updated.value?.qty ?: 0.0)
                        "used ${trimQty(qty)} ${stock.unit} — $left ${stock.unit} left"
                    }
                    is WloResult.Err -> "that unit doesn't match the stock — deduction needs the same kind"
                }
        }
    }

    private fun setStaple(
        groceryItemId: String,
        staple: Boolean,
    ) {
        val id = profileId ?: return
        viewModelScope.launch { pantry.setStaple(id, groceryItemId, staple, clock.now()) }
    }

    private fun setOutOfStock(
        groceryItemId: String,
        outOfStock: Boolean,
    ) {
        val id = profileId ?: return
        viewModelScope.launch { pantry.setOutOfStock(id, groceryItemId, outOfStock, clock.now()) }
    }

    // --- small helpers -----------------------------------------------------------

    private fun today(): Long = DayBoundary.epochDay(clock.now(), zone)

    /** Today in the device zone — the check-in sheet's expiry chips anchor here. */
    public fun todayEpochDay(): Long = today()

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

    private fun monthWord(epochDay: Long): String =
        LocalDate
            .fromEpochDays(epochDay.toInt())
            .month.name
            .lowercase()
            .take(3)

    public companion object {
        public const val SUGGESTION_MIN_CHARS: Int = 2
        public const val SUGGESTION_LIMIT: Int = 5
    }
}
