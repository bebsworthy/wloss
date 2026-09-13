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

    /**
     * v3 → v4: F02 manual logging lands on the spine (WLO-0025).
     *
     *  1. R-B7 Fresh Start ledger columns on the event stores — hide-not-delete
     *     is demanded by F06 §4 ("hide-not-delete everything before a chosen
     *     date; reversible") for measurements and by the diary's archive rule
     *     for entries; the columns land NOW so no later migration is needed.
     *     NULL = visible; nothing existing changes meaning.
     *  2. `food_items` is realized per F02 §3/§5: aliases, serving presets
     *     (JSON), source wire, label-verified flags, updated-at. NOT NULL new
     *     columns carry DEFAULTs matching the exported `4.json` createSql.
     *  3. `diary_entries` + append-only `diary_entry_revisions` (correction
     *     audit, R-B8) are created.
     *  4. The FTS5 `food_search` index (ADR-003 `@Fts5`; contentful standalone
     *     pattern — `food_items`' TEXT PK cannot join an external-content
     *     index) is created and BACKFILLED from existing catalog rows, so
     *     search works immediately after the migration.
     */
    public val MIGRATION_3_4: Migration =
        object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                // 1. Fresh Start ledger columns (R-B7).
                connection.alterAddColumn("ALTER TABLE measurement_events ADD COLUMN hiddenAtEpochMs INTEGER")
                connection.alterAddColumn("ALTER TABLE measurement_events ADD COLUMN hiddenReason TEXT")

                // 2. food_items realization (DDL mirrors schemas/4.json).
                connection.alterAddColumn("ALTER TABLE food_items ADD COLUMN aliases TEXT")
                connection.alterAddColumn("ALTER TABLE food_items ADD COLUMN servingPresetsJson TEXT")
                connection.alterAddColumn("ALTER TABLE food_items ADD COLUMN source TEXT NOT NULL DEFAULT 'custom'")
                connection.alterAddColumn("ALTER TABLE food_items ADD COLUMN macrosVerified INTEGER NOT NULL DEFAULT false")
                connection.alterAddColumn("ALTER TABLE food_items ADD COLUMN verifiedAtEpochMs INTEGER")
                connection.alterAddColumn("ALTER TABLE food_items ADD COLUMN updatedAtEpochMs INTEGER")

                // 3. Diary + correction audit.
                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `diary_entries` (" +
                        "`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `dayEpochDay` INTEGER NOT NULL, " +
                        "`mealSlot` TEXT NOT NULL, `foodItemId` TEXT, `textHint` TEXT, `quantity` REAL NOT NULL, " +
                        "`unit` TEXT NOT NULL, `computedKcal` REAL NOT NULL, `computedProteinG` REAL, " +
                        "`computedCarbG` REAL, `computedFatG` REAL, `computedFiberG` REAL, " +
                        "`enteredVia` TEXT NOT NULL, `provenanceScalar` TEXT NOT NULL, `revision` INTEGER NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, `editedAtEpochMs` INTEGER, `archivedAtEpochMs` INTEGER, " +
                        "`hiddenAtEpochMs` INTEGER, `hiddenReason` TEXT, PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_diary_entries_profileId_dayEpochDay` " +
                        "ON `diary_entries` (`profileId`, `dayEpochDay`)",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_diary_entries_foodItemId` ON `diary_entries` (`foodItemId`)",
                )
                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `diary_entry_revisions` (" +
                        "`id` TEXT NOT NULL, `entryId` TEXT NOT NULL, `revision` INTEGER NOT NULL, " +
                        "`mealSlot` TEXT NOT NULL, `foodItemId` TEXT, `textHint` TEXT, `quantity` REAL NOT NULL, " +
                        "`unit` TEXT NOT NULL, `computedKcal` REAL NOT NULL, `computedProteinG` REAL, " +
                        "`computedCarbG` REAL, `computedFatG` REAL, `computedFiberG` REAL, " +
                        "`enteredVia` TEXT NOT NULL, `editedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_diary_entry_revisions_entryId` " +
                        "ON `diary_entry_revisions` (`entryId`)",
                )

                // 4. FTS5 search index + backfill (exact createSql from 4.json).
                connection.exec(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `food_search` " +
                        "USING FTS5(`name`, `brand`, `aliases`, `foodId`, tokenize=`unicode61`)",
                )
                connection.exec(
                    "INSERT INTO `food_search`(`name`, `brand`, `aliases`, `foodId`) " +
                        "SELECT `name`, `brand`, `aliases`, `id` FROM `food_items`",
                )
            }
        }

    /**
     * v4 → v5: the F12 egress receipt ledger (WLO-0026 PART A, F12 §3.6/§3.8).
     * `network_receipts` is append-only and hash-chained (prevHash/hash columns,
     * chain engine in :core:consent) — the Room shape mirrors the exported
     * `5.json` createSql exactly.
     */
    public val MIGRATION_4_5: Migration =
        object : Migration(4, 5) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `network_receipts` (" +
                        "`seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `purpose` TEXT NOT NULL, " +
                        "`host` TEXT NOT NULL, `operation` TEXT NOT NULL, `bytes` INTEGER NOT NULL, " +
                        "`outcome` TEXT NOT NULL, `atEpochMs` INTEGER NOT NULL, " +
                        "`prevHashHex` TEXT NOT NULL, `hashHex` TEXT NOT NULL)",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_network_receipts_purpose` " +
                        "ON `network_receipts` (`purpose`)",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_network_receipts_atEpochMs` " +
                        "ON `network_receipts` (`atEpochMs`)",
                )
            }
        }

    public val ALL: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}

/** Small extension mirroring the statement-prepare/step pattern used above. */
private fun SQLiteConnection.exec(sql: String) {
    prepare(sql).use { statement: SQLiteStatement -> statement.step() }
}

/** ALTER TABLE ADD COLUMN helper (DOLL statements share the prepare/step shape). */
private fun SQLiteConnection.alterAddColumn(sql: String) {
    exec(sql)
}
