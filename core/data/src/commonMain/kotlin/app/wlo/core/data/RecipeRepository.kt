package app.wlo.core.data

import androidx.room3.withWriteTransaction
import app.wlo.core.common.AppError
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.database.RecipeEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * The recipe library door (F03 §3; R-S3 content rules). Recipes are VERSIONED
 * (recipeId + version immutable rows; an edit writes vN+1) and archive-don't-
 * delete. `ensureSeeded` copies the shipped CC0 seed bundle into the
 * profile's own rows exactly once (R-B9 partition-ready), so the library the
 * planner sees is fully user-editable and excludable.
 */
public interface RecipeRepository {
    /** Latest active version of every non-archived recipe, name-sorted. */
    public suspend fun library(profileId: String): WloResult<List<Recipe>>

    public fun observeLibrary(profileId: String): Flow<WloResult<List<Recipe>>>

    /** Latest version of one recipe; null when absent or archived. */
    public suspend fun byId(recipeId: String): WloResult<Recipe?>

    /** Substring name search (FTS-level relevance is a v1.x concern). */
    public suspend fun search(
        profileId: String,
        query: String,
        limit: Int = DEFAULT_SEARCH_LIMIT,
    ): WloResult<List<Recipe>>

    /** Creates version 1 of a user recipe (manual/import/AI-draft-reviewed). */
    public suspend fun create(
        recipe: NewRecipe,
        at: Instant,
    ): WloResult<Recipe>

    /** Writes version N+1 of [recipeId] (immutable-version house rule). */
    public suspend fun edit(
        recipeId: String,
        updated: NewRecipe,
        at: Instant,
    ): WloResult<Recipe>

    public suspend fun archive(
        recipeId: String,
        at: Instant,
    ): WloResult<Unit>

    /**
     * Idempotent seed copy (R-S3): copies the shipped grocery catalog +
     * recipes into [profileId]'s rows when the profile has no recipes yet.
     * Returns the number of recipes copied (0 = already seeded).
     */
    public suspend fun ensureSeeded(
        profileId: String,
        at: Instant,
    ): WloResult<Int>

    public companion object {
        public const val DEFAULT_SEARCH_LIMIT: Int = 40
    }
}

public class RoomRecipeRepository public constructor(
    private val db: WloDatabase,
    private val seedReader: RecipeSeed.Reader = RecipeSeed.defaultReader,
) : RecipeRepository {
    private val dao = db.recipes()
    private val groceries = db.groceryItems()

    override suspend fun library(profileId: String): WloResult<List<Recipe>> =
        storageGuard("recipes.library") { dao.activeLatest(profileId).map { it.toDomain() } }

    override fun observeLibrary(profileId: String): Flow<WloResult<List<Recipe>>> =
        dao
            .observeActiveLatest(profileId)
            .map { rows -> WloResult.ok(rows.map { it.toDomain() }) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "recipes.observeLibrary"))) }

    override suspend fun byId(recipeId: String): WloResult<Recipe?> =
        storageGuard("recipes.byId") { dao.latest(recipeId)?.takeIf { it.archivedAtEpochMs == null }?.toDomain() }

    override suspend fun search(
        profileId: String,
        query: String,
        limit: Int,
    ): WloResult<List<Recipe>> =
        storageGuard("recipes.search") {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                emptyList()
            } else {
                dao.searchByName(profileId, trimmed, limit).map { it.toDomain() }
            }
        }

    override suspend fun create(
        recipe: NewRecipe,
        at: Instant,
    ): WloResult<Recipe> =
        storageGuard("recipes.create") {
            val domain =
                Recipe(
                    id = UuidStrings.newId(),
                    version = 1,
                    name = recipe.name,
                    servingsBase = recipe.servingsBase,
                    cuisine = recipe.cuisine,
                    slots = recipe.slots,
                    tags = recipe.tags,
                    fodmapTags = recipe.fodmapTags,
                    ingredients = recipe.ingredients,
                    steps = recipe.steps,
                    nutrition = recipe.nutrition,
                    nutritionBasis = recipe.nutritionBasis,
                    source = recipe.source,
                    license = recipe.license,
                )
            dao.upsertAll(listOf(domain.toEntity(recipe.profileId, at)))
            domain
        }

    override suspend fun edit(
        recipeId: String,
        updated: NewRecipe,
        at: Instant,
    ): WloResult<Recipe> {
        val load = storageGuard("recipes.edit.load") { dao.latest(recipeId) }
        val existing = load.getOrNull() ?: return notFoundOr(load, recipeId)
        return storageGuard("recipes.edit.write") {
            val domain =
                Recipe(
                    id = recipeId,
                    version = existing.version + 1,
                    name = updated.name,
                    servingsBase = updated.servingsBase,
                    cuisine = updated.cuisine,
                    slots = updated.slots,
                    tags = updated.tags,
                    fodmapTags = updated.fodmapTags,
                    ingredients = updated.ingredients,
                    steps = updated.steps,
                    nutrition = updated.nutrition,
                    nutritionBasis = updated.nutritionBasis,
                    source = updated.source,
                    license = updated.license ?: existing.license,
                    rating = existing.rating,
                    lastPlannedAtEpochMs = existing.lastPlannedAtEpochMs,
                )
            dao.upsertAll(listOf(domain.toEntity(updated.profileId, at)))
            domain
        }
    }

    override suspend fun archive(
        recipeId: String,
        at: Instant,
    ): WloResult<Unit> = storageGuard("recipes.archive") { dao.archive(recipeId, at.toEpochMilliseconds()) }

    override suspend fun ensureSeeded(
        profileId: String,
        at: Instant,
    ): WloResult<Int> =
        storageGuard("recipes.ensureSeeded") {
            if (dao.count(profileId) > 0) {
                return@storageGuard 0
            }
            val bundle =
                RecipeSeed.load(seedReader)
                    ?: return@storageGuard 0
            db.withWriteTransaction {
                groceries.upsertAll(
                    bundle.groceryItems.map { it.toGroceryItem().toEntity(profileId, at) },
                )
                dao.upsertAll(bundle.recipes.map { it.toRecipe().toEntity(profileId, at) })
            }
            bundle.recipes.size
        }

    private fun notFoundOr(
        load: WloResult<RecipeEntity?>,
        recipeId: String,
    ): WloResult<Recipe> =
        when (load) {
            is WloResult.Err -> WloResult.err(load.error)
            is WloResult.Ok -> WloResult.err(AppError.InvalidInput("no such recipe: $recipeId"))
        }
}

/** Local UUID helper (keeps the import surface of this file small). */
internal object UuidStrings {
    public fun newId(): String =
        kotlin.uuid.Uuid
            .random()
            .toString()
}
