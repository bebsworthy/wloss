package app.wlo.core.database

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration harness (acceptance criterion 3) — runs as a plain JVM unit test.
 *
 * Decision (documented in this module's README): Room 3's `MigrationTestHelper`
 * JVM artifact takes a schema directory + a `BundledSQLiteDriver`, so
 * v1→v2 validation needs NO emulator/instrumentation. room3-testing-jvm and
 * sqlite-bundled-jvm both publish stable JVM variants (verified in
 * dl.google.com group index, 2026-09-12). The instrumented path stays
 * available for PART B's UI-layer tests, but schema validation lives here.
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
                connection
                    .prepare(
                        "INSERT INTO provenance_events " +
                            "(day, kind, valueText, provenanceJson, createdAtEpochMs) " +
                            "VALUES ('2026-09-12', 'bmi', '24.2 kg/m2', '{\"kind\":\"derived\"}', 1000)",
                    ).use { statement ->
                        statement.step()
                    }
            }

            val migrated = helper.runMigrationsAndValidate(2, listOf(Migrations.MIGRATION_1_2))
            migrated.use { connection ->
                connection
                    .prepare(
                        "SELECT day, kind, valueText, provenanceJson, note FROM provenance_events",
                    ).use { statement ->
                        assertTrue(statement.step(), "row must survive the migration")
                        assertEquals("2026-09-12", statement.getText(0))
                        assertEquals("bmi", statement.getText(1))
                        assertEquals("24.2 kg/m2", statement.getText(2))
                        // v2 column exists and defaults to NULL for pre-existing rows.
                        assertTrue(statement.isNull(4))
                    }
            }
        }
}
