package app.wlo.core.engines

import app.wlo.core.common.fromCanonical
import app.wlo.core.common.toCanonical
import app.wlo.core.model.MeasureKind
import app.wlo.core.model.MeasureUnit
import app.wlo.core.model.Recipe
import kotlinx.serialization.Serializable

/**
 * F04 list engine (F04 §3): turns plan slots into ONE consolidated, unit-aware
 * list and reconciles plan edits as deltas — never a regeneration (the
 * anti-Mealime contract: check-state survives any plan edit). Pure functions
 * (D7); the repositories own persistence.
 *
 * Consolidation semantics (F04 §3's canonical cases, implemented exactly):
 *  - same unit sums: eggs ×2 + eggs ×3 → **×5**
 *  - same-kind convertible units merge into the DOMINANT unit (the unit
 *    carrying the largest canonical amount): milk 250 ml + 1 cup → **≈ 487 ml**
 *  - cross-kind units NEVER fake a conversion: flour 300 g + 2 cups group as
 *    one item with two lines, per-source amounts preserved
 *  - every line keeps its per-recipe provenance (F04 §3 data model)
 *
 * Reconciliation semantics (F04 §3's contract table):
 *  - quantity unchanged → row untouched, check preserved
 *  - quantity changed → same row, qty updated, delta recorded (the "+2" chip)
 *  - new ingredient → new row, unchecked
 *  - ingredient removed → row archived (struck through, recoverable)
 *  - re-added → the archived row restores with its check preserved
 */
public object ListExpansion {
    /** One scaled ingredient demand from one planned slot. */
    @Serializable
    public data class Requirement(
        public val groceryItemId: String,
        public val name: String,
        public val qty: Double,
        /** [MeasureUnit] wire name. */
        public val unit: String,
        public val recipeId: String,
        public val slotId: String,
        public val dayEpochDay: Long,
    )

    /** Per-recipe provenance behind one consolidated amount (F04 §3 "sources"). */
    @Serializable
    public data class SourceLine(
        public val recipeId: String,
        public val slotId: String,
        public val dayEpochDay: Long,
        public val qty: Double,
        public val unit: String,
    )

    /** One consolidated amount in one unit, with its provenance. */
    @Serializable
    public data class ConsolidatedLine(
        public val qty: Double,
        public val unit: String,
        public val sources: List<SourceLine> = emptyList(),
    )

    /** One list row's target state — one item, one line per unit-kind present. */
    @Serializable
    public data class ConsolidatedItem(
        public val groceryItemId: String,
        public val name: String,
        public val aisle: String,
        public val lines: List<ConsolidatedLine>,
    )

    /**
     * Scales a recipe's ingredient lines onto a slot's demand. Arbitrary
     * servings propagate (F03 §1: "never a 2/4/6 straitjacket") — scale =
     * demand / recipe.servingsBase, where demand is the slot's BATCH for cook
     * events (eaten-here + leftovers, F03 §3) and the eaten servings
     * otherwise. Leftover children are the CALLER's exclusion: ingredient
     * weight is charged to the cook event's slot only.
     */
    public fun expand(
        recipe: Recipe,
        slotId: String,
        dayEpochDay: Long,
        slotServings: Double,
        batchServings: Double? = null,
    ): List<Requirement> {
        if (recipe.servingsBase <= 0.0) return emptyList()
        val demand = batchServings ?: slotServings
        val scale = demand / recipe.servingsBase
        return recipe.ingredients.map { ingredient ->
            Requirement(
                groceryItemId = ingredient.groceryItemId,
                name = ingredient.name,
                qty = ingredient.qty * scale,
                unit = ingredient.unit,
                recipeId = recipe.id,
                slotId = slotId,
                dayEpochDay = dayEpochDay,
            )
        }
    }

    /** Expands a whole slot set (skipping leftover children) and consolidates it. */
    public fun expandAll(
        recipes: List<Recipe>,
        slots: List<SlotDraftLike>,
    ): List<Requirement> {
        val byId = recipes.associateBy { it.id }
        return slots
            .filter { it.parentSlotId() == null }
            .flatMap { slot ->
                val recipe = byId[slot.recipeId()] ?: return@flatMap emptyList<Requirement>()
                expand(recipe, slot.slotId(), slot.dayEpochDay(), slot.servings(), slot.batchServings())
            }
    }

