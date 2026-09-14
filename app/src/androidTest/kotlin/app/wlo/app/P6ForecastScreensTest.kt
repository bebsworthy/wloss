package app.wlo.app

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.NewDiaryEntry
import app.wlo.core.data.WeighInRepository
import app.wlo.core.engines.EnergyDay
import app.wlo.core.engines.EnergyEngine
import app.wlo.core.engines.EngineState
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.EntryVia
import app.wlo.core.model.MealSlot
import app.wlo.core.model.MeasurementSource
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WLO-0034 screenshot evidence (rule-free, the F10ScreensTest pattern): the
 * reworked forecast card — Hub cold-start on the shipped seed, Hub measured
 * once the logged week crosses the F07 UPDATING gate, and the onboarding
 * preview's forecast step (the ratified flow-07 frame 3). Images land on
 * /sdcard/p6-forecast-*.png and are pulled to /tmp/wlo-uifix/ by the operator.
 *
 * The demo [FixedClock] pins "today" at 2026-09-08 07:12 — the screenshots
 * render that Tuesday regardless of the wall clock; this test is written to
 * work under it (all seeded days are offsets from the clock's today).
 */
@RunWith(AndroidJUnit4::class)
public class P6ForecastScreensTest {
    @Test
    public fun captureForecastCardEvidence() {
        // 1 · The Hub on the shipped seed (7 usable diary days < 10 → F07
        //     DEVELOPING → the forecast stays cold-start, pill "estimated").
        val seeded = SeedingRobot.onboardAndSeedWeek()
        shotForecastCard("p6-forecast-hub-coldstart")

        // 2 · Cross the F07 §4 UPDATING gate: extend the diary week onto six
        //     earlier days through the real diary door (quick-add entries, the
        //     same door the capture flow writes), and re-weigh today so the
        //     solve window's LAST trend scalar is the honest current EWMA (the
        //     onboarding anchor event would otherwise read as today's trend).
        //     Quality flips → the forecast runs measured; the pill reads
        //     "derived". The shot NAME records which mode the engine picked.
        extendDiaryWeek(seeded.profileId)
        val measured = forecastRunsMeasured(seeded.profileId)
        shotForecastCard(
            if (measured) "p6-forecast-hub-measured" else "p6-forecast-hub-extended-coldstart",
        )
    }

    /**
     * The onboarding preview (cold-start, flow-07 frame 3): fresh install
     * (the orchestrator clears between tests), wizard driven to the forecast
     * step by the hedge text.
     */
    @Test
    public fun captureOnboardingForecastStep() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val ui = device()
        ui.waitForIdle()
        clickWhen(ui, "Set up my plan")
        for (attempt in 0 until 8) {
            if (ui.hasObject(By.textContains(HEDGE))) break
            if (!clickWhen(ui, "Continue")) break
            SystemClock.sleep(400)
        }
        check(ui.hasObject(By.textContains(HEDGE))) { "the forecast step never appeared" }
        SystemClock.sleep(1200) // let the 600 ms bloom settle
        shot("p6-forecast-onboarding")
        scenario.close()
    }

    /** Scrolls the Hub to the forecast card (bottom) and captures it. */
    private fun shotForecastCard(name: String) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        TestNav.awaitSurface(scenario, "hub")
        SystemClock.sleep(1500)
        val ui = device()
        // Swipe up until the footer under the forecast card is on screen.
        for (attempt in 0 until 6) {
            if (ui.hasObject(By.textContains("Computed on your device"))) break
            ui.swipe(540, 800, 540, 300, 12)
            SystemClock.sleep(500)
        }
        SystemClock.sleep(800)
        shot(name)
        scenario.close()
    }

    /**
     * Six earlier days, three quick-add entries each (~800 kcal/day), written
     * through the real diary door — intake scalars the day projection picks
     * up exactly like manual logs. Then a fresh today re-weigh: the onboarding
     * anchor trend event (82.0, captured 07:12) would otherwise sort after the
     * series' morning trend and read as today's trend — the re-weigh appends
     * the honest current EWMA as the day's LAST trend event.
     */
    private fun extendDiaryWeek(profileId: String): Unit =
        runBlocking {
            val clock = koin().get<ClockPort>()
            val zone = TimeZone.currentSystemDefault()
            val today = DayBoundary.epochDay(clock.now(), zone)
            val diary = koin().get<DiaryRepository>()
            val weighIns = koin().get<WeighInRepository>()
            val now = clock.now()
            val meals = listOf(MealSlot.BREAKFAST to 420.0, MealSlot.LUNCH to 280.0, MealSlot.DINNER to 100.0)
            for (offset in 7..12) {
                for ((slot, kcal) in meals) {
                    diary.logEntry(
                        entry =
                            NewDiaryEntry(
                                profileId = profileId,
                                dayEpochDay = today - offset,
                                mealSlot = slot,
                                foodItemId = null,
                                textHint = "quick-add",
                                quantity = 1.0,
                                unit = "serving",
                                kcalOnly = kcal,
                                enteredVia = EntryVia.QUICK_ADD,
                            ),
                        at = now,
                    )
                }
            }
            weighIns.appendWeighIn(
                profileId = profileId,
                dayEpochDay = today,
                weightKg = 77.0,
                capturedAt = now,
                source = MeasurementSource.MANUAL,
            )
        }

    /** Mirrors the Hub's gate: F07 quality UPDATING over the trailing window. */
    private fun forecastRunsMeasured(profileId: String): Boolean =
        runBlocking {
            val clock = koin().get<ClockPort>()
            val zone = TimeZone.currentSystemDefault()
            val today = DayBoundary.epochDay(clock.now(), zone)
            val projection = koin().get<DayProjectionRepository>()
            val from = today - ConstantsRegistry.QUALITY_WINDOW_DAYS + 1
            val views = projection.range(profileId, from, today).getOrNull().orEmpty()
            val energyDays =
                views.map {
                    EnergyDay(
                        epochDay = it.dayEpochDay,
                        intakeKcal = it.intakeKcal?.value,
                        trendWeightKg = it.trendWeightKg?.value,
                    )
                }
            EnergyEngine.quality(energyDays, today) is EngineState.Updating
        }

    private fun clickWhen(
        ui: UiDevice,
        text: String,
    ): Boolean {
        val node = ui.findObjects(By.textContains(text)).firstOrNull() ?: return false
        node.click()
        return true
    }

    private fun shot(name: String) {
        OnboardingRobot.shell("screencap -p /sdcard/$name.png")
        SystemClock.sleep(150)
    }

    private fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun koin(): org.koin.core.Koin =
        org.koin.core.context.GlobalContext
            .get()

    private companion object {
        /** The single hedge — only the forecast step shows it. */
        const val HEDGE: String = "an estimate, not a promise"
    }
}
