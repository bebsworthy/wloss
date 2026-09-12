package app.wlo.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Suspend-only DAOs (Room 3 disallows blocking DAO functions, ADR-003);
 * observation via Flow through the invalidation tracker. DAOs are storage
 * plumbing — features go through `:core:data` repositories, which own the
 * domain semantics (R-B8 append-only, projection-only day writes, two-writer
 * Targets).
 */
@Dao
public interface ProfileDao {
    @Upsert
    public suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM profiles WHERE id = :id")
    public suspend fun byId(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    public fun observeById(id: String): Flow<ProfileEntity?>

    @Query(
        "SELECT * FROM profiles WHERE archivedAtEpochMs IS NULL " +
            "ORDER BY createdAtEpochMs ASC LIMIT 1",
    )
    public fun observeActive(): Flow<ProfileEntity?>

    @Query(
        "SELECT * FROM profiles WHERE archivedAtEpochMs IS NULL " +
            "ORDER BY createdAtEpochMs ASC LIMIT 1",
    )
    public suspend fun active(): ProfileEntity?

    @Query("UPDATE profiles SET archivedAtEpochMs = :atEpochMs WHERE id = :id")
    public suspend fun archive(
        id: String,
        atEpochMs: Long,
    )

    @Query("UPDATE profiles SET unitPreference = :unit WHERE id = :id")
    public suspend fun setUnitPreference(
        id: String,
        unit: String,
    )
}

/** APPEND-ONLY (R-B8): insert + queries only — no update, no delete. */
@Dao
public interface MeasurementEventDao {
    @Insert
    public suspend fun insert(event: MeasurementEventEntity)

    @Insert
    public suspend fun insertAll(events: List<MeasurementEventEntity>)

    @Query(
        "SELECT * FROM measurement_events " +
            "WHERE profileId = :profileId AND dayEpochDay BETWEEN :fromDay AND :toDay " +
            "ORDER BY capturedAtEpochMs ASC",
    )
    public suspend fun range(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): List<MeasurementEventEntity>

    @Query(
        "SELECT * FROM measurement_events " +
            "WHERE profileId = :profileId AND dayEpochDay BETWEEN :fromDay AND :toDay " +
            "ORDER BY capturedAtEpochMs ASC",
    )
    public fun observeRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<MeasurementEventEntity>>

    @Query(
        "SELECT * FROM measurement_events " +
            "WHERE profileId = :profileId AND kind = :kind AND dayEpochDay BETWEEN :fromDay AND :toDay " +
            "ORDER BY capturedAtEpochMs ASC",
    )
    public suspend fun rangeOfKind(
        profileId: String,
        kind: String,
        fromDay: Long,
        toDay: Long,
    ): List<MeasurementEventEntity>

    @Query("SELECT COUNT(*) FROM measurement_events WHERE profileId = :profileId")
    public suspend fun count(profileId: String): Int
}

@Dao
public interface MeasurementEventAttrDao {
    @Upsert
    public suspend fun upsertAll(attrs: List<MeasurementEventAttrEntity>)

    @Query("SELECT * FROM measurement_event_attrs WHERE eventId = :eventId")
    public suspend fun forEvent(eventId: String): List<MeasurementEventAttrEntity>
}

/**
 * Day scalars: write access belongs to the `:core:data` projection pipeline
 * only (Appendix A.3); consumers read through DayProjectionRepository.
 */
@Dao
public interface DayRecordDao {
    @Upsert
    public suspend fun upsert(record: DayRecordEntity)

    @Query("SELECT * FROM day_records WHERE profileId = :profileId AND dayEpochDay = :day")
    public suspend fun day(
        profileId: String,
        day: Long,
    ): DayRecordEntity?

    @Query(
        "SELECT * FROM day_records WHERE profileId = :profileId AND dayEpochDay BETWEEN :fromDay AND :toDay " +
            "ORDER BY dayEpochDay ASC",
    )
    public suspend fun range(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): List<DayRecordEntity>

    @Query("SELECT * FROM day_records WHERE profileId = :profileId AND dayEpochDay = :day")
    public fun observeDay(
        profileId: String,
        day: Long,
    ): Flow<DayRecordEntity?>

