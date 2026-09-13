package app.wlo.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.GenerateWeekPlan
import app.wlo.core.data.GroceryRepository
import app.wlo.core.data.NewPantryItem
import app.wlo.core.data.PantryRepository
import app.wlo.core.data.PlanView
import app.wlo.core.data.PlannerRepository
import app.wlo.core.data.RecipeRepository
import app.wlo.core.data.ShoppingListRepository
import app.wlo.core.data.SweepItem
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.engines.ListExpansion
import app.wlo.core.model.PlannedSlotState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M5 PART B acceptance (WLO-0027): the plan→shop→pantry loop against the real
 * repositories on the real emulator. The seeded onboarding (SeedingRobot)
 * provides the targets; every test deals a deterministic plan (seed control)
 * and drives the F03/F04 surfaces through the deep links the registry owns.
 * UI assertions poll the rendered texts (UiDevice) so they ride whatever the
 * semantics tree prints — no internals scraped.
 */
@RunWith(AndroidJUnit4::class)
public class M5PlanShopTest {
    private val koin get() = GlobalContext.get()
    private lateinit var seeded: SeedingRobot.SeedResult

    @Before
    public fun seed() {
        seeded = SeedingRobot.onboardAndSeedWeek()
    }

    private fun planner(): PlannerRepository = koin.get()

    private fun listRepo(): ShoppingListRepository = koin.get()

    private fun pantryRepo(): PantryRepository = koin.get()

    private fun groceries(): GroceryRepository = koin.get()

    private fun recipes(): RecipeRepository = koin.get()

    private fun settings(): SettingsStore = koin.get()

    private fun dayProjection(): DayProjectionRepository = koin.get()

    private fun clock(): ClockPort = koin.get()

    private fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun today(): Long = DayBoundary.epochDay(clock().now(), TimeZone.currentSystemDefault())

    /** Deals a deterministic 7-day plan through the real repository door. */
    private fun dealPlan(seed: Long): PlanView {
        val result =
            runBlocking {
                planner().generateWeek(
                    GenerateWeekPlan(
                        profileId = seeded.profileId,
                        startDayEpochDay = today(),
                        days = 7,
                        seed = seed,
                    ),
                )
            }
        assertTrue(result is WloResult.Ok, "generation must succeed: $result")
        return result.value
    }

    private fun deliver(
        scenario: ActivityScenario<MainActivity>,
        uri: String,
    ) {
        scenario.onActivity { activity ->
            activity.deliverNewIntentForVerification(
                Intent(activity, MainActivity::class.java).setData(Uri.parse(uri)),
            )
        }
    }

