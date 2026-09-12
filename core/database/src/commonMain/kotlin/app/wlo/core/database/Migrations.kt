package app.wlo.core.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/**
 * Hand-written migrations. Every schema change lands with a migration +
 * exported-schema test in the same PR (ADR-003 house rule); see
 * `MigrationTest` and the exported schemas under `schemas/`.
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

    public val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
