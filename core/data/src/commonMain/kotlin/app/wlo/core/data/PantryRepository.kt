package app.wlo.core.data

import app.wlo.core.common.AppError
import app.wlo.core.common.ClockPort
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.database.PantryItemEntity
import app.wlo.core.database.WloDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * The pantry door (F04 §3): "what's in the house" ground truth — stock
 * levels, expiry timeline, staples and the out-of-stock flags that put an
 * item on the next generated list. Emits the downstream signals F03/F10
 * consume ([expiringSoon], [runningLow]); nothing here nags more than once
 * per item per week — cadence is the caller's (F10's) business.
 */
public interface PantryRepository {
    public fun observeStock(profileId: String): Flow<WloResult<List<PantryStockItem>>>

    public suspend fun stock(profileId: String): WloResult<List<PantryStockItem>>

    public suspend fun byGroceryItem(
        profileId: String,
        groceryItemId: String,
    ): WloResult<PantryStockItem?>

    /** Upserts a row (stock-take, manual add, barcode check-in via the F02 port). */
    public suspend fun upsert(
        entry: NewPantryItem,
        at: Instant,
    ): WloResult<PantryStockItem>

    /**
     * "Sweep to pantry" (F04 §2): checked list items land in inventory.
     * Purchased-only by default (the sweep default, F04 §10); the caller
     * decides scope. Adds to existing stock and bumps purchase history.
     */
    public suspend fun sweep(
        profileId: String,
        items: List<SweepItem>,
        at: Instant,
    ): WloResult<Int>

    /** Staples participate in list-generation deduction when R-S5's toggle is ON. */
    public suspend fun setStaple(
        profileId: String,
        groceryItemId: String,
        staple: Boolean,
        at: Instant,
    ): WloResult<Unit>

    /** Out-of-stock flags put an item on the next generated list (F04 §3). */
    public suspend fun setOutOfStock(
        profileId: String,
        groceryItemId: String,
        outOfStock: Boolean,
        at: Instant,
    ): WloResult<Unit>

    /**
     * The deduction flow: removes consumed quantity from stock (floor 0).
     * R-S5: PARTIAL-STOCK DEDUCTION IS OFF BY DEFAULT — the generation-time
     * path only calls this when the settings toggle is ON; manual "mark used"
     * always may. Units must be the same kind (cross-kind requests are errors,
     * never silent guesses).
     */
    public suspend fun deduct(
        profileId: String,
        groceryItemId: String,
        qty: Double,
        unit: String,
        at: Instant,
    ): WloResult<PantryStockItem?>

    /** expiringSoon (F04 §3 signal: ≤ 3 days, caller sets the horizon). */
    public suspend fun expiringSoon(
        profileId: String,
        asOfDayEpochDay: Long,
        withinDays: Int = EXPIRING_SOON_DAYS,
    ): WloResult<List<PantryStockItem>>

    /** runningLow (F04 §3 signal: out-of-stock flag or zero stock). */
    public suspend fun runningLow(profileId: String): WloResult<List<PantryStockItem>>

    public companion object {
        public const val EXPIRING_SOON_DAYS: Int = 3
    }
}

/** One sweep line (a checked list row moving into inventory). */
public data class SweepItem(
    public val groceryItemId: String,
    public val name: String,
    public val qty: Double,
    public val unit: String,
    /** Purchased-only sweeps set null; sweep-all sweeps may pre-fill expiry. */
    public val expiryEpochDay: Long? = null,
)

