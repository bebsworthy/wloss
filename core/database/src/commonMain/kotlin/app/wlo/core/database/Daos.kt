package app.wlo.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
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

    /** F13 backup snapshot (M6): full-table dump for the vault pipeline. */
    @Query("SELECT * FROM profiles ORDER BY createdAtEpochMs ASC")
    public suspend fun all(): List<ProfileEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(profiles: List<ProfileEntity>)

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

    /** One kind's event count in a day range — the logbook's "N more" (WLO-0055). */
    @Query(
        "SELECT COUNT(*) FROM measurement_events " +
            "WHERE profileId = :profileId AND kind = :kind AND dayEpochDay BETWEEN :fromDay AND :toDay",
    )
    public suspend fun countOfKind(
        profileId: String,
        kind: String,
        fromDay: Long,
        toDay: Long,
    ): Int

    /** F13 backup snapshot (M6). */
    @Query("SELECT * FROM measurement_events ORDER BY capturedAtEpochMs ASC")
    public suspend fun all(): List<MeasurementEventEntity>

    @Query("SELECT * FROM measurement_events WHERE id = :id")
    public suspend fun byId(id: String): MeasurementEventEntity?

    /** R-B8 amendment (WLO-0035): a user-initiated hard delete. */
    @Query("DELETE FROM measurement_events WHERE id = :eventId")
    public suspend fun deleteById(eventId: String)

    /** Day-scoped kind delete — the weigh-in door drops stale TREND scalars. */
    @Query(
        "DELETE FROM measurement_events " +
            "WHERE profileId = :profileId AND dayEpochDay = :day AND kind = :kind",
    )
    public suspend fun deleteKindForDay(
        profileId: String,
        day: Long,
        kind: String,
    ): Int

    /** F13 staged restore (M6): append/reconcile — existing rows stay LOCAL. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(events: List<MeasurementEventEntity>)
}

@Dao
public interface MeasurementEventAttrDao {
    @Upsert
    public suspend fun upsertAll(attrs: List<MeasurementEventAttrEntity>)

    @Query("SELECT * FROM measurement_event_attrs WHERE eventId = :eventId")
    public suspend fun forEvent(eventId: String): List<MeasurementEventAttrEntity>

    /** Sidecar rows of one profile's events in a day range (logbook flags, EAV metrics). */
    @Query(
        "SELECT attr.* FROM measurement_event_attrs attr " +
            "JOIN measurement_events e ON e.id = attr.eventId " +
            "WHERE e.profileId = :profileId AND e.dayEpochDay BETWEEN :fromDay AND :toDay",
    )
    public suspend fun forRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): List<MeasurementEventAttrEntity>

    /** R-B8 amendment (WLO-0035): the sidecar goes with its event. */
    @Query("DELETE FROM measurement_event_attrs WHERE eventId = :eventId")
    public suspend fun deleteForEvent(eventId: String)

    /** F13 backup snapshot (M6). */
    @Query("SELECT * FROM measurement_event_attrs")
    public suspend fun all(): List<MeasurementEventAttrEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(attrs: List<MeasurementEventAttrEntity>)
}

@Dao
public interface HealthConnectDao {
    @Query("SELECT * FROM health_connect_records WHERE recordId = :recordId")
    public suspend fun record(recordId: String): HealthConnectRecordEntity?

    @Upsert
    public suspend fun upsertRecord(record: HealthConnectRecordEntity)

    @Query("DELETE FROM health_connect_records WHERE recordId = :recordId")
    public suspend fun deleteRecord(recordId: String)

    @Query("SELECT * FROM health_connect_records ORDER BY recordId")
    public suspend fun allRecords(): List<HealthConnectRecordEntity>

    @Query("SELECT * FROM health_connect_records WHERE profileId = :profileId AND metric = :metric")
    public suspend fun records(
        profileId: String,
        metric: String,
    ): List<HealthConnectRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertRecordsIgnoring(records: List<HealthConnectRecordEntity>)

    @Query("SELECT * FROM health_connect_sync_state WHERE profileId = :profileId AND metric = :metric")
    public suspend fun syncState(
        profileId: String,
        metric: String,
    ): HealthConnectSyncStateEntity?

