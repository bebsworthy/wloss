package app.wlo.core.database

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection

/**
 * M6 (WLO-0028): declared FOREIGN KEY constraints (e.g.
 * measurement_event_attrs → measurement_events) are ENFORCED — Room's KMP
 * drivers do not switch the pragma on by default, and the staged restore's
 * commit-or-nothing guarantee leans on referential integrity. Applied to both
 * platform builders so JVM tests and production behave identically.
 */
internal fun RoomDatabase.Builder<WloDatabase>.enforceForeignKeys(): RoomDatabase.Builder<WloDatabase> =
    addCallback(
        object : RoomDatabase.Callback() {
            override suspend fun onOpen(connection: SQLiteConnection) {
                connection.prepare("PRAGMA foreign_keys = ON").use { it.step() }
            }
        },
    )
