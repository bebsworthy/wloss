package app.wlo.app

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
 * five-tab navigation bar (R-D2), tab switching, and the provenance-chip demo
 * (criterion 4 end-to-end: measured / derived / held all rendered through
 * ProvenanceChip).
 */
@RunWith(AndroidJUnit4::class)
public class WloShellTest {
    @get:Rule
    public val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    public fun launch_rendersDarkWithFiveTabs() {
        composeTestRule.waitForIdle()
        assertMostlyDark()
        for (tab in listOf("hub", "plan", "insights", "archive", "digestion")) {
            composeTestRule.onNodeWithTag("tab-$tab").assertIsDisplayed()
        }
    }

    @Test
    public fun tap_eachTab_showsItsPlaceholder() {
        for (route in listOf("plan", "insights", "archive", "digestion")) {
            composeTestRule.onNodeWithTag("tab-$route").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("title-$route").assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("tab-hub").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Morning").assertIsDisplayed()
    }

    @Test
    public fun hub_showsMeasuredDerivedAndHeldChips() {
        // Chip words (label style, lowercase per DESIGN-SYSTEM §2).
        composeTestRule.onNodeWithText("measured").assertIsDisplayed()
        // Values render in tabular figures with unit glyphs (R-D10/R-D12).
        // A value appears twice by design — big stat + inside its chip — so
        // existence is checked on the collection.
        composeTestRule.onAllNodesWithText("82.4 kg").onFirst().assertExists()
        composeTestRule.onNodeWithText("derived").assertExists()
        composeTestRule.onAllNodesWithText("2,410 kcal").onFirst().assertExists()
        composeTestRule.onNodeWithText("held").assertExists()
        composeTestRule.onAllNodesWithText("1,900 kcal").onFirst().assertExists()
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