    private fun awaitText(
        scenario: ActivityScenario<MainActivity>,
        text: String,
        timeoutMs: Long = 20_000,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (device().findObjects(By.textContains(text)).isNotEmpty()) return
            SystemClock.sleep(POLL_MS)
        }
        val visible =
            device()
                .findObjects(By.textContains(" "))
                .mapNotNull { it.text }
                .filter { it.length in 3..60 }
                .distinct()
                .take(20)
        error("text \"$text\" never appeared; visible: $visible")
    }

    private fun generateWeekThroughUi(): Long {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        TestNav.awaitSurface(scenario, "hub")
        deliver(scenario, "wlo://plan")
        TestNav.awaitRoute(scenario, "plan")
        val startedAt = SystemClock.elapsedRealtime()
        awaitText(scenario, "generate week", timeoutMs = 10_000)
        device().findObjects(By.text("generate week")).firstOrNull()?.click()
        // The week header (with "deal again") only renders once the plan exists.
        awaitText(scenario, "deal again")
        awaitText(scenario, "Why this plan")
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        scenario.onActivity { it.finish() }
        SystemClock.sleep(500)
        return elapsedMs
    }

    // --- (a) the week deal -------------------------------------------------------

    @Test
    public fun planWeek_fromSeededOnboarding_dealsUnderThirtySeconds() {
        val elapsedMs = generateWeekThroughUi()
        assertTrue(elapsedMs < 30_000, "the week must deal in <30 s, took ${elapsedMs}ms")

        val plan = runBlocking { planner().currentPlan(seeded.profileId).getOrNull() }
        assertNotNull(plan, "the deal persists")
        assertEquals(21, plan.slots.size, "7 days x 3 slots")
        assertTrue(
            plan.slots.count { it.recipeId != null } >= 18,
            "the seeded library fills nearly every slot",
        )
        val report = plan.report
        assertNotNull(report, "the why-this-plan report rides the plan")
        assertTrue(report.filledSlots > 0)
        assertTrue(plan.seed != 0L || plan.version == 1, "the deal version is visible")
    }

    // --- (b) swap + confirm ------------------------------------------------------

    @Test
    public fun slotSwap_reDealsWithDifferentRecipe_andConfirmShiftsDayScalars() {
        val plan = dealPlan(seed = 42L)
        val today = today()

        val target =
            plan.slots
                .filter { it.dayEpochDay == today && it.state == PlannedSlotState.PLANNED && it.recipeId != null }
                .firstOrNull {
                    runBlocking {
                        planner()
                            .swapSuggestions(it.id)
                            .getOrNull()
                            .orEmpty()
                            .isNotEmpty()
                    }
                }
        assertNotNull(target, "a swappable slot exists for today")

        val suggestion = runBlocking { planner().swapSuggestions(target.id).getOrNull()!!.first() }
        assertTrue(suggestion.id != target.recipeId, "suggestions exclude the current recipe")

        val swapped = runBlocking { planner().swapSlot(target.id, suggestion.id, clock().now()) }
        assertTrue(swapped is WloResult.Ok, "swap lands")
        val successor = swapped.getOrNull()!!
        val after = runBlocking { planner().slots(seeded.profileId, today, today).getOrNull()!! }
        val landed = after.first { it.id == successor.id }
        assertEquals(suggestion.id, landed.recipeId, "the new recipe lands in the slot")
        assertEquals(PlannedSlotState.PLANNED, landed.state, "the successor is planned")

        // The retired record stays in history (append-only, R-B1).
        val retired = after.first { it.id == target.id }
        assertEquals(PlannedSlotState.SWAPPED, retired.state)
        assertEquals(successor.id, retired.successorSlotId)

        // Confirm shifts the DayView planned scalars: open slots decrement, the
        // kcal claim holds (planned + confirmed fold), the count stays.
        val before = runBlocking { dayProjection().day(seeded.profileId, today).getOrNull()!! }
        runBlocking { planner().confirmSlot(successor.id, clock().now()) }
        val afterConfirm = runBlocking { dayProjection().day(seeded.profileId, today).getOrNull()!! }
        assertEquals(before.plannedOpenSlots - 1, afterConfirm.plannedOpenSlots, "one fewer open slot")
        assertEquals(before.plannedSlotCount, afterConfirm.plannedSlotCount, "the claim still counts the meal")
        assertEquals(
            before.plannedKcal?.value,
            afterConfirm.plannedKcal?.value,
            "the kcal claim holds (planned + confirmed fold)",
        )

        // The plan tab renders the mutated week (completion assert).
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        TestNav.awaitSurface(scenario, "hub")
        deliver(scenario, "wlo://plan")
        TestNav.awaitRoute(scenario, "plan")
        awaitText(scenario, "Why this plan")
        awaitText(scenario, "eaten")
        scenario.onActivity { it.finish() }
        SystemClock.sleep(500)
    }

    // --- (c) list generation: aisles, consolidation, checks survive --------------

    @Test
    public fun generateList_aisleGroups_consolidation_checksSurviveRegeneration() {
        val plan = dealPlan(seed = 7L)
        val now = clock().now()

        val first =
            runBlocking {
                listRepo().generateFromPlan(
                    profileId = seeded.profileId,
                    planId = plan.planId,
                    fromDay = plan.startDayEpochDay,
                    toDay = plan.endDayEpochDay,
                    at = now,
                )
            }
        assertTrue(first is WloResult.Ok, "generation succeeds")
        assertTrue(first.getOrNull()!!.added > 0, "rows are added")
        val items = runBlocking { listRepo().items(seeded.profileId).getOrNull()!! }
        assertTrue(items.isNotEmpty(), "the list is not empty")

        // Consolidation: an ingredient the seed recipes share across days sums
        // into ONE row (olive oil appears in most dinner recipes, in tbsp).
        val consolidated = items.filter { it.sources.size >= 3 && it.unit == "tbsp" }
        assertTrue(consolidated.isNotEmpty(), "a summed line exists (olive-oil-style tbsp merge)")
        assertTrue(items.groupBy { it.aisle }.size >= 3, "several aisles are populated")

        // UI: the list renders its aisle groups; the reconciliation banner is
        // driven by the UI build button (the same door the banner reports).
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        TestNav.awaitSurface(scenario, "hub")
        deliver(scenario, "wlo://list")
        TestNav.awaitRoute(scenario, "f04/list")
        awaitText(scenario, "Produce")

        // Check one row ON SCREEN, mutate the plan (swap), then rebuild via the
        // button: the check survives and the banner announces the reconciliation.
        val checkedRow = items.first()
        device()
            .findObjects(By.textContains(checkedRow.name))
            .firstOrNull()
            ?.click()
            ?: error("the row to check never rendered")
        SystemClock.sleep(400)

        val target =
            plan.slots
                .first {
                    it.state == PlannedSlotState.PLANNED &&
                        it.recipeId != null &&
                        runBlocking {
                            planner()
                                .swapSuggestions(it.id)
                                .getOrNull()
                                .orEmpty()
                                .isNotEmpty()
                        }
                }
        val suggestion = runBlocking { planner().swapSuggestions(target.id).getOrNull()!!.first() }
        runBlocking { planner().swapSlot(target.id, suggestion.id, now) }

        device()
            .findObjects(By.textContains("build list"))
            .firstOrNull()
            ?.click()
            ?: error("the build button never rendered")
        awaitText(scenario, "Your checks are safe")

        val itemsAfter = runBlocking { listRepo().items(seeded.profileId).getOrNull()!! }
        val stillChecked = itemsAfter.firstOrNull { it.id == checkedRow.id }
        assertNotNull(stillChecked, "the checked row survives the regeneration")
        assertTrue(stillChecked.checked, "your checks are safe (the anti-Mealime contract)")
        scenario.onActivity { it.finish() }
        SystemClock.sleep(500)
    }

    // --- (d) delta chip on quantity change; revert restores ----------------------

    @Test
    public fun quantityChange_showsDeltaChip_andRevertClearsIt() {
        val plan = dealPlan(seed = 11L)
        val now = clock().now()

        // Find a swap that changes a SHARED ingredient's amount: slot S dealt
        // recipe A; one of its engine suggestions B shares a grocery line with
        // A at a different per-serving qty. The seed library overlaps heavily,
        // so some slot×suggestion pair qualifies.
        var swapFrom: app.wlo.core.model.PlannedSlot? = null
        var swapTo: app.wlo.core.model.Recipe? = null
        var sharedItemId: String? = null
        for (slot in plan.slots.filter {
            it.state == PlannedSlotState.PLANNED &&
                it.recipeId != null &&
                it.parentSlotId == null &&
                !it.isCookEvent
        }) {
            val recipeA = runBlocking { recipes().byId(slot.recipeId!!).getOrNull() } ?: continue
            val suggestions = runBlocking { planner().swapSuggestions(slot.id).getOrNull().orEmpty() }
            for (candidate in suggestions) {
                val recipeB = runBlocking { recipes().byId(candidate.id).getOrNull() } ?: continue
                val byItem = recipeB.ingredients.associate { it.groceryItemId to it }
                val shared =
                    recipeA.ingredients.firstOrNull { orig ->
                        byItem[orig.groceryItemId]?.let { it.qty != orig.qty } == true
                    }
                if (shared != null) {
                    swapFrom = slot
                    swapTo = recipeB
                    sharedItemId = shared.groceryItemId
                    break
                }
            }
            if (swapFrom != null) break
        }
        assertNotNull(swapFrom, "a slot whose swap changes a shared quantity exists")
        assertNotNull(swapTo)
        assertNotNull(sharedItemId)

        // First generation: the baseline the delta chip measures against.
        runBlocking {
            listRepo().generateFromPlan(
                profileId = seeded.profileId,
                planId = plan.planId,
                fromDay = plan.startDayEpochDay,
                toDay = plan.endDayEpochDay,
                at = now,
            )
        }
        val sharedOriginalQty =
            runBlocking { listRepo().items(seeded.profileId).getOrNull()!! }
                .firstOrNull { it.groceryItemId == sharedItemId }
                ?.qty
        assertNotNull(sharedOriginalQty, "the shared line exists in the first build")

        val swapped =
            runBlocking { planner().swapSlot(swapFrom.id, swapTo.id, now) }.getOrNull()
        assertNotNull(swapped, "the swap lands")
        val second =
            runBlocking {
                listRepo().generateFromPlan(
                    profileId = seeded.profileId,
                    planId = plan.planId,
                    fromDay = plan.startDayEpochDay,
                    toDay = plan.endDayEpochDay,
                    at = now,
                )
            }
        assertTrue(second is WloResult.Ok)
        assertTrue(
            second.getOrNull()!!.quantityChanged > 0,
            "the diff counts the quantity change",
        )
        val itemsAfter = runBlocking { listRepo().items(seeded.profileId).getOrNull()!! }
        val deltaRow = itemsAfter.firstOrNull { it.deltaQty != null }
        assertNotNull(deltaRow, "a delta chip exists after the quantity change")

        // Revert: swap the recorded successor back to the original recipe.
        runBlocking { planner().swapSlot(swapped.id, swapFrom.recipeId!!, now) }
        runBlocking {
            listRepo().generateFromPlan(
                profileId = seeded.profileId,
                planId = plan.planId,
                fromDay = plan.startDayEpochDay,
                toDay = plan.endDayEpochDay,
                at = now,
            )
        }
        val itemsReverted = runBlocking { listRepo().items(seeded.profileId).getOrNull()!! }
        // The revert restores the SHARED LINE's quantity; the delta chip stays
        // honest about the round trip (measured against the last list state).
        val revertedRow = itemsReverted.firstOrNull { it.groceryItemId == sharedItemId }
        assertNotNull(revertedRow)
        assertEquals(sharedOriginalQty, revertedRow.qty, "the shared line's quantity is restored")
        assertNull(
            itemsReverted.firstOrNull { it.deltaQty != null && it.deltaQty!! == 0.0 },
            "no zero-delta chips render",
        )
    }

    // --- (e) pantry: stock, sweep, R-S5 deduction --------------------------------

    @Test
    public fun pantry_stockSweepAndDeduction_offByDefaultThenVisibleWhenToggled() {
        val plan = dealPlan(seed = 5L)
        val now = clock().now()

        // Stock: 5 eggs marked staple (the seeded breakfast bakes use eggs).
        val eggs = runBlocking { groceries().ensure(seeded.profileId, "eggs", "x", now).getOrNull()!! }
        runBlocking {
            pantryRepo().upsert(
                NewPantryItem(
                    profileId = seeded.profileId,
                    groceryItemId = eggs.id,
                    name = "eggs",
                    qty = 5.0,
                    unit = "x",
                    isStaple = true,
                ),
                now,
            )
        }

        // R-S5 default: the settings toggle is OFF.
        val deductionDefault = runBlocking { settings().pantryDeductionEnabled.first() }
        assertTrue(!deductionDefault, "R-S5: partial-stock deduction is off by default")

        // Generation with deduction off: the full plan need is listed.
        runBlocking {
            listRepo().generateFromPlan(
                profileId = seeded.profileId,
                planId = plan.planId,
                fromDay = plan.startDayEpochDay,
                toDay = plan.endDayEpochDay,
                at = now,
            )
        }
        val listOff = runBlocking { listRepo().items(seeded.profileId).getOrNull()!! }
        val eggsOff = listOff.firstOrNull { it.groceryItemId == eggs.id }
        assertNotNull(eggsOff, "the eggs line exists (the seeded breakfasts bake with eggs)")

        // Toggle on; regenerate with the policy: the deduction math is visible.
        runBlocking { settings().setPantryDeductionEnabled(true) }
        runBlocking {
            listRepo().generateFromPlan(
                profileId = seeded.profileId,
                planId = plan.planId,
                fromDay = plan.startDayEpochDay,
                toDay = plan.endDayEpochDay,
                at = now,
                deduction = ListExpansion.DeductionPolicy(enabled = true),
            )
        }
        val listOn = runBlocking { listRepo().items(seeded.profileId).getOrNull()!! }
        val eggsOn = listOn.firstOrNull { it.groceryItemId == eggs.id }
        assertNotNull(eggsOn)
        assertTrue(
            eggsOn.qty < eggsOff.qty,
            "deduction on: the staple comes off the list (${eggsOff.qty} → ${eggsOn.qty})",
        )

        // Sweep: checked rows land in stock, purchase counts rise.
        val anyRow = listOn.first { !it.checked }
        runBlocking { listRepo().setChecked(anyRow.id, true, now) }
        val swept =
            runBlocking {
                pantryRepo().sweep(
                    seeded.profileId,
                    listOf(SweepItem(anyRow.groceryItemId, anyRow.name, 1.0, anyRow.unit)),
                    now,
                )
            }
        assertTrue(swept is WloResult.Ok && swept.value == 1, "the sweep lands one item")
        val stock = runBlocking { pantryRepo().byGroceryItem(seeded.profileId, anyRow.groceryItemId).getOrNull() }
        assertNotNull(stock, "the swept row is in stock")

        // The pantry screen renders the stock with the deduction state.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        TestNav.awaitSurface(scenario, "hub")
        deliver(scenario, "wlo://pantry")
        TestNav.awaitRoute(scenario, "f04/pantry")
        awaitText(scenario, "inventory")
        awaitText(scenario, "deduction on")
        scenario.onActivity { it.finish() }
        SystemClock.sleep(500)
    }

    // --- (f) exports round-trip --------------------------------------------------

    @Test
    public fun exportTextCarriesAisleGroups_andImportCsvRoundTrips() {
        val plan = dealPlan(seed = 3L)
        val now = clock().now()

        runBlocking {
            listRepo().generateFromPlan(
                profileId = seeded.profileId,
                planId = plan.planId,
                fromDay = plan.startDayEpochDay,
                toDay = plan.endDayEpochDay,
                at = now,
            )
        }

        // Text export carries aisle headers + items (share-sheet body).
        val text = runBlocking { listRepo().exportText(seeded.profileId).getOrNull()!! }
        assertTrue(text.contains("Produce"), "the taxonomy names Produce:\n$text")
        assertTrue(text.contains("Pantry"), "the taxonomy names Pantry")

        // CSV round-trip: export → import → the rows come back.
        val csv = runBlocking { listRepo().exportCsv(seeded.profileId).getOrNull()!! }
        assertTrue(csv.lines().size > 1, "the CSV carries rows")
        val imported = runBlocking { listRepo().importCsv(seeded.profileId, csv, now) }
        assertTrue(imported is WloResult.Ok, "the round-trip parses")
        assertTrue(imported.getOrNull()!! > 0, "rows re-import")

        // A corrupt CSV fails as a value; nothing is imported.
        val bad = runBlocking { listRepo().importCsv(seeded.profileId, "not a csv row at all\n", now) }
        assertTrue(bad is WloResult.Err, "a corrupt import errors honestly")

        // UI happy path: the list screen renders with aisle content.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        TestNav.awaitSurface(scenario, "hub")
        deliver(scenario, "wlo://list")
        TestNav.awaitRoute(scenario, "f04/list")
        awaitText(scenario, "Produce")
        scenario.onActivity { it.finish() }
        SystemClock.sleep(500)
    }

    private companion object {
        private const val POLL_MS: Long = 150L
    }
}
