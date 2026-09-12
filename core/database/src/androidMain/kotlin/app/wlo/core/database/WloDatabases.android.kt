package app.wlo.core.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Android construction. `:app` (composition root) supplies the Context and
 * binds this into Koin in the app-shell milestone; core never reaches for a
 * global context.
 */
public fun androidDatabaseBuilder(
    context: Context,
    path: String,
): RoomDatabase.Builder<WloDatabase> =
    Room
        .databaseBuilder<WloDatabase>(context = context, name = path)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(*Migrations.ALL)
