package app.wlo.core.data

import androidx.room3.withWriteTransaction
import app.wlo.core.common.AppError
import app.wlo.core.common.ClockPort
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.common.map
import app.wlo.core.database.AisleCorrectionEntity
import app.wlo.core.database.GroceryItemEntity
import app.wlo.core.database.ListItemEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.engines.AisleEngine
import app.wlo.core.engines.ListExpansion
import app.wlo.core.engines.ListExport
import app.wlo.core.model.Aisle
import app.wlo.core.model.MeasureUnit
import app.wlo.core.model.PlannedSlotState
import app.wlo.core.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * The shopping-list door (F04): generation is delta reconciliation against
 * the persisted rows — never a regeneration (the anti-Mealime contract, F04
 * §3). Check-state rides the row and survives every plan edit; removals are
 * struck through (archived) and recoverable; the aisle engine learns forever.
 */
public interface ShoppingListRepository {
    public fun observeList(
        profileId: String,
        listId: String = DEFAULT_LIST,
    ): Flow<WloResult<List<ListItem>>>

    public suspend fun items(
        profileId: String,
        listId: String = DEFAULT_LIST,
    ): WloResult<List<ListItem>>

    /** Struck-through rows, most recent first (the recoverable removals). */
    public suspend fun struckThrough(
        profileId: String,
        listId: String = DEFAULT_LIST,
    ): WloResult<List<ListItem>>

    /**
     * Expands the plan's date range into ONE consolidated list and reconciles
     * it into the persisted rows. Returns the counted diff for the
     * "plan changed: N added, M up" banner.
     */
    public suspend fun generateFromPlan(
        profileId: String,
        planId: String,
        fromDay: Long,
        toDay: Long,
        at: Instant,
        listId: String = DEFAULT_LIST,
        deduction: ListExpansion.DeductionPolicy = ListExpansion.DeductionPolicy(),
    ): WloResult<ReconciliationSummary>

    public suspend fun setChecked(
        itemId: String,
        checked: Boolean,
        at: Instant,
    ): WloResult<Unit>

    /**
     * Manual add (R-U15: the always-there path). Creates the canonical
     * grocery item when the name is new, aisle-guessed by the engine.
     */
    public suspend fun addItem(
        profileId: String,
        name: String,
        qty: Double,
        unit: String,
        at: Instant,
        listId: String = DEFAULT_LIST,
    ): WloResult<ListItem>

    /** Restores a struck-through row (recoverable removal, F04 §3). */
    public suspend fun restore(
        itemId: String,
        at: Instant,
    ): WloResult<Unit>

    /** One drag teaches the item's aisle forever (F04 §3 "teach-the-system loop"). */
    public suspend fun learnAisle(
        profileId: String,
        itemId: String,
        aisle: Aisle,
        at: Instant,
    ): WloResult<Unit>

    // --- exports (F04 §9: a format, not a partnership) ---

    public suspend fun exportRows(
        profileId: String,
        listId: String = DEFAULT_LIST,
    ): WloResult<List<ListExport.ExportRow>>

    public suspend fun exportText(
        profileId: String,
        listId: String = DEFAULT_LIST,
    ): WloResult<String>

    public suspend fun exportCsv(
        profileId: String,
        listId: String = DEFAULT_LIST,
    ): WloResult<String>

    public suspend fun exportJson(
        profileId: String,
        listId: String = DEFAULT_LIST,
    ): WloResult<String>

    /** Own-format CSV round-trip; appends the rows unchecked (D8: parse errors are values). */
    public suspend fun importCsv(
        profileId: String,
        csv: String,
        at: Instant,
        listId: String = DEFAULT_LIST,
    ): WloResult<Int>

    public companion object {
        /** The default list: renameable, never deletable (Paprika's anchor rule). */
        public const val DEFAULT_LIST: String = "shopping"
    }
}