public class RoomPantryRepository public constructor(
    private val db: WloDatabase,
    private val clock: ClockPort,
) : PantryRepository {
    private val dao = db.pantryItems()

    override fun observeStock(profileId: String): Flow<WloResult<List<PantryStockItem>>> =
        dao
            .observeActive(profileId)
            .map { rows -> WloResult.ok(rows.map { it.toDomain() }) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "pantry.observeStock"))) }

    override suspend fun stock(profileId: String): WloResult<List<PantryStockItem>> =
        storageGuard("pantry.stock") { dao.active(profileId).map { it.toDomain() } }

    override suspend fun byGroceryItem(
        profileId: String,
        groceryItemId: String,
    ): WloResult<PantryStockItem?> = storageGuard("pantry.byGroceryItem") { dao.byGroceryItem(profileId, groceryItemId)?.toDomain() }

    override suspend fun upsert(
        entry: NewPantryItem,
        at: Instant,
    ): WloResult<PantryStockItem> =
        storageGuard("pantry.upsert") {
            val nowMs = at.toEpochMilliseconds()
            val existing = dao.byGroceryItem(entry.profileId, entry.groceryItemId)
            val entity =
                if (existing == null) {
                    PantryItemEntity(
                        id = UuidStrings.newId(),
                        profileId = entry.profileId,
                        groceryItemId = entry.groceryItemId,
                        name = entry.name,
                        qty = entry.qty,
                        unit = entry.unit,
                        expiryEpochDay = entry.expiryEpochDay,
                        addedAtEpochMs = nowMs,
                        lastPurchasedAtEpochMs = if (entry.qty > 0) nowMs else null,
                        purchaseCount = if (entry.qty > 0) 1 else 0,
                        isStaple = entry.isStaple,
                        outOfStock = entry.outOfStock,
                        updatedAtEpochMs = nowMs,
                    )
                } else {
                    existing.copy(
                        qty = entry.qty,
                        unit = entry.unit,
                        name = entry.name.ifBlank { existing.name },
                        expiryEpochDay = entry.expiryEpochDay ?: existing.expiryEpochDay,
                        isStaple = entry.isStaple || existing.isStaple,
                        outOfStock = entry.outOfStock,
                        updatedAtEpochMs = nowMs,
                    )
                }
            dao.upsert(entity)
            entity.toDomain()
        }

    override suspend fun sweep(
        profileId: String,
        items: List<SweepItem>,
        at: Instant,
    ): WloResult<Int> =
        storageGuard("pantry.sweep") {
            val nowMs = at.toEpochMilliseconds()
            items.forEach { item ->
                val existing = dao.byGroceryItem(profileId, item.groceryItemId)
                val entity =
                    if (existing == null) {
                        PantryItemEntity(
                            id = UuidStrings.newId(),
                            profileId = profileId,
                            groceryItemId = item.groceryItemId,
                            name = item.name,
                            qty = item.qty,
                            unit = item.unit,
                            expiryEpochDay = item.expiryEpochDay,
                            addedAtEpochMs = nowMs,
                            lastPurchasedAtEpochMs = nowMs,
                            purchaseCount = 1,
                            outOfStock = false,
                            updatedAtEpochMs = nowMs,
                        )
                    } else {
                        existing.copy(
                            qty = existing.qty + item.qty,
                            unit = item.unit,
                            expiryEpochDay = item.expiryEpochDay ?: existing.expiryEpochDay,
                            lastPurchasedAtEpochMs = nowMs,
                            purchaseCount = existing.purchaseCount + 1,
                            outOfStock = false,
                            updatedAtEpochMs = nowMs,
                        )
                    }
                dao.upsert(entity)
            }
            items.size
        }

    override suspend fun setStaple(
        profileId: String,
        groceryItemId: String,
        staple: Boolean,
        at: Instant,
    ): WloResult<Unit> =
        mutate(profileId, groceryItemId, "pantry.setStaple") {
            it.copy(isStaple = staple, updatedAtEpochMs = at.toEpochMilliseconds())
        }

    override suspend fun setOutOfStock(
        profileId: String,
        groceryItemId: String,
        outOfStock: Boolean,
        at: Instant,
    ): WloResult<Unit> =
        mutate(profileId, groceryItemId, "pantry.setOutOfStock") {
            it.copy(outOfStock = outOfStock, updatedAtEpochMs = at.toEpochMilliseconds())
        }

    override suspend fun deduct(
        profileId: String,
        groceryItemId: String,
        qty: Double,
        unit: String,
        at: Instant,
    ): WloResult<PantryStockItem?> {
        val load = storageGuard("pantry.deduct.load") { dao.byGroceryItem(profileId, groceryItemId) }
        val existing = load.getOrNull() ?: return notFoundNullableOr(load, groceryItemId)
        if (existing.unit != unit) {
            return WloResult.err(
                AppError.InvalidInput(
                    "pantry unit mismatch for $groceryItemId: stock ${existing.unit} vs deduct $unit " +
                        "(cross-kind deduction would be a guess — F04 §3)",
                ),
            )
        }
        return storageGuard("pantry.deduct.write") {
            val updated =
                existing
                    .copy(qty = (existing.qty - qty).coerceAtLeast(0.0), updatedAtEpochMs = at.toEpochMilliseconds())
                    .also { dao.upsert(it) }
            updated.toDomain()
        }
    }

    override suspend fun expiringSoon(
        profileId: String,
        asOfDayEpochDay: Long,
        withinDays: Int,
    ): WloResult<List<PantryStockItem>> =
        storageGuard("pantry.expiringSoon") {
            dao.expiringBefore(profileId, asOfDayEpochDay + withinDays).map { it.toDomain() }
        }

    override suspend fun runningLow(profileId: String): WloResult<List<PantryStockItem>> =
        storageGuard("pantry.runningLow") {
            dao
                .active(profileId)
                .filter { it.outOfStock || it.qty <= 0.0 }
                .map { it.toDomain() }
        }

    private suspend fun mutate(
        profileId: String,
        groceryItemId: String,
        detail: String,
        transform: (PantryItemEntity) -> PantryItemEntity,
    ): WloResult<Unit> {
        val load = storageGuard("$detail.load") { dao.byGroceryItem(profileId, groceryItemId) }
        val existing = load.getOrNull() ?: return notFoundOr(load, groceryItemId)
        return storageGuard("$detail.write") { dao.upsert(transform(existing)) }
    }

    private fun notFoundOr(
        load: WloResult<PantryItemEntity?>,
        groceryItemId: String,
    ): WloResult<Unit> =
        when (load) {
            is WloResult.Err -> WloResult.err(load.error)
            is WloResult.Ok -> WloResult.err(AppError.InvalidInput("no pantry stock for item: $groceryItemId"))
        }

    /** Deducting an absent stock row is a no-op success — nothing consumed. */
    private fun notFoundNullableOr(
        load: WloResult<PantryItemEntity?>,
        groceryItemId: String,
    ): WloResult<PantryStockItem?> =
        when (load) {
            is WloResult.Err -> WloResult.err(load.error)
            is WloResult.Ok -> WloResult.ok(null)
        }
}
