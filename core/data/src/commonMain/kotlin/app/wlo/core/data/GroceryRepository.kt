package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.database.WloDatabase
import app.wlo.core.engines.AisleEngine
import app.wlo.core.model.GroceryItem
import app.wlo.core.model.MeasureUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * The canonical grocery-item door (F04 §3's item space): the read/ensure face
 * of the same rows the seed bundle, list generation and manual adds write.
 * F04's pickers and the pantry check-in match typed/scanned names against this
 * catalog; nothing here ever deletes — items archive with their feature.
 */
public interface GroceryRepository {
    /** Name-ordered active catalog (profile-scoped; R-B9 partition-ready). */
    public suspend fun all(profileId: String): WloResult<List<GroceryItem>>

    public fun observeAll(profileId: String): Flow<WloResult<List<GroceryItem>>>

    /** Substring name (and alias) search for the pickers. */
    public suspend fun search(
        profileId: String,
        query: String,
        limit: Int = DEFAULT_SEARCH_LIMIT,
    ): WloResult<List<GroceryItem>>

    public suspend fun byExactName(
        profileId: String,
        name: String,
    ): WloResult<GroceryItem?>

    public suspend fun byId(id: String): WloResult<GroceryItem?>

    /**
     * Returns the catalog row for [name] or creates one — aisle guessed by the
     * shipped engine (the F04 §3 manual-add path shares this rule).
     */
    public suspend fun ensure(
        profileId: String,
        name: String,
        unit: String,
        at: Instant,
    ): WloResult<GroceryItem>

    public companion object {
        public const val DEFAULT_SEARCH_LIMIT: Int = 40
    }
}

public class RoomGroceryRepository public constructor(
    private val db: WloDatabase,
) : GroceryRepository {
    private val dao = db.groceryItems()

    override suspend fun all(profileId: String): WloResult<List<GroceryItem>> =
        storageGuard("grocery.all") { dao.active(profileId).map { it.toDomain() } }

    override fun observeAll(profileId: String): Flow<WloResult<List<GroceryItem>>> =
        dao
            .observeActive(profileId)
            .map { rows -> WloResult.ok(rows.map { it.toDomain() }) }
            .catch {
                emit(
                    WloResult.err(
                        app.wlo.core.common.AppError
                            .Storage(cause = it, detail = "grocery.observeAll"),
                    ),
                )
            }

    override suspend fun search(
        profileId: String,
        query: String,
        limit: Int,
    ): WloResult<List<GroceryItem>> =
        storageGuard("grocery.search") {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                emptyList()
            } else {
                dao.search(profileId, trimmed, limit).map { it.toDomain() }
            }
        }

    override suspend fun byExactName(
        profileId: String,
        name: String,
    ): WloResult<GroceryItem?> = storageGuard("grocery.byExactName") { dao.byExactName(profileId, name.trim())?.toDomain() }

    override suspend fun byId(id: String): WloResult<GroceryItem?> = storageGuard("grocery.byId") { dao.byId(id)?.toDomain() }

    override suspend fun ensure(
        profileId: String,
        name: String,
        unit: String,
        at: Instant,
    ): WloResult<GroceryItem> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return WloResult.err(
                app.wlo.core.common.AppError
                    .InvalidInput("item name is empty"),
            )
        }
        val unit0 =
            MeasureUnit.fromWireName(unit)
                ?: return WloResult.err(
                    app.wlo.core.common.AppError
                        .InvalidInput("unknown unit: $unit"),
                )
        return storageGuard("grocery.ensure") {
            dao.byExactName(profileId, trimmed)?.toDomain() ?: run {
                val guessed = AisleEngine.assign(AisleEngine.Item("__new__", trimmed, null), emptyMap())
                val domain =
                    GroceryItem(
                        id = UuidStrings.newId(),
                        name = trimmed,
                        aisle = guessed.wireName,
                        defaultUnit = unit0.wireName,
                    )
                dao.upsert(domain.toEntity(profileId, at))
                domain
            }
        }
    }
}
