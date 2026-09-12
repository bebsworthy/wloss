package app.wlo.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * Suspend-only DAO (Room 3 disallows blocking DAO functions, ADR-003);
 * observation via Flow through the invalidation tracker.
 */
@Dao
public interface ProvenanceEventDao {
    @Insert
    public suspend fun insert(event: ProvenanceEventEntity): Long

    @Query("SELECT * FROM provenance_events WHERE day = :day ORDER BY createdAtEpochMs ASC")
    public suspend fun forDay(day: String): List<ProvenanceEventEntity>

    @Query("SELECT * FROM provenance_events WHERE day = :day ORDER BY createdAtEpochMs ASC")
    public fun observeDay(day: String): Flow<List<ProvenanceEventEntity>>

    @Query("SELECT COUNT(*) FROM provenance_events")
    public suspend fun count(): Int
}
