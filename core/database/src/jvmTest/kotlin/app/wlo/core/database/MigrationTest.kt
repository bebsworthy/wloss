package app.wlo.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration harness (acceptance 4 — THE deliberate schema-change demo):
 * runs as a plain JVM unit test (see README — room3-testing-jvm +
 * BundledSQLiteDriver, no emulator needed).
 *
 * v2 → v3 drops the M1 placeholder table and creates the real spine; the
 * test proves (a) the drop, (b) every new table's exact shape — the helper
 * validates the migrated database against the exported `3.json` — and
 * (c) the structural guarantees the spine relies on (FK clause on the EAV
 * sidecar, unique (profileId, version) on targets_versions). v3 → v4 lands
 * the F02 diary + FTS5 search and proves the FTS backfill finds pre-existing
 * catalog rows. v4 → v5 lands the F12 egress receipt ledger. v1 → v2 remains
 * from M1: users on schema v1 chain 1→2→3→4→5.
 */
class MigrationTest {
    private val schemasDir: java.nio.file.Path =
        java.nio.file.Path
            .of(System.getProperty("user.dir"))
            .resolve("schemas")

    @get:Rule
    val helper =
        MigrationTestHelper(
            schemasDir,
            Files.createTempDirectory("wlo-migration-test").resolve("migration-test.db"),
            BundledSQLiteDriver(),
            WloDatabase::class,
        )

    @Test
    fun migrate1To2_preservesRowsAndAddsNoteColumn() =
        runTest {
            helper.createDatabase(1).use { connection ->
                connection.exec(
                    "INSERT INTO provenance_events " +
                        "(day, kind, valueText, provenanceJson, createdAtEpochMs) " +
                        "VALUES ('2026-09-12', 'bmi', '24.2 kg/m2', '{\"kind\":\"derived\"}', 1000)",
                )
            }

            val migrated = helper.runMigrationsAndValidate(2, listOf(Migrations.MIGRATION_1_2))
            migrated.use { connection ->
                connection
                    .prepare("SELECT day, kind, valueText, provenanceJson, note FROM provenance_events")
                    .use { statement ->
                        assertTrue(statement.step(), "row must survive the migration")
                        assertEquals("2026-09-12", statement.getText(0))
                        assertEquals("bmi", statement.getText(1))
                        assertEquals("24.2 kg/m2", statement.getText(2))
                        // v2 column exists and defaults to NULL for pre-existing rows.
                        assertTrue(statement.isNull(4))
                    }
            }
        }

    @Test
    fun migrate2To3_dropsPlaceholderAndCreatesTheSpine() =
        runTest {
            helper.createDatabase(2).use { connection ->
                connection.exec(
                    "INSERT INTO provenance_events " +
                        "(day, kind, valueText, provenanceJson, createdAtEpochMs, note) " +
                        "VALUES ('2026-09-12', 'bmi', '24.2 kg/m2', '{\"kind\":\"derived\"}', 1000, 'demo')",
                )
            }

            val migrated = helper.runMigrationsAndValidate(3, listOf(Migrations.MIGRATION_2_3))
            migrated.use { connection ->
                // (a) The M1 placeholder is gone.
                val tableCount =
                    queryLong(
                        connection,
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='provenance_events'",
                    )
                assertEquals(0L, tableCount, "placeholder table must be dropped")

                // (b) Every spine table exists; the helper has already
                // validated each table's exact shape against schemas/3.json.
                val expectedTables =
                    listOf(
                        "profiles",
                        "measurement_events",
                        "measurement_event_attrs",
                        "day_records",
                        "provenance",
                        "consent_ledger",
                        "food_items",
                        "targets_versions",
                    )
                expectedTables.forEach { table ->
                    assertEquals(
                        1L,
                        queryLong(
                            connection,
                            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$table'",
                        ),
                        "$table must exist after migration",
                    )
                }

                // (c) The EAV sidecar carries its FK clause (sqlite_master SQL
                // is the structural proof — pragma-dependent enforcement varies
                // by driver connection).
                val attrsSql = queryText(connection, "SELECT sql FROM sqlite_master WHERE name='measurement_event_attrs'")
                assertTrue(attrsSql.contains("FOREIGN KEY(`eventId`) REFERENCES `measurement_events`(`id`)"), attrsSql)

                // (d) targets_versions is uniquely versioned per profile.
                val uniqueIdx =
                    queryText(
                        connection,
                        "SELECT sql FROM sqlite_master WHERE type='index' AND name='index_targets_versions_profileId_version'",
                    )
                assertTrue(uniqueIdx.startsWith("CREATE UNIQUE INDEX"), uniqueIdx)
            }
        }

