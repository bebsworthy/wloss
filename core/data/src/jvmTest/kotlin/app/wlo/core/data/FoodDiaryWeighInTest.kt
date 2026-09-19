package app.wlo.core.data

import app.wlo.core.common.AppError
import app.wlo.core.common.WloResult
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.datastore.SettingsStoreFactory
import app.wlo.core.engines.OutlierVerdict
import app.wlo.core.engines.WeightSample
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.EntryVia
import app.wlo.core.model.FoodSource
import app.wlo.core.model.MealSlot
import app.wlo.core.model.Provenance
import app.wlo.core.model.TrendMethod
import app.wlo.core.testing.DiarySeeder
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M3 data-layer doors on the JVM Room driver (T-J): FTS5 food search
 * (exact + prefix multi-keyword), custom-food lifecycle, the diary with its
 * revision chain + projection integration (A.3: the intake scalar folds the
 * diary in), and the F06 weigh-in semantics (verbatim events, lowest-of-day,
 * ±3σ guard → flagged-but-kept).
 */
class FoodDiaryWeighInTest {
    private val dir = Files.createTempDirectory("wlo-diary-test")
    private val db: WloDatabase = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()
    private val settings = SettingsStoreFactory.create(dir.resolve("settings.preferences_pb").toString().toPath())

    private val profiles = RoomProfileRepository(db, settings)
    private val projector = DayProjector(db, clock)
    private val foods = RoomFoodRepository(db)
    private val diary = RoomDiaryRepository(db, projector, foods)
    private val measurements = RoomMeasurementRepository(db, projector)
    private val weighIns = RoomWeighInRepository(db, measurements, projector)
    private val targets = RoomTargetsRepository(db)
    private val projection = RoomDayProjectionRepository(db, projector, targets)

    private val day0 = 20_708L // 2026-09-12

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun <T> WloResult<T>.okOrDie(): T =
        when (this) {
            is WloResult.Ok -> value
            is WloResult.Err -> error("unexpected error: ${error.debugMessage} (cause: ${error.cause})")
        }

    private suspend fun aProfile(): String =
        profiles
            .create(
                NewProfile(birthYear = 1990, heightCm = 178.0, startWeightKg = 84.0),
                clock.now(),
            ).okOrDie()
            .id

    private fun seedFood(
        profileId: String,
        seed: DiarySeeder.SeedFood,
    ): NewCustomFood =
        NewCustomFood(
            profileId = profileId,
            name = seed.name,
            brand = seed.brand,
            kcalPer100g = seed.kcalPer100g,
            proteinGPer100g = seed.proteinGPer100g,
            carbGPer100g = seed.carbGPer100g,
            fatGPer100g = seed.fatGPer100g,
            fiberGPer100g = seed.fiberGPer100g,
        )

    // --- FTS5 food search ---

    @Test
    fun customFoodCreateSearchAndArchive() =
        runTest {
            val profileId = aProfile()
            val seeds = DiarySeeder.foods().take(3)
            seeds.forEach { seed -> foods.createCustomFood(seedFood(profileId, seed), clock.now()).okOrDie() }

            // Prefix multi-keyword across name + brand (implicit AND).
            val hits = foods.search(profileId, "grilled chick").okOrDie()
            assertEquals(1, hits.size)
            assertEquals("Grilled chicken breast", hits.single().item.name)

            // Exact (case-insensitive) name match is promoted to the front.
            val promoted = foods.search(profileId, "golden oats").okOrDie()
            assertTrue(promoted.first().exactNameMatch, "exact name match leads")
            assertEquals("Golden oats", promoted.first().item.name)

            // Hostile FTS5 syntax is sanitized, not crashed on.
            assertEquals(0, foods.search(profileId, "chicken\"NOT (bogus").okOrDie().size)

            // Archive removes it from search but the row survives (F13 §3).
            val stored =
                foods
                    .search(profileId, "golden oats")
                    .okOrDie()
                    .single()
                    .item
            foods.archive(stored.id, clock.now()).okOrDie()
            assertEquals(0, foods.search(profileId, "golden oats").okOrDie().size)
            assertEquals("Golden oats", foods.byId(stored.id).okOrDie()?.name)
            assertEquals(FoodSource.CUSTOM, stored.source)
            assertNotNull(stored.createdAt)
        }

