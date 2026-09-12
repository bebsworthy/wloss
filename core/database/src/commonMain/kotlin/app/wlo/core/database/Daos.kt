package app.wlo.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
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
 * item sets archivedAtEpochMs; diary history stays honest by reference.
 */
@Dao
public interface FoodItemDao {
    @Upsert
    public suspend fun upsert(item: FoodItemEntity)

    @Query("SELECT * FROM food_items WHERE id = :id")
    public suspend fun byId(id: String): FoodItemEntity?

    @Query("SELECT * FROM food_items WHERE archivedAtEpochMs IS NULL ORDER BY name ASC")
    public fun observeActive(): Flow<List<FoodItemEntity>>

    @Query("SELECT * FROM food_items WHERE archivedAtEpochMs IS NULL AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    public suspend fun searchActive(query: String): List<FoodItemEntity>

    @Query("UPDATE food_items SET archivedAtEpochMs = :atEpochMs WHERE id = :id")
    public suspend fun archive(
        id: String,
        atEpochMs: Long,
    )
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
