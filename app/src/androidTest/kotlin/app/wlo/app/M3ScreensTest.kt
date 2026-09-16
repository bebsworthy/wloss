package app.wlo.app

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import app.wlo.app.di.FixedClock
import app.wlo.app.navigation.WloTabs
import app.wlo.core.common.ClockPort
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * M3 screenshot harness: drives the real app through each PART-B surface and
 * screencaps to /sdcard (pulled to /tmp/wlo-setup/m3-*.png by the driver).
 * Runs in the suite as a reachability walk — the behavioral assertions live
 * in [M3DiaryHubTest], [M3WeighInTest] and [M3DeepLinkSweepTest].
 */
@RunWith(AndroidJUnit4::class)
public class M3ScreensTest {
    // The hub camera tile opens the REAL capture flow (M4 PART B) — grant the
    // camera before launch so no system dialog blocks the composition.
    @get:Rule(order = 1)
    public val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.CAMERA)

    @get:Rule(order = 2)
    public val rule = createAndroidComposeRule<MainActivity>()

    @Test
    public fun captureDaySurfaces() {
        val ui = OnboardingRobot.device()
        SeedingRobot.onboardAndSeedWeek()
        TestNav.awaitTag(rule, "hub-trend-card")
        shot("m3-hub-morning")

        // Diary day view (seeded).
        rule.onNodeWithTag("hub-open-diary", useUnmergedTree = true).performScrollTo().performClick()
        TestNav.awaitTag(rule, "f02-diary-title")
        shot("m3-diary-day")

        // Provenance + revision sheet.
        rule.onAllNodesWithText("Golden oats").onFirst().performClick()
        TestNav.awaitTag(rule, "f02-entry-sheet")
        openEditor()
        rule.onNodeWithTag("f02-edit-field", useUnmergedTree = true).performTextClearance()
        rule.onNodeWithTag("f02-edit-field", useUnmergedTree = true).performTextInput("70")
        rule.onNodeWithTag("f02-edit-save", useUnmergedTree = true).performClick()
        TestNav.awaitTag(rule, "f02-revision-list")
        shot("m3-provenance-revisions")
        backTo(ui, "hub-quick-actions") // IME → sheet → diary pops

        // Ladder: the hub camera tile now opens the REAL capture flow (M4
        // PART B); the manual ladder is its R-U15 twin, one tap away — which
        // this also exercises.
        rule.onNodeWithTag("hub-log-food", useUnmergedTree = true).performScrollTo().performClick()
        TestNav.awaitTag(rule, "f02-capture-viewfinder")
        rule.onNodeWithTag("f02-capture-manual", useUnmergedTree = true).performClick()
        TestNav.awaitTag(rule, "f02-search-field")
        rule.onNodeWithTag("f02-search-field").performTextInput("oats")
        TestNav.awaitTag(rule, "f02-search-results")
        shot("m3-search-results")
        rule.onAllNodesWithText("Golden oats").onFirst().performClick()
        TestNav.awaitTag(rule, "f02-portion-sheet")
        shot("m3-portion-sheet")
        ui.pressBack()

        // Custom food with the energy-density rail visible.
        rule.onNodeWithTag("f02-open-custom", useUnmergedTree = true).performScrollTo().performClick()
        TestNav.awaitTag(rule, "f02-custom-sheet")
        rule.onNodeWithTag("f02-custom-name", useUnmergedTree = true).performTextInput("Butter, clarified")
        // Type over the rail (900 kcal/100 g is the physical ceiling): the note
        // appears live, before any save; re-type once if the first input raced
        // the sheet's composition.
        var railVisible = false
        for (attempt in 0 until 2) {
            rule.onNodeWithTag("f02-custom-kcal", useUnmergedTree = true).performTextClearance()
            rule.onNodeWithTag("f02-custom-kcal", useUnmergedTree = true).performTextInput("1200")
            val deadline = System.currentTimeMillis() + 3_000
            while (System.currentTimeMillis() < deadline) {
                if (rule.onAllNodesWithText("above any real food's ceiling", substring = true).fetchSemanticsNodes().isNotEmpty()) {
                    railVisible = true
                    break
                }
                Thread.sleep(150)
            }
            if (railVisible) break
        }
        assertTrue("the energy-density rail note must appear while typing", railVisible)
        ui.pressBack() // one back over the numpad closes only the keyboard
        Thread.sleep(500)
        shot("m3-custom-food-rail")
        backTo(ui, "hub-quick-actions")

        // Weigh-in sheet, the outlier prompt, the trend chart + tuner, math docs.
        rule.onNodeWithTag("hub-trend-card", useUnmergedTree = true).performScrollTo().performClick()
        TestNav.awaitTag(rule, "f06-title")
        rule.onNodeWithTag("f06-open-sheet", useUnmergedTree = true).performClick()
        TestNav.awaitTag(rule, "f06-weighin-sheet")
        shot("m3-weighin-sheet")
        rule.onNodeWithTag("f06-weight-field", useUnmergedTree = true).performTextClearance()
        rule.onNodeWithTag("f06-weight-field", useUnmergedTree = true).performTextInput("95.0")
        rule.onNodeWithTag("f06-save-weighin", useUnmergedTree = true).performClick()
        TestNav.awaitTag(rule, "f06-outlier-banner")
        shot("m3-outlier-prompt")
        rule.onNodeWithTag("f06-outlier-keep", useUnmergedTree = true).performClick()
        dismissIme(ui) // ESC hides the keyboard, never a nav layer
        scrollToTag("f06-trend-card")
        Thread.sleep(600)
        shot("m3-trend-tuner")
        rule.onNodeWithTag("f06-open-math", useUnmergedTree = true).performScrollTo().performClick()
        TestNav.awaitTag(rule, "f06-math-title")
        shot("m3-math-doc")
        backTo(ui, "hub-quick-actions")

        // Documented stubs — reached by rail taps only (deep links in a
        // rule-based test hang the rule's teardown; the M1 note's trap).
        rule.onNodeWithTag("hub-digestion", useUnmergedTree = true).performScrollTo().performClick()
        TestNav.awaitTag(rule, "title-stub")
        shot("m3-stub-gut")
        backTo(ui, "hub-quick-actions")
        SystemClock.sleep(500)
    }

    @Test
    public fun captureEveningHub() {
        SeedingRobot.onboardAndSeedWeek()
        // The evening phase is captured by overriding the bound clock BEFORE
        // the Hub composes — pinned to TODAY 19:30 local so the seeded week
        // (authored relative to the real clock, WLO-0049) stays coherent. The
        // Day Model rules themselves are unchanged and engine-golden-tested.
        val zone = TimeZone.currentSystemDefault()
        val today =
            kotlin.time.Clock.System
                .now()
                .toLocalDateTime(zone)
                .date
        val evening = LocalDateTime(today, LocalTime.fromSecondOfDay(19 * 3600 + 30 * 60)).toInstant(zone)
        loadKoinModules(
            module {
                single<ClockPort> { FixedClock(evening) }
            },
        )
        TestNav.awaitTag(rule, "hub-close-card")
        shot("m3-hub-evening")
    }

    @Test
    public fun hubSurface_activityRecreationRefreshesTheLifecycleSnapshot() {
        SeedingRobot.onboardAndSeedWeek()
        rule.onAllNodesWithContentDescription("Hub", useUnmergedTree = true).onFirst().performClick()
        TestNav.awaitTag(rule, "hub-trend-card")

        rule.activityRule.scenario.recreate()

        TestNav.awaitTag(rule, "hub-trend-card")
    }

    private fun shot(name: String) {
        OnboardingRobot.shell("screencap -p /sdcard/$name.png")
    }

    /**
     * Opens the inline correct-editor, tolerating the sheet's open animation:
     * polls for the edit field and re-taps "correct" once if it has not
     * appeared (the first tap can land on the pre-recomposition node).
     */
    private fun openEditor() {
        var attempts = 0
        while (attempts < 3) {
            attempts += 1
            rule.onNodeWithTag("f02-entry-correct", useUnmergedTree = true).performClick()
            for (poll in 0 until 10) {
                if (rule.onAllNodesWithTag("f02-edit-field", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
                    return
                }
                Thread.sleep(150)
            }
        }
        error("the correct-editor never opened")
    }

    /**
     * Backs through the IME, bottom-sheet and nested-route layers until the
     * Hub owns the shell again. Nested destinations deliberately replace the
     * Material 3 navigation bar with an app bar, so a tab is not an available
     * escape hatch while those layers are being dismissed.
     */
    private fun backTo(
        ui: UiDevice,
        hubTag: String,
    ) {
        dismissIme(ui)
        SystemClock.sleep(BACK_SETTLE_MS)
        repeat(MAX_BACK_STEPS) {
            if (currentRoute() == WloTabs.HUB) {
                TestNav.awaitTag(rule, hubTag)
                return
            }

            val sheetVisible =
                listOf("f02-entry-sheet", "f02-custom-sheet", "f02-portion-sheet")
                    .any { tag ->
                        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                    }
            if (sheetVisible) {
                ui.pressBack()
            } else {
                val navigateUp = rule.onAllNodesWithContentDescription("Navigate up", useUnmergedTree = true)
                check(navigateUp.fetchSemanticsNodes().isNotEmpty()) {
                    "nested route has neither a dismissible sheet nor Navigate up; route=${currentRoute()}"
                }
                navigateUp.onFirst().performClick()
            }
            SystemClock.sleep(BACK_SETTLE_MS)
        }

        check(currentRoute() == WloTabs.HUB) {
            "back navigation did not return to the Hub; route=${currentRoute()}"
        }
        TestNav.awaitTag(rule, hubTag)
    }

    private fun currentRoute(): String? {
        var route: String? = null
        rule.activityRule.scenario.onActivity { activity -> route = activity.currentDestinationForVerification }
        return route
    }

    /** Hides the keyboard with ESC (a back press could pop a nav layer instead). */
    private fun dismissIme(ui: UiDevice) {
        ui.executeShellCommand("input keyevent 111")
    }

    /** Scrolls to a tag once it exists (post-navigation recomposition settles live). */
    private fun scrollToTag(tag: String) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val node = rule.onAllNodesWithTag(tag, useUnmergedTree = true)
            if (node.fetchSemanticsNodes().isNotEmpty()) {
                node.onFirst().performScrollTo()
                return
            }
            Thread.sleep(200)
        }
        error("scroll target \"$tag\" never appeared")
    }

    private companion object {
        const val TIMEOUT_MS: Long = 15_000
        const val POLL_MS: Long = 200L
        const val BACK_SETTLE_MS: Long = 350L
        const val MAX_BACK_STEPS: Int = 6
    }
}
