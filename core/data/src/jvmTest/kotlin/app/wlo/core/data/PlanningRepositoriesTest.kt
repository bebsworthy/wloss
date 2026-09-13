package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.datastore.SettingsStoreFactory
import app.wlo.core.documents.Cadence
import app.wlo.core.documents.Energy
import app.wlo.core.documents.FiberTarget
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.engines.ListExpansion
import app.wlo.core.model.Aisle
import app.wlo.core.model.PlannedSlotState
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The F03↔F04 loop on the JVM Room driver (M5 acceptance): seeds the R-S3
 * library, generates a week against real Targets, walks the R-B1 state
 * machine, folds planned scalars into the day record, and proves the
 * anti-Mealime contract — plan edits reconcile by delta with checks intact.
 */
class PlanningRepositoriesTest {
    private val dir = Files.createTempDirectory("wlo-planning-test")
    private val db: WloDatabase = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()
    private val settings = SettingsStoreFactory.create(dir.resolve("settings.preferences_pb").toString().toPath())

    private val targets = RoomTargetsRepository(db)
    private val projector = DayProjector(db, clock)
    private val recipes = RoomRecipeRepository(db)
    private val planner = RoomPlannerRepository(db, targets, recipes, projector, clock)
    private val pantry = RoomPantryRepository(db, clock)
    private val list = RoomShoppingListRepository(db, planner, recipes, pantry, clock)
    private val projection = RoomDayProjectionRepository(db, projector, targets)

    @AfterTest
    fun tearDown() {
        db.close()
    }

    // 2026-09-12 (the FakeClock's date) is a Saturday; plan the next week from Monday.
    private val weekStart: Long = LocalDate(2026, 9, 14).toEpochDays()

    private suspend fun aProfileWithTargets(): String {
        val profileId = "p-test"
        db.profiles().upsert(
            app.wlo.core.database.ProfileEntity(
                id = profileId,
                sex = null,
                birthYear = 1994,
                heightCm = 178.0,
                startWeightKg = 82.0,
                activityLevel = "moderate",
                unitPreference = "metric",
                createdAtEpochMs = clock.now().toEpochMilliseconds(),
            ),
        )
        val document =
            TargetsDocument(
                goal = Goal(targetWeightKg = 76.0, pacePctPerWeek = -0.5),
                energy =
                    Energy(
                        cadence = Cadence.DAILY,
                        budgetKcal = 1_900.0,
                        floorKcal = 1_500.0,
                    ),
                macros = Macros(split = MacroSplit.Preset("balanced"), proteinFloorG = 90.0),
                fiber = FiberTarget(targetG = 28.0),
            )
        val outcome = TargetsWriters(db, clock).studio().writeFirst(profileId, document)
        assertTrue(
            outcome is app.wlo.core.data.TargetsWriteOutcome.Written,
            "targets v1 written, was: $outcome",
        )
        return profileId
    }

    @Test
    fun seedsOnceGeneratesAWeekAndWalksTheStateMachine() =
        runTest {
            val profileId = aProfileWithTargets()

            // Seeding is idempotent (R-S3: inspectable, excludable user data).
            val firstSeed = recipes.ensureSeeded(profileId, clock.now()).okOrDie()
            assertTrue(firstSeed >= 50, "the full seed library lands ($firstSeed recipes)")
            assertEquals(0, recipes.ensureSeeded(profileId, clock.now()).okOrDie(), "second seeding is a no-op")

            val library = recipes.library(profileId).okOrDie()
            assertTrue(library.all { it.source == app.wlo.core.model.RecipeSource.SEED })

            // Generate against today's targets.
            val plan = planner.generateWeek(GenerateWeekPlan(profileId, weekStart)).okOrDie()
            assertEquals(21, plan.slots.size, "7 days × 3 slots")
            assertEquals(21, assertNotNull(plan.report).filledSlots, "the seed library fills a balanced week")
            val openSlots = plan.slots.filter { it.state == PlannedSlotState.PLANNED }
            assertEquals(21, openSlots.size)
            assertTrue(openSlots.all { it.kcalPerServing != null }, "slots carry denormalized nutrition")

            // Determinism at the repository boundary too.
            val planAgain = planner.generateWeek(GenerateWeekPlan(profileId, weekStart, seed = plan.seed)).okOrDie()
            assertEquals(
                plan.slots.map { it.mealSlot to it.dayEpochDay to it.recipeId },
                planAgain.slots.map { it.mealSlot to it.dayEpochDay to it.recipeId },
                "same seed → same deal",
            )

            // R-B1 state machine through the door.
            val slotId = plan.slots.first().id
            val confirmed = planner.confirmSlot(slotId, clock.now()).okOrDie()
            assertEquals(PlannedSlotState.CONFIRMED, confirmed.state)
            assertTrue(planner.confirmSlot(slotId, clock.now()) is WloResult.Err, "confirmed is terminal")
            assertTrue(planner.swapSlot(slotId, plan.slots.first().recipeId!!, clock.now()) is WloResult.Err, "no swap after confirm")

            val swapped = planner.swapSlot(plan.slots[1].id, plan.slots[1].recipeId!!, clock.now()).okOrDie()
            assertEquals(PlannedSlotState.PLANNED, swapped.state, "the successor is planned")
            assertEquals(plan.slots[1].id, swapped.replacesSlotId, "the chain is recorded")
            val retired = planner.slots(profileId, weekStart, weekStart + 6).okOrDie().first { it.id == plan.slots[1].id }
            assertEquals(PlannedSlotState.SWAPPED, retired.state)
            assertEquals(swapped.id, retired.successorSlotId)

            // planned → replaced carries the F02 entry link (R-B1).
            val replaced = planner.replaceSlot(plan.slots[2].id, "entry-42", clock.now()).okOrDie()
            assertEquals(PlannedSlotState.REPLACED, replaced.state)
            assertEquals("entry-42", replaced.replacedByEntryId)
        }