public class RoomShoppingListRepository public constructor(
    private val db: WloDatabase,
    private val planner: PlannerRepository,
    private val recipes: RecipeRepository,
    private val pantry: PantryRepository,
    private val clock: ClockPort,
) : ShoppingListRepository {
    private val rows = db.listItems()
    private val groceries = db.groceryItems()
    private val corrections = db.aisleCorrections()

    override fun observeList(
        profileId: String,
        listId: String,
    ): Flow<WloResult<List<ListItem>>> =
        rows
            .observeActive(profileId, listId)
            .map { entities -> WloResult.ok(entities.map { it.toDomain() }) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "list.observeList"))) }

    override suspend fun items(
        profileId: String,
        listId: String,
    ): WloResult<List<ListItem>> = storageGuard("list.items") { rows.active(profileId, listId).map { it.toDomain() } }

    override suspend fun struckThrough(
        profileId: String,
        listId: String,
    ): WloResult<List<ListItem>> = storageGuard("list.struckThrough") { rows.struckThrough(profileId, listId).map { it.toDomain() } }

    override suspend fun generateFromPlan(
        profileId: String,
        planId: String,
        fromDay: Long,
        toDay: Long,
        at: Instant,
        listId: String,
        deduction: ListExpansion.DeductionPolicy,
    ): WloResult<ReconciliationSummary> {
        // 1. The plan's live slots in range (planned only: confirmed meals were
        // eaten, skipped meals are honest misses, swapped rows are retired,
        // leftover children are charged to their cook slot).
        val planSlots =
            planner.slots(profileId, fromDay, toDay).getOrNull().orEmpty().filter {
                it.planId == planId &&
                    it.state == PlannedSlotState.PLANNED &&
                    it.recipeId != null &&
                    it.parentSlotId == null
            }
        val recipeIds = planSlots.mapNotNull { it.recipeId }.distinct()
        val recipeRows =
            recipeIds.mapNotNull { id -> recipes.byId(id).getOrNull() }
        val recipeById: Map<String, Recipe> = recipeRows.associateBy { it.id }

        // 2. Expand + consolidate (engines own the math; F04 §3 semantics).
        val draftSlots =
            planSlots.map { slot ->
                SlotRef(
                    sid = slot.id,
                    rid = slot.recipeId ?: "",
                    day = slot.dayEpochDay,
                    servingsN = slot.servings,
                    batchN = slot.batchServings,
                    parentId = slot.parentSlotId,
                )
            }
        val requirements = ListExpansion.expandAll(recipeRows, draftSlots)
        var consolidated = ListExpansion.consolidate(requirements)

        // 3. Aisles (shipped taxonomy + learned corrections) and names.
        val learned = corrections.forProfile(profileId).associate { it.groceryItemId to it.aisle }
        val groceryById = groceries.active(profileId).associateBy { it.id }
        val aisleFor: (String, String) -> String = { itemId, displayName ->
            val known = groceryById[itemId]
            AisleEngine
                .assign(
                    AisleEngine.Item(
                        id = itemId,
                        name = known?.name ?: displayName,
                        aisleTag = known?.aisle,
                        aliases = known?.toDomain()?.aliases.orEmpty(),
                    ),
                    learned,
                ).wireName
        }

        // 4. Pantry deduction (R-S5: policy.enabled defaults OFF).
        if (deduction.enabled) {
            val stock = pantry.stock(profileId).getOrNull().orEmpty()
            consolidated =
                ListExpansion.applyDeduction(
                    consolidated,
                    stock.map {
                        ListExpansion.PantryStock(
                            groceryItemId = it.groceryItemId,
                            qty = it.qty,
                            unit = it.unit,
                            isStaple = it.isStaple,
                            outOfStock = it.outOfStock,
                        )
                    },
                    deduction,
                )
        }

        val required =
            consolidated.map { item ->
                item.copy(aisle = aisleFor(item.groceryItemId, item.name))
            }

        // 5. Reconcile against the persisted rows — the anti-Mealime core.
        // Struck-through rows come along so a plan edit back RESTORES them
        // (with their checks) instead of duplicating fresh rows.
        val existing =
            (rows.active(profileId, listId) + rows.struckThrough(profileId, listId))
                .distinctBy { it.id }
                .map { it.toExisting() }
        val ops = ListExpansion.reconcile(existing, required)
        val nowMs = at.toEpochMilliseconds()
        return storageGuard("list.generateFromPlan") {
            db.withWriteTransaction {
                ops.forEach { op ->
                    when (op) {
                        is ListExpansion.ReconciliationOp.Unchanged ->
                            rows.clearDelta(op.rowId)

                        is ListExpansion.ReconciliationOp.QtyChanged -> {
                            val row = rows.byId(op.rowId) ?: return@forEach
                            rows.upsert(
                                row.copy(
                                    qty = op.newQty,
                                    unit = op.newUnit,
                                    deltaQty = op.deltaQty,
                                    updatedAtEpochMs = nowMs,
                                ),
                            )
                        }

                        is ListExpansion.ReconciliationOp.Added -> {
                            // Fresh row, unchecked, at its aisle; idempotent within
                            // the same generation (existing pending row of the
                            // same item+unit is updated instead of duplicated).
                            val existingRows = rows.byGroceryItem(profileId, listId, op.groceryItemId)
                            val sameUnit =
                                existingRows.firstOrNull { it.unit == op.unit && it.archivedAtEpochMs == null }
                            if (sameUnit != null) {
                                rows.upsert(
                                    sameUnit.copy(
                                        qty = op.qty,
                                        updatedAtEpochMs = nowMs,
                                    ),
                                )
                            } else {
                                ensureGroceryItem(profileId, op.groceryItemId, op.name, at)
                                rows.upsert(
                                    ListItemEntity(
                                        id = UuidStrings.newId(),
                                        profileId = profileId,
                                        listId = listId,
                                        groceryItemId = op.groceryItemId,
                                        name = op.name,
                                        qty = op.qty,
                                        unit = op.unit,
                                        aisle = aisleFor(op.groceryItemId, op.name),
                                        state = "pending",
                                        sourcesJson = sourcesJsonFor(op.groceryItemId, requirements),
                                        createdAtEpochMs = nowMs,
                                    ),
                                )
                            }
                        }

                        is ListExpansion.ReconciliationOp.Removed ->
                            rows.archive(op.rowId, nowMs)

                        is ListExpansion.ReconciliationOp.Restored -> {
                            val row = rows.byId(op.rowId) ?: return@forEach
                            rows.upsert(
                                row.copy(
                                    qty = op.newQty,
                                    unit = op.newUnit,
                                    deltaQty = op.deltaQty,
                                    archivedAtEpochMs = null,
                                    updatedAtEpochMs = nowMs,
                                ),
                            )
                        }
                    }
                }
                // Refresh provenance rows for every live item (plan may have moved).
                rows.active(profileId, listId).forEach { row ->
                    val json = sourcesJsonFor(row.groceryItemId, requirements)
                    if (json != "[]" && row.sourcesJson != json) {
                        rows.upsert(row.copy(sourcesJson = json, updatedAtEpochMs = nowMs))
                    }
                }
            }
            summarize(ops)
        }
    }

    override suspend fun setChecked(
        itemId: String,
        checked: Boolean,
        at: Instant,
    ): WloResult<Unit> =
        storageGuard("list.setChecked") {
            rows.setChecked(
                itemId,
                if (checked) "checked" else "pending",
                if (checked) at.toEpochMilliseconds() else null,
                at.toEpochMilliseconds(),
            )
        }

    override suspend fun addItem(
        profileId: String,
        name: String,
        qty: Double,
        unit: String,
        at: Instant,
        listId: String,
    ): WloResult<ListItem> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return WloResult.err(AppError.InvalidInput("item name is empty"))
        }
        val unit0 =
            MeasureUnit.fromWireName(unit)
                ?: return WloResult.err(AppError.InvalidInput("unknown unit: $unit"))
        return storageGuard("list.addItem") {
            val learned = corrections.forProfile(profileId).associate { it.groceryItemId to it.aisle }
            val known = groceries.byExactName(profileId, trimmed)
            val groceryId =
                known?.id ?: run {
                    val guessed = AisleEngine.assign(AisleEngine.Item("__new__", trimmed, null), learned)
                    val newId = UuidStrings.newId()
                    groceries.upsert(
                        GroceryItemEntity(
                            id = newId,
                            profileId = profileId,
                            name = trimmed,
                            aisle = guessed.wireName,
                            defaultUnit = unit0.wireName,
                            createdAtEpochMs = at.toEpochMilliseconds(),
                        ),
                    )
                    newId
                }
            val entity =
                ListItemEntity(
                    id = UuidStrings.newId(),
                    profileId = profileId,
                    listId = listId,
                    groceryItemId = groceryId,
                    name = known?.name ?: trimmed,
                    qty = qty,
                    unit = unit0.wireName,
                    aisle = known?.aisle ?: AisleEngine.assign(AisleEngine.Item(groceryId, trimmed, null), learned).wireName,
                    state = "pending",
                    sourcesJson = "[]",
                    createdAtEpochMs = at.toEpochMilliseconds(),
                )
            rows.upsert(entity)
            entity.toDomain()
        }
    }

    override suspend fun restore(
        itemId: String,
        at: Instant,
    ): WloResult<Unit> {
        val load = storageGuard("list.restore.load") { rows.byId(itemId) }
        val row = load.getOrNull() ?: return notFoundItemOr(load, itemId)
        return storageGuard("list.restore.write") {
            rows.upsert(row.copy(archivedAtEpochMs = null, updatedAtEpochMs = at.toEpochMilliseconds()))
        }
    }

    override suspend fun learnAisle(
        profileId: String,
        itemId: String,
        aisle: Aisle,
        at: Instant,
    ): WloResult<Unit> {
        val load = storageGuard("list.learnAisle.load") { rows.byId(itemId) }
        val row = load.getOrNull() ?: return notFoundItemOr(load, itemId)
        return storageGuard("list.learnAisle.write") {
            db.withWriteTransaction {
                corrections.upsert(
                    AisleCorrectionEntity(
                        profileId = profileId,
                        groceryItemId = row.groceryItemId,
                        aisle = aisle.wireName,
                        updatedAtEpochMs = at.toEpochMilliseconds(),
                    ),
                )
                // Learned aisles apply to every live row of the item, all lists.
                rows.byGroceryItem(profileId, row.listId, row.groceryItemId).forEach { live ->
                    rows.upsert(live.copy(aisle = aisle.wireName, updatedAtEpochMs = at.toEpochMilliseconds()))
                }
            }
        }
    }

    override suspend fun exportRows(
        profileId: String,
        listId: String,
    ): WloResult<List<ListExport.ExportRow>> =
        storageGuard("list.exportRows") {
            rows
                .active(profileId, listId)
                .map { it.toDomain() }
                .map { item ->
                    ListExport.ExportRow(
                        item = item.name,
                        qty = item.qty,
                        unit = item.unit,
                        aisle = item.aisle,
                        state = if (item.checked) ListExport.STATE_CHECKED else ListExport.STATE_PENDING,
                        sources =
                            item.sources.map {
                                ListExport.SourceRef(it.recipeId, it.dayEpochDay, it.qty, it.unit)
                            },
                    )
                }
        }

    override suspend fun exportText(
        profileId: String,
        listId: String,
    ): WloResult<String> =
        exportRows(profileId, listId).map { all ->
            val live = all.filter { it.state == ListExport.STATE_PENDING }
            ListExport.toText(
                live,
                AisleEngine.taxonomyOrder().map { it.wireName },
                title = "WLO shopping list",
            )
        }

    override suspend fun exportCsv(
        profileId: String,
        listId: String,
    ): WloResult<String> = exportRows(profileId, listId).map { ListExport.toCsv(it) }

    override suspend fun exportJson(
        profileId: String,
        listId: String,
    ): WloResult<String> = exportRows(profileId, listId).map { ListExport.toJson(it) }

    override suspend fun importCsv(
        profileId: String,
        csv: String,
        at: Instant,
        listId: String,
    ): WloResult<Int> =
        when (val parsed = ListExport.parseCsv(csv)) {
            is ListExport.ListCsvResult.Failed -> WloResult.err(AppError.Parse(parsed.reason))
            is ListExport.ListCsvResult.Ok ->
                storageGuard("list.importCsv") {
                    parsed.rows.forEach { row ->
                        addItem(profileId, row.item, row.qty, row.unit, at, listId).getOrNull()?.let { created ->
                            if (row.state == ListExport.STATE_CHECKED) {
                                rows.setChecked(created.id, "checked", at.toEpochMilliseconds(), at.toEpochMilliseconds())
                            }
                        }
                    }
                    parsed.rows.size
                }
        }

    // --- internals ---------------------------------------------------------------

    private fun sourcesJsonFor(
        groceryItemId: String,
        requirements: List<ListExpansion.Requirement>,
    ): String =
        encodeSources(
            requirements
                .filter { it.groceryItemId == groceryItemId }
                .map { ListSourceLine(it.recipeId, it.slotId, it.dayEpochDay, round4(it.qty), it.unit) },
        )

    private fun notFoundItemOr(
        load: WloResult<ListItemEntity?>,
        itemId: String,
    ): WloResult<Unit> =
        when (load) {
            is WloResult.Err -> WloResult.err(load.error)
            is WloResult.Ok -> WloResult.err(AppError.InvalidInput("no such list item: $itemId"))
        }

    private suspend fun ensureGroceryItem(
        profileId: String,
        groceryItemId: String,
        name: String,
        at: Instant,
    ) {
        if (groceries.byId(groceryItemId) == null) {
            groceries.upsert(
                GroceryItemEntity(
                    id = groceryItemId,
                    profileId = profileId,
                    name = name,
                    aisle = Aisle.OTHER.wireName,
                    defaultUnit = MeasureUnit.COUNT.wireName,
                    createdAtEpochMs = at.toEpochMilliseconds(),
                ),
            )
        }
    }

    private fun summarize(ops: List<ListExpansion.ReconciliationOp>): ReconciliationSummary =
        ReconciliationSummary(
            added = ops.count { it is ListExpansion.ReconciliationOp.Added },
            quantityChanged = ops.count { it is ListExpansion.ReconciliationOp.QtyChanged },
            removed = ops.count { it is ListExpansion.ReconciliationOp.Removed },
            restored = ops.count { it is ListExpansion.ReconciliationOp.Restored },
            unchanged = ops.count { it is ListExpansion.ReconciliationOp.Unchanged },
        )

    private fun ListItemEntity.toExisting(): ListExpansion.ExistingItem =
        ListExpansion.ExistingItem(
            id = id,
            groceryItemId = groceryItemId,
            qty = qty,
            unit = unit,
            checked = state == "checked",
            archived = archivedAtEpochMs != null,
        )

    /** Structural view of a planned slot for the expansion engine. */
    private class SlotRef(
        val sid: String,
        val rid: String,
        val day: Long,
        val servingsN: Double,
        val batchN: Double?,
        val parentId: String?,
    ) : ListExpansion.SlotDraftLike {
        override fun slotId(): String = sid

        override fun recipeId(): String = rid

        override fun dayEpochDay(): Long = day

        override fun servings(): Double = servingsN

        override fun batchServings(): Double? = batchN

        override fun parentSlotId(): String? = parentId
    }
}

private fun round4(value: Double): Double = kotlin.math.round(value * 10_000.0) / 10_000.0