    @Test
    fun migrate3To4_realizesFoodDiaryAndBackfillsFts() =
        runTest {
            helper.createDatabase(3).use { connection ->
                // A v3-era catalog row (the M2 stub shape) must come through.
                connection.exec(
                    "INSERT INTO food_items " +
                        "(id, profileId, name, brand, kcalPer100g, createdAtEpochMs) " +
                        "VALUES ('f1', 'p1', 'Rye bread', 'Bakery', 250.0, 1000)",
                )
                // A v3 measurement event must gain (null) Fresh Start columns.
                connection.exec(
                    "INSERT INTO measurement_events " +
                        "(id, profileId, dayEpochDay, kind, valueReal, unit, source, capturedAtEpochMs) " +
                        "VALUES ('m1', 'p1', 20708, 'weight', 84.2, 'kg', 'scale', 1000)",
                )
            }

            val migrated = helper.runMigrationsAndValidate(4, listOf(Migrations.MIGRATION_3_4))
            migrated.use { connection ->
                // (a) The FTS index was backfilled: the pre-existing row is searchable.
                val matches =
                    queryLong(
                        connection,
                        "SELECT COUNT(*) FROM food_search WHERE food_search MATCH 'rye*'",
                    )
                assertEquals(1L, matches, "existing catalog rows must be searchable after migration")

                // (b) The backfilled row carries the source DEFAULT from the ALTER.
                val source = queryText(connection, "SELECT source FROM food_items WHERE id = 'f1'")
                assertEquals("custom", source)

                // (c) Fresh Start ledger columns exist and default to NULL (visible).
                assertTrue(
                    queryLong(connection, "SELECT COUNT(*) FROM measurement_events WHERE hiddenAtEpochMs IS NULL") == 1L,
                    "pre-existing events must stay visible (NULL = visible, R-B7)",
                )

                // (d) The diary + audit tables exist with their indices.
                listOf(
                    "diary_entries",
                    "diary_entry_revisions",
                    "index_diary_entries_profileId_dayEpochDay",
                    "index_diary_entry_revisions_entryId",
                ).forEach { name ->
                    assertEquals(
                        1L,
                        queryLong(connection, "SELECT COUNT(*) FROM sqlite_master WHERE name='$name'"),
                        "$name must exist after migration",
                    )
                }
            }
        }

    @Test
    fun migrate4To5_createsTheEgressReceiptLedger() =
        runTest {
            helper.createDatabase(4).use { connection ->
                // A v4-era diary row must come through untouched.
                connection.exec(
                    "INSERT INTO diary_entries " +
                        "(id, profileId, dayEpochDay, mealSlot, quantity, unit, computedKcal, " +
                        "enteredVia, provenanceScalar, revision, createdAtEpochMs) " +
                        "VALUES ('e1', 'p1', 20708, 'breakfast', 60.0, 'g', 220.0, 'manual', " +
                        "'diary/kcal/e1', 0, 1000)",
                )
            }

            val migrated = helper.runMigrationsAndValidate(5, listOf(Migrations.MIGRATION_4_5))
            migrated.use { connection ->
                // (a) The receipt table exists with its indices (the helper has
                // already validated the exact shape against schemas/5.json).
                listOf(
                    "network_receipts",
                    "index_network_receipts_purpose",
                    "index_network_receipts_atEpochMs",
                ).forEach { name ->
                    assertEquals(
                        1L,
                        queryLong(connection, "SELECT COUNT(*) FROM sqlite_master WHERE name='$name'"),
                        "$name must exist after migration",
                    )
                }

                // (b) The chain columns are usable: the AUTOINCREMENT seq
                // begins at 1 and the prev/hash pair is writable.
                connection.exec(
                    "INSERT INTO network_receipts " +
                        "(purpose, host, operation, bytes, outcome, atEpochMs, prevHashHex, hashHex) " +
                        "VALUES ('zoo-download', 'huggingface.co', 'food-classifier/1', 9835830, " +
                        "'ok', 1000, '" + "0".repeat(64) + "', 'abc')",
                )
                val seq = queryLong(connection, "SELECT MIN(seq) FROM network_receipts")
                assertEquals(1L, seq, "AUTOINCREMENT receipt seq starts at 1 (chain base)")

                // (c) The v4 diary row survived.
                assertEquals(
                    1L,
                    queryLong(connection, "SELECT COUNT(*) FROM diary_entries WHERE id='e1'"),
                    "pre-existing diary rows must survive the migration",
                )
            }
        }

    @Test
    fun v1DatabaseChainsThroughToV5() =
        runTest {
            helper.createDatabase(1).close()
            helper
                .runMigrationsAndValidate(
                    5,
                    listOf(
                        Migrations.MIGRATION_1_2,
                        Migrations.MIGRATION_2_3,
                        Migrations.MIGRATION_3_4,
                        Migrations.MIGRATION_4_5,
                    ),
                ).use { connection ->
                    assertEquals(
                        1L,
                        queryLong(connection, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='diary_entries'"),
                        "the chained 1→2→3→4→5 migration reaches the M3 diary schema",
                    )
                    assertEquals(
                        1L,
                        queryLong(connection, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='network_receipts'"),
                        "the chained 1→2→3→4→5 migration reaches the M4 egress receipt ledger",
                    )
                }
        }

    private fun queryLong(
        connection: SQLiteConnection,
        sql: String,
    ): Long =
        connection.prepare(sql).use { statement ->
            assertTrue(statement.step(), "query must return a row: $sql")
            statement.getLong(0)
        }

    private fun queryText(
        connection: SQLiteConnection,
        sql: String,
    ): String =
        connection.prepare(sql).use { statement ->
            assertTrue(statement.step(), "query must return a row: $sql")
            statement.getText(0)
        }
}

/** prepare/step helper matching the migration-side pattern (ADR-003 renames). */
public fun SQLiteConnection.exec(sql: String) {
    prepare(sql).use { statement -> statement.step() }
}
