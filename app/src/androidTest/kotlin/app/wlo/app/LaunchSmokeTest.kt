package app.wlo.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** CI instrumented job (API 29 emulator) sanity check; PART B adds real tests. */
@RunWith(AndroidJUnit4::class)
public class LaunchSmokeTest {
    @Test
    public fun mainActivityLaunches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                check(activity.hasWindowFocus() || !activity.isFinishing)
            }
        }
    }
}
