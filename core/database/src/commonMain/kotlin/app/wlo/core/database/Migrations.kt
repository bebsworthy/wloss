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

    /**
     * v5 → v6: the F03/F04 planning surface (WLO-0027 PART A; F03 §3, F04 §3,
     * rulings R-B1/R-B9/R-S3/R-S8) — seven new tables, all profile-scoped:
     *
     *  1. `recipes` — versioned (recipeId, version PK), per-serving macros,
     *     JSON columns for slots/tags/FODMAP(R-S8)/ingredients/steps, source +
     *     license per R-S3. Archive-don't-delete.
     *  2. `grocery_items` — the canonical item space's catalog half (names,
     *     shipped aisle tag, default unit, density hint).
     *  3. `plan_versions` — one active plan per profile (supersede-on-write).
     *  4. `plan_slots` — the R-B1 slot state machine rows (append-only
     *     records: swap/skip/confirm/replace touch state columns only).
     *  5. `list_items` — delta-reconciled list rows (checks ride the row).
     *  6. `pantry_items` — stock levels, expiry, staples, out-of-stock flags.
     *  7. `aisle_corrections` — the learned per-item aisle loop.
     *
     * DDL mirrors the exported `6.json` createSql statements exactly.
     */
    public val MIGRATION_5_6: Migration =
        object : Migration(5, 6) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `recipes` (" +
                        "`recipeId` TEXT NOT NULL, `version` INTEGER NOT NULL, `profileId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `servingsBase` REAL NOT NULL, `cuisine` TEXT, " +
                        "`slotsJson` TEXT NOT NULL, `tagsJson` TEXT NOT NULL, `fodmapTagsJson` TEXT NOT NULL, " +
                        "`ingredientsJson` TEXT NOT NULL, `stepsJson` TEXT NOT NULL, " +
                        "`kcalPerServing` REAL NOT NULL, `proteinGPerServing` REAL NOT NULL, " +
                        "`carbGPerServing` REAL NOT NULL, `fatGPerServing` REAL NOT NULL, " +
                        "`fiberGPerServing` REAL NOT NULL, `nutritionBasis` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, `license` TEXT, `rating` INTEGER, " +
                        "`lastPlannedAtEpochMs` INTEGER, `createdAtEpochMs` INTEGER NOT NULL, " +
                        "`updatedAtEpochMs` INTEGER, `archivedAtEpochMs` INTEGER, " +
                        "PRIMARY KEY(`recipeId`, `version`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_recipes_recipeId` ON `recipes` (`recipeId`)",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_recipes_profileId_archivedAtEpochMs` " +
                        "ON `recipes` (`profileId`, `archivedAtEpochMs`)",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `grocery_items` (" +
                        "`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`aisle` TEXT NOT NULL, `defaultUnit` TEXT NOT NULL, `densityGPerMl` REAL, " +
                        "`gramsPerPiece` REAL, `aliasesJson` TEXT, `createdAtEpochMs` INTEGER NOT NULL, " +
                        "`archivedAtEpochMs` INTEGER, PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_grocery_items_profileId` " +
                        "ON `grocery_items` (`profileId`)",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `plan_versions` (" +
                        "`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                        "`startDayEpochDay` INTEGER NOT NULL, `endDayEpochDay` INTEGER NOT NULL, " +
                        "`seed` INTEGER NOT NULL, `settingsJson` TEXT NOT NULL, `reportJson` TEXT NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, `supersededAtEpochMs` INTEGER, " +
                        "PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_plan_versions_profileId_version` " +
                        "ON `plan_versions` (`profileId`, `version`)",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `plan_slots` (" +
                        "`id` TEXT NOT NULL, `planId` TEXT NOT NULL, `profileId` TEXT NOT NULL, " +
                        "`dayEpochDay` INTEGER NOT NULL, `mealSlot` TEXT NOT NULL, `recipeId` TEXT, " +
                        "`recipeVersion` INTEGER, `recipeName` TEXT, `servings` REAL NOT NULL, " +
                        "`state` TEXT NOT NULL, `replacedByEntryId` TEXT, `successorSlotId` TEXT, " +
                        "`replacesSlotId` TEXT, `parentSlotId` TEXT, `isCookEvent` INTEGER NOT NULL, " +
                        "`batchServings` REAL, `kcalPerServing` REAL, `proteinGPerServing` REAL, " +
                        "`carbGPerServing` REAL, `fatGPerServing` REAL, `fiberGPerServing` REAL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER, PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_plan_slots_planId` ON `plan_slots` (`planId`)",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_plan_slots_profileId_dayEpochDay` " +
                        "ON `plan_slots` (`profileId`, `dayEpochDay`)",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_plan_slots_recipeId` ON `plan_slots` (`recipeId`)",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `list_items` (" +
                        "`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `listId` TEXT NOT NULL, " +
                        "`groceryItemId` TEXT NOT NULL, `name` TEXT NOT NULL, `qty` REAL NOT NULL, " +
                        "`unit` TEXT NOT NULL, `aisle` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                        "`checkedAtEpochMs` INTEGER, `deltaQty` REAL, `sourcesJson` TEXT NOT NULL, " +
                        "`createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER, " +
                        "`archivedAtEpochMs` INTEGER, PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_list_items_profileId_listId` " +
                        "ON `list_items` (`profileId`, `listId`)",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_list_items_groceryItemId` " +
                        "ON `list_items` (`groceryItemId`)",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `pantry_items` (" +
                        "`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `groceryItemId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `qty` REAL NOT NULL, `unit` TEXT NOT NULL, " +
                        "`expiryEpochDay` INTEGER, `addedAtEpochMs` INTEGER NOT NULL, " +
                        "`lastPurchasedAtEpochMs` INTEGER, `purchaseCount` INTEGER NOT NULL, " +
                        "`isStaple` INTEGER NOT NULL, `outOfStock` INTEGER NOT NULL, " +
                        "`updatedAtEpochMs` INTEGER, `archivedAtEpochMs` INTEGER, PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_pantry_items_profileId` ON `pantry_items` (`profileId`)",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_pantry_items_groceryItemId` " +
                        "ON `pantry_items` (`groceryItemId`)",
                )

                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `aisle_corrections` (" +
                        "`profileId` TEXT NOT NULL, `groceryItemId` TEXT NOT NULL, " +
                        "`aisle` TEXT NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`profileId`, `groceryItemId`))",
                )
            }
        }

    /** Weight-first onboarding no longer fabricates demographics just to create a usable profile. */
    public val MIGRATION_6_7: Migration =
        object : Migration(6, 7) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `profiles_new` (" +
                        "`id` TEXT NOT NULL, `sex` TEXT, `birthYear` INTEGER, `heightCm` REAL, " +
                        "`startWeightKg` REAL, `activityLevel` TEXT NOT NULL, " +
                        "`unitPreference` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, " +
                        "`archivedAtEpochMs` INTEGER, PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "INSERT INTO `profiles_new` SELECT `id`, `sex`, `birthYear`, `heightCm`, " +
                        "`startWeightKg`, `activityLevel`, `unitPreference`, `createdAtEpochMs`, " +
                        "`archivedAtEpochMs` FROM `profiles`",
                )
                connection.exec("DROP TABLE `profiles`")
                connection.exec("ALTER TABLE `profiles_new` RENAME TO `profiles`")
            }
        }

    /** Health Connect identity/cursor/log storage (WLO-0038). */
    public val MIGRATION_7_8: Migration =
        object : Migration(7, 8) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `health_connect_records` (" +
                        "`recordId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `measurementEventId` TEXT NOT NULL, " +
                        "`dataOriginPackage` TEXT NOT NULL, `clientRecordId` TEXT, `clientRecordVersion` INTEGER, " +
                        "`recordingMethod` INTEGER NOT NULL, `lastModifiedAtEpochMs` INTEGER NOT NULL, " +
                        "`capturedAtEpochMs` INTEGER NOT NULL, `zoneOffsetSeconds` INTEGER, `metric` TEXT NOT NULL, " +
                        "`canonicalValue` REAL NOT NULL, PRIMARY KEY(`recordId`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_health_connect_records_measurementEventId` ON `health_connect_records` (`measurementEventId`)",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_health_connect_records_profileId_metric` ON `health_connect_records` (`profileId`, `metric`)",
                )
                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `health_connect_sync_state` (" +
                        "`profileId` TEXT NOT NULL, `metric` TEXT NOT NULL, `changeToken` TEXT NOT NULL, " +
                        "`lastSyncAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `metric`))",
                )
                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `health_connect_import_log` (" +
                        "`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `atEpochMs` INTEGER NOT NULL, " +
                        "`outcome` TEXT NOT NULL, `inserted` INTEGER NOT NULL, `updated` INTEGER NOT NULL, " +
                        "`deleted` INTEGER NOT NULL, `skipped` INTEGER NOT NULL, `conflicts` INTEGER NOT NULL, " +
                        "`retryable` INTEGER NOT NULL, `detail` TEXT, PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_health_connect_import_log_profileId_atEpochMs` ON `health_connect_import_log` (`profileId`, `atEpochMs`)",
                )
            }
        }

    /**
     * Fixed daily-weight policy metadata. Legacy rows remain null until the
     * repository captures the device zone once, preserving existing day buckets.
     */
    /** WLO-0156: item metadata and honest unknown diary nutrition. */
    public val MIGRATION_10_11: Migration =
        object : Migration(10, 11) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.exec("ALTER TABLE plan_slots ADD COLUMN sortOrder INTEGER")
            }
        }

    public val MIGRATION_9_10: Migration =
        object : Migration(9, 10) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.exec("ALTER TABLE plan_slots ADD COLUMN itemJson TEXT")
                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `diary_entries_new` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `dayEpochDay` INTEGER NOT NULL, `mealSlot` TEXT NOT NULL, `foodItemId` TEXT, `textHint` TEXT, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `computedKcal` REAL, `computedProteinG` REAL, `computedCarbG` REAL, `computedFatG` REAL, `computedFiberG` REAL, `enteredVia` TEXT NOT NULL, `provenanceScalar` TEXT NOT NULL, `revision` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `editedAtEpochMs` INTEGER, `archivedAtEpochMs` INTEGER, `hiddenAtEpochMs` INTEGER, `hiddenReason` TEXT, PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "INSERT INTO `diary_entries_new` (`id`, `profileId`, `dayEpochDay`, `mealSlot`, `foodItemId`, `textHint`, `quantity`, `unit`, `computedKcal`, `computedProteinG`, `computedCarbG`, `computedFatG`, `computedFiberG`, `enteredVia`, `provenanceScalar`, `revision`, `createdAtEpochMs`, `editedAtEpochMs`, `archivedAtEpochMs`, `hiddenAtEpochMs`, `hiddenReason`) SELECT `id`, `profileId`, `dayEpochDay`, `mealSlot`, `foodItemId`, `textHint`, `quantity`, `unit`, `computedKcal`, `computedProteinG`, `computedCarbG`, `computedFatG`, `computedFiberG`, `enteredVia`, `provenanceScalar`, `revision`, `createdAtEpochMs`, `editedAtEpochMs`, `archivedAtEpochMs`, `hiddenAtEpochMs`, `hiddenReason` FROM `diary_entries`",
                )
                connection.exec("DROP TABLE `diary_entries`")
                connection.exec("ALTER TABLE `diary_entries_new` RENAME TO `diary_entries`")
                connection.exec(
                    "CREATE INDEX IF NOT EXISTS `index_diary_entries_profileId_dayEpochDay` ON `diary_entries` (`profileId`, `dayEpochDay`)",
                )
                connection.exec("CREATE INDEX IF NOT EXISTS `index_diary_entries_foodItemId` ON `diary_entries` (`foodItemId`)")
                connection.exec(
                    "CREATE TABLE IF NOT EXISTS `diary_entry_revisions_new` (`id` TEXT NOT NULL, `entryId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `mealSlot` TEXT NOT NULL, `foodItemId` TEXT, `textHint` TEXT, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `computedKcal` REAL, `computedProteinG` REAL, `computedCarbG` REAL, `computedFatG` REAL, `computedFiberG` REAL, `enteredVia` TEXT NOT NULL, `editedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                connection.exec(
                    "INSERT INTO `diary_entry_revisions_new` (`id`, `entryId`, `revision`, `mealSlot`, `foodItemId`, `textHint`, `quantity`, `unit`, `computedKcal`, `computedProteinG`, `computedCarbG`, `computedFatG`, `computedFiberG`, `enteredVia`, `editedAtEpochMs`) SELECT `id`, `entryId`, `revision`, `mealSlot`, `foodItemId`, `textHint`, `quantity`, `unit`, `computedKcal`, `computedProteinG`, `computedCarbG`, `computedFatG`, `computedFiberG`, `enteredVia`, `editedAtEpochMs` FROM `diary_entry_revisions`",
                )
                connection.exec("DROP TABLE `diary_entry_revisions`")
                connection.exec("ALTER TABLE `diary_entry_revisions_new` RENAME TO `diary_entry_revisions`")
                connection.exec("CREATE INDEX IF NOT EXISTS `index_diary_entry_revisions_entryId` ON `diary_entry_revisions` (`entryId`)")
                connection.exec("ALTER TABLE diary_entries ADD COLUMN itemJson TEXT")
                connection.exec("ALTER TABLE diary_entry_revisions ADD COLUMN itemJson TEXT")
            }
        }

    public val MIGRATION_8_9: Migration =
        object : Migration(8, 9) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.alterAddColumn("ALTER TABLE profiles ADD COLUMN weightPolicyTimeZoneId TEXT")
                connection.alterAddColumn("ALTER TABLE profiles ADD COLUMN weightPolicyVersion TEXT")
            }
        }

    public val ALL: Array<Migration> =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
        )
}

/** Small extension mirroring the statement-prepare/step pattern used above. */
private fun SQLiteConnection.exec(sql: String) {
    prepare(sql).use { statement: SQLiteStatement -> statement.step() }
}

/** ALTER TABLE ADD COLUMN helper (DOLL statements share the prepare/step shape). */
private fun SQLiteConnection.alterAddColumn(sql: String) {
    exec(sql)
}