    /** Structural view of a planned slot — keeps :core:data rows out of the engine. */
    public interface SlotDraftLike {
        public fun slotId(): String

        public fun recipeId(): String?

        public fun dayEpochDay(): Long

        /** Servings eaten at this slot (the nutrition basis). */
        public fun servings(): Double

        /** Cook events carry the whole batch here (the ingredient basis). */
        public fun batchServings(): Double? = null

        public fun parentSlotId(): String?
    }

    /**
     * Unit-aware consolidation (see the object KDoc for the exact semantics).
     * Deterministic: line order follows first appearance; the dominant unit
     * is the largest canonical contributor, ties resolved toward the metric
     * base unit then the alphabetically-smaller wire name.
     */
    public fun consolidate(requirements: List<Requirement>): List<ConsolidatedItem> {
        val byItem = requirements.groupBy { it.groceryItemId }
        return byItem.entries
            .map { (itemId, reqs) ->
                val first = reqs.first()
                val byKind = reqs.groupBy { MeasureUnit.fromWireName(it.unit)?.kind ?: MeasureKind.COUNT }
                val lines =
                    byKind.entries
                        .map { (kind, kindReqs) -> consolidateKind(kind, kindReqs) }
                        .sortedBy { kindRank(MeasureUnit.fromWireName(it.unit)?.kind ?: MeasureKind.COUNT) }
                ConsolidatedItem(
                    groceryItemId = itemId,
                    name = first.name,
                    aisle = "",
                    lines = lines,
                )
            }.sortedBy { it.name.lowercase() }
    }

    private fun consolidateKind(
        kind: MeasureKind,
        reqs: List<Requirement>,
    ): ConsolidatedLine {
        val canonicalTotal = reqs.sumOf { req -> unit(req.unit).toCanonical(req.qty) }
        // Dominant unit = largest canonical contributor among the sources.
        val dominant =
            reqs
                .groupBy { it.unit }
                .entries
                .maxWithOrNull(
                    compareBy<Map.Entry<String, List<Requirement>>> { it.value.sumOf { r -> unit(r.unit).toCanonical(r.qty) } }
                        .thenBy { metricBaseRank(unit(it.key)) }
                        .thenBy { it.key },
                )?.key
                ?: metricBaseName(kind)
        val qty = roundDisplay(unit(dominant).fromCanonical(canonicalTotal), kind)
        return ConsolidatedLine(
            qty = qty,
            unit = dominant,
            sources = reqs.map { SourceLine(it.recipeId, it.slotId, it.dayEpochDay, round4(it.qty), it.unit) },
        )
    }

    /** Deterministic line order: mass, then volume, then count (the display convention);
     * the stable sort preserves first-appearance order within a kind. */
    private fun kindRank(kind: MeasureKind): Int =
        when (kind) {
            MeasureKind.MASS -> 0
            MeasureKind.VOLUME -> 1
            MeasureKind.COUNT -> 2
        }

    private fun metricBaseRank(u: MeasureUnit): Int =
        when (u) {
            MeasureUnit.GRAM, MeasureUnit.MILLILITER, MeasureUnit.COUNT -> 0
            else -> 1
        }

    private fun metricBaseName(kind: MeasureKind): String =
        when (kind) {
            MeasureKind.MASS -> MeasureUnit.GRAM.wireName
            MeasureKind.VOLUME -> MeasureUnit.MILLILITER.wireName
            MeasureKind.COUNT -> MeasureUnit.COUNT.wireName
        }

    private fun unit(wire: String): MeasureUnit = MeasureUnit.fromWireName(wire) ?: MeasureUnit.COUNT

    /**
     * Display rounding: counts to halves ("onions ×1.5", F04 §3's canonical
     * case), mass/volume to one decimal. A canonical 487 ml stays 487.
     */
    private fun roundDisplay(
        value: Double,
        kind: MeasureKind,
    ): Double =
        when (kind) {
            MeasureKind.COUNT -> kotlin.math.round(value * 2.0) / 2.0
            else -> round4(value)
        }

