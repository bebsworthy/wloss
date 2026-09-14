package app.wlo.app

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * Shell acceptance on the API 29 emulator: dark-first rendering (R-D1), the
 * five-tab navigation bar (R-D2), tab switching, and the provenance-chip path
 * end-to-end on the REAL hub (F01 finishes into it: measured/derived trend +
 * budget chips, the R-A5 ESTIMATED forecast). Each test starts from a cleared,
 * cold install — the wizard owns the first frame until a plan exists.
 */
@RunWith(AndroidJUnit4::class)
public class WloShellTest {
    @get:Rule
    public val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    public fun freshLaunch_rendersDarkWizard_beforeAnyPlanExists() {
        composeTestRule.waitForIdle()
        assertMostlyDark()
        composeTestRule.onNodeWithTag("onboarding-step-WELCOME").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding-skip").assertIsDisplayed()
    }

    @Test
    public fun afterOnboarding_showsFiveTabs_andTabSwitching() {
        composeTestRule.driveToHub()
        // The bar is the WloBottomBar atom (M3 NavigationBar) — tabs carry no
        // per-tab test tags; each item's icon is labelled with the tab name.
        // The icon's description lives on the unmerged node (the NavigationBar
        // item's merged semantics swallow it), so query that tree.
        for (tab in listOf("Hub", "Plan", "Insights", "Archive", "Digestion")) {
            composeTestRule.onNodeWithContentDescription(tab, useUnmergedTree = true).assertIsDisplayed()
        }
        for (tab in listOf("Plan", "Insights", "Archive", "Digestion")) {
            composeTestRule.onNodeWithContentDescription(tab, useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("title-${tab.lowercase()}").assertIsDisplayed()
        }
        composeTestRule.onNodeWithContentDescription("Hub", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hub-trend-card").assertIsDisplayed()
    }

    @Test
    public fun hub_afterOnboarding_rendersDerivedAndEstimatedChips() {
        composeTestRule.driveToHub()

        // Trend + budget arrive provenance-chipped from the day projection
        // (derived); the R-A5 cold-start forecast is estimated until the F07
        // quality gate goes UPDATING. WLO-0034: the card carries ONE provenance
        // pill — the standard chip in its header slot (the hand-built
        // ESTIMATED stamp is gone). WLO-0033 wave 2: the budget's chip is the
        // mock's ⓘ on the "of N kcal" target line — its merged description
        // carries the provenance word (the header never does, R-D12 round 8).
        composeTestRule.onNodeWithTag("hub-trend-card").assertIsDisplayed()
        composeTestRule
            .onAllNodesWithContentDescription("derived", substring = true, useUnmergedTree = true)
            .onFirst()
            .assertExists()
        composeTestRule.onAllNodesWithText("estimated").onFirst().assertExists()
        // WloCardHeader uppercases the source string; the hero numeral renders
        // WITHOUT the unit (defect 14) — the start weight stands in alone.
        composeTestRule.onAllNodesWithText("WEIGHT TREND").onFirst().assertExists()
        composeTestRule.onAllNodesWithText("82.0").onFirst().assertExists()
    }

    /** Grid-luminance check: the window must render the canonical dark palette. */
    private fun assertMostlyDark() {
        val bitmap = composeTestRule.onRoot().captureToImage()
        val pixelMap = bitmap.toPixelMap()
        var dark = 0
        var total = 0
        val stepX = (bitmap.width / 6.0).roundToInt().coerceAtLeast(1)
        val stepY = (bitmap.height / 10.0).roundToInt().coerceAtLeast(1)
        var x = stepX
        while (x < bitmap.width) {
            var y = stepY
            while (y < bitmap.height) {
                val pixel = pixelMap[x, y]
                val luminance = 0.2126f * pixel.red + 0.7152f * pixel.green + 0.0722f * pixel.blue
                total++
                if (luminance < 0.30f) dark++
                y += stepY
            }
            x += stepX
        }
        val darkFraction = dark.toFloat() / total
        assertTrue(
            "expected a dark-first shell, dark fraction was $darkFraction",
            darkFraction > 0.7f,
        )
    }
}
