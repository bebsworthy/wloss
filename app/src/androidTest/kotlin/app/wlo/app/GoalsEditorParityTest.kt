package app.wlo.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device acceptance for WLO-0074's Settings entry and honest initial state. */
@RunWith(AndroidJUnit4::class)
public class GoalsEditorParityTest {
    @get:Rule
    public val rule = createAndroidComposeRule<MainActivity>()

    @Before
    public fun seedAndRecreate() {
        SeedingRobot.onboardAndSeedWeek()
        rule.activityRule.scenario.recreate()
    }

    @Test
    public fun settingsGoalEditorUsesGlobalUnitsAndWithholdsDateUntilSafetyAnswers() {
        TestNav.deliverPumpingClock(rule, "wlo://settings", "app/settings")
        rule.onNodeWithTag("settings-unit-lb").performScrollTo().performClick()
        rule.onNodeWithTag("settings-open-goals").performScrollTo().performClick()
        TestNav.awaitRoutePumpingClock(rule, "f01/studio")

        rule.onNodeWithTag("f01-goals-title").assertIsDisplayed()
        assertTrue(rule.onAllNodesWithText("lb", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithText("A few answers needed", substring = true).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithTag("f01-goals-forecast-card", useUnmergedTree = true).assertCountEquals(0)
    }
}
