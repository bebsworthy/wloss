package app.wlo.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.GenerateWeekPlan
import app.wlo.core.data.NewPantryItem
import app.wlo.core.data.PlannerRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * M5 screenshot sweep (rule-free, like the M3 stub sweep): walks the real
 * plan→shop→pantry loop on a seeded install and screencaps every surface the
 * review reads — dark, tnum, badges honest, zero guilt. Images land on
 * /sdcard/m5-*.png and are pulled to /tmp/wlo-setup/ by the operator.
 */
@RunWith(AndroidJUnit4::class)
public class M5ScreensTest {
    private val koin get() = GlobalContext.get()
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

    private fun today(): Long {
        val clock: ClockPort = koin.get()
        return DayBoundary.epochDay(clock.now(), TimeZone.currentSystemDefault())
    }

    private var activeScenario: ActivityScenario<MainActivity>? = null

    private fun currentRoute(): String? {
        var route: String? = null
        activeScenario?.onActivity { activity -> route = activity.currentDestinationForVerification }
        return route
    }

    private fun deliver(
        scenario: ActivityScenario<MainActivity>,
        uri: String,
    ) {
        scenario.onActivity { activity ->
            activity.deliverNewIntentForVerification(
                Intent(activity, MainActivity::class.java).setData(Uri.parse(uri)),
            )
        }
    }

    private fun awaitText(
        text: String,
        timeoutMs: Long = 20_000,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (device().findObjects(By.textContains(text)).isNotEmpty()) return
            SystemClock.sleep(150)
        }
    }

    private fun tapText(text: String): Boolean {
        val node = device().findObjects(By.text(text)).firstOrNull() ?: return false
        node.click()
        return true
    }

    private fun tapTextContaining(text: String): Boolean {
        val node = device().findObjects(By.textContains(text)).firstOrNull() ?: return false
        node.click()
        return true
    }

    @Test
    public fun captureM5Surfaces() {
        val planner: PlannerRepository = koin.get()
        val clock: ClockPort = koin.get()
        val plan =
            runBlocking {
                val result =
                    planner.generateWeek(
                        GenerateWeekPlan(
                            profileId = seeded.profileId,
                            startDayEpochDay = today(),
                            days = 7,
                            seed = 9L,
                        ),
                    )
                (result as WloResult.Ok).value
            }

        // Pantry stock BEFORE the first list build so the R-S5 prompt fires.
        runBlocking {
            val groceryRepo = koin.get<app.wlo.core.data.GroceryRepository>()
            val eggs = groceryRepo.ensure(seeded.profileId, "eggs", "x", clock.now()).getOrNull()!!
            koin.get<app.wlo.core.data.PantryRepository>().upsert(
                NewPantryItem(
                    profileId = seeded.profileId,
                    groceryItemId = eggs.id,
                    name = "eggs",
                    qty = 5.0,
                    unit = "x",
                    isStaple = true,
                ),
                clock.now(),
            )
        }

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activeScenario = scenario
        TestNav.awaitSurface(scenario, "hub")

        // --- the R-S5 one-time prompt + the reconciliation banner (list) ------
        deliver(scenario, "wlo://list")
        TestNav.awaitRoute(scenario, "f04/list")
        awaitText("Build list")
        tapTextContaining("Build list")
        awaitText("DEDUCT WHAT YOU HAVE AT HOME?")
        shot("m5-rs5-prompt")
        tapText("Turn it on")
        awaitText("Your checks are safe")
        shot("m5-reconciliation-banner")
        tapText("Got it")
        SystemClock.sleep(300)
        shot("m5-shopping-list")

        // --- the plan tab: week grid, why-this-plan, adherence ---------------
        deliver(scenario, "wlo://plan")
        TestNav.awaitRoute(scenario, "plan")
        awaitText("Deal again")
        SystemClock.sleep(700)
        shot("m5-week-grid")

        // Why-this-plan lives in the week header card (top of the scroll).
        shot("m5-why-plan")

        // Scroll to the adherence strip (past the seven day sections).
        repeat(4) {
            device().swipe(360, 1200, 360, 250, 15)
            SystemClock.sleep(350)
        }
        awaitText("ADHERENCE")
        shot("m5-adherence")
        device().swipe(360, 400, 360, 1300, 12)
        SystemClock.sleep(500)

        // --- slot detail sheet ------------------------------------------------
        // Scroll to the top (today's card sits right under the week header)
        // and tap its first slot row directly — the row is clickable and its
        // fixed position is stable for the seeded 1080x2400 emulator.
        device().swipe(360, 400, 360, 1500, 20)
        SystemClock.sleep(500)
        var openedSlot = false
        for (attempt in 0 until 4) {
            device().click(540, 1090)
            SystemClock.sleep(900)
            if (device().findObjects(By.text("Ate this")).isNotEmpty()) {
                openedSlot = true
                break
            }
            device().swipe(360, 1100, 360, 900, 8)
            SystemClock.sleep(400)
        }
        if (openedSlot) {
            shot("m5-slot-sheet")
            for (attempt in 0 until 5) {
                val swapNode =
                    device()
                        .findObjects(By.text("Swap"))
                        .firstOrNull { it.visibleCenter.y > device().displayHeight * 0.4 }
                swapNode?.click()
                SystemClock.sleep(900)
                if (device().findObjects(By.textContains("top swaps")).isNotEmpty()) {
                    SystemClock.sleep(500)
                    shot("m5-swap-sheet")
                    break
                }
            }
            // One back closes whichever sheet is open (the VM dismissed the
            // slot sheet when the swap sheet opened). Only go back when a
            // sheet exists — a bare back on the tab root would kill the
            // activity.
            if (device().findObjects(By.text("Ate this")).isNotEmpty() ||
                device().findObjects(By.textContains("top swaps")).isNotEmpty()
            ) {
                device().pressBack()
                SystemClock.sleep(500)
            }
        }

        // --- recipes segment + the R-U15 editor -------------------------------
        var inRecipes = false
        for (attempt in 0 until 4) {
            if (tapText("Recipes")) {
                awaitText("New recipe")
                inRecipes = true
                break
            }
            SystemClock.sleep(500)
        }
        if (inRecipes) {
            shot("m5-recipes")
            tapText("New recipe")
            awaitText("per-serving nutrition")
            SystemClock.sleep(700)
            shot("m5-recipe-create")
            tapText("Discard")
            SystemClock.sleep(500)
        }

        // --- pantry -----------------------------------------------------------
        deliver(scenario, "wlo://pantry")
        SystemClock.sleep(800)
        if (currentRoute() != "f04/pantry") deliver(scenario, "wlo://pantry")
        TestNav.awaitRoute(scenario, "f04/pantry")
        awaitText("INVENTORY")
        SystemClock.sleep(400)
        shot("m5-pantry")
        if (tapTextContaining("Add stock")) {
            awaitText("STOCK-TAKE")
            shot("m5-checkin")
            device().pressBack()
            SystemClock.sleep(300)
        }

        // --- export share sheet -----------------------------------------------
        deliver(scenario, "wlo://list")
        SystemClock.sleep(800)
        if (currentRoute() != "f04/list") deliver(scenario, "wlo://list")
        TestNav.awaitRoute(scenario, "f04/list")
        awaitText("Share text")
        if (tapText("Share text")) {
            SystemClock.sleep(1200)
            shot("m5-export-share")
            device().pressBack()
            SystemClock.sleep(300)
        }

        scenario.onActivity { it.finish() }
        SystemClock.sleep(500)
    }
}