    @Query(
        "SELECT * FROM day_records WHERE profileId = :profileId AND dayEpochDay BETWEEN :fromDay AND :toDay " +
            "ORDER BY dayEpochDay ASC",
    )
    public fun observeRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<DayRecordEntity>>
}

@Dao
public interface ProvenanceDao {
    @Upsert
    public suspend fun upsert(provenance: ProvenanceEntity)

    @Query(
        "SELECT * FROM provenance WHERE profileId = :profileId AND dayEpochDay BETWEEN :fromDay AND :toDay",
    )
    public suspend fun range(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): List<ProvenanceEntity>

    @Query(
        "SELECT * FROM provenance WHERE profileId = :profileId AND dayEpochDay BETWEEN :fromDay AND :toDay",
    )
    public fun observeRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<ProvenanceEntity>>
}

/**
 * Append-only consent ledger (F12): record + read only — revocation is a new
 * row, never an edit (consumers arrive with M6; schema first per F13 §3).
 */
@Dao
public interface ConsentLedgerDao {
    @Insert
    public suspend fun append(entry: ConsentLedgerEntity)

    @Query("SELECT * FROM consent_ledger ORDER BY seq ASC")
    public suspend fun all(): List<ConsentLedgerEntity>

    @Query("SELECT * FROM consent_ledger ORDER BY seq ASC")
    public fun observeAll(): Flow<List<ConsentLedgerEntity>>
}

/**
 * Archive-don't-delete catalog (F13 §3): NO delete method exists — retiring an
 * item sets archivedAtEpochMs; diary history stays honest by reference. Text
 * search goes through [FoodSearchDao] (FTS5); this DAO keeps the row door.
 */
@Dao
public interface FoodItemDao {
    @Upsert
    public suspend fun upsert(item: FoodItemEntity)

    @Query("SELECT * FROM food_items WHERE id = :id")
    public suspend fun byId(id: String): FoodItemEntity?

    @Query(
        "SELECT * FROM food_items WHERE profileId = :profileId AND archivedAtEpochMs IS NULL " +
            "ORDER BY name ASC",
    )
    public fun observeActive(profileId: String): Flow<List<FoodItemEntity>>

    @Query(
        "SELECT * FROM food_items WHERE archivedAtEpochMs IS NULL " +
            "AND LOWER(name) = LOWER(:name) LIMIT 1",
    )
    public suspend fun byExactName(name: String): FoodItemEntity?

    @Query("SELECT COUNT(*) FROM food_items WHERE profileId = :profileId")
    public suspend fun count(profileId: String): Int

    @Query("SELECT COUNT(*) FROM food_items")
    public suspend fun countAll(): Int

    @Query("UPDATE food_items SET archivedAtEpochMs = :atEpochMs WHERE id = :id")
    public suspend fun archive(
        id: String,
        atEpochMs: Long,
    )
}

/**
 * FTS5 search index (F02 §3 rung 5): exact + prefix multi-keyword matching.
 * Contentful standalone table synced from `food_items` by `:core:data` write
 * paths (see [FoodSearchEntity]). The MATCH string is built — escaped — by
 * FoodRepository: each query token becomes `token*`, AND-combined. Ranking:
 * Room's SQL verifier does not know FTS5's `bm25()`/`rank`, so the DAO orders
 * by name and FoodRepository promotes exact matches client-side (v1 ladder).
 */
@Dao
public interface FoodSearchDao {
    @Insert
    public suspend fun insert(row: FoodSearchEntity)

    @Query("DELETE FROM food_search WHERE foodId = :foodId")
    public suspend fun deleteForFood(foodId: String)

    @Query(
        "SELECT food_items.* FROM food_items " +
            "WHERE id IN (SELECT foodId FROM food_search WHERE food_search MATCH :matchQuery) " +
            "AND profileId = :profileId AND archivedAtEpochMs IS NULL " +
            "ORDER BY name COLLATE NOCASE ASC LIMIT :limit",
    )
    public suspend fun search(
        matchQuery: String,
        profileId: String,
        limit: Int,
    ): List<FoodItemEntity>