    @Test
    fun dayViewFoldsThePlannedSideOfThePlan() =
        runTest {
            val profileId = aProfileWithTargets()
            planner.generateWeek(GenerateWeekPlan(profileId, weekStart)).okOrDie()

            val day = projection.day(profileId, weekStart + 1).okOrDie()
            assertNotNull(day.plannedKcal, "the plan's claim renders in the day record")
            assertEquals(3, day.plannedSlotCount)
            assertEquals(3, day.plannedOpenSlots)
            val plannedKcal = day.plannedKcal!!.value
            assertTrue(plannedKcal in 1_000.0..3_000.0, "planned kcal is a plausible day ($plannedKcal)")

            // Skipping a slot shrinks the claim (skipped contributes nothing).
            val slots = planner.slots(profileId, weekStart + 1, weekStart + 1).okOrDie()
            planner.skipSlot(slots.first().id, clock.now()).okOrDie()
            val afterSkip = projection.day(profileId, weekStart + 1).okOrDie()
            assertEquals(2, afterSkip.plannedOpenSlots)
            assertTrue(afterSkip.plannedKcal!!.value < plannedKcal)
        }

    @Test
    fun listGenerationReconcilesByDeltaAndPreservesChecks() =
        runTest {
            val profileId = aProfileWithTargets()
            recipes.ensureSeeded(profileId, clock.now()).okOrDie()
            val plan = planner.generateWeek(GenerateWeekPlan(profileId, weekStart)).okOrDie()

            // First generation: everything arrives unchecked.
            val summary1 =
                list
                    .generateFromPlan(profileId, plan.planId, weekStart, weekStart + 6, clock.now())
                    .okOrDie()
            assertTrue(summary1.added > 10, "the week's list is real (${summary1.added} items)")
            val items1 = list.items(profileId).okOrDie()
            assertTrue(items1.none { it.checked })

            // Aisles come from the shipped taxonomy, learned corrections aside.
            assertTrue(items1.all { Aisle.fromWireName(it.aisle) != null })

            // Check an item, then re-generate the SAME plan: untouched, still checked.
            val checked = items1.first()
            list.setChecked(checked.id, true, clock.now()).okOrDie()
            val summary2 =
                list
                    .generateFromPlan(profileId, plan.planId, weekStart, weekStart + 6, clock.now())
                    .okOrDie()
            assertTrue(!summary2.touched(), "an identical plan edits nothing: $summary2")
            assertTrue(
                list
                    .items(profileId)
                    .okOrDie()
                    .first { it.id == checked.id }
                    .checked,
            )

            // Servings double (company tonight): quantities rise, checks survive, deltas recorded.
            val doubled = plan.slots.map { it.copy(servings = it.servings * 2) }
            doubled.forEach { db.planSlots().setServings(it.id, it.servings, clock.now().toEpochMilliseconds()) }
            projector.refresh(profileId, weekStart, weekStart + 6)
            val summary3 =
                list
                    .generateFromPlan(profileId, plan.planId, weekStart, weekStart + 6, clock.now())
                    .okOrDie()
            assertTrue(summary3.quantityChanged > 0, "servings change = quantity deltas: $summary3")
            assertTrue(summary3.added == 0, "growth never resets the list")
            val stillChecked = list.items(profileId).okOrDie().first { it.id == checked.id }
            assertTrue(stillChecked.checked, "the anti-Mealime core")
            assertNotNull(stillChecked.deltaQty, "the +N chip payload is recorded")

            // Plan half the week away: items vanish as strikes, checks survive in archive.
            val half = plan.slots.filter { it.dayEpochDay >= weekStart + 3 }
            half.forEach { planner.skipSlot(it.id, clock.now()).okOrDie() }

            val summary4 =
                list
                    .generateFromPlan(profileId, plan.planId, weekStart, weekStart + 6, clock.now())
                    .okOrDie()
            assertTrue(summary4.removed > 0, "removed items strike through: $summary4")
            val struck = list.struckThrough(profileId).okOrDie()
            assertTrue(struck.isNotEmpty(), "strikes are recoverable, not deleted")

            // Restore the plan: strikes come back with their checks.
            half.forEach { slot ->
                // skip is terminal; emulate the plan edit by re-planning the slot as planned
                db.planSlots().insert(
                    app.wlo.core.database.PlanSlotEntity(
                        id = slot.id + "-restored",
                        planId = slot.planId,
                        profileId = profileId,
                        dayEpochDay = slot.dayEpochDay,
                        mealSlot = slot.mealSlot,
                        recipeId = slot.recipeId,
                        recipeVersion = slot.recipeVersion,
                        recipeName = slot.recipeName,
                        servings = slot.servings,
                        state = "planned",
                        kcalPerServing = slot.kcalPerServing,
                        proteinGPerServing = slot.proteinGPerServing,
                        carbGPerServing = slot.carbGPerServing,
                        fatGPerServing = slot.fatGPerServing,
                        fiberGPerServing = slot.fiberGPerServing,
                        createdAtEpochMs = clock.now().toEpochMilliseconds(),
                    ),
                )
            }
            val summary5 =
                list
                    .generateFromPlan(profileId, plan.planId, weekStart, weekStart + 6, clock.now())
                    .okOrDie()
            assertTrue(summary5.restored > 0, "plan edits back restore struck items: $summary5")
            assertTrue(list.items(profileId).okOrDie().any { it.checked }, "checks ride the row through everything")
        }

