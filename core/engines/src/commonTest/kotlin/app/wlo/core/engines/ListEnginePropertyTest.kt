package app.wlo.core.engines

import app.wlo.core.model.Aisle
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property tests for the F04 list engine (T-J; F04 §3 semantics):
 * consolidation is order-independent (it commutes and associates over the
 * source lines), delta reconciliation never destroys check-state, and the
 * aisle engine is TOTAL. Kotest-property rides inside kotlin-test (house
 * pattern, T-J); generated cases ride beside the frozen golden scenarios.
 */
class ListEnginePropertyTest {
    private val items = listOf("eggs", "milk", "rice", "onions", "flour", "spinach")
    private val units = listOf("g", "kg", "ml", "l", "cup", "tbsp", "x")

    private fun arbRequirements(): Arb<List<ListExpansion.Requirement>> =
        Arb
            .list(
                Arb.bind(Arb.element(items), Arb.numericDouble(0.5, 900.0), Arb.element(units)) { item, qty, unit ->
                    Triple(item, qty, unit)
                },
                range = 1..24,
            ).map { triples ->
                triples.mapIndexed { index, (item, qty, unit) ->
                    ListExpansion.Requirement(
                        groceryItemId = item,
                        name = item,
                        qty = qty,
                        unit = unit,
                        recipeId = "r${index % 4}",
                        slotId = "s$index",
                        dayEpochDay = index.toLong(),
                    )
                }
            }

    @Test
    fun consolidationIsOrderIndependent() =
        runTest {
            checkAll(iterations = 200, arbRequirements()) { requirements ->
                val a = ListExpansion.consolidate(requirements)
                val b = ListExpansion.consolidate(requirements.reversed())

                fun canonical(result: List<ListExpansion.ConsolidatedItem>) =
                    result
                        .associate { item ->
                            item.groceryItemId to
                                item.lines
                                    .map { line -> line.unit to line.qty }
                                    .sortedBy { it.first }
                        }

                assertEquals(canonical(a).keys, canonical(b).keys)
                canonical(a).forEach { (item, lines) ->
                    val other =
                        assertNotNull(canonical(b)[item], "item $item present in both orders")
                    assertEquals(lines, other, "consolidation must not depend on source order")
                }
            }
        }

    @Test
    fun consolidationSumsLikeTheSpecExamples() {
        // F04 §3's canonical case: eggs ×2 + eggs ×3 → ×5.
        val eggs =
            ListExpansion
                .consolidate(
                    listOf(
                        req("eggs", 2.0, "x", "pancakes"),
                        req("eggs", 3.0, "x", "dinner"),
                    ),
                ).single()
        assertEquals(5.0, eggs.lines.single().qty)

        // Convertible units merge into the dominant display unit:
        // milk 250 ml + 1 cup (240 ml) → 490 ml in ml.
        val milk =
            ListExpansion
                .consolidate(
                    listOf(
                        req("milk", 250.0, "ml", "porridge"),
                        req("milk", 1.0, "cup", "latte"),
                    ),
                ).single()
        assertEquals("ml", milk.lines.single().unit)
        assertEquals(490.0, milk.lines.single().qty)

        // Cross-kind lines stay separate and honest: flour 300 g + 2 cups.
        val flour =
            ListExpansion
                .consolidate(
                    listOf(
                        req("flour", 300.0, "g", "bread"),
                        req("flour", 2.0, "cup", "pancakes"),
                    ),
                ).single()
        assertEquals(2, flour.lines.size)
        assertTrue(flour.lines.any { it.unit == "g" && it.qty == 300.0 })
        assertTrue(flour.lines.any { it.unit == "cup" && it.qty == 2.0 })
    }

    @Test
    fun reconcilingAListThatMatchesThePlanChangesNothing() =
        runTest {
            checkAll(iterations = 200, arbRequirements()) { planA ->
                val listA = ListExpansion.consolidate(planA)
                val rowsA =
                    listA.flatMap { item ->
                        item.lines.mapIndexed { index, line ->
                            ListExpansion.ExistingItem(
                                id = "row-${item.groceryItemId}-${line.unit}-$index",
                                groceryItemId = item.groceryItemId,
                                qty = line.qty,
                                unit = line.unit,
                                checked = index % 2 == 0,
                            )
                        }
                    }
                val ops = ListExpansion.reconcile(rowsA, listA)
                assertTrue(
                    ops.all { it is ListExpansion.ReconciliationOp.Unchanged },
                    "a list that already matches the plan is untouched (checks preserved): $ops",
                )
            }
        }

