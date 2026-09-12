package app.wlo.core.testing

import app.wlo.core.engines.FoodMath
import app.wlo.core.model.FoodItem
import app.wlo.core.model.FoodSource
import app.wlo.core.model.MealSlot
import app.wlo.core.model.MeasurementSource
import kotlinx.datetime.Instant

/**
 * Deterministic 7-day simulated diary (M3 harness, WLO-0025): a fixed food
 * set, entries across every meal slot (incl. a zero-kcal water/drink entry),
 * and TWO weigh-ins on one day with intra-day noise — the exact shape the
 * F06 lowest-of-day and F02 day-view semantics have to survive. Used by
 * repository tests now and debug builds later; every value is fixed (no
 * random, no clock — callers pass ids/timestamps), so expectations can be
 * hand-computed from the constants below.
 */
public object DiarySeeder {
    /** The simulated week's catalog (stable keys; macros chosen round for math). */
    public data class SeedFood(
        public val key: String,
        public val name: String,
        public val brand: String?,
        public val kcalPer100g: Double,
        public val proteinGPer100g: Double,
        public val carbGPer100g: Double,
        public val fatGPer100g: Double,
        public val fiberGPer100g: Double,
    )

    /** One diary entry of the simulated week; kcal is pre-computed via FoodMath. */
    public data class SeedEntry(
        public val dayOffset: Int,
        public val slot: MealSlot,
        public val foodKey: String,
        public val quantity: Double,
        public val unit: String,
        public val kcal: Double,
        public val textHint: String? = null,
    )

    /** One weigh-in event of the simulated week (verbatim event, R-B8). */
    public data class SeedWeighIn(
        public val dayOffset: Int,
        public val hourOfDay: Int,
        public val minuteOfHour: Int,
        public val weightKg: Double,
        public val source: String,
    )

    /** The fixed catalog: 7 foods, per-100g values chosen hand-checkable. */
    public fun foods(): List<SeedFood> =
        listOf(
            SeedFood("oats", "Golden oats", "Nordic", 372.0, 13.5, 58.0, 7.0, 10.0),
            SeedFood("chicken", "Grilled chicken breast", "FarmCo", 165.0, 31.0, 0.0, 3.6, 0.0),
            SeedFood("lentils", "Smoky lentil stew", null, 116.0, 9.0, 20.0, 0.4, 8.0),
            SeedFood("apple", "Fresh apple", null, 52.0, 0.3, 14.0, 0.2, 2.4),
            SeedFood("yogurt", "Creamy yogurt", "Sunbow", 61.0, 3.5, 4.7, 3.3, 0.0),
            SeedFood("rye", "Rye bread", "Bakery", 250.0, 8.5, 48.0, 3.3, 5.8),
            SeedFood("salmon", "Roasted salmon", "Verde", 208.0, 20.0, 0.0, 13.0, 0.0),
        )

    /** Seven days: breakfast + lunch + dinner every day, snack/drink in rotation. */
    public fun entries(): List<SeedEntry> {
        val catalog = foods().associateBy { it.key }
        val pattern: List<List<Pair<String, Double>>> =
            listOf(
                listOf("oats" to 60.0, "yogurt" to 150.0, "apple" to 180.0), // day 0
                listOf("oats" to 60.0, "chicken" to 150.0, "rye" to 60.0), // day 1
                listOf("oats" to 50.0, "lentils" to 300.0, "apple" to 180.0), // day 2
                listOf("oats" to 60.0, "chicken" to 150.0, "salmon" to 130.0), // day 3
                listOf("oats" to 70.0, "lentils" to 300.0, "yogurt" to 150.0), // day 4
                listOf("oats" to 60.0, "chicken" to 130.0, "apple" to 180.0), // day 5
                listOf("oats" to 60.0, "salmon" to 130.0, "rye" to 60.0), // day 6
            )
        val slotsPerIndex = listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER)
        val out = mutableListOf<SeedEntry>()
        pattern.forEachIndexed { day, meals ->
            meals.forEachIndexed { index, (key, grams) ->
                val food = catalog.getValue(key)
                out +=
                    SeedEntry(
                        dayOffset = day,
                        slot = slotsPerIndex[index],
                        foodKey = key,
                        quantity = grams,
                        unit = "g",
                        kcal = FoodMath.scale(food.kcalPer100g, grams) ?: 0.0,
                    )
            }
            // Rotation: a snack on even days, the water quick-add every day.
            if (day % 2 == 0) {
                out +=
                    SeedEntry(
                        dayOffset = day,
                        slot = MealSlot.SNACK,
                        foodKey = "apple",
                        quantity = 90.0,
                        unit = "g",
                        kcal = FoodMath.scale(catalog.getValue("apple").kcalPer100g, 90.0) ?: 0.0,
                    )
            }
            out +=
                SeedEntry(
                    dayOffset = day,
                    slot = MealSlot.DRINK,
                    foodKey = "water",
                    quantity = 500.0,
                    unit = "g",
                    kcal = 0.0,
                    textHint = "tap water",
                )
        }
        return out
    }

    /**
     * The double weigh-in day (day 3): a 07:10 morning weigh-in and a 19:45
     * evening re-weigh — 0.65 kg of intra-day noise on the same calendar day,
     * exactly the R-B8 case the lowest-of-day view exists for.
     */
    public fun weighIns(): List<SeedWeighIn> =
        listOf(
            SeedWeighIn(3, 7, 10, 81.20, MeasurementSource.SCALE),
            SeedWeighIn(3, 19, 45, 81.85, MeasurementSource.SCALE),
        )

    /** The lowest-of-day value for a day with weigh-ins (81.20 on day 3). */
    public const val DOUBLE_WEIGH_IN_DAY_OFFSET: Int = 3

    /**
     * Deterministic synthetic catalog for search/perf harnesses: [count]
     * foods with stable ids (`synth-<i>`), names and aliases — no randomness.
     */
    public fun syntheticFoods(
        profileId: String,
        count: Int,
        createdAt: Instant,
    ): List<FoodItem> {
        val adjectives = listOf("golden", "smoky", "crispy", "roasted", "fresh", "creamy", "spicy", "herbed")
        val bases = listOf("oats", "lentils", "chicken", "tofu", "yogurt", "rye bread", "salmon", "apples")
        val brands = listOf("FarmCo", "Nordic", "Verde", "Acme", "Sunbow")
        return (0 until count).map { index ->
            FoodItem(
                id = "synth-$index",
                profileId = profileId,
                name = "${adjectives[index % adjectives.size]} ${bases[(index / adjectives.size) % bases.size]} $index",
                brand = brands[index % brands.size],
                aliases = if (index % 5 == 0) "staple ${bases[index % bases.size]}" else null,
                kcalPer100g = 50.0 + (index % 300),
                proteinGPer100g = 5.0 + (index % 20),
                carbGPer100g = 10.0 + (index % 40),
                fatGPer100g = 1.0 + (index % 25),
                fiberGPer100g = 1.0 + (index % 10),
                source = FoodSource.SEED,
                createdAt = createdAt,
            )
        }
    }
}