    @Test
    fun exportsRoundTripAndImportsRespectChecks() =
        runTest {
            val profileId = aProfileWithTargets()
            recipes.ensureSeeded(profileId, clock.now()).okOrDie()
            val plan = planner.generateWeek(GenerateWeekPlan(profileId, weekStart)).okOrDie()
            list.generateFromPlan(profileId, plan.planId, weekStart, weekStart + 6, clock.now()).okOrDie()
            val first = list.items(profileId).okOrDie().first()
            list.setChecked(first.id, true, clock.now()).okOrDie()

            val csv = list.exportCsv(profileId).okOrDie()
            assertTrue(csv.startsWith("item,qty,unit,aisle,state,sources"))
            assertTrue(csv.contains("checked"), "the check state exports")

            // Import into a second list: rows arrive, the checked row stays checked.
            val imported = list.importCsv(profileId, csv, clock.now(), listId = "imported").okOrDie()
            assertTrue(imported > 10)
            val importedItems = list.items(profileId, "imported").okOrDie()
            assertTrue(importedItems.any { it.checked }, "own-format round-trip preserves state")

            val text = list.exportText(profileId).okOrDie()
            assertTrue(text.contains("WLO shopping list"), "share-sheet format is human-first")
            assertTrue(list.exportJson(profileId).okOrDie().startsWith("["))
        }