    @Test
    fun reconciliationNeverDestroysChecks() =
        runTest {
            checkAll(iterations = 200, arbRequirements()) { planA ->
                val listA = ListExpansion.consolidate(planA)
                val rows =
                    listA.flatMap { item ->
                        item.lines.mapIndexed { index, line ->
                            ListExpansion.ExistingItem(
                                id = "row-${item.groceryItemId}-${line.unit}-$index",
                                groceryItemId = item.groceryItemId,
                                qty = line.qty,
                                unit = line.unit,
                                checked = true,
                            )
                        }
                    }
                // Grow the plan: same items, more of everything (a servings change).
                val planB = planA.map { it.copy(qty = it.qty * 2.0) }
                val ops = ListExpansion.reconcile(rows, ListExpansion.consolidate(planB))
                ops.filterIsInstance<ListExpansion.ReconciliationOp.QtyChanged>().forEach {
                    assertTrue(it.checked, "a quantity change never unchecks a row")
                }
                assertTrue(
                    ops.none { it is ListExpansion.ReconciliationOp.Removed },
                    "growth never strikes items through",
                )
                // And the revert restores the exact prior state (same rows, same checks).
                val rowsB =
                    ops.fold(rows) { acc, op ->
                        when (op) {
                            is ListExpansion.ReconciliationOp.QtyChanged ->
                                acc.map { if (it.id == op.rowId) it.copy(qty = op.newQty, unit = op.newUnit) else it }

                            else -> acc
                        }
                    }
                val revertOps = ListExpansion.reconcile(rowsB, listA)
                val rowsRestored =
                    revertOps.fold(rowsB) { acc, op ->
                        when (op) {
                            is ListExpansion.ReconciliationOp.QtyChanged ->
                                acc.map { if (it.id == op.rowId) it.copy(qty = op.newQty, unit = op.newUnit) else it }

                            else -> acc
                        }
                    }
                assertEquals(
                    rows,
                    rowsRestored,
                    "reverting the plan edit restores the prior list exactly (ids, quantities, checks)",
                )
            }
        }

    @Test
    fun aisleAssignmentIsTotal() =
        runTest {
            checkAll(iterations = 100, Arb.string(1..30)) { name ->
                val aisle = AisleEngine.assign(AisleEngine.Item(id = "x", name = name, aisleTag = null))
                assertNotNull(aisle, "every item resolves to an aisle (total function)")
                assertTrue(Aisle.entries.contains(aisle))
            }
        }

    @Test
    fun learnedAisleCorrectionAlwaysWins() {
        val learned =
            AisleEngine.assign(
                AisleEngine.Item(id = "i1", name = "jasmine rice", aisleTag = "pantry"),
                corrections = mapOf("i1" to "household"),
            )
        assertEquals(Aisle.HOUSEHOLD, learned)
    }

    @Test
    fun deductionFollowsRs5Semantics() {
        val items =
            listOf(
                ListExpansion.ConsolidatedItem(
                    "eggs",
                    "eggs",
                    "",
                    listOf(ListExpansion.ConsolidatedLine(5.0, "x")),
                ),
            )
        val stock = listOf(ListExpansion.PantryStock("eggs", 3.0, "x", isStaple = true))
        // R-S5: OFF by default — nothing changes.
        assertEquals(items, ListExpansion.applyDeduction(items, stock, ListExpansion.DeductionPolicy()))
        // ON: partial-stock math — need 5, have 3 → buy 2.
        val deducted =
            ListExpansion.applyDeduction(items, stock, ListExpansion.DeductionPolicy(enabled = true)).single()
        assertEquals(2.0, deducted.lines.single().qty)
        // Non-staples are never silently reduced, even when ON.
        val nonStaple =
            ListExpansion
                .applyDeduction(
                    items,
                    listOf(ListExpansion.PantryStock("eggs", 3.0, "x", isStaple = false)),
                    ListExpansion.DeductionPolicy(enabled = true),
                ).single()
        assertEquals(5.0, nonStaple.lines.single().qty)
    }

    private fun req(
        item: String,
        qty: Double,
        unit: String,
        recipe: String,
    ): ListExpansion.Requirement = ListExpansion.Requirement(item, item, qty, unit, recipe, "s1", 1)
}
