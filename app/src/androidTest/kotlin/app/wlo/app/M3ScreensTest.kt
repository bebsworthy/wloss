package app.wlo.app

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import app.wlo.app.di.FixedClock
import app.wlo.core.common.ClockPort
import kotlinx.datetime.Instant
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
    @get:Rule
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

        // Ladder: search results → portion sheet (rail quick action, no intents).
        rule.onAllNodesWithText("log food").onFirst().performClick()
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
                if (rule.onAllNodesWithText("above the physical ceiling", substring = true).fetchSemanticsNodes().isNotEmpty()) {
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
        rule.onAllNodesWithText("weigh in").onFirst().performClick()
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
        ui.pressBack()

        // Documented stubs — reached by rail taps only (deep links in a
        // rule-based test hang the rule's teardown; the M1 note's trap).
        rule.onNodeWithTag("tab-hub").performClick()
        TestNav.awaitTag(rule, "hub-quick-actions")
        rule.onAllNodesWithText("digestion").onFirst().performClick()
        TestNav.awaitTag(rule, "title-stub")
        shot("m3-stub-gut")
        rule.onNodeWithTag("tab-hub").performClick()
        TestNav.awaitTag(rule, "hub-quick-actions")
        SystemClock.sleep(500)
    }

    @Test
    public fun captureEveningHub() {
        SeedingRobot.onboardAndSeedWeek()
        // The demo clock is frozen at 07:12 (M1 seam); the evening phase is
        // captured by overriding the bound clock BEFORE the Hub composes —
        // the Day Model rules themselves are unchanged and engine-golden-tested.
        loadKoinModules(
            module {
                single<ClockPort> { FixedClock(Instant.parse("2026-09-08T19:30:00Z")) }
            },
        )
        TestNav.awaitTag(rule, "hub-close-card")
        shot("m3-hub-evening")
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
     * Backs through the IME / bottom-sheet layers (whichever hold) and, once
     * they are gone, pops the surface by tab; waits for [hubTag] on the Hub.
     */
    private fun backTo(
        ui: UiDevice,
        hubTag: String,
    ) {
        ui.pressBack()
        ui.pressBack()
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (rule.onAllNodesWithTag(hubTag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(POLL_MS)
        }
        rule.onNodeWithTag("tab-hub").performClick()
        TestNav.awaitTag(rule, hubTag)
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
    }
}
