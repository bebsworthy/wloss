package app.wlo.core.engines

import app.wlo.core.model.PlannedSlot
import app.wlo.core.model.PlannedSlotState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The R-B1 slot state machine (F03 §3/§7): PLANNED is the only mutable
 * state; confirmed/skipped/replaced are terminal; swaps retire + insert.
 * Also pins the planned-day projection (planned vs logged distinction) and
 * the R-B4 adherence metrics with their honest data gate.
 */
class PlannerStateMachineTest {
    private fun slot(
        state: PlannedSlotState,
        id: String = "s1",
    ): PlannedSlot =
        PlannedSlot(
            id = id,
            planId = "p1",
            profileId = "u1",
            dayEpochDay = 20_700,
            mealSlot = "dinner",
            recipeId = "r1",
            recipeName = "Red lentil curry",
            servings = 1.0,
            state = state,
            kcalPerServing = 500.0,
            proteinGPerServing = 30.0,
            carbGPerServing = 60.0,
            fatGPerServing = 15.0,
            fiberGPerServing = 12.0,
        )

    @Test
    fun plannedMovesAnywhere() {
        PlannedSlotState.entries
            .filter { it != PlannedSlotState.PLANNED }
            .forEach { to ->
                val moved = PlannerEngine.transition(slot(PlannedSlotState.PLANNED), to)
                assertNotNull(moved, "planned → ${to.wireName} is legal")
                assertEquals(to, moved.state)
            }
    }

    @Test
    fun terminalStatesNeverMove() {
        listOf(
            PlannedSlotState.CONFIRMED,
            PlannedSlotState.SWAPPED,
            PlannedSlotState.SKIPPED,
            PlannedSlotState.REPLACED,
        ).forEach { from ->
            PlannedSlotState.entries.forEach { to ->
                assertNull(
                    PlannerEngine.transition(slot(from), to),
                    "${from.wireName} is terminal — even to itself (append-only history)",
                )
            }
        }
    }

    @Test
    fun plannedDayTotalsFollowTheRuling() {
        val slots =
            listOf(
                slot(PlannedSlotState.PLANNED, "a"), // counted
                slot(PlannedSlotState.CONFIRMED, "b"), // counted (the plan's full claim)
                slot(PlannedSlotState.SKIPPED, "c"), // not counted
                slot(PlannedSlotState.REPLACED, "d"), // not counted — the F02 entry owns it
                slot(PlannedSlotState.SWAPPED, "e"), // retired — successor carries it
            )
        val totals = PlannerEngine.plannedDayTotals(slots)
        assertEquals(2, totals.slotCount)
        assertEquals(1000.0, totals.kcal)
        assertEquals(60.0, totals.proteinG)
        // Leftover children carry their own macros: nutrition follows eating slots.
        val withLeftover =
            PlannerEngine.plannedDayTotals(
                listOf(
                    slot(PlannedSlotState.PLANNED, "cook").copy(isCookEvent = true, servings = 2.0, batchServings = 4.0),
                    slot(PlannedSlotState.PLANNED, "leftover").copy(parentSlotId = "cook", servings = 2.0),
                ),
            )
        assertEquals(2, withLeftover.slotCount)
        assertEquals(2000.0, withLeftover.kcal)
    }

    @Test
    fun replacedSlotKeepsItsDiaryLink() {
        val replaced = PlannerEngine.transition(slot(PlannedSlotState.PLANNED), PlannedSlotState.REPLACED)!!
        val linked = replaced.copy(replacedByEntryId = "entry-9")
        assertEquals("entry-9", linked.replacedByEntryId, "R-B1: the F02 entry owns the nutrition; F03 keeps the link")
    }

    @Test
    fun adherenceIsRefusedBelowTheDataGate() {
        // Two logged days < the 3-day gate → refused, never guessed.
        val report =
            AdherenceMetrics.compute(
                AdherenceMetrics.Input(
                    slots = listOf(slot(PlannedSlotState.CONFIRMED, "a"), slot(PlannedSlotState.CONFIRMED, "b")),
                    actualKcalByDay = mapOf(20_700L to 950.0, 20_701L to 1_050.0),
                    asOfDayEpochDay = 20_705,
                ),
            )
        assertTrue(!report.meaningful)
        assertEquals("not yet meaningful", report.gateReason)
        assertEquals(null, report.planCoveragePct)
    }

    @Test
    fun adherenceComputesOnceMeaningful() {
        val slots =
            listOf(
                slot(PlannedSlotState.CONFIRMED, "a"),
                slot(PlannedSlotState.CONFIRMED, "b"),
                slot(PlannedSlotState.CONFIRMED, "c").copy(dayEpochDay = 20_702),
                slot(PlannedSlotState.PLANNED, "d").copy(dayEpochDay = 20_701),
                slot(PlannedSlotState.SKIPPED, "e").copy(dayEpochDay = 20_702),
                slot(PlannedSlotState.SWAPPED, "f").copy(dayEpochDay = 20_702),
            )
        val report =
            AdherenceMetrics.compute(
                AdherenceMetrics.Input(
                    slots = slots,
                    actualKcalByDay = mapOf(20_700L to 950.0, 20_701L to 1_050.0, 20_702L to 480.0),
                    asOfDayEpochDay = 20_705,
                ),
            )
        assertTrue(report.meaningful)
        // Coverage: past slots a..f minus swapped(f, retired) → 5 rows; 4 terminal.
        assertEquals(80.0, report.planCoveragePct)
        // Energy fidelity: median |actual − planned| over the two days with both sides.
        assertNotNull(report.energyFidelityKcal)
        assertEquals(1, report.swapGravity.size)
        assertEquals("r1", report.swapGravity.single().recipeId)
        assertEquals(1, report.swapGravity.single().swaps)
        // Cook realism: no planned leftovers → null (refused, not zero).
        assertEquals(null, report.cookRealism)
    }
}
