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
 * sidecar, unique (profileId, version) on targets_versions). v1 → v2 remains
 * from M1: users on schema v1 chain 1→2→3.
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
    fun v1DatabaseChainsThroughToV3() =
        runTest {
            helper.createDatabase(1).close()
            helper.runMigrationsAndValidate(3, listOf(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3)).use { connection ->
                assertEquals(
                    1L,
                    queryLong(connection, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='day_records'"),
                    "the chained 1→2→3 migration reaches the spine",
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
