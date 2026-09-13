package app.wlo.core.data

import app.wlo.core.model.FodmapTags
import app.wlo.core.model.GroceryItem
import app.wlo.core.model.NutritionBasis
import app.wlo.core.model.NutritionPerServing
import app.wlo.core.model.Recipe
import app.wlo.core.model.RecipeIngredient
import app.wlo.core.model.RecipeSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The shipped seed-content bundle (R-S3): ~50 CC0 starter recipes + the
 * canonical grocery catalog they draw on. Ships as a plain, inspectable data
 * file in this module's resources (`recipes/seed_recipes.json`) — no funnel,
 * no paywall, user-editable and excludable (F03 §9). Loading follows the
 * [app.wlo.core.documents.OnboardingTemplates] pattern: an injectable reader
 * (JVM classpath default; Android wires an asset reader in :app).
 */
public object RecipeSeed {
    public const val RESOURCE_PATH: String = "recipes/seed_recipes.json"

    public fun interface Reader {
        /** The resource text, or null when the platform front has no such asset. */
        public fun read(path: String): String?
    }

    public val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    public fun load(reader: Reader = defaultReader): WloSeedLibrary? {
        val text = reader.read(RESOURCE_PATH) ?: return null
        return try {
            json.decodeFromString(WloSeedLibrary.serializer(), text)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** JVM classpath reader (tests + the desktop purity target). */
    public val defaultReader: Reader =
        Reader { path ->
            RecipeSeed::class.java.classLoader
                .getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }
}

/** Root of the seed resource (unknown keys ignored — provenance notes ride along). */
@Serializable
public data class WloSeedLibrary(
    public val schemaVersion: Int = 1,
    public val license: String = "CC0",
    public val contentNote: String? = null,
    public val groceryItems: List<SeedGroceryItem> = emptyList(),
    public val recipes: List<SeedRecipe> = emptyList(),
)

@Serializable
public data class SeedGroceryItem(
    public val id: String,
    public val name: String,
    public val aisle: String,
    public val unit: String,
    public val densityGPerMl: Double? = null,
    public val gramsPerPiece: Double? = null,
    public val aliases: List<String> = emptyList(),
    /** Per-100 g macros of the edible portion (kcal derives via Atwater). */
    public val per100: SeedPer100? = null,
    /** Validator facets (meat/fish/dairy/egg/gluten/nuts/soy/…) — content metadata only. */
    public val facets: List<String> = emptyList(),
)

@Serializable
public data class SeedPer100(
    public val proteinG: Double = 0.0,
    public val carbG: Double = 0.0,
    public val fatG: Double = 0.0,
    public val fiberG: Double = 0.0,
)

@Serializable
public data class SeedRecipe(
    public val id: String,
    public val version: Int = 1,
    public val name: String,
    public val servingsBase: Double,
    public val cuisine: String? = null,
    public val slots: List<String> = emptyList(),
    public val tags: List<String> = emptyList(),
    public val fodmapTags: List<String> = emptyList(),
    public val ingredients: List<SeedIngredient> = emptyList(),
    public val steps: List<String> = emptyList(),
    public val nutrition: SeedNutrition,
    public val nutritionBasis: String = "estimated",
    public val source: String = "seed",
    public val license: String = "CC0",
)

@Serializable
public data class SeedIngredient(
    public val groceryItemId: String,
    public val name: String,
    public val qty: Double,
    public val unit: String,
    public val optional: Boolean = false,
)

@Serializable
public data class SeedNutrition(
    public val kcal: Double,
    public val proteinG: Double,
    public val carbG: Double,
    public val fatG: Double,
    public val fiberG: Double,
)

// --- seed → canonical model mapping -----------------------------------------

internal fun SeedGroceryItem.toGroceryItem(): GroceryItem =
    GroceryItem(
        id = id,
        name = name,
        aisle = aisle,
        defaultUnit = unit,
        densityGPerMl = densityGPerMl,
        gramsPerPiece = gramsPerPiece,
        aliases = aliases,
    )

internal fun SeedRecipe.toRecipe(): Recipe =
    Recipe(
        id = id,
        version = version,
        name = name,
        servingsBase = servingsBase,
        cuisine = cuisine,
        slots = slots,
        tags = tags,
        fodmapTags = fodmapTags,
        ingredients =
            ingredients.map {
                RecipeIngredient(
                    groceryItemId = it.groceryItemId,
                    name = it.name,
                    qty = it.qty,
                    unit = it.unit,
                    optional = it.optional,
                )
            },
        steps = steps,
        nutrition =
            NutritionPerServing(
                kcal = nutrition.kcal,
                proteinG = nutrition.proteinG,
                carbG = nutrition.carbG,
                fatG = nutrition.fatG,
                fiberG = nutrition.fiberG,
            ),
        nutritionBasis = NutritionBasis.fromWireName(nutritionBasis) ?: NutritionBasis.ESTIMATED,
        source = RecipeSource.fromWireName(source) ?: RecipeSource.SEED,
        license = license,
    )

// --- entity ↔ domain codecs (JSON columns; seeds share the shapes) -----------

internal val PlanningJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

@Serializable
internal data class RecipeIngredientRow(
    val groceryItemId: String,
    val name: String,
    val qty: Double,
    val unit: String,
    val optional: Boolean = false,
)

internal fun encodeIngredients(ingredients: List<RecipeIngredient>): String =
    PlanningJson.encodeToString(
        ListSerializer(RecipeIngredientRow.serializer()),
        ingredients.map { RecipeIngredientRow(it.groceryItemId, it.name, it.qty, it.unit, it.optional) },
    )

internal fun decodeIngredients(json: String?): List<RecipeIngredient> =
    json
        ?.let { raw ->
            runCatching {
                PlanningJson
                    .decodeFromString(ListSerializer(RecipeIngredientRow.serializer()), raw)
                    .map { RecipeIngredient(it.groceryItemId, it.name, it.qty, it.unit, it.optional) }
            }.getOrDefault(emptyList())
        } ?: emptyList()

internal fun encodeStringList(values: List<String>): String = PlanningJson.encodeToString(ListSerializer(String.serializer()), values)

internal fun decodeStringList(json: String?): List<String> =
    json
        ?.let { raw ->
            runCatching {
                PlanningJson.decodeFromString(ListSerializer(String.serializer()), raw)
            }.getOrDefault(emptyList())
        } ?: emptyList()

internal fun validFodmapTags(tags: List<String>): Boolean = tags.all { FodmapTags.ALL.contains(it) }

@Serializable
internal data class SourceLineRow(
    val recipeId: String,
    val slotId: String,
    val dayEpochDay: Long,
    val qty: Double,
    val unit: String,
)

internal fun encodeSources(sources: List<ListSourceLine>): String =
    PlanningJson.encodeToString(
        ListSerializer(SourceLineRow.serializer()),
        sources.map { SourceLineRow(it.recipeId, it.slotId, it.dayEpochDay, it.qty, it.unit) },
    )

internal fun decodeSources(json: String?): List<ListSourceLine> =
    json
        ?.let { raw ->
            runCatching {
                PlanningJson
                    .decodeFromString(ListSerializer(SourceLineRow.serializer()), raw)
                    .map { ListSourceLine(it.recipeId, it.slotId, it.dayEpochDay, it.qty, it.unit) }
            }.getOrDefault(emptyList())
        } ?: emptyList()
