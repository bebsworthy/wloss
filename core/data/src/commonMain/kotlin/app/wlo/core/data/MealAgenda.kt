package app.wlo.core.data

import androidx.room3.withWriteTransaction
import app.wlo.core.common.ClockPort
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.database.PlanSlotEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.engines.PlannerEngine
import app.wlo.core.model.FoodItem
import app.wlo.core.model.MealSlot
import app.wlo.core.model.Recipe
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** Frozen nutrition per displayed unit; null means unknown, including kcal. */
@Serializable
public data class AgendaFood(
    val name: String,
    val unit: String = "serving",
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val recipeId: String? = null,
    val recipeVersion: Int? = null,
    val foodId: String? = null,
) {
    public fun nutrients(quantity: Double): List<Double?> =
        listOf(kcal, protein, carbs, fat, fiber).map { it?.times(quantity) }

    public fun valid(): Boolean =
        name.isNotBlank() &&
            unit.isNotBlank() &&
            (recipeId == null || foodId == null) &&
            nutrients(1.0).all { it == null || (it.isFinite() && it >= 0) }

    public companion object {
        public fun recipe(recipe: Recipe): AgendaFood =
            AgendaFood(
                recipe.name,
                "serving",
                recipe.nutrition.kcal,
                recipe.nutrition.proteinG,
                recipe.nutrition.carbG,
                recipe.nutrition.fatG,
                recipe.nutrition.fiberG,
                recipe.id,
                recipe.version,
            )

        public fun food(food: FoodItem): AgendaFood =
            AgendaFood(
                food.name,
                "g",
                food.kcalPer100g?.div(100),
                food.proteinGPer100g?.div(100),
                food.carbGPer100g?.div(100),
                food.fatGPer100g?.div(100),
                food.fiberGPer100g?.div(100),
                foodId = food.id,
            )
    }
}

@Serializable
public data class AgendaItem(
    val id: String,
    val day: Long,
    val meal: String,
    val food: AgendaFood,
    val quantity: Double,
    val eaten: Boolean = false,
    val revision: Long = 0,
    val draft: Boolean = false,
    val legacyClaim: Boolean = false,
)

public data class NutrientCoverage(
    val known: Double,
    val missing: Int,
) {
    public val complete: Boolean get() = missing == 0

    public fun withinFraction(target: Double): Float =
        if (known >
            target
        ) {
            (target / known).toFloat()
        } else {
            (known / target).toFloat()
        }
}

public fun agendaCoverage(items: List<AgendaItem>): List<NutrientCoverage> =
    (0..4).map { n ->
        val values = items.map { it.food.nutrients(it.quantity)[n] }
        NutrientCoverage(values.filterNotNull().sum(), values.count { it == null })
    }

@Serializable
public data class AgendaDraft(
    val profile: String,
    val items: List<AgendaItem>,
    val fingerprints: Map<String, String>,
    val targetsVersion: Int?,
    val committedId: String = Uuid.random().toString(),
)