    @Upsert
    public suspend fun upsertSyncState(state: HealthConnectSyncStateEntity)

    @Query("SELECT * FROM health_connect_sync_state ORDER BY profileId, metric")
    public suspend fun allSyncStates(): List<HealthConnectSyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertSyncStatesIgnoring(states: List<HealthConnectSyncStateEntity>)

    @Insert
    public suspend fun appendLog(log: HealthConnectImportLogEntity)

    @Query("SELECT * FROM health_connect_import_log WHERE profileId = :profileId ORDER BY atEpochMs DESC LIMIT 1")
    public suspend fun latestLog(profileId: String): HealthConnectImportLogEntity?

    @Query("SELECT * FROM health_connect_import_log ORDER BY atEpochMs, id")
    public suspend fun allLogs(): List<HealthConnectImportLogEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertLogsIgnoring(logs: List<HealthConnectImportLogEntity>)
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

    /** F13 backup snapshot (M6). */
    @Query("SELECT * FROM provenance")
    public suspend fun all(): List<ProvenanceEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(rows: List<ProvenanceEntity>)
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

    /** F13 staged restore (M6): explicit-seq rows from a verified chain. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun appendAllIgnoring(entries: List<ConsentLedgerEntity>)

    /**
     * Restore-only insert that preserves sequence 0. Room treats 0 as
     * "generate a key" for [ConsentLedgerEntity]'s auto-generated primary
     * key, which would rewrite a verified genesis row and break suffix replay.
     */
    @Query(
        """
        INSERT OR IGNORE INTO consent_ledger
            (seq, profileId, capability, decision, atEpochMs, prevHashHex, hashHex)
        VALUES
            (:seq, :profileId, :capability, :decision, :atEpochMs, :prevHashHex, :hashHex)
        """,
    )
    public suspend fun appendRestoredIgnoring(
        seq: Long,
        profileId: String,
        capability: String,
        decision: String,
        atEpochMs: Long,
        prevHashHex: String,
        hashHex: String,
    )

    /** F13 restore reconciliation: the local chain head (null = empty ledger). */
    @Query("SELECT * FROM consent_ledger ORDER BY seq DESC LIMIT 1")
    public suspend fun last(): ConsentLedgerEntity?
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

    /** F13 backup snapshot (M6): includes archived rows (archive-don't-delete). */
    @Query("SELECT * FROM food_items ORDER BY name COLLATE NOCASE ASC")
    public suspend fun all(): List<FoodItemEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(items: List<FoodItemEntity>)
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

    /** F13 staged restore (M6): rebuild the FTS mirror for restored catalog rows. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAll(rows: List<FoodSearchEntity>)

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

    /** F13 backup snapshot (M6): includes archived + hidden rows (R-B7 ledger). */
    @Query("SELECT * FROM diary_entries ORDER BY createdAtEpochMs ASC")
    public suspend fun all(): List<DiaryEntryEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(entries: List<DiaryEntryEntity>)

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

    /** F13 backup snapshot (M6). */
    @Query("SELECT * FROM diary_entry_revisions ORDER BY entryId, revision ASC")
    public suspend fun all(): List<DiaryEntryRevisionEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(revisions: List<DiaryEntryRevisionEntity>)
}

/** Projection POJO for [NetworkReceiptDao.countByPurpose]. */
public data class PurposeCount(
    public val purpose: String,
    public val count: Long,
)

/**
 * Egress receipt ledger (F12 §3.6, M4): append + read only — there is no
 * update or delete path; the hash chain is the tamper evidence. Writes go
 * through `:core:network`'s RoomEgressLedger, which assigns seq + chain hashes
 * under a mutex; the debug egress monitor reads through the same ledger API.
 */
@Dao
public interface NetworkReceiptDao {
    @Insert
    public suspend fun append(receipt: NetworkReceiptEntity)

    @Query("SELECT * FROM network_receipts ORDER BY seq ASC LIMIT :limit")
    public suspend fun recent(limit: Long): List<NetworkReceiptEntity>

