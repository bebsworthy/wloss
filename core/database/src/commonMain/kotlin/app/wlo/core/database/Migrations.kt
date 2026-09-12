package app.wlo.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/**
 * Hand-written migrations. Every schema change lands with a migration +
 * exported-schema test in the same PR (ADR-003 house rule); see
 * `MigrationTest` and the exported schemas under `schemas/`. The DDL below
 * mirrors the exported `3.json` createSql statements exactly.
 */
public object Migrations {
    /**
     * v1 → v2: adds the nullable `note` column to provenance_events. Room 3
     * migrations are suspend functions receiving the SQLiteDriver connection
     * (SupportSQLiteDatabase/execSQL are gone — ADR-003 API renames).
     */
    public val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.prepare("ALTER TABLE provenance_events ADD COLUMN note TEXT").use { statement ->
                    statement.step()
                }
            }
        }

    /**
     * v2 → v3: the M1 placeholder `provenance_events` table is DROPPED and the
     * real data spine is created (F13 §3 + Appendix A): profiles,
     * measurement_events (+ EAV sidecar), day_records, provenance,
     * consent_ledger, food_items, targets_versions. The placeholder carried
     * only M1 demo rows, so nothing survives except schema shape — the
     * MigrationTest proves both the drop and the new-table shape.
     */
    public val MIGRATION_2_3: Migration =
        object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.exec("DROP TABLE IF EXISTS provenance_events")

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `profiles` (" +
                        "`id` TEXT NOT NULL, `sex` TEXT, `birthYear` INTEGER NOT NULL, " +
                        "`heightCm` REAL NOT NULL, `startWeightKg` REAL NOT NULL, " +
                        "`activityLevel` TEXT NOT NULL, `unitPreference` TEXT NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, `archivedAtEpochMs` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `measurement_events` (" +
                        "`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `dayEpochDay` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, `valueReal` REAL NOT NULL, `unit` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, `capturedAtEpochMs` INTEGER NOT NULL, `note` TEXT, " +
                        "PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_measurement_events_profileId_dayEpochDay` " +
                        "ON `measurement_events` (`profileId`, `dayEpochDay`)",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `measurement_event_attrs` (" +
                        "`eventId` TEXT NOT NULL, `attr` TEXT NOT NULL, `valueText` TEXT, `valueReal` REAL, " +
                        "PRIMARY KEY(`eventId`, `attr`), " +
                        "FOREIGN KEY(`eventId`) REFERENCES `measurement_events`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE NO ACTION )",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_measurement_event_attrs_eventId` " +
                        "ON `measurement_event_attrs` (`eventId`)",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `day_records` (" +
                        "`profileId` TEXT NOT NULL, `dayEpochDay` INTEGER NOT NULL, " +
                        "`trendWeightKg` REAL, `intakeKcal` REAL, `burnKcal` REAL, " +
                        "`computedAtEpochMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`profileId`, `dayEpochDay`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_day_records_profileId` ON `day_records` (`profileId`)",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `provenance` (" +
                        "`profileId` TEXT NOT NULL, `dayEpochDay` INTEGER NOT NULL, `scalar` TEXT NOT NULL, " +
                        "`method` TEXT NOT NULL, `formulaVersion` TEXT NOT NULL, `inputsHash` TEXT NOT NULL, " +
                        "`computedAtEpochMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`profileId`, `dayEpochDay`, `scalar`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_provenance_profileId` ON `provenance` (`profileId`)",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `consent_ledger` (" +
                        "`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `profileId` TEXT NOT NULL, " +
                        "`capability` TEXT NOT NULL, `decision` TEXT NOT NULL, `atEpochMs` INTEGER NOT NULL, " +
                        "`prevHashHex` TEXT NOT NULL, `hashHex` TEXT NOT NULL)",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `food_items` (" +
                        "`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, " +
                        "`kcalPer100g` REAL, `proteinGPer100g` REAL, `carbGPer100g` REAL, `fatGPer100g` REAL, " +
                        "`fiberGPer100g` REAL, `createdAtEpochMs` INTEGER NOT NULL, " +
                        "`archivedAtEpochMs` INTEGER, PRIMARY KEY(`id`))",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `targets_versions` (" +
                        "`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                        "`documentJson` TEXT NOT NULL, `writtenBy` TEXT NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, `supersededAtEpochMs` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_targets_versions_profileId_version` " +
                        "ON `targets_versions` (`profileId`, `version`)",
                )
            }
        }

    public val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}

/** Small extension mirroring the statement-prepare/step pattern used above. */
private fun SQLiteConnection.exec(sql: String) {
    prepare(sql).use { statement: SQLiteStatement -> statement.step() }
}