/** One item store, evolved in place from plan_slots; no parallel plan/diary authority. */
public class MealAgendaRepository(
    private val db: WloDatabase,
    private val diary: DiaryRepository,
    private val recipes: RecipeRepository,
    private val targets: TargetsRepository,
    private val projection: DayProjectionRepository,
    private val projector: DayProjector,
    private val clock: ClockPort,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val slots = db.planSlots()

    public suspend fun slotDay(
        profile: String,
        id: String,
    ): Long? = slots.byId(id)?.takeIf { it.profileId == profile }?.dayEpochDay

    public fun observe(
        profile: String,
        from: Long,
        to: Long,
    ): Flow<List<AgendaItem>> =
        combine(
            slots.observeRange(profile, from, to),
            combine(
                (from..to).map {
                    diary.observeDay(profile, it)
                },
            ) { days -> days.flatMap { it.getOrNull()?.entries.orEmpty() } },
        ) { plans, actual ->
            val planned =
                plans
                    .filter {
                        it.state in listOf("planned", "confirmed") &&
                            it.replacedByEntryId == null &&
                            (it.recipeId != null || it.itemJson != null)
                    }.map { it.agenda() }
            planned +
                actual.map { entry ->
                    // Diary stores extended totals. A unit snapshot is recovered for replacement.
                    val q = entry.quantity.takeIf { it > 0 } ?: 1.0
                    val linked = plans.firstOrNull { it.replacedByEntryId == entry.id }
                    AgendaItem(
                        entry.id,
                        entry.dayEpochDay,
                        displayMeal(entry.mealSlot.wireName),
                        (
                            entry.itemJson?.let { json.decodeFromString<AgendaFood>(it) }
                                ?: AgendaFood(
                                    entry.textHint ?: "Food",
                                    entry.unit,
                                    entry.kcal?.div(q),
                                    entry.proteinG?.div(q),
                                    entry.carbG?.div(q),
                                    entry.fatG?.div(q),
                                    entry.fiberG?.div(q),
                                    linked?.recipeId,
                                    linked?.recipeVersion,
                                    entry.foodItemId,
                                )
                        ),
                        q,
                        true,
                        entry.revision.toLong(),
                    )
                }
        }

    /** Date-effective goal versions; no fabricated historical target before its first publication. */
    public fun observeTargets(
        profile: String,
        from: Long,
        to: Long,
    ): Flow<List<DayView>> =
        projection.observeRange(profile, from, to).map { result ->
            val history = targets.history(profile).requireValue().sortedBy { it.createdAtEpochMs }
            result.requireValue().map { day ->
                val effective =
                    history.lastOrNull {
                        app.wlo.core.common.DayBoundary.epochDay(
                            kotlinx.datetime.Instant.fromEpochMilliseconds(it.createdAtEpochMs),
                            kotlinx.datetime.TimeZone.currentSystemDefault(),
                        ) <=
                            day.dayEpochDay
                    }
                val resolved = effective?.let { dayProjectionOf(it.document, it.version, day.dayEpochDay) }
                day.copy(
                    budgetKcal = resolved?.budget,
                    proteinG = resolved?.proteinG,
                    carbG = resolved?.carbG,
                    fatG = resolved?.fatG,
                    fiberG = resolved?.fiberG,
                )
            }
        }

    public suspend fun add(
        profile: String,
        item: AgendaItem,
    ): WloResult<Unit> =
        storageGuard("agenda.add") {
            require(item.food.valid() && item.quantity.isFinite() && item.quantity > 0)
            db.withWriteTransaction {
                if (item.eaten) {
                    // Stable client operation ID makes retries idempotent, including process interruption.
                    diary
                        .logEntry(
                            NewDiaryEntry(
                                profile,
                                item.day,
                                MealSlot.fromWireName(item.meal) ?: MealSlot.SNACK,
                                textHint = item.food.name,
                                quantity = item.quantity,
                                unit = item.food.unit,
                                planNutrition = item.nutrition(),
                                operationId = item.id,
                                itemJson = json.encodeToString(AgendaFood.serializer(), item.food),
                            ),
                            clock.now(),
                        ).requireValue()
                } else if (slots.byId(item.id) == null) {
                    slots.insert(item.entity(profile).copy(sortOrder = nextOrder(profile, item.day)))
                }
            }
        }

    public suspend fun replace(
        profile: String,
        previous: AgendaItem,
        food: AgendaFood,
        quantity: Double,
    ): WloResult<Unit> =
        storageGuard("agenda.replace") {
            require(food.valid() && quantity.isFinite() && quantity > 0)
            db.withWriteTransaction {
                val updated = previous.copy(food = food, quantity = quantity)
                if (previous.eaten) {
                    val current = db.diaryEntries().byId(previous.id)
                    check(
                        current?.profileId == profile &&
                            current.revision.toLong() == previous.revision &&
                            current.archivedAtEpochMs == null,
                    ) {
                        "Food changed; reopen it and try again"
                    }
                    diary
                        .editEntry(
                            previous.id,
                            EditDiaryEntry(
                                MealSlot.fromWireName(previous.meal) ?: MealSlot.SNACK,
                                textHint = food.name,
                                quantity = quantity,
                                unit = food.unit,
                                planNutrition = updated.nutrition(),
                                itemJson = json.encodeToString(AgendaFood.serializer(), food),
                            ),
                            clock.now(),
                        ).requireValue()
                } else {
                    val current = slots.byId(previous.id)
                    check(
                        current?.profileId == profile &&
                            current.state in listOf("planned", "confirmed") &&
                            current.replacedByEntryId == null &&
                            current.updatedAtEpochMs.orZero() == previous.revision,
                    ) {
                        "Food changed; reopen it and try again"
                    }
                    val successor =
                        updated
                            .copy(
                                id = Uuid.random().toString(),
                            ).entity(profile)
                            .copy(
                                replacesSlotId = previous.id,
                                createdAtEpochMs = current.createdAtEpochMs,
                                sortOrder =
                                    current.sortOrder ?: current.createdAtEpochMs,
                            )
                    slots.update(
                        current.copy(
                            state = "swapped",
                            successorSlotId = successor.id,
                            updatedAtEpochMs = clock.now().toEpochMilliseconds(),
                        ),
                    )
                    slots.insert(successor)
                }
            }
        }

    public suspend fun remove(
        profile: String,
        item: AgendaItem,
    ): WloResult<Unit> =
        storageGuard("agenda.remove") {
            db.withWriteTransaction {
                if (item.eaten) {
                    val current = db.diaryEntries().byId(item.id)
                    check(current?.profileId == profile && current.revision.toLong() == item.revision)
                    diary.archiveEntry(item.id, clock.now()).requireValue()
                } else {
                    val current = slots.byId(item.id)
                    check(
                        current?.profileId == profile &&
                            current.state in listOf("planned", "confirmed") &&
                            current.replacedByEntryId == null &&
                            current.updatedAtEpochMs.orZero() == item.revision,
                    )
                    slots.setState(item.id, "skipped", clock.now().toEpochMilliseconds())
                }
            }
        }

    public suspend fun undoRemove(
        profile: String,
        item: AgendaItem,
    ): WloResult<Unit> =
        storageGuard("agenda.undoRemove") {
            db.withWriteTransaction {
                if (item.eaten) {
                    val current = db.diaryEntries().byId(item.id)
                    check(
                        current?.profileId == profile &&
                            current.revision.toLong() == item.revision &&
                            current.archivedAtEpochMs != null,
                    )
                    db.diaryEntries().update(current.copy(archivedAtEpochMs = null))
                    projector.refresh(profile, item.day, item.day)
                } else {
                    val current = slots.byId(item.id)
                    check(
                        current?.profileId == profile && current.state == "skipped" && current.successorSlotId == null,
                    )
                    slots.setState(
                        item.id,
                        if (item.legacyClaim) "confirmed" else "planned",
                        clock.now().toEpochMilliseconds(),
                    )
                }
            }
        }

    public suspend fun preview(
        profile: String,
        from: Long,
        to: Long,
        meals: Set<String>,
        weekdaysOnly: Boolean,
    ): WloResult<AgendaDraft> =
        storageGuard("agenda.preview") {
            require(to >= from && to - from < 31 && meals.isNotEmpty()) { "Choose meals and a range of up to 31 days" }
            recipes.ensureSeeded(profile, clock.now()).requireValue()
            val library = recipes.library(profile).requireValue()
            val version = targets.current(profile).requireValue()?.version
            val settings =
                db
                    .planVersions()
                    .current(
                        profile,
                    )?.settingsJson
                    ?.let { runCatching { json.decodeFromString<PlannerEngine.Settings>(it) }.getOrNull() }
                    ?: PlannerEngine.Settings()
            val proposed = mutableListOf<AgendaItem>()
            val fingerprints = mutableMapOf<String, String>()
            for (day in from..to) {
                currentCoroutineContext().ensureActive()
                if (weekdaysOnly && LocalDate.fromEpochDays(day.toInt()).dayOfWeek.ordinal >= 5) continue
                val actual = diary.day(profile, day).requireValue().entries
                val plans =
                    slots.range(profile, day, day).filter {
                        it.state in listOf("planned", "confirmed") &&
                            (it.recipeId != null || it.itemJson != null)
                    }
                val empty =
                    meals.filter { meal ->
                        plans.none { displayMeal(it.mealSlot) == meal } &&
                            actual.none { displayMeal(it.mealSlot.wireName) == meal }
                    }
                if (empty.isEmpty()) continue
                val budget = observeTargets(profile, day, day).first().single()
                val target =
                    budget.budgetKcal?.value ?: error("Set a calorie goal before requesting target-fit suggestions")
                val covered = plans.filter { it.replacedByEntryId == null }.map { it.agenda() }
                val known = agendaCoverage(covered)[0]
                if (!known.complete || actual.any { it.kcal == null }) continue
                val remaining = (target - known.known - actual.sumOf { it.kcal ?: 0.0 }).coerceAtLeast(0.0)
                if (remaining == 0.0) continue
                val generated =
                    PlannerEngine.generate(
                        library,
                        PlannerEngine.PlanRequest(
                            startDayEpochDay = day,
                            days = 1,
                            slots = empty,
                            targets = listOf(PlannerEngine.DayTargets(day, remaining, null, null, null, null)),
                            settings = settings,
                            seed = day,
                        ),
                    )
                for (slot in generated.slots) {
                    val recipe = library.firstOrNull { it.id == slot.recipeId } ?: continue
                    proposed +=
                        AgendaItem(
                            Uuid.random().toString(),
                            day,
                            slot.mealSlot,
                            AgendaFood.recipe(recipe),
                            slot.servings,
                            draft = true,
                        )
                    fingerprints["$day:${slot.mealSlot}"] = fingerprint(profile, day, slot.mealSlot)
                }
            }
            AgendaDraft(profile, proposed, fingerprints, version)
        }

    public suspend fun commit(draft: AgendaDraft): WloResult<Unit> =
        storageGuard("agenda.commit") {
            db.withWriteTransaction {
                if (draft.items.isNotEmpty() &&
                    draft.items.all { slots.byId(it.id) != null }
                ) {
                    return@withWriteTransaction
                }
                check(
                    targets.current(draft.profile).requireValue()?.version == draft.targetsVersion,
                ) { "Goals changed; refresh suggestions" }
                draft.fingerprints.forEach { (key, value) ->
                    val (day, meal) = key.split(':')
                    check(
                        fingerprint(draft.profile, day.toLong(), meal) == value,
                    ) { "Meals changed; refresh suggestions" }
                }
                draft.items.forEach { item ->
                    slots.insert(
                        item
                            .copy(
                                draft = false,
                            ).entity(draft.profile)
                            .copy(sortOrder = nextOrder(draft.profile, item.day)),
                    )
                }
            }
        }

    private suspend fun nextOrder(
        profile: String,
        day: Long,
    ): Long = (slots.range(profile, day, day).maxOfOrNull { it.sortOrder ?: it.createdAtEpochMs } ?: 0L) + 1L

    private suspend fun fingerprint(
        profile: String,
        day: Long,
        meal: String,
    ): String =
        (
            slots
                .range(
                    profile,
                    day,
                    day,
                ).filter { displayMeal(it.mealSlot) == meal }
                .map { "${it.id}:${it.state}:${it.updatedAtEpochMs}" } +
                diary
                    .day(
                        profile,
                        day,
                    ).requireValue()
                    .entries
                    .filter { displayMeal(it.mealSlot.wireName) == meal }
                    .map { "${it.id}:${it.revision}" }
        ).sorted().joinToString("|")

    private fun PlanSlotEntity.agenda(): AgendaItem =
        AgendaItem(
            id,
            dayEpochDay,
            displayMeal(mealSlot),
            itemJson?.let { json.decodeFromString<AgendaFood>(it) }
                ?: AgendaFood(
                    recipeName ?: "Recipe",
                    "serving",
                    kcalPerServing,
                    proteinGPerServing,
                    carbGPerServing,
                    fatGPerServing,
                    fiberGPerServing,
                    recipeId,
                    recipeVersion,
                ),
            servings,
            revision = updatedAtEpochMs.orZero(),
            legacyClaim = state == "confirmed" && replacedByEntryId == null,
        )

    private fun AgendaItem.entity(profile: String): PlanSlotEntity =
        PlanSlotEntity(
            id = id,
            planId = "",
            profileId = profile,
            dayEpochDay = day,
            mealSlot = meal,
            recipeId = food.recipeId,
            recipeVersion = food.recipeVersion,
            recipeName = food.name,
            servings = quantity,
            kcalPerServing = food.kcal,
            proteinGPerServing = food.protein,
            carbGPerServing = food.carbs,
            fatGPerServing = food.fat,
            fiberGPerServing = food.fiber,
            itemJson = json.encodeToString(AgendaFood.serializer(), food),
            createdAtEpochMs = clock.now().toEpochMilliseconds(),
        )

    private fun AgendaItem.nutrition(): PlanNutrition {
        val n = food.nutrients(quantity)
        return PlanNutrition(id, n[0], n[1], n[2], n[3], n[4])
    }
}

private fun Long?.orZero(): Long = this ?: 0

private fun displayMeal(meal: String): String = if (meal == "drink") "snack" else meal

private fun <T> WloResult<T>.requireValue(): T =
    when (this) {
        is WloResult.Ok -> value
        is WloResult.Err -> error(error.toString())
    }