    @Query("SELECT * FROM network_receipts ORDER BY seq ASC")
    public fun observeAll(): Flow<List<NetworkReceiptEntity>>

    @Query("SELECT purpose, COUNT(*) AS count FROM network_receipts GROUP BY purpose")
    public suspend fun countByPurpose(): List<PurposeCount>

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM network_receipts")
    public suspend fun totalBytes(): Long

    @Query("SELECT * FROM network_receipts ORDER BY seq DESC LIMIT 1")
    public suspend fun last(): NetworkReceiptEntity?
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

    /** F13 backup snapshot (M6): full version history. */
    @Query("SELECT * FROM targets_versions ORDER BY profileId, version ASC")
    public suspend fun all(): List<TargetsVersionEntity>

    /** F13 staged restore (M6): keep-local on conflict (both id and (profile, version)). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(versions: List<TargetsVersionEntity>)
}

// --- v6: F03 planner + F04 list/pantry ---------------------------------------

/**
 * Recipe versions (F03 §3): immutable (recipeId, version) rows; edits write
 * vN+1. Archive-don't-delete — no delete method exists.
 */
@Dao
public interface RecipeDao {
    @Upsert
    public suspend fun upsertAll(recipes: List<RecipeEntity>)

    @Query(
        "SELECT * FROM recipes WHERE recipeId = :recipeId " +
            "ORDER BY version DESC LIMIT 1",
    )
    public suspend fun latest(recipeId: String): RecipeEntity?

    @Query(
        "SELECT * FROM recipes AS r WHERE r.profileId = :profileId AND r.archivedAtEpochMs IS NULL " +
            "AND r.version = (SELECT MAX(r2.version) FROM recipes AS r2 " +
            "WHERE r2.recipeId = r.recipeId AND r2.archivedAtEpochMs IS NULL) " +
            "ORDER BY r.name COLLATE NOCASE ASC",
    )
    public suspend fun activeLatest(profileId: String): List<RecipeEntity>

    @Query(
        "SELECT * FROM recipes WHERE profileId = :profileId AND archivedAtEpochMs IS NULL " +
            "AND LOWER(name) LIKE '%' || LOWER(:query) || '%' " +
            "ORDER BY name COLLATE NOCASE ASC LIMIT :limit",
    )
    public suspend fun searchByName(
        profileId: String,
        query: String,
        limit: Int,
    ): List<RecipeEntity>

    @Query(
        "SELECT * FROM recipes AS r WHERE r.profileId = :profileId AND r.archivedAtEpochMs IS NULL " +
            "AND r.version = (SELECT MAX(r2.version) FROM recipes AS r2 " +
            "WHERE r2.recipeId = r.recipeId AND r2.archivedAtEpochMs IS NULL) " +
            "ORDER BY r.name COLLATE NOCASE ASC",
    )
    public fun observeActiveLatest(profileId: String): Flow<List<RecipeEntity>>

    @Query("SELECT COUNT(*) FROM recipes WHERE profileId = :profileId")
    public suspend fun count(profileId: String): Int

    @Query(
        "UPDATE recipes SET archivedAtEpochMs = :atEpochMs WHERE recipeId = :recipeId " +
            "AND archivedAtEpochMs IS NULL",
    )
    public suspend fun archive(
        recipeId: String,
        atEpochMs: Long,
    )

    /** F13 backup snapshot (M6): every version of every recipe, archived included. */
    @Query("SELECT * FROM recipes ORDER BY recipeId, version ASC")
    public suspend fun all(): List<RecipeEntity>

    /** F13 staged restore (M6): keep-local on conflict (immutable versions). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(recipes: List<RecipeEntity>)
}

/** Canonical grocery catalog (F04): archive-don't-delete, like the food catalog. */
@Dao
public interface GroceryItemDao {
    @Upsert
    public suspend fun upsertAll(items: List<GroceryItemEntity>)

    @Upsert
    public suspend fun upsert(item: GroceryItemEntity)

    @Query("SELECT * FROM grocery_items WHERE id = :id")
    public suspend fun byId(id: String): GroceryItemEntity?

