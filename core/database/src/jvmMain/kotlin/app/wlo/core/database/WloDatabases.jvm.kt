package app.wlo.core.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * JVM construction (purity target + migration/DAO tests). Room KMP's
 * name-based builder treats [path] as the database file location on JVM.
 * BundledSQLiteDriver keeps JVM runs free of native SQLite dependencies.
 */
public fun jvmDatabaseBuilder(path: String): RoomDatabase.Builder<WloDatabase> =
    Room
        .databaseBuilder<WloDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*Migrations.ALL)
        .enforceForeignKeys()