    @Query(
        "SELECT food_items.* FROM food_items " +
            "WHERE id IN (SELECT foodId FROM food_search WHERE food_search MATCH :matchQuery) " +
            "AND profileId = :profileId AND archivedAtEpochMs IS NULL " +
            "ORDER BY name COLLATE NOCASE ASC LIMIT :limit",
    )
    public fun observeSearch(
        matchQuery: String,
        profileId: String,
        limit: Int,
    ): Flow<List<FoodItemEntity>>

    @Query("SELECT COUNT(*) FROM food_search WHERE food_search MATCH :matchQuery")
    public suspend fun countMatches(matchQuery: String): Int
}

/**
 * The food diary (R-B1): F02 owns it; the day view groups by meal slot.
 * NO delete method — a removed entry is archived (R-B7 hide-not-delete) and
 * every accepted edit first appends the prior state to `diary_entry_revisions`.
 */
@Dao
public interface DiaryEntryDao {
    @Insert
    public suspend fun insert(entry: DiaryEntryEntity)

    @Update
    public suspend fun update(entry: DiaryEntryEntity)

    @Query("SELECT * FROM diary_entries WHERE id = :id")
    public suspend fun byId(id: String): DiaryEntryEntity?

    @Query(
        "SELECT * FROM diary_entries WHERE profileId = :profileId AND dayEpochDay = :day " +
            "AND archivedAtEpochMs IS NULL ORDER BY createdAtEpochMs ASC",
    )
    public suspend fun day(
        profileId: String,
        day: Long,
    ): List<DiaryEntryEntity>

    @Query(
        "SELECT * FROM diary_entries WHERE profileId = :profileId AND dayEpochDay = :day " +
            "AND archivedAtEpochMs IS NULL ORDER BY createdAtEpochMs ASC",
    )
    public fun observeDay(
        profileId: String,
        day: Long,
    ): Flow<List<DiaryEntryEntity>>

    @Query(
        "SELECT * FROM diary_entries WHERE profileId = :profileId AND dayEpochDay BETWEEN :fromDay AND :toDay " +
            "AND archivedAtEpochMs IS NULL ORDER BY dayEpochDay ASC, createdAtEpochMs ASC",
    )
    public suspend fun range(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): List<DiaryEntryEntity>

    @Query("SELECT COUNT(*) FROM diary_entries WHERE profileId = :profileId")
    public suspend fun count(profileId: String): Int

    @Query("UPDATE diary_entries SET archivedAtEpochMs = :atEpochMs WHERE id = :id")
    public suspend fun archive(
        id: String,
        atEpochMs: Long,
    )
}

/** Append-only audit chain (R-B8): insert + read only — no update, no delete. */
@Dao
public interface DiaryEntryRevisionDao {
    @Insert
    public suspend fun insert(revision: DiaryEntryRevisionEntity)

    @Query("SELECT * FROM diary_entry_revisions WHERE entryId = :entryId ORDER BY revision ASC")
    public suspend fun forEntry(entryId: String): List<DiaryEntryRevisionEntity>
}

@Dao
public interface TargetsVersionDao {
    @Insert
    public suspend fun insert(version: TargetsVersionEntity)

    @Query(
        "SELECT * FROM targets_versions WHERE profileId = :profileId AND supersededAtEpochMs IS NULL " +
            "ORDER BY version DESC LIMIT 1",
    )
    public suspend fun current(profileId: String): TargetsVersionEntity?

    @Query(
        "SELECT * FROM targets_versions WHERE profileId = :profileId AND supersededAtEpochMs IS NULL " +
            "ORDER BY version DESC LIMIT 1",
    )
    public fun observeCurrent(profileId: String): Flow<TargetsVersionEntity?>

    @Query("SELECT * FROM targets_versions WHERE profileId = :profileId ORDER BY version ASC")
    public suspend fun history(profileId: String): List<TargetsVersionEntity>

    @Query("SELECT MAX(version) FROM targets_versions WHERE profileId = :profileId")
    public suspend fun maxVersion(profileId: String): Int?

    @Query(
        "UPDATE targets_versions SET supersededAtEpochMs = :atEpochMs " +
            "WHERE profileId = :profileId AND version = :version",
    )
    public suspend fun supersede(
        profileId: String,
        version: Int,
        atEpochMs: Long,
    )
}
