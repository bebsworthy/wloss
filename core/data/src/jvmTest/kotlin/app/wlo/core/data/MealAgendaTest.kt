package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.database.ProfileEntity
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.documents.Cadence
import app.wlo.core.documents.Energy
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealAgendaTest {
    private val dir = Files.createTempDirectory("agenda-test")
    private val db = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()
    private val foods = RoomFoodRepository(db)
    private val projector = DayProjector(db, clock)
    private val diary = RoomDiaryRepository(db, projector, foods)
    private val recipes = RoomRecipeRepository(db)
    private val targets = RoomTargetsRepository(db)
    private val projection = RoomDayProjectionRepository(db, projector, targets)
    private val agenda = MealAgendaRepository(db, diary, recipes, targets, projection, projector, clock)
    private val day = 20717L

    @AfterTest fun close() {
        db.close()
    }

    private suspend fun profile() {
        db.profiles().upsert(
            ProfileEntity("p", null, 1990, 175.0, 80.0, "moderate", "metric", clock.now().toEpochMilliseconds()),
        )
        assertIs<TargetsWriteOutcome.Written>(
            TargetsWriters(
                db,
                clock,
            ).studio().writeFirst(
                "p",
                TargetsDocument(
                    goal = Goal(75.0, -0.5),
                    energy = Energy(cadence = Cadence.DAILY, budgetKcal = 1900.0, floorKcal = 1500.0),
                    macros = Macros(MacroSplit.Preset("balanced"), 90.0),
                ),
            ),
        )
    }

    private fun food(
        id: String,
        kcal: Double? = 100.0,
        eaten: Boolean = false,
    ) = AgendaItem(id, day, "lunch", AgendaFood(id, kcal = kcal), 1.0, eaten)

    private suspend fun rows() = agenda.observe("p", day, day).first()

    @Test fun independentItemsPersistWithoutGeneratedWeekAndRetriesAreIdempotent() =
        runTest {
            profile()
            (1..7).forEach { assertIs<WloResult.Ok<Unit>>(agenda.add("p", food("$it"))) }
            agenda.add("p", food("1"))
            assertEquals(7, rows().size)
            assertEquals(700.0, agendaCoverage(rows())[0].known)
            assertNull(db.planVersions().current("p"))
            assertEquals(7, db.planSlots().range("p", day, day).size)
        }

    @Test fun legacyConfirmedClaimRemainsEditableWithoutInventingIntake() =
        runTest {
            profile()
            agenda.add("p", food("legacy"))
            db.planSlots().setState("legacy", "confirmed", clock.now().toEpochMilliseconds())
            val row = rows().single()
            assertTrue(row.legacyClaim)
            assertFalse(row.eaten)
            assertEquals(day, agenda.slotDay("p", row.id))
            assertNull(agenda.slotDay("another-profile", row.id))
            assertIs<WloResult.Ok<Unit>>(agenda.remove("p", row))
            assertIs<WloResult.Ok<Unit>>(agenda.undoRemove("p", row))
            assertTrue(rows().single().legacyClaim)
            val restored = rows().single()
            assertIs<WloResult.Ok<Unit>>(agenda.replace("p", restored, restored.food, 2.0))
            assertEquals(200.0, agendaCoverage(rows())[0].known)
            assertTrue(
                diary
                    .day("p", day)
                    .getOrNull()!!
                    .entries
                    .isEmpty(),
            )
        }

    @Test fun replaceSameFoodPortionRetiresOneRowAndKeepsSiblings() =
        runTest {
            profile()
            agenda.add("p", food("a"))
            agenda.add("p", food("b"))
            val previous = rows().first { it.id == "a" }
            assertIs<WloResult.Ok<Unit>>(agenda.replace("p", previous, previous.food, 2.0))
            assertEquals(2, rows().size)
            assertEquals(listOf("a", "b"), rows().map { it.food.name })
            assertEquals(300.0, agendaCoverage(rows())[0].known)
            assertEquals("swapped", db.planSlots().byId("a")?.state)
            assertIs<WloResult.Err>(agenda.replace("p", previous, previous.food, 3.0))
        }

    @Test fun removeUndoAndDiaryCorrectionKeepOneOwner() =
        runTest {
            profile()
            val item = food("actual", eaten = true)
            agenda.add("p", item)
            agenda.add("p", item)
            assertEquals(
                1,
                diary
                    .day("p", day)
                    .getOrNull()!!
                    .entries.size,
            )
            val row = rows().single()
            agenda.replace("p", row, row.food, 2.0)
            assertEquals(
                200.0,
                diary
                    .day("p", day)
                    .getOrNull()!!
                    .totals.kcal,
            )
            assertEquals(1, diary.revisionsOf(row.id).getOrNull()!!.size)
            val edited = rows().single()
            agenda.remove("p", edited)
            assertTrue(rows().isEmpty())
            agenda.undoRemove("p", edited)
            assertEquals(1, rows().size)
        }

    @Test fun unknownIntakeNeverBecomesZeroOrACompleteEngineInput() =
        runTest {
            profile()
            agenda.add("p", food("known", eaten = true))
            agenda.add("p", food("unknown", null, true))
            val summary = agendaCoverage(rows())[0]
            assertEquals(100.0, summary.known)
            assertEquals(1, summary.missing)
            assertNull(
                diary
                    .day("p", day)
                    .getOrNull()!!
                    .totals.kcal,
            )
            assertNull(projection.day("p", day).getOrNull()!!.intakeKcal)
            assertNull(db.diaryEntries().byId("unknown")!!.computedKcal)
        }

    @Test fun plannedToActualLinkDoesNotDoubleCount() =
        runTest {
            profile()
            agenda.add("p", food("plan"))
            agenda.add("p", food("actual", eaten = true))
            db.planSlots().setReplaced("plan", "confirmed", "actual", clock.now().toEpochMilliseconds())
            assertEquals(1, rows().size)
            assertEquals(100.0, agendaCoverage(rows())[0].known)
            agenda.remove("p", rows().single())
            assertTrue(rows().isEmpty())
        }

    @Test fun emptyDatesStillExposeTargets() =
        runTest {
            profile()
            val days = projection.observeRange("p", day, day + 6).first().getOrNull()!!
            assertEquals(7, days.size)
            assertEquals(1900.0, days.last().budgetKcal?.value)
        }

    @Test fun previewDiscardDoesNotWriteAndCommitDetectsConcurrentMealEdit() =
        runTest {
            profile()
            val draft = agenda.preview("p", day, day, setOf("lunch"), false).getOrNull()!!
            assertTrue(draft.items.isNotEmpty())
            assertTrue(rows().isEmpty())
            agenda.add("p", food("manual"))
            assertIs<WloResult.Err>(agenda.commit(draft))
            assertEquals(listOf("manual"), rows().map { it.id })
        }

    @Test fun commitIsAtomicAndIdempotent() =
        runTest {
            profile()
            val draft = agenda.preview("p", day, day, setOf("lunch", "dinner"), false).getOrNull()!!
            assertIs<WloResult.Ok<Unit>>(agenda.commit(draft))
            assertIs<WloResult.Ok<Unit>>(agenda.commit(draft))
            assertEquals(draft.items.size, rows().size)
        }

    @Test fun overageSegmentsUseTotalAsDenominatorAndUnknownIsPerNutrient() {
        val total = NutrientCoverage(2410.0, 0)
        assertEquals(1900f / 2410f, total.withinFraction(1900.0), 0.00001f)
        val nutrients = agendaCoverage(listOf(food("x")))
        assertTrue(nutrients[0].complete)
        assertFalse(nutrients[1].complete)
        assertEquals(0.0, agendaCoverage(emptyList())[0].known)
    }
}
