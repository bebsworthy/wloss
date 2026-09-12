package app.wlo.core.data

import androidx.room3.withWriteTransaction
import app.wlo.core.common.AppError
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.database.FoodItemEntity
import app.wlo.core.database.FoodSearchEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.engines.FoodMath
import app.wlo.core.model.FoodItem
import app.wlo.core.model.FoodSource
import app.wlo.core.model.ServingPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/**
 * The food catalog door (F02 §3 ladder rung 5 + F13 §3 archive-don't-delete).
 * Search is exact + prefix multi-keyword over the FTS5 `food_search` index —
 * fuzzy/embedding matching is F02 §8 later work and deliberately absent.
 * Custom foods are `source = CUSTOM` rows; only they are editable.
 */
public interface FoodRepository {
    /**
     * Local catalog search: each whitespace token becomes an escaped FTS5
     * prefix term, AND-combined across name/brand/aliases. Exact name matches
     * are promoted to the front of the result (the v1 relevance ladder —
     * the DAO's bm25 ranking is not reachable through Room's query verifier).
     */
    public suspend fun search(
        profileId: String,
        query: String,
        limit: Int = DEFAULT_SEARCH_LIMIT,
    ): WloResult<List<FoodHit>>

    public suspend fun byId(id: String): WloResult<FoodItem?>

    public fun observeActive(profileId: String): Flow<WloResult<List<FoodItem>>>

    /** Creates a `source = CUSTOM` row (R-U15: the equal-status manual path). */
    public suspend fun createCustomFood(
        food: NewCustomFood,
        at: Instant,
    ): WloResult<FoodItem>

    /** Edits a custom food (only); the FTS index is re-synced in the same write. */
    public suspend fun updateCustomFood(
        foodId: String,
        food: NewCustomFood,
        at: Instant,
    ): WloResult<FoodItem>

    /** Catalog retirement (never a delete — F13 §3; the DAO has no delete). */
    public suspend fun archive(
        id: String,
        at: Instant,
    ): WloResult<Unit>

    public companion object {
        public const val DEFAULT_SEARCH_LIMIT: Int = 30
    }
}

/** Create/update input for a user-owned food (F02 §3 label path). */
@Serializable
public data class NewCustomFood(
    public val profileId: String,
    public val name: String,
    public val brand: String? = null,
    /** Free-text alternate names; every word is searchable. */
    public val aliases: String? = null,
    public val kcalPer100g: Double? = null,
    public val proteinGPer100g: Double? = null,
    public val carbGPer100g: Double? = null,
    public val fatGPer100g: Double? = null,
    public val fiberGPer100g: Double? = null,
    public val servingPresets: List<ServingPreset> = emptyList(),
    /** User confirmed the values against the physical label (F02 §5). */
    public val macrosVerified: Boolean = false,
)

/** One search hit: the item plus whether it matched exactly (the promotion flag). */
public data class FoodHit(
    public val item: FoodItem,
    public val exactNameMatch: Boolean,
)

internal val ServingPresetCodec: Json =
    Json { ignoreUnknownKeys = true }

internal fun encodePresets(presets: List<ServingPreset>): String? =
    presets.takeIf { it.isNotEmpty() }?.let { ServingPresetCodec.encodeToString(ListSerializer(ServingPreset.serializer()), it) }

internal fun decodePresets(json: String?): List<ServingPreset> =
    json?.let { raw ->
        runCatching { ServingPresetCodec.decodeFromString(ListSerializer(ServingPreset.serializer()), raw) }
            .getOrDefault(emptyList())
    } ?: emptyList()

/**
 * FTS5 MATCH builder: FTS5 query syntax is user input, never trusted —
 * punctuation and operators are stripped, each remaining token becomes a
 * `token*` prefix term, terms are whitespace-joined (FTS5 implicit AND).
 * A query with no usable tokens yields `null` → no search is run.
 */
internal fun ftsMatchQuery(query: String): String? =
    query
        .split(Regex("\\s+"))
        .mapNotNull { token ->
            val cleaned = token.filter { it.isLetterOrDigit() }
            cleaned.takeIf { it.isNotEmpty() }?.let { "$it*" }
        }.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = " ")