    @Test
    fun energyDensityRailRejectsTheAbsurd() =
        runTest {
            val profileId = aProfile()
            val outcome =
                foods.createCustomFood(
                    NewCustomFood(profileId = profileId, name = "Cursed bar", kcalPer100g = 270_000.0),
                    clock.now(),
                )
            val err = assertIs<WloResult.Err>(outcome)
            assertIs<AppError.InvalidInput>(err.error)
        }

    @Test
    fun onlyCustomFoodsAreEditable() =
        runTest {
            val profileId = aProfile()
            val created = foods.createCustomFood(seedFood(profileId, DiarySeeder.foods()[0]), clock.now()).okOrDie()
            val updated =
                foods
                    .updateCustomFood(
                        created.id,
                        seedFood(profileId, DiarySeeder.foods()[0]).copy(name = "Steel-cut oats"),
                        clock.now(),
                    ).okOrDie()
            assertEquals("Steel-cut oats", updated.name)
            assertNotNull(updated.updatedAt)

            val missing = foods.updateCustomFood("nope", seedFood(profileId, DiarySeeder.foods()[0]), clock.now())
            val missingErr = assertIs<WloResult.Err>(missing)
            assertIs<AppError.InvalidInput>(missingErr.error)
        }

    // --- Diary + projection (A.3 folds the diary into intakeKcal) ---

    private suspend fun seedWeek(profileId: String): Map<String, String> {
        val catalog =
            DiarySeeder
                .foods()
                .associate { it.key to foods.createCustomFood(seedFood(profileId, it), clock.now()).okOrDie().id }
        val entryIds = mutableMapOf<String, String>()
        DiarySeeder.entries().forEach { seed ->
            val entry =
                if (seed.foodKey == "water") {
                    diary
                        .logEntry(
                            NewDiaryEntry(
                                profileId = profileId,
                                dayEpochDay = day0 + seed.dayOffset,
                                mealSlot = seed.slot,
                                textHint = seed.textHint,
                                quantity = seed.quantity,
                                unit = seed.unit,
                                kcalOnly = 0.0,
                                enteredVia = EntryVia.QUICK_ADD,
                            ),
                            clock.now(),
                        ).okOrDie()
                } else {
                    diary
                        .logEntry(
                            NewDiaryEntry(
                                profileId = profileId,
                                dayEpochDay = day0 + seed.dayOffset,
                                mealSlot = seed.slot,
                                foodItemId = catalog.getValue(seed.foodKey),
                                quantity = seed.quantity,
                                unit = seed.unit,
                                enteredVia = EntryVia.MANUAL_SEARCH,
                            ),
                            clock.now(),
                        ).okOrDie()
                }
            entryIds["${seed.dayOffset}:${seed.slot}:${seed.foodKey}"] = entry.id
        }
        return entryIds
    }

    @Test
    fun diaryWeekDayViewsTotalsAndProjectionIntake() =
        runTest {
            val profileId = aProfile()
            seedWeek(profileId)

            // Day 0: oats 60g (223.2) + yogurt 150g (91.5) + apple 180g (93.6)
            //        + apple snack 90g (46.8) + water (0) = 455.1 kcal.
            val day0Diary = diary.day(profileId, day0).okOrDie()
            assertEquals(5, day0Diary.entries.size)
            assertEquals(MealSlot.BREAKFAST, day0Diary.slots.keys.first())
            assertEquals(setOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER, MealSlot.SNACK, MealSlot.DRINK), day0Diary.slots.keys)
            assertEquals(455.1, day0Diary.totals.kcal!!, absoluteTolerance = 1e-9)

