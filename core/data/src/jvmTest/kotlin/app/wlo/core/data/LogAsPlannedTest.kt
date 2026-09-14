package app.wlo.core.data

import app.wlo.core.common.AppError
import app.wlo.core.common.WloResult
import app.wlo.core.database.PlanSlotEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.documents.Cadence
import app.wlo.core.documents.Energy
import app.wlo.core.documents.FiberTarget
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.model.EntryVia
import app.wlo.core.model.MealSlot
import app.wlo.core.model.PlannedSlotState
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Hub's "Log as planned" one-tap (R-B1) on the JVM Room driver: the
 * planned recipe replays into the diary as ONE record (R-D11, EntryVia.PLAN,
 * macros = the slot's denormalized per-serving nutrition at scale 1.0), the
 * slot retires planned → confirmed with the entry link, and the day record's
 * intake + open-slot count both move without an explicit recompute.
 */
class LogAsPlannedTest {
    private val dir = Files.createTempDirectory("wlo-log-as-planned-test")
    private val db: WloDatabase = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()

    private val targets = RoomTargetsRepository(db)
    private val projector = DayProjector(db, clock)
    private val recipes = RoomRecipeRepository(db)
    private val foods = RoomFoodRepository(db)
    private val diary = RoomDiaryRepository(db, projector, foods)
    private val planner = RoomPlannerRepository(db, targets, recipes, diary, projector, clock)
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
        assertTrue(outcome is TargetsWriteOutcome.Written, "targets v1 written, was: $outcome")
        return profileId
    }

    @Test
    fun oneTapReplaysThePlanAndRetiresTheSlot() =
        runTest {
            val profileId = aProfileWithTargets()
            val plan = planner.generateWeek(GenerateWeekPlan(profileId, weekStart)).okOrDie()
            val slot = plan.slots.first()
            assertEquals(PlannedSlotState.PLANNED, slot.state)
            assertTrue(slot.kcalPerServing != null, "the deal denormalizes nutrition onto slots")

            val before = projection.day(profileId, slot.dayEpochDay).okOrDie()
            assertEquals(3, before.plannedOpenSlots)
            assertNull(before.intakeKcal)

            val logged = planner.logAsPlanned(slot.id, clock.now()).okOrDie()

            // The entry replays the plan: PLAN provenance, the slot's own
            // name/meal/day, and the per-serving macros at scale 1.0.
            assertEquals(EntryVia.PLAN, logged.enteredVia)
            assertEquals(slot.recipeName, logged.textHint)
            assertEquals(slot.mealSlot, logged.mealSlot.wireName)
            assertEquals(slot.dayEpochDay, logged.dayEpochDay)
            assertEquals(slot.kcalPerServing!!, logged.kcal, absoluteTolerance = 1e-9)
            assertEquals(slot.proteinGPerServing!!, logged.proteinG!!, absoluteTolerance = 1e-9)
            assertEquals(slot.carbGPerServing!!, logged.carbG!!, absoluteTolerance = 1e-9)
            assertEquals(slot.fatGPerServing!!, logged.fatG!!, absoluteTolerance = 1e-9)
            assertEquals(slot.fiberGPerServing!!, logged.fiberG!!, absoluteTolerance = 1e-9)

            // The slot retired to the KEPT terminal with the entry link.
            val retired = planner.slots(profileId, weekStart, weekStart + 6).okOrDie().first { it.id == slot.id }
            assertEquals(PlannedSlotState.CONFIRMED, retired.state)
            assertEquals(logged.id, retired.replacedByEntryId)

            // One tap → one record: the retired slot refuses a second replay.
            val again = assertIs<WloResult.Err>(planner.logAsPlanned(slot.id, clock.now()))
            assertIs<AppError.InvalidInput>(again.error)
            val dayAfterLog = diary.day(profileId, slot.dayEpochDay).okOrDie()
            assertEquals(1, dayAfterLog.entries.size)

            // The day projection moved on BOTH sides with no explicit
            // recompute: the diary door folded intake into the day record and
            // the read-time planned fold drops the confirmed slot from the
            // open count while the plan's claim stays whole.
            val after = projection.day(profileId, slot.dayEpochDay).okOrDie()
            assertEquals(logged.kcal, after.intakeKcal!!.value, absoluteTolerance = 1e-9)
            assertEquals(2, after.plannedOpenSlots)
            assertEquals(3, after.plannedSlotCount, "confirmed stays inside the plan's claim")
        }

    @Test
    fun missingSlotFailsCleanly() =
        runTest {
            val profileId = aProfileWithTargets()
            planner.generateWeek(GenerateWeekPlan(profileId, weekStart)).okOrDie()

            val outcome = planner.logAsPlanned("no-such-slot", clock.now())
            val err = assertIs<WloResult.Err>(outcome)
            assertIs<AppError.InvalidInput>(err.error)
        }

    @Test
    fun freeTextSlotHasNothingToReplay() =
        runTest {
            val profileId = aProfileWithTargets()
            val plan = planner.generateWeek(GenerateWeekPlan(profileId, weekStart)).okOrDie()

            // The honest "add anything" card: no recipe behind the slot.
            db.planSlots().insert(
                PlanSlotEntity(
                    id = "free-text",
                    planId = plan.planId,
                    profileId = profileId,
                    dayEpochDay = weekStart,
                    mealSlot = MealSlot.SNACK.wireName,
                    recipeName = "a handful of nuts",
                    servings = 1.0,
                    createdAtEpochMs = clock.now().toEpochMilliseconds(),
                ),
            )

            val err = assertIs<WloResult.Err>(planner.logAsPlanned("free-text", clock.now()))
            assertIs<AppError.InvalidInput>(err.error)
            val day = diary.day(profileId, weekStart).okOrDie()
            assertTrue(day.entries.isEmpty(), "no record leaks from the refusal")
        }

    private fun <T> WloResult<T>.okOrDie(): T =
        when (this) {
            is WloResult.Ok -> value
            is WloResult.Err -> error("unexpected error: ${error.debugMessage} (cause: ${error.cause})")
        }
}
