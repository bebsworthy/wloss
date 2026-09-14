package app.wlo.app

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WLO-0033 wave 2 screenshot sweep (rule-free, the M5 pattern): the rebuilt
 * Hub — the ring calories card, the one-row meals card with its "log as
 * planned" CTA, and the header streak chip — on a seeded install with a dealt
 * plan. Images land on /sdcard/p5-*.png and are pulled to /tmp/wlo-uifix/ by
 * the operator.
 */
@RunWith(AndroidJUnit4::class)
public class F10ScreensTest {
    private lateinit var seeded: SeedingRobot.SeedResult

    @Before
    public fun seed() {
        seeded = SeedingRobot.onboardAndSeedWeek()
    }

    private fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun shot(name: String) {
        OnboardingRobot.shell("screencap -p /sdcard/$name.png")
        SystemClock.sleep(150)
    }

    @Test
    public fun captureF10HubSurfaces() {
        // The deterministic deal: today's slots feed the meals card + ring.
        SeedingRobot.dealWeekPlan(profileId = seeded.profileId, startDayEpochDay = seeded.today, seed = 7L)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        TestNav.awaitSurface(scenario, "hub")
        SystemClock.sleep(1500)

        // Top of the hub: header (streak chip) + the calories ring — the
        // seeded today diary already carries ~690 kcal, so the ring is filled.
        device().swipe(360, 400, 360, 1300, 12)
        SystemClock.sleep(600)
        device().swipe(360, 400, 360, 1300, 12)
        SystemClock.sleep(800)
        shot("p5-hub-morning")

        // One tap on the CTA: the ring sweeps up (400 ms ease-out) and the
        // meals row advances — the midday "after a log" frame.
        var logged = false
        for (attempt in 0 until 4) {
            val cta = device().findObjects(By.text("Log as planned")).firstOrNull()
            if (cta != null) {
                cta.click()
                logged = true
                break
            }
            device().swipe(360, 1100, 360, 700, 8)
            SystemClock.sleep(500)
        }
        if (logged) {
            SystemClock.sleep(2000)
            device().swipe(360, 400, 360, 1300, 12)
            SystemClock.sleep(800)
            shot("p5-hub-consumed")
        }

        scenario.onActivity { it.finish() }
        SystemClock.sleep(500)
    }
}