            // The A.3 day projection folds the diary in (R-B1: one intake door).
            val view = projection.day(profileId, day0).okOrDie()
            val intake = assertNotNull(view.intakeKcal)
            assertEquals(455.1, intake.value, absoluteTolerance = 1e-9)
            assertIs<Provenance.Derived>(view.intakeKcal?.provenance)

            // Slot grouping renders every simulated day without surprises.
            val week = DiarySeeder.entries().groupBy { it.dayOffset }
            week.forEach { (offset, seeds) ->
                val day = diary.day(profileId, day0 + offset).okOrDie()
                assertEquals(
                    seeds.sumOf { it.kcal!! },
                    day.totals.kcal!!,
                    absoluteTolerance = 1e-9,
                    "day $offset totals must match the seeder's pre-computed kcal",
                )
            }
        }

    @Test
    fun editAppendsRevisionAndUpdatesTotalsAndProjection() =
        runTest {
            val profileId = aProfile()
            val entryIds = seedWeek(profileId)
            val entryId = entryIds.getValue("0:${MealSlot.BREAKFAST}:oats")
            val before =
                diary
                    .day(profileId, day0)
                    .okOrDie()
                    .totals.kcal!!

            // Correct the portion: 60 g → 40 g of oats (372/100·40 = 148.8).
            val catalog =
                foods
                    .search(profileId, "golden oats")
                    .okOrDie()
                    .single()
                    .item
            val corrected =
                diary
                    .editEntry(
                        entryId,
                        EditDiaryEntry(mealSlot = MealSlot.BREAKFAST, foodItemId = catalog.id, quantity = 40.0, unit = "g"),
                        clock.now(),
                    ).okOrDie()

            assertEquals(2, corrected.revision, "the revision pointer bumps on edit")
            assertNotNull(corrected.editedAt)
            assertEquals(148.8, corrected.kcal!!, absoluteTolerance = 1e-9)

            // The audit chain keeps the prior state verbatim.
            val revisions = diary.revisionsOf(entryId).okOrDie()
            assertEquals(1, revisions.size)
            assertEquals(1, revisions.single().revision)
            assertEquals(60.0, revisions.single().quantity)
            assertEquals(223.2, revisions.single().kcal!!, absoluteTolerance = 1e-9)

            // Totals and the projection moved with the correction.
            val after =
                diary
                    .day(profileId, day0)
                    .okOrDie()
                    .totals.kcal!!
            assertEquals(before - 223.2 + 148.8, after, absoluteTolerance = 1e-9)
            val projected = assertNotNull(projection.day(profileId, day0).okOrDie().intakeKcal)
            assertEquals(after, projected.value, absoluteTolerance = 1e-9)
        }

    @Test
    fun archiveHidesFromViewsButKeepsTheRow() =
        runTest {
            val profileId = aProfile()
            val entryIds = seedWeek(profileId)
            val snackId = entryIds.getValue("0:${MealSlot.SNACK}:apple")
            val withSnack =
                diary
                    .day(profileId, day0)
                    .okOrDie()
                    .totals.kcal!!

            diary.archiveEntry(snackId, clock.now()).okOrDie()

            val after = diary.day(profileId, day0).okOrDie()
            assertNull(after.entries.firstOrNull { it.id == snackId }, "archived entries leave the day view")
            assertEquals(
                withSnack - 46.8,
                after.totals.kcal!!,
                absoluteTolerance = 1e-9,
                "the projection drops archived intake (R-B7 hide-not-delete)",
            )
            // History stays queryable.
            assertTrue(db.diaryEntries().byId(snackId)!!.archivedAtEpochMs != null)
        }

    // --- Weigh-in semantics (F06 §3, R-B8) ---

    @Test
    fun doubleWeighInKeptVerbatimLowestOfDayWins() =
        runTest {
            val profileId = aProfile()
            DiarySeeder.weighIns().forEachIndexed { index, seed ->
                weighIns
                    .appendWeighIn(
                        profileId = profileId,
                        dayEpochDay = day0 + seed.dayOffset,
                        weightKg = seed.weightKg,
                        capturedAt = Instant.fromEpochMilliseconds(1_788_760_000_000L + index * 45_000_000L),
                        source = seed.source,
                    ).okOrDie()
            }

            val day = day0 + DiarySeeder.DOUBLE_WEIGH_IN_DAY_OFFSET
            val raw = weighIns.dayWeighIns(profileId, day).okOrDie()
            assertEquals(2, raw.size, "R-B8: both same-day weigh-ins stay verbatim")
            assertEquals(listOf(81.20, 81.85), raw.map { it.valueReal })

            val lowest = assertNotNull(weighIns.lowestOfDay(profileId, day).okOrDie())
            assertEquals(81.20, lowest.valueReal, "lowest-of-day is the daily scalar (F06 §3)")

            val scalars = weighIns.dailyScalars(profileId, day, day).okOrDie()
            assertEquals(listOf(WeightSample(day, 81.20)), scalars)
        }

    @Test
    fun trendUsesTheSelectedSmootherWithRegistryProvenance() =
        runTest {
            val profileId = aProfile()
            // Five days of descending scalars.
            (0 until 5).forEach { offset ->
                weighIns
                    .appendWeighIn(
                        profileId,
                        day0 + offset,
                        82.0 - offset * 0.2,
                        clock.now(),
                    ).okOrDie()
            }
            val ewma = weighIns.trend(profileId, day0 - 1, day0 + 4, TrendMethod.EWMA).okOrDie()
            assertEquals(5, ewma.points.size)
            val first = ewma.points.first().trendKg
            val provenance = assertIs<Provenance.Derived>(first.provenance)
            assertEquals(ConstantsRegistry.EWMA_FORMULA_VERSION, provenance.formulaVersion)

            // Switching the selection changes the series, never the events.
            val ma7 = weighIns.trend(profileId, day0 - 1, day0 + 4, TrendMethod.MOVING_AVERAGE_7D).okOrDie()
            assertTrue(
                abs(
                    ma7.points
                        .last()
                        .trendKg.value -
                        ewma.points
                            .last()
                            .trendKg.value,
                ) > 1e-9,
            )
        }

    @Test
    fun outlierGuardFlagsButNeverDrops() =
        runTest {
            val profileId = aProfile()
            // A quiet week of daily weigh-ins, then a +5 kg jump.
            (0 until 5).forEach { offset ->
                weighIns.appendWeighIn(profileId, day0 + offset, 80.0 + offset * 0.05, clock.now()).okOrDie()
            }
            val jumped =
                weighIns
                    .appendWeighIn(profileId, day0 + 5, 85.0, clock.now())
                    .okOrDie()
            assertIs<OutlierVerdict.Flagged>(jumped.verdict)

            // The event is stored anyway (R-B8), with the flag on the EAV sidecar.
            val attrs = measurements.attrsOf(jumped.event.id).okOrDie()
            assertEquals(WeighInAttribute.OUTLIER.wireName, attrs.single().attr)
            assertEquals("flagged", attrs.single().valueText)

            val raw = weighIns.dayWeighIns(profileId, day0 + 5).okOrDie()
            assertEquals(1, raw.size, "flagged events are kept, never dropped")

            // The day projection's trend scalar was persisted from the smoother.
            val view = projection.day(profileId, day0 + 5).okOrDie()
            assertNotNull(view.trendWeightKg)
        }

    @Test
    fun observeDayEmitsDiaryUpdates() =
        runTest {
            val profileId = aProfile()
            val first =
                diary
                    .logEntry(
                        NewDiaryEntry(
                            profileId = profileId,
                            dayEpochDay = day0,
                            mealSlot = MealSlot.LUNCH,
                            kcalOnly = 300.0,
                            enteredVia = EntryVia.QUICK_ADD,
                        ),
                        clock.now(),
                    ).okOrDie()
            val observed = diary.observeDay(profileId, day0).first().okOrDie()
            assertEquals(listOf(first.id), observed.entries.map { it.id })
            assertEquals(300.0, observed.totals.kcal!!, absoluteTolerance = 1e-9)
        }
}
