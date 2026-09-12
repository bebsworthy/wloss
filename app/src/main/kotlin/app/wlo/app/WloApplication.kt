package app.wlo.app

import android.app.Application
import app.wlo.app.di.appModule
import app.wlo.app.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Composition root (D1): Koin starts here and binds the real platform
 * implementations (DataStore settings, the Room database builder) that no
 * other module may see.
 */
public class WloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@WloApplication)
            modules(appModule, platformModule)
        }
    }
}