    private fun round4(value: Double): Double = kotlin.math.round(value * 10_000.0) / 10_000.0

    // --- Delta reconciliation (F04 §3's contract table) -------------------------

    /** Current persisted row state, as the engine sees it. */
    @Serializable
    public data class ExistingItem(
        public val id: String,
        public val groceryItemId: String,
        public val qty: Double,
        public val unit: String,
        public val checked: Boolean,
        public val archived: Boolean = false,
    )

    public sealed class ReconciliationOp {
        /** Quantity unchanged — row untouched, check preserved. */
        public data class Unchanged(
            public val rowId: String,
        ) : ReconciliationOp()

        /** Quantity changed — same row keeps its id + check; [delta] feeds the "+N" chip. */
        public data class QtyChanged(
            public val rowId: String,
            public val newQty: Double,
            public val newUnit: String,
            public val deltaQty: Double,
            public val checked: Boolean,
        ) : ReconciliationOp()

        /** New ingredient — appended unchecked at its aisle. */
        public data class Added(
            public val groceryItemId: String,
            public val name: String,
            public val qty: Double,
            public val unit: String,
        ) : ReconciliationOp()

        /** Ingredient removed — row struck through (archived), recoverable, check preserved. */
        public data class Removed(
            public val rowId: String,
        ) : ReconciliationOp()

        /** A previously removed ingredient came back — row restores, check preserved. */
        public data class Restored(
            public val rowId: String,
            public val newQty: Double,
            public val newUnit: String,
            public val deltaQty: Double,
            public val checked: Boolean,
        ) : ReconciliationOp()
    }

    /**
     * Diffs the persisted rows against the freshly consolidated plan state —
     * the ONLY way a plan edit reaches the list (F04 §3 "reconciliation, not
     * regeneration"). Matching is per (item, unit-kind) on canonical totals,
     * so a dominant-unit flip (all-cups → all-ml) is a quantity change, not a
     * remove+add; a kind with no row at all appends a fresh unchecked row;
     * unarchived rows the plan no longer needs are removed (struck through);
     * archived rows the plan needs again are restored. Checks ride the row,
     * so they survive every branch — the anti-Mealime contract as a type.
     */
    public fun reconcile(
        existing: List<ExistingItem>,
        required: List<ConsolidatedItem>,
    ): List<ReconciliationOp> {
        val ops = mutableListOf<ReconciliationOp>()
        val requiredByItem = required.associateBy { it.groceryItemId }
        val handledItems = mutableSetOf<String>()

        for ((itemId, rows) in existing.groupBy { it.groceryItemId }) {
            val need = requiredByItem[itemId]
            if (need == null) {
                rows.filter { !it.archived }.forEach { ops += ReconciliationOp.Removed(it.id) }
                continue
            }
            handledItems.add(itemId)
            val neededByKind = need.lines.associateBy { unit(it.unit).kind }
            val rowsByKind = rows.groupBy { unit(it.unit).kind }

            // 1. Rows whose kind the plan no longer asks for → removed.
            rowsByKind
                .filterKeys { kind -> !neededByKind.contains(kind) }
                .forEach { (_, kindRows) ->
                    kindRows.filter { !it.archived }.forEach { ops += ReconciliationOp.Removed(it.id) }
                }

            // 2. Needed kinds: reconcile against the kind's primary row (the
            // first live row, else the first archived one); extra live rows of
            // the same kind are a v1 invariant violation and pass through
            // untouched rather than being merged silently.
            neededByKind.forEach { (kind, line) ->
                val kindRows = rowsByKind[kind].orEmpty()
                val primary =
                    kindRows.firstOrNull { !it.archived } ?: kindRows.firstOrNull()
                if (primary == null) {
                    ops +=
                        ReconciliationOp.Added(
                            groceryItemId = itemId,
                            name = need.name,
                            qty = roundDisplay(line.qty, kind),
                            unit = line.unit,
                        )
                    return@forEach
                }
                val rowCanonical = unit(primary.unit).toCanonical(primary.qty)
                val newCanonical = unit(line.unit).toCanonical(line.qty)
                val deltaCanonical = newCanonical - rowCanonical
                val displayUnit =
                    if (rowCanonical >= newCanonical) primary.unit else line.unit
                val newDisplay = roundDisplay(unit(displayUnit).fromCanonical(newCanonical), kind)
                val deltaDisplay = roundDisplay(unit(displayUnit).fromCanonical(deltaCanonical), kind)
                when {
                    kindInvariant(kind, rowCanonical, newCanonical) && !primary.archived ->
                        ops += ReconciliationOp.Unchanged(primary.id)

                    kindInvariant(kind, rowCanonical, newCanonical) ->
                        ops += ReconciliationOp.Restored(primary.id, newDisplay, displayUnit, 0.0, primary.checked)

                    primary.archived ->
                        ops += ReconciliationOp.Restored(primary.id, newDisplay, displayUnit, deltaDisplay, primary.checked)

                    else ->
                        ops +=
                            ReconciliationOp.QtyChanged(primary.id, newDisplay, displayUnit, deltaDisplay, primary.checked)
                }
            }
        }

        requiredByItem
            .filterKeys { !handledItems.contains(it) }
            .forEach { (_, item) ->
                item.lines.forEach { line ->
                    ops +=
                        ReconciliationOp.Added(
                            groceryItemId = item.groceryItemId,
                            name = item.name,
                            qty = roundDisplay(line.qty, unit(line.unit).kind),
                            unit = line.unit,
                        )
                }
            }
        return ops
    }

