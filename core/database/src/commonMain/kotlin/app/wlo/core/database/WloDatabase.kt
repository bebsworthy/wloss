package app.wlo.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * WLO relational core, schema v1 (M1 seed). Every schema change lands with a
 * migration + exported-schema test in the same PR (ADR-003 house rule); see
 * [Migrations] and schemas/ (exported by the androidx.room3 Gradle plugin).
 */
@Database(entities = [ProvenanceEventEntity::class], version = 2, exportSchema = true)
public abstract class WloDatabase : RoomDatabase() {
    public abstract fun provenanceEvents(): ProvenanceEventDao

    public companion object {
        public const val NAME: String = "wlo.db"
    }
}
