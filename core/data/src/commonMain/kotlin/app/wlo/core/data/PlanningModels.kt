package app.wlo.core.data

import app.wlo.core.database.GroceryItemEntity
import app.wlo.core.database.ListItemEntity
import app.wlo.core.database.PantryItemEntity
import app.wlo.core.database.PlanSlotEntity
import app.wlo.core.database.RecipeEntity
import app.wlo.core.engines.PlannerEngine
import app.wlo.core.model.GroceryItem
import app.wlo.core.model.NutritionBasis
import app.wlo.core.model.NutritionPerServing
import app.wlo.core.model.PlannedSlot
import app.wlo.core.model.PlannedSlotState
import app.wlo.core.model.Recipe
import app.wlo.core.model.RecipeSource
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

// Domain shapes + entity mappers for the F03/F04 planning surface. Entities
// stay dumb rows in :core:database; the mapping (and every JSON column codec)
// lives here.

internal fun RecipeEntity.toDomain(): Recipe =
    Recipe(
        id = recipeId,
        version = version,
        name = name,
        servingsBase = servingsBase,
        cuisine = cuisine,
        slots = decodeStringList(slotsJson),
        tags = decodeStringList(tagsJson),
        fodmapTags = decodeStringList(fodmapTagsJson),
        ingredients = decodeIngredients(ingredientsJson),
        steps = decodeStringList(stepsJson),
        nutrition =
            NutritionPerServing(
                kcal = kcalPerServing,
                proteinG = proteinGPerServing,
                carbG = carbGPerServing,
                fatG = fatGPerServing,
                fiberG = fiberGPerServing,
            ),
        nutritionBasis = NutritionBasis.fromWireName(nutritionBasis) ?: NutritionBasis.ESTIMATED,
        source = RecipeSource.fromWireName(source) ?: RecipeSource.MANUAL,
        license = license,
        rating = rating,
        lastPlannedAtEpochMs = lastPlannedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun Recipe.toEntity(
    profileId: String,
    now: Instant,
): RecipeEntity =
    RecipeEntity(
        recipeId = id,
        version = version,
        profileId = profileId,
        name = name,
        servingsBase = servingsBase,
        cuisine = cuisine,
        slotsJson = encodeStringList(slots),
        tagsJson = encodeStringList(tags),
        fodmapTagsJson = encodeStringList(fodmapTags),
        ingredientsJson = encodeIngredients(ingredients),
        stepsJson = encodeStringList(steps),
        kcalPerServing = nutrition.kcal,
        proteinGPerServing = nutrition.proteinG,
        carbGPerServing = nutrition.carbG,
        fatGPerServing = nutrition.fatG,
        fiberGPerServing = nutrition.fiberG,
        nutritionBasis = nutritionBasis.wireName,
        source = source.wireName,
        license = license,
        rating = rating,
        lastPlannedAtEpochMs = lastPlannedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
        createdAtEpochMs = now.toEpochMilliseconds(),
    )

internal fun GroceryItemEntity.toDomain(): GroceryItem =
    GroceryItem(
        id = id,
        name = name,
        aisle = aisle,
        defaultUnit = defaultUnit,
        densityGPerMl = densityGPerMl,
        gramsPerPiece = gramsPerPiece,
        aliases = decodeStringList(aliasesJson),
    )

internal fun GroceryItem.toEntity(
    profileId: String,
    now: Instant,
): GroceryItemEntity =
    GroceryItemEntity(
        id = id,
        profileId = profileId,
        name = name,
        aisle = aisle,
        defaultUnit = defaultUnit,
        densityGPerMl = densityGPerMl,
        gramsPerPiece = gramsPerPiece,
        aliasesJson = encodeStringList(aliases),
        createdAtEpochMs = now.toEpochMilliseconds(),
    )

internal fun PlanSlotEntity.toDomain(): PlannedSlot =
    PlannedSlot(
        id = id,
        planId = planId,
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        mealSlot = mealSlot,
        recipeId = recipeId,
        recipeVersion = recipeVersion,
        recipeName = recipeName,
        servings = servings,
        state = PlannedSlotState.fromWireName(state) ?: PlannedSlotState.PLANNED,
        replacedByEntryId = replacedByEntryId,
        successorSlotId = successorSlotId,
        replacesSlotId = replacesSlotId,
        parentSlotId = parentSlotId,
        isCookEvent = isCookEvent,
        batchServings = batchServings,
        kcalPerServing = kcalPerServing,
        proteinGPerServing = proteinGPerServing,
        carbGPerServing = carbGPerServing,
        fatGPerServing = fatGPerServing,
        fiberGPerServing = fiberGPerServing,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        itemJson = itemJson,
        sortOrder = sortOrder,
    )

/** One persisted shopping-list row (F04 §3 `ListItem`), checks riding the row. */
@Serializable
public data class ListItem(
    public val id: String,
    public val listId: String,
    public val groceryItemId: String,
    public val name: String,
    public val qty: Double,
    public val unit: String,
    public val aisle: String,
    public val checked: Boolean,
    public val checkedAt: Instant? = null,
    /** The "+2" chip payload from the last reconciliation; null = no pending delta. */
    public val deltaQty: Double? = null,
    /** Per-recipe provenance (F04 §3 "which meals need it, at what scaled amounts"). */
    public val sources: List<ListSourceLine> = emptyList(),
    public val archivedAt: Instant? = null,
)

@Serializable
public data class ListSourceLine(
    public val recipeId: String,
    public val slotId: String,
    public val dayEpochDay: Long,
    public val qty: Double,
    public val unit: String,
)

internal fun ListItemEntity.toDomain(): ListItem =
    ListItem(
        id = id,
        listId = listId,
        groceryItemId = groceryItemId,
        name = name,
        qty = qty,
        unit = unit,
        aisle = aisle,
        checked = state == "checked",
        checkedAt = checkedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        deltaQty = deltaQty,
        sources = decodeSources(sourcesJson),
        archivedAt = archivedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
    )

/** One pantry row (F04 §3 `PantryItem`). */
@Serializable
public data class PantryStockItem(
    public val id: String,
    public val groceryItemId: String,
    public val name: String,
    public val qty: Double,
    public val unit: String,
    public val expiryEpochDay: Long? = null,
    public val addedAt: Instant,
    public val lastPurchasedAt: Instant? = null,
    public val purchaseCount: Int = 0,
    public val isStaple: Boolean = false,
    public val outOfStock: Boolean = false,
)

internal fun PantryItemEntity.toDomain(): PantryStockItem =
    PantryStockItem(
        id = id,
        groceryItemId = groceryItemId,
        name = name,
        qty = qty,
        unit = unit,
        expiryEpochDay = expiryEpochDay,
        addedAt = Instant.fromEpochMilliseconds(addedAtEpochMs),
        lastPurchasedAt = lastPurchasedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        purchaseCount = purchaseCount,
        isStaple = isStaple,
        outOfStock = outOfStock,
    )

/** A plan header + its slot records + the generation report (the "why this plan"). */
public data class PlanView(
    public val planId: String,
    public val profileId: String,
    public val version: Int,
    public val startDayEpochDay: Long,
    public val endDayEpochDay: Long,
    public val seed: Long,
    public val settings: PlannerEngine.Settings?,
    public val report: PlannerEngine.Report?,
    public val createdAt: Instant,
    public val slots: List<PlannedSlot>,
)

/** Counted reconciliation outcome (the "your checks are safe" banner numbers). */
public data class ReconciliationSummary(
    public val added: Int = 0,
    public val quantityChanged: Int = 0,
    public val removed: Int = 0,
    public val restored: Int = 0,
    public val unchanged: Int = 0,
) {
    public fun touched(): Boolean = added + quantityChanged + removed + restored > 0
}

/** Generation input for a week (or any range) of slots. */
@Serializable
public data class GenerateWeekPlan(
    public val profileId: String,
    public val startDayEpochDay: Long,
    public val days: Int = 7,
    public val mealSlots: List<String> = listOf("breakfast", "lunch", "dinner"),
    public val settings: PlannerEngine.Settings = PlannerEngine.Settings(),
    /** Same inputs + same seed → same plan; a fresh seed re-rolls the deal. */
    public val seed: Long = 0L,
)

/** Create/update input for a user recipe (an edit writes version N+1, F03 §3). */
@Serializable
public data class NewRecipe(
    public val profileId: String,
    public val name: String,
    public val servingsBase: Double,
    public val cuisine: String? = null,
    public val slots: List<String> = emptyList(),
    public val tags: List<String> = emptyList(),
    public val fodmapTags: List<String> = emptyList(),
    public val ingredients: List<app.wlo.core.model.RecipeIngredient> = emptyList(),
    public val steps: List<String> = emptyList(),
    public val nutrition: NutritionPerServing,
    public val nutritionBasis: NutritionBasis = NutritionBasis.ESTIMATED,
    public val source: RecipeSource = RecipeSource.MANUAL,
    public val license: String? = null,
)

/** Create/update input for a pantry row (F04 §3 stock-take / sweep defaults). */
@Serializable
public data class NewPantryItem(
    public val profileId: String,
    public val groceryItemId: String,
    public val name: String,
    public val qty: Double,
    public val unit: String,
    public val expiryEpochDay: Long? = null,
    public val isStaple: Boolean = false,
    public val outOfStock: Boolean = false,
)

internal fun newSlotId(): String = Uuid.random().toString()