    @Test
    fun pantrySweepDeductsAndSignalsRunLow() =
        runTest {
            val profileId = aProfileWithTargets()
            recipes.ensureSeeded(profileId, clock.now()).okOrDie()
            val plan = planner.generateWeek(GenerateWeekPlan(profileId, weekStart)).okOrDie()
            list.generateFromPlan(profileId, plan.planId, weekStart, weekStart + 6, clock.now()).okOrDie()

            // Sweep everything checked (purchased-only default) into the pantry.
            val items = list.items(profileId).okOrDie()
            items.take(3).forEach { list.setChecked(it.id, true, clock.now()).okOrDie() }
            val checked = items.take(3)
            val swept =
                pantry
                    .sweep(
                        profileId,
                        checked.map { SweepItem(it.groceryItemId, it.name, it.qty, it.unit) },
                        clock.now(),
                    ).okOrDie()
            assertEquals(3, swept)
            assertEquals(3, pantry.stock(profileId).okOrDie().size)

            // Deduct (R-S5 manual "mark used" path): floor at zero, unit-mismatch is an error.
            val row = checked[0]
            pantry.deduct(profileId, row.groceryItemId, row.qty / 2, row.unit, clock.now()).okOrDie()
            val half = pantry.byGroceryItem(profileId, row.groceryItemId).okOrDie()!!
            assertEquals(row.qty / 2, half.qty)
            assertTrue(
                pantry.deduct(profileId, row.groceryItemId, 1.0, "g", clock.now()) is WloResult.Err,
                "cross-kind deduction is refused, never guessed",
            )
            pantry.deduct(profileId, row.groceryItemId, row.qty * 5, row.unit, clock.now()).okOrDie()
            val emptied = pantry.byGroceryItem(profileId, row.groceryItemId).okOrDie()!!
            assertEquals(0.0, emptied.qty)

            // runningLow flags empty stock; deduction ENGINE policy defaults OFF (R-S5).
            assertTrue(pantry.runningLow(profileId).okOrDie().any { it.groceryItemId == row.groceryItemId })
            assertTrue(!ListExpansion.DeductionPolicy().enabled, "R-S5: partial-stock deduction is off by default globally")
        }

    @Test
    fun aisleCorrectionsLearnForever() =
        runTest {
            val profileId = aProfileWithTargets()
            recipes.ensureSeeded(profileId, clock.now()).okOrDie()
            val plan = planner.generateWeek(GenerateWeekPlan(profileId, weekStart)).okOrDie()
            list.generateFromPlan(profileId, plan.planId, weekStart, weekStart + 6, clock.now()).okOrDie()
            val item = list.items(profileId).okOrDie().first()

            list.learnAisle(profileId, item.id, Aisle.HOUSEHOLD, clock.now()).okOrDie()
            val learned = list.items(profileId).okOrDie().first { it.id == item.id }
            assertEquals(Aisle.HOUSEHOLD.wireName, learned.aisle)

            // The learned aisle survives a regeneration (the teach-the-system loop).
            list.generateFromPlan(profileId, plan.planId, weekStart, weekStart + 6, clock.now()).okOrDie()
            assertEquals(
                Aisle.HOUSEHOLD.wireName,
                list
                    .items(profileId)
                    .okOrDie()
                    .first { it.groceryItemId == item.groceryItemId }
                    .aisle,
            )
        }

    @Test
    fun adherenceGatesThenComputes() =
        runTest {
            val profileId = aProfileWithTargets()
            recipes.ensureSeeded(profileId, clock.now()).okOrDie()
            val plan = planner.generateWeek(GenerateWeekPlan(profileId, weekStart)).okOrDie()

            // No logged days → refused honestly (F03 §4, R-B4).
            val refused = planner.adherence(profileId, weekStart + 7).okOrDie()
            assertTrue(!refused.meaningful)
            assertEquals("not yet meaningful", refused.gateReason)

            // Log three days' actuals via the diary door… through the DAO (repo tests
            // elsewhere cover DiaryRepository; here the metric definition matters).
            plan.slots
                .filter { it.dayEpochDay <= weekStart + 2 }
                .forEach { planner.confirmSlot(it.id, clock.now()).okOrDie() }
            (0..2).forEach { offset ->
                db.diaryEntries().insert(
                    app.wlo.core.database.DiaryEntryEntity(
                        id = "e$offset",
                        profileId = profileId,
                        dayEpochDay = weekStart + offset,
                        mealSlot = "dinner",
                        quantity = 100.0,
                        unit = "g",
                        computedKcal = 1_800.0,
                        enteredVia = "plan",
                        provenanceScalar = "diary/kcal/e$offset",
                        revision = 0,
                        createdAtEpochMs = clock.now().toEpochMilliseconds(),
                    ),
                )
            }
            val report = planner.adherence(profileId, weekStart + 7).okOrDie()
            assertTrue(report.meaningful)
            assertNotNull(report.energyFidelityKcal)
        }

    private fun <T> WloResult<T>.okOrDie(): T =
        when (this) {
            is WloResult.Ok -> value
            is WloResult.Err -> error("expected Ok: ${error.debugMessage} / ${error.cause?.toString()}")
        }
}