    @Query(
        "SELECT * FROM grocery_items WHERE profileId = :profileId AND archivedAtEpochMs IS NULL " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    public suspend fun active(profileId: String): List<GroceryItemEntity>

    @Query(
        "SELECT * FROM grocery_items WHERE profileId = :profileId AND archivedAtEpochMs IS NULL " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    public fun observeActive(profileId: String): Flow<List<GroceryItemEntity>>

    @Query(
        "SELECT * FROM grocery_items WHERE profileId = :profileId AND archivedAtEpochMs IS NULL " +
            "AND (LOWER(name) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(COALESCE(aliasesJson, '')) LIKE '%' || LOWER(:query) || '%') " +
            "ORDER BY name COLLATE NOCASE ASC LIMIT :limit",
    )
    public suspend fun search(
        profileId: String,
        query: String,
        limit: Int,
    ): List<GroceryItemEntity>

    @Query(
        "SELECT * FROM grocery_items WHERE profileId = :profileId AND archivedAtEpochMs IS NULL " +
            "AND LOWER(name) = LOWER(:name) LIMIT 1",
    )
    public suspend fun byExactName(
        profileId: String,
        name: String,
    ): GroceryItemEntity?

    @Query("SELECT COUNT(*) FROM grocery_items WHERE profileId = :profileId")
    public suspend fun count(profileId: String): Int

    /** F13 backup snapshot (M6). */
    @Query("SELECT * FROM grocery_items ORDER BY name COLLATE NOCASE ASC")
    public suspend fun all(): List<GroceryItemEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(items: List<GroceryItemEntity>)
}

/** Plan generations: one active plan per profile (supersede on new write). */
@Dao
public interface PlanVersionDao {
    @Insert
    public suspend fun insert(plan: PlanVersionEntity)

    @Query(
        "SELECT * FROM plan_versions WHERE profileId = :profileId AND supersededAtEpochMs IS NULL " +
            "ORDER BY version DESC LIMIT 1",
    )
    public suspend fun current(profileId: String): PlanVersionEntity?

    @Query(
        "SELECT * FROM plan_versions WHERE profileId = :profileId AND supersededAtEpochMs IS NULL " +
            "ORDER BY version DESC LIMIT 1",
    )
    public fun observeCurrent(profileId: String): Flow<PlanVersionEntity?>

    @Query("SELECT * FROM plan_versions WHERE id = :planId")
    public suspend fun byId(planId: String): PlanVersionEntity?

    @Query("SELECT MAX(version) FROM plan_versions WHERE profileId = :profileId")
    public suspend fun maxVersion(profileId: String): Int?

    @Query(
        "UPDATE plan_versions SET supersededAtEpochMs = :atEpochMs " +
            "WHERE profileId = :profileId AND supersededAtEpochMs IS NULL",
    )
    public suspend fun supersedeActive(
        profileId: String,
        atEpochMs: Long,
    )

    @Query("SELECT COUNT(*) FROM plan_versions WHERE profileId = :profileId")
    public suspend fun count(profileId: String): Int

    /** F13 backup snapshot (M6): superseded generations included (they are history). */
    @Query("SELECT * FROM plan_versions ORDER BY profileId, version ASC")
    public suspend fun all(): List<PlanVersionEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(plans: List<PlanVersionEntity>)
}

/**
 * Planned slots (R-B1): append-only records — swap/skip/confirm/replace UPDATE
 * only the state columns (never a delete); a swap inserts a fresh planned
 * successor. No delete method exists (Fresh Start may hide plans later; v1
 * supersedes whole plan versions instead).
 */
@Dao
public interface PlanSlotDao {
    @Insert
    public suspend fun insertAll(slots: List<PlanSlotEntity>)

    @Insert
    public suspend fun insert(slot: PlanSlotEntity)

    /** F13 backup snapshot (M6). */
    @Query("SELECT * FROM plan_slots ORDER BY dayEpochDay ASC, createdAtEpochMs ASC")
    public suspend fun all(): List<PlanSlotEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(slots: List<PlanSlotEntity>)

    @Query("SELECT * FROM plan_slots WHERE id = :id")
    public suspend fun byId(id: String): PlanSlotEntity?

    @Query("SELECT * FROM plan_slots WHERE planId = :planId ORDER BY dayEpochDay ASC, createdAtEpochMs ASC")
    public suspend fun forPlan(planId: String): List<PlanSlotEntity>

    @Query("SELECT * FROM plan_slots WHERE planId = :planId ORDER BY dayEpochDay ASC, createdAtEpochMs ASC")
    public fun observeForPlan(planId: String): Flow<List<PlanSlotEntity>>

    @Query(
        "SELECT * FROM plan_slots WHERE profileId = :profileId AND dayEpochDay BETWEEN :fromDay AND :toDay " +
            "ORDER BY dayEpochDay ASC, createdAtEpochMs ASC",
    )
    public suspend fun range(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): List<PlanSlotEntity>

    @Query(
        "SELECT * FROM plan_slots WHERE profileId = :profileId AND dayEpochDay BETWEEN :fromDay AND :toDay " +
            "ORDER BY dayEpochDay ASC, createdAtEpochMs ASC",
    )
    public fun observeRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<List<PlanSlotEntity>>

    @Query(
        "UPDATE plan_slots SET state = :state, updatedAtEpochMs = :atEpochMs WHERE id = :id",
    )
    public suspend fun setState(
        id: String,
        state: String,
        atEpochMs: Long,
    )

    @Query(
        "UPDATE plan_slots SET state = :state, replacedByEntryId = :entryId, updatedAtEpochMs = :atEpochMs " +
            "WHERE id = :id",
    )
    public suspend fun setReplaced(
        id: String,
        state: String,
        entryId: String,
        atEpochMs: Long,
    )

    @Query(
        "UPDATE plan_slots SET successorSlotId = :successorId, updatedAtEpochMs = :atEpochMs WHERE id = :id",
    )
    public suspend fun setSuccessor(
        id: String,
        successorId: String,
        atEpochMs: Long,
    )

    /** The servings dial (F03 §6: company tonight) — quantities re-derive downstream. */
    @Query(
        "UPDATE plan_slots SET servings = :servings, updatedAtEpochMs = :atEpochMs WHERE id = :id",
    )
    public suspend fun setServings(
        id: String,
        servings: Double,
        atEpochMs: Long,
    )
}

/** Shopping-list rows: delta reconciliation updates in place; archive = strike-through. */
@Dao
public interface ListItemDao {
    @Upsert
    public suspend fun upsertAll(items: List<ListItemEntity>)

    @Upsert
    public suspend fun upsert(item: ListItemEntity)

    @Query("SELECT * FROM list_items WHERE id = :id")
    public suspend fun byId(id: String): ListItemEntity?

    @Query(
        "SELECT * FROM list_items WHERE profileId = :profileId AND listId = :listId AND archivedAtEpochMs IS NULL " +
            "ORDER BY createdAtEpochMs ASC",
    )
    public suspend fun active(
        profileId: String,
        listId: String,
    ): List<ListItemEntity>

    @Query(
        "SELECT * FROM list_items WHERE profileId = :profileId AND listId = :listId AND archivedAtEpochMs IS NULL " +
            "ORDER BY createdAtEpochMs ASC",
    )
    public fun observeActive(
        profileId: String,
        listId: String,
    ): Flow<List<ListItemEntity>>

    @Query(
        "SELECT * FROM list_items WHERE profileId = :profileId AND listId = :listId " +
            "AND archivedAtEpochMs IS NOT NULL ORDER BY updatedAtEpochMs DESC",
    )
    public suspend fun struckThrough(
        profileId: String,
        listId: String,
    ): List<ListItemEntity>

    @Query(
        "SELECT * FROM list_items WHERE profileId = :profileId AND listId = :listId " +
            "AND groceryItemId = :groceryItemId AND archivedAtEpochMs IS NULL",
    )
    public suspend fun byGroceryItem(
        profileId: String,
        listId: String,
        groceryItemId: String,
    ): List<ListItemEntity>

    @Query(
        "UPDATE list_items SET state = :state, checkedAtEpochMs = :checkedAtEpochMs, updatedAtEpochMs = :atEpochMs " +
            "WHERE id = :id",
    )
    public suspend fun setChecked(
        id: String,
        state: String,
        checkedAtEpochMs: Long?,
        atEpochMs: Long,
    )

    /** The delta chip is consumed once rendered (or superseded by the next reconciliation). */
    @Query("UPDATE list_items SET deltaQty = NULL WHERE id = :id")
    public suspend fun clearDelta(id: String)

    /** Struck-through removal (F04 §3): recoverable, never a hard delete. */
    @Query(
        "UPDATE list_items SET archivedAtEpochMs = :atEpochMs, updatedAtEpochMs = :atEpochMs WHERE id = :id",
    )
    public suspend fun archive(
        id: String,
        atEpochMs: Long,
    )

    @Query("SELECT COUNT(*) FROM list_items WHERE profileId = :profileId")
    public suspend fun count(profileId: String): Int

    /** F13 backup snapshot (M6): struck-through rows included (recoverable history). */
    @Query("SELECT * FROM list_items ORDER BY createdAtEpochMs ASC")
    public suspend fun all(): List<ListItemEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(items: List<ListItemEntity>)
}

/** Pantry stock (F04 §3): upsert semantics; archive = consumed/removed, reversible. */
@Dao
public interface PantryItemDao {
    @Upsert
    public suspend fun upsertAll(items: List<PantryItemEntity>)

    @Upsert
    public suspend fun upsert(item: PantryItemEntity)

    @Query("SELECT * FROM pantry_items WHERE id = :id")
    public suspend fun byId(id: String): PantryItemEntity?

    @Query(
        "SELECT * FROM pantry_items WHERE profileId = :profileId AND archivedAtEpochMs IS NULL " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    public suspend fun active(profileId: String): List<PantryItemEntity>

    @Query(
        "SELECT * FROM pantry_items WHERE profileId = :profileId AND archivedAtEpochMs IS NULL " +
            "ORDER BY name COLLATE NOCASE ASC",
    )
    public fun observeActive(profileId: String): Flow<List<PantryItemEntity>>

    @Query(
        "SELECT * FROM pantry_items WHERE profileId = :profileId AND groceryItemId = :groceryItemId " +
            "AND archivedAtEpochMs IS NULL LIMIT 1",
    )
    public suspend fun byGroceryItem(
        profileId: String,
        groceryItemId: String,
    ): PantryItemEntity?

    @Query(
        "SELECT * FROM pantry_items WHERE profileId = :profileId AND archivedAtEpochMs IS NULL " +
            "AND expiryEpochDay IS NOT NULL AND expiryEpochDay <= :byDay ORDER BY expiryEpochDay ASC",
    )
    public suspend fun expiringBefore(
        profileId: String,
        byDay: Long,
    ): List<PantryItemEntity>

    @Query("SELECT COUNT(*) FROM pantry_items WHERE profileId = :profileId")
    public suspend fun count(profileId: String): Int

    /** F13 backup snapshot (M6). */
    @Query("SELECT * FROM pantry_items ORDER BY addedAtEpochMs ASC")
    public suspend fun all(): List<PantryItemEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(items: List<PantryItemEntity>)
}

/** Learned aisle overrides (F04 "teach-the-system loop"): one row per (profile, item). */
@Dao
public interface AisleCorrectionDao {
    @Upsert
    public suspend fun upsert(correction: AisleCorrectionEntity)

    @Query("SELECT * FROM aisle_corrections WHERE profileId = :profileId")
    public suspend fun forProfile(profileId: String): List<AisleCorrectionEntity>

    @Query("SELECT * FROM aisle_corrections WHERE profileId = :profileId")
    public fun observeForProfile(profileId: String): Flow<List<AisleCorrectionEntity>>

    /** F13 backup snapshot (M6). */
    @Query("SELECT * FROM aisle_corrections")
    public suspend fun all(): List<AisleCorrectionEntity>

    /** F13 staged restore (M6): keep-local on conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAllIgnoring(corrections: List<AisleCorrectionEntity>)
}
