package app.wlo.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * WLO relational core, schema v4 (M3 — F02 manual logging on the M2 spine).
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
        FoodSearchEntity::class,
        DiaryEntryEntity::class,
        DiaryEntryRevisionEntity::class,
        TargetsVersionEntity::class,
    ],
    version = 4,
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

    /** FTS5 mirror of the searchable `food_items` text — synced by FoodRepository. */
    public abstract fun foodSearch(): FoodSearchDao

    public abstract fun diaryEntries(): DiaryEntryDao

    /** Append-only correction audit — write access belongs to DiaryRepository.edit. */
    public abstract fun diaryEntryRevisions(): DiaryEntryRevisionDao

    public abstract fun targetsVersions(): TargetsVersionDao

    public companion object {
        public const val NAME: String = "wlo.db"
    }
}
