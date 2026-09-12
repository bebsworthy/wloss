package app.wlo.core.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DAO test proving the FTS5 index works on the BundledSQLiteDriver (JVM,
 * ADR-003 posture): `@Fts5` virtual-table creation, the exact + prefix
 * multi-keyword MATCH contract FoodRepository builds, and the sync verbs
 * (delete + re-insert on update). Carries the JVM TIMING SMOKE for the M3
 * acceptance: 1,000 seeded foods, the search query set — asserted generously
 * (real numbers are logged for the report; the instrumented
 * `FoodSearchPerfTest` in :app asserts the strict <50 ms budget on-device).
 */
class FoodSearchDaoTest {
    private lateinit var db: WloDatabase

    @BeforeTest
    fun setUp() {
        val dir = kotlin.io.path.createTempDirectory("wlo-fts-test")
        db =
            androidx.room3.Room
                .databaseBuilder<WloDatabase>(name = dir.resolve("wlo.db").toString())
                .setDriver(BundledSQLiteDriver())
                .addMigrations(*Migrations.ALL)
                .build()
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun food(
        id: String,
        name: String,
        brand: String? = null,
        aliases: String? = null,
        archived: Boolean = false,
    ): FoodItemEntity =
        FoodItemEntity(
            id = id,
            profileId = "p1",
            name = name,
            brand = brand,
            aliases = aliases,
            kcalPer100g = 100.0,
            createdAtEpochMs = 1_000L,
            archivedAtEpochMs = if (archived) 2_000L else null,
        )

    private suspend fun seedIndexedFood(entity: FoodItemEntity) {
        db.foodItems().upsert(entity)
        db.foodSearch().deleteForFood(entity.id)
        db.foodSearch().insert(
            FoodSearchEntity(
                name = entity.name,
                brand = entity.brand,
                aliases = entity.aliases,
                foodId = entity.id,
            ),
        )
    }

    @Test
    fun fts_prefixMultiKeywordMatchesAcrossColumns() {
        kotlinx.coroutines.test.runTest {
            seedIndexedFood(food("1", "Chicken breast", brand = "FarmCo", aliases = "poultry fillet"))
            seedIndexedFood(food("2", "Chicken thighs", brand = "FarmCo"))
            seedIndexedFood(food("3", "Beef steak"))

            // Single token, prefix: hits both chickens.
            val chicken = db.foodSearch().search("chicken*", "p1", 20)
            assertEquals(2, chicken.size)

            // Multi-keyword (implicit AND): chicken + brand token.
            val narrowed = db.foodSearch().search("chicken* farm*", "p1", 20)
            assertEquals(2, narrowed.size)

            // Alias column participates.
            assertEquals(1, db.foodSearch().search("poultr*", "p1", 20).size)

            // No hit: AND semantics exclude.
            assertEquals(0, db.foodSearch().search("chicken* beef*", "p1", 20).size)
        }
    }

    @Test
    fun fts_archivedRowsAreExcludedByTheJoin() {
        kotlinx.coroutines.test.runTest {
            seedIndexedFood(food("1", "Oat milk", archived = true))
            seedIndexedFood(food("2", "Oat bread"))
            assertEquals(listOf("2"), db.foodSearch().search("oat*", "p1", 20).map { it.id })
        }
    }

    @Test
    fun fts_updateSyncReplacesTheIndexedText() {
        kotlinx.coroutines.test.runTest {
            seedIndexedFood(food("1", "Old name"))
            val updated = food("1", "New harvest muesli", aliases = "granola")
            seedIndexedFood(updated) // repository sync = delete + insert

            assertEquals(0, db.foodSearch().countMatches("old*"))
            assertEquals(listOf("1"), db.foodSearch().search("muesl*", "p1", 20).map { it.id })
            assertEquals(listOf("1"), db.foodSearch().search("granol*", "p1", 20).map { it.id })
        }
    }

    @Test
    fun perfSmokeThousandFoodsSearchSetIsFast() {
        kotlinx.coroutines.test.runTest {
            // --- seed 1,000 foods deterministically ---
            val adjectives = listOf("golden", "smoky", "crispy", "roasted", "fresh", "creamy", "spicy", "herbed")
            val bases = listOf("oats", "lentils", "chicken", "tofu", "yogurt", "rye bread", "salmon", "apples")
            val brands = listOf("FarmCo", "Nordic", "Verde", "Acme", "Sunbow")
            val rng = Random(42)
            repeat(1_000) { index ->
                val name = "${adjectives[index % adjectives.size]} ${bases[(index / adjectives.size) % bases.size]} #$index"
                seedIndexedFood(
                    food(
                        id = "food-$index",
                        name = name,
                        brand = brands[index % brands.size],
                        aliases = if (index % 5 == 0) "staple ${bases[index % bases.size]}" else null,
                    ),
                )
            }
            assertEquals(1_000, db.foodItems().countAll())

            // --- the search query set (exact + prefix multi-keyword mix) ---
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

            // Warm-up pass (page cache + JIT): never asserted.
            querySet.forEach { db.foodSearch().search(it, "p1", 30) }

            // Measured pass. JVM timings are machine-dependent — the assertion
            // is deliberately generous (non-flaky); the actual numbers are
            // logged for the milestone report (typical: well under 1 ms/query).
            val timings = mutableListOf<Double>()
            var totalHits = 0
            val totalNanos =
                measureNanoTime {
                    repeat(5) {
                        querySet.forEach { query ->
                            val perQuery =
                                measureNanoTime {
                                    totalHits += db.foodSearch().search(query, "p1", 30).size
                                }
                            timings += perQuery / 1_000_000.0
                        }
                    }
                }
            val sorted = timings.sorted()
            val p50 = sorted[sorted.size / 2]
            val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
            println(
                "FTS5 JVM perf smoke: 1,000 foods · ${querySet.size} queries × 5 passes · " +
                    "p50=%.3f ms p95=%.3f ms max=%.3f ms total=%.1f ms hits=%d"
                        .format(p50, p95, sorted.last(), totalNanos / 1_000_000.0, totalHits),
            )
            assertTrue(totalHits > 0, "the query set must match the seeded corpus")
            assertTrue(p95 < 250.0, "JVM smoke budget: p95 under 250 ms (got %.3f ms)".format(p95))
        }
    }
}