    /**
     * Canonical totals within the display quantization count as unchanged:
     * counts render at half-unit resolution ("onions ×1.5"), so a sub-half
     * difference is the same shopping reality, not a plan change. Mass and
     * volume render at 4 decimals — a tight tolerance.
     */
    private fun kindInvariant(
        kind: MeasureKind,
        a: Double,
        b: Double,
    ): Boolean =
        when (kind) {
            MeasureKind.COUNT -> kotlin.math.abs(a - b) <= 0.5
            else -> kotlin.math.abs(a - b) <= 1e-3
        }

    // --- Pantry deduction (R-S5; F04 §3 staples + partial-stock math) ------------

    @Serializable
    public data class PantryStock(
        public val groceryItemId: String,
        public val qty: Double,
        public val unit: String,
        public val isStaple: Boolean,
        public val outOfStock: Boolean = false,
    )

    /**
     * R-S5: partial-stock deduction is OFF by default globally (the toggle
     * lives in settings; [DeductionPolicy.enabled] defaults false). When ON,
     * STAPLES are auto-deducted from the generated list with partial-stock
     * math ("need 5, have 3 → buy 2"); non-staple items are never silently
     * reduced — surprising deductions are the #1 trust risk in F04 (§5).
     */
    @Serializable
    public data class DeductionPolicy(
        public val enabled: Boolean = false,
        public val staplesOnly: Boolean = true,
    )

    public fun applyDeduction(
        items: List<ConsolidatedItem>,
        pantry: List<PantryStock>,
        policy: DeductionPolicy,
    ): List<ConsolidatedItem> {
        if (!policy.enabled) return items
        val stockByItem = pantry.associateBy { it.groceryItemId }
        return items.map { item ->
            val stock = stockByItem[item.groceryItemId]
            if (stock == null || stock.qty <= 0.0 || stock.outOfStock) return@map item
            if (policy.staplesOnly && !stock.isStaple) return@map item
            val stockKind = unit(stock.unit).kind
            val lines =
                item.lines.map { line ->
                    if (unit(line.unit).kind != stockKind) {
                        line
                    } else {
                        val remainingCanonical =
                            unit(line.unit).toCanonical(line.qty) - unit(stock.unit).toCanonical(stock.qty)
                        val buy = if (remainingCanonical > 0.0) remainingCanonical else 0.0
                        line.copy(
                            qty = roundDisplay(unit(line.unit).fromCanonical(buy), stockKind),
                            sources = line.sources,
                        )
                    }
                }
            item.copy(lines = lines)
        }
    }
}
