package app.wlo.app

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.vault.AppLockController
import app.wlo.core.vault.AppLockPosture
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regression coverage for WLO-0059's process-start fail-closed contract. */
@RunWith(AndroidJUnit4::class)
public class AppLockColdStartTest {
    @Test
    public fun enabledLock_coldStartShowsNoApplicationDestinationAndSecuresWindow() {
        val koin = GlobalContext.get()
        val settings = koin.get<SettingsStore>()
        val controller = koin.get<AppLockController>()
        runBlocking { settings.setAppLockEnabled(true) }

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(AppLockPosture.LOCKED, controller.posture.value)
                    assertEquals(
                        null,
                        activity.currentDestinationForVerification,
                        "the application NavHost must not compose behind the startup gate",
                    )
                    assertTrue(
                        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
                        "the window must be secure before authentication",
                    )
                }
            }
        } finally {
            runBlocking { settings.setAppLockEnabled(false) }
            controller.onUnlock()
        }
    }
}
