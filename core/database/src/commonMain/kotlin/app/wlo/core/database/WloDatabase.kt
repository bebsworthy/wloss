package app.wlo.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * WLO relational core, schema v3 (M2 data spine — F13 §3, Appendix A).
 * Every schema change lands with a migration + exported-schema test in the
 * same PR (ADR-003 house rule); see [Migrations] and schemas/ (exported by
 * the androidx.room3 Gradle plugin).
 */
@Database(
    entities = [
        ProfileEntity::class,
        MeasurementEventEntity::class,
        MeasurementEventAttrEntity::class,
        DayRecordEntity::class,
        ProvenanceEntity::class,
        ConsentLedgerEntity::class,
        FoodItemEntity::class,
        TargetsVersionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
public abstract class WloDatabase : RoomDatabase() {
    public abstract fun profiles(): ProfileDao

    public abstract fun measurementEvents(): MeasurementEventDao

    public abstract fun measurementEventAttrs(): MeasurementEventAttrDao

    /** Writes flow only through the :core:data projection pipeline (A.3). */
    public abstract fun dayRecords(): DayRecordDao

    public abstract fun provenance(): ProvenanceDao

    public abstract fun consentLedger(): ConsentLedgerDao

    public abstract fun foodItems(): FoodItemDao

    public abstract fun targetsVersions(): TargetsVersionDao

    public companion object {
        public const val NAME: String = "wlo.db"
    }
}
