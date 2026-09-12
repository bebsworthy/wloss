package app.wlo.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wlo.core.database.FoodItemEntity
import app.wlo.core.database.FoodSearchEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.androidDatabaseBuilder
import app.wlo.core.testing.DiarySeeder
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureNanoTime

/**
 * FTS5 search perf on-device (M3 acceptance: 1,000 foods, the search query
 * set, median < 50 ms). Companion of the JVM timing smoke in
 * :core:database (`FoodSearchDaoTest.perfSmokeThousandFoodsSearchSetIsFast`,
 * log-only) — THIS test asserts the strict budget on real Android SQLite via
 * the same BundledSQLiteDriver posture. Seeding is deterministic
 * ([DiarySeeder.syntheticFoods]); the search set matches the JVM smoke.
 */
@RunWith(AndroidJUnit4::class)
class FoodSearchPerfTest {
    private val context: Context = ApplicationProvider.getApplicationContext<Context>()

    // Unique per run so repeated orchestrator passes seed a fresh database.
    private val dbFile: File = File(context.cacheDir, "wlo-perf-fts-${System.nanoTime()}.db")

    private val db: WloDatabase =
        androidDatabaseBuilder(context = context, path = dbFile.absolutePath).build()

    @After
    fun cleanUp() {
        db.close()
        // Bundled SQLite may leave sidecar files next to the database.
        listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach { it.delete() }
    }

    @Test
    fun thousandFoods_searchSet_medianUnderFiftyMillis() {
        runBlocking {
            val seeds =
                DiarySeeder.syntheticFoods(
                    profileId = "perf",
                    count = 1_000,
                    createdAt = Instant.fromEpochMilliseconds(0),
                )
            val items = db.foodItems()
            val index = db.foodSearch()
            seeds.forEach { food ->
                val entity =
                    FoodItemEntity(
                        id = food.id,
                        profileId = food.profileId,
                        name = food.name,
                        brand = food.brand,
                        aliases = food.aliases,
                        kcalPer100g = food.kcalPer100g,
                        proteinGPer100g = food.proteinGPer100g,
                        carbGPer100g = food.carbGPer100g,
                        fatGPer100g = food.fatGPer100g,
                        fiberGPer100g = food.fiberGPer100g,
                        createdAtEpochMs = food.createdAt.toEpochMilliseconds(),
                    )
                items.upsert(entity)
                index.insert(
                    FoodSearchEntity(
                        name = entity.name,
                        brand = entity.brand,
                        aliases = entity.aliases,
                        foodId = entity.id,
                    ),
                )
            }
            assertEquals(1_000, items.countAll())

            val querySet =
                listOf(
                    "golden oats*",
                    "chicken* farm*",
                    "lentil*",
                    "smoky* nordic*",
                    "staple* lentil*",
                    "crispy tofu*",
                    "yogurt*",
                    "rye* bread* sunbow*",
                    "roasted apples*",
                    "herbed* 40*",
                    "salmon* verde*",
                    "fresh*",
                )

            // Warm-up pass, then 5 measured passes; the per-query median must
            // clear the 50 ms budget with an order of magnitude to spare
            // (measured on-device: typically well under 1 ms).
            querySet.forEach { index.search(it, "perf", 30) }
            val timings = mutableListOf<Double>()
            var hits = 0
            repeat(5) {
                querySet.forEach { query ->
                    val ms =
                        measureNanoTime {
                            hits += index.search(query, "perf", 30).size
                        } / 1_000_000.0
                    timings += ms
                }
            }
            val median = timings.sorted()[timings.size / 2]
            val p95 = timings.sorted()[(timings.size * 0.95).toInt().coerceAtMost(timings.size - 1)]
            println(
                "FTS5 on-device perf: 1,000 foods · median=%.3f ms p95=%.3f ms max=%.3f ms hits=%d"
                    .format(median, p95, timings.max(), hits),
            )
            assertTrue("the query set must match the seeded corpus", hits > 0)
            assertTrue("on-device budget: median query under 50 ms (got %.3f ms)".format(median), median < 50.0)
        }
    }
}
