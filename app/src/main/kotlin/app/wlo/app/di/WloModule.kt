package app.wlo.app.di

import android.content.Context
import androidx.room3.RoomDatabase
import app.wlo.app.ui.hub.HubViewModel
import app.wlo.core.common.ClockPort
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.androidDatabaseBuilder
import app.wlo.core.datastore.SettingsStoreFactory
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module
import java.io.File

private const val SETTINGS_FILE: String = "wlo.settings.preferences_pb"

private fun settingsPath(context: Context): Path = File(context.filesDir, SETTINGS_FILE).absolutePath.toPath()

/**
 * State holders + ports: the MVI-lite hub and the frozen demo clock. Pure
 * definitions — `verify()` exercises this graph in unit tests.
 */
public val appModule: Module =
    module {
        single<ClockPort> { FixedClock(FixedClock.DEMO_NOW) }
        factory { HubViewModel(clock = get()) }
    }

/**
 * Platform bindings (Android-only, D1: visible only here): preferences
 * DataStore and the Room database. Lazily built — nothing touches them before
 * the first real feature lands. The context dependency arrives from Koin's
 * android context, not from this graph.
 */
public val platformModule: Module =
    module {
        single { SettingsStoreFactory.create(file = settingsPath(get<Context>())) }
        single(qualifier = qualifier("wlo-db-builder")) {
            androidDatabaseBuilder(context = get<Context>(), path = WloDatabase.NAME)
        }
        single<WloDatabase> {
            get<RoomDatabase.Builder<WloDatabase>>(qualifier = qualifier("wlo-db-builder")).build()
        }
    }