public class RoomFoodRepository public constructor(
    private val db: WloDatabase,
) : FoodRepository {
    private val items = db.foodItems()
    private val searchIndex = db.foodSearch()

    override suspend fun search(
        profileId: String,
        query: String,
        limit: Int,
    ): WloResult<List<FoodHit>> =
        storageGuard("food.search") {
            val match = ftsMatchQuery(query)
            if (match == null) {
                emptyList()
            } else {
                searchIndex
                    .search(match, profileId, limit * 2)
                    .map { entity -> FoodHit(entity.toDomain(), exactNameMatch(entity, query)) }
                    .sortedWith(compareByDescending<FoodHit> { it.exactNameMatch }.thenBy { it.item.name.lowercase() })
                    .take(limit)
            }
        }

    override suspend fun byId(id: String): WloResult<FoodItem?> = storageGuard("food.byId") { items.byId(id)?.toDomain() }

    override fun observeActive(profileId: String): Flow<WloResult<List<FoodItem>>> =
        items
            .observeActive(profileId)
            .map { list -> WloResult.ok(list.map { it.toDomain() }) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "food.observeActive"))) }

    override suspend fun createCustomFood(
        food: NewCustomFood,
        at: Instant,
    ): WloResult<FoodItem> {
        val rail = railViolation(food)
        if (rail != null) return WloResult.err(rail)
        return storageGuard("food.create") {
            val entity =
                FoodItemEntity(
                    id = Uuid.random().toString(),
                    profileId = food.profileId,
                    name = food.name,
                    brand = food.brand,
                    aliases = food.aliases,
                    kcalPer100g = food.kcalPer100g,
                    proteinGPer100g = food.proteinGPer100g,
                    carbGPer100g = food.carbGPer100g,
                    fatGPer100g = food.fatGPer100g,
                    fiberGPer100g = food.fiberGPer100g,
                    servingPresetsJson = encodePresets(food.servingPresets),
                    source = FoodSource.CUSTOM.wireName,
                    macrosVerified = food.macrosVerified,
                    verifiedAtEpochMs = food.macrosVerified.takeIf { it }?.let { at.toEpochMilliseconds() },
                    createdAtEpochMs = at.toEpochMilliseconds(),
                    updatedAtEpochMs = null,
                )
            syncIndex(entity)
            entity.toDomain()
        }
    }

    override suspend fun updateCustomFood(
        foodId: String,
        food: NewCustomFood,
        at: Instant,
    ): WloResult<FoodItem> {
        val rail = railViolation(food)
        if (rail != null) return WloResult.err(rail)
        val load = storageGuard("food.update.load") { items.byId(foodId) }
        val existing = load.getOrNull() ?: return notFoundOr(load)
        if (existing.source != FoodSource.CUSTOM.wireName) {
            // Lookup-backed rows are re-synced from their source, never
            // hand-edited; duplicate the item as custom instead (F02 §3).
            return WloResult.err(AppError.InvalidInput("only custom foods are editable: $foodId"))
        }
        return storageGuard("food.update.write") {
            val entity =
                existing.copy(
                    name = food.name,
                    brand = food.brand,
                    aliases = food.aliases,
                    kcalPer100g = food.kcalPer100g,
                    proteinGPer100g = food.proteinGPer100g,
                    carbGPer100g = food.carbGPer100g,
                    fatGPer100g = food.fatGPer100g,
                    fiberGPer100g = food.fiberGPer100g,
                    servingPresetsJson = encodePresets(food.servingPresets),
                    macrosVerified = food.macrosVerified,
                    verifiedAtEpochMs = food.macrosVerified.takeIf { it }?.let { at.toEpochMilliseconds() },
                    updatedAtEpochMs = at.toEpochMilliseconds(),
                )
            syncIndex(entity)
            entity.toDomain()
        }
    }

    /** D8 plumbing: pass a storage error through, or map a missing row to InvalidInput. */
    private fun notFoundOr(load: WloResult<FoodItemEntity?>): WloResult<FoodItem> =
        when (load) {
            is WloResult.Err -> WloResult.err(load.error)
            is WloResult.Ok ->
                WloResult.err(AppError.InvalidInput("no such food: ${load.value?.id ?: "?"}"))
        }

    override suspend fun archive(
        id: String,
        at: Instant,
    ): WloResult<Unit> = storageGuard("food.archive") { items.archive(id, at.toEpochMilliseconds()) }

    /**
     * Row + FTS index are kept in step in one write transaction — the index
     * is a mirror, never an independent truth.
     */
    private suspend fun syncIndex(entity: FoodItemEntity) {
        db.withWriteTransaction {
            items.upsert(entity)
            searchIndex.deleteForFood(entity.id)
            searchIndex.insert(
                FoodSearchEntity(
                    name = entity.name,
                    brand = entity.brand,
                    aliases = entity.aliases,
                    foodId = entity.id,
                ),
            )
        }
    }

    private fun exactNameMatch(
        entity: FoodItemEntity,
        query: String,
    ): Boolean = entity.name.equals(query.trim(), ignoreCase = true)

    private fun railViolation(food: NewCustomFood): AppError? =
        food.kcalPer100g
            ?.takeIf { !FoodMath.withinEnergyRail(it) }
            ?.let {
                // F02 §3: the 27M-kcal candy bar cannot exist, even manually.
                AppError.InvalidInput("kcal/100g ${food.kcalPer100g} exceeds the energy-density rail")
            }
}

internal fun FoodItemEntity.toDomain(): FoodItem =
    FoodItem(
        id = id,
        profileId = profileId,
        name = name,
        brand = brand,
        aliases = aliases,
        kcalPer100g = kcalPer100g,
        proteinGPer100g = proteinGPer100g,
        carbGPer100g = carbGPer100g,
        fatGPer100g = fatGPer100g,
        fiberGPer100g = fiberGPer100g,
        servingPresets = decodePresets(servingPresetsJson),
        source = FoodSource.fromWireName(source) ?: FoodSource.CUSTOM,
        macrosVerified = macrosVerified,
        verifiedAt = verifiedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
        updatedAt = updatedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        archivedAt = archivedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
    )
