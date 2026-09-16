package app.wlo.app

import android.os.SystemClock
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Weight-first acceptance: onboarding completes offline without creating a
 * Diet Plan, and optional goals never leak an ungated forecast date.
 */
@RunWith(AndroidJUnit4::class)
public class OnboardingFlowTest {
    @get:Rule
    public val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    public fun happyPath_completesUnderThreeMinutes_andLandsOnUsefulWeight() {
        val startedAt = SystemClock.elapsedRealtime()
        val rule = composeTestRule

        rule.onNodeWithTag("weight-first-onboarding").assertIsDisplayed()
        rule.driveToHub()

        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        assertTrue(
            "the full wizard took ${elapsedMs}ms — must stay under the 3-minute budget",
            elapsedMs < THREE_MINUTES_MS,
        )

        // A raw first weight is useful without a Diet Plan. Forecast and
        // calorie-plan cards stay absent until the shared safety contract and
        // required facts support them.
        rule.onNodeWithTag("hub-trend-card").assertIsDisplayed()
        rule.onAllNodesWithTag("hub-forecast-card", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    public fun gainGoal_isSupportedButDoesNotShowAConfidentDate() {
        val rule = composeTestRule
        rule.onNodeWithTag("weight-first-next").performClick()
        rule.onNodeWithTag("weight-first-unit-kg").performClick()
        rule.onNodeWithTag("weight-first-next").performClick()
        rule.onNodeWithText("Gain").performClick()
        rule.onNodeWithText("Gain goals are supported", substring = true).assertIsDisplayed()
        rule.onAllNodesWithText("forecast date", substring = true).assertCountEquals(0)
    }

    @Test
    public fun poundChoice_drivesOnboarding_andRemainsTheOnlySettingsSwitch() {
        val rule = composeTestRule
        rule.onNodeWithTag("weight-first-next").performClick()
        rule.onNodeWithTag("weight-first-unit-lb").performClick().assertIsSelected()
        rule.onNodeWithTag("weight-first-next").performClick()
        rule.onNodeWithTag("weight-first-next").performClick()
        rule.onNodeWithTag("weight-first-source-manual").performClick()
        rule.onNodeWithTag("weight-first-weight").performTextInput("180.8")
        rule.onNodeWithTag("weight-first-finish").performClick()
        TestNav.awaitRoutePumpingClock(rule, "f06/weight")
        rule.onNodeWithContentDescription("Hub", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("hub-settings").performClick()
        TestNav.awaitRoutePumpingClock(rule, "app/settings")
        rule.onNodeWithTag("settings-unit-lb").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("settings-unit-kg").performClick().assertIsSelected()
    }

    private companion object {
        const val THREE_MINUTES_MS: Long = 180_000
    }
}
