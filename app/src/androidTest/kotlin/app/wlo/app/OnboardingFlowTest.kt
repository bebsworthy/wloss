package app.wlo.app

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F01 acceptance on the emulator (F01 §1): the full wizard completes offline
 * in under 3 minutes and lands on the Hub with the R-A5 ESTIMATED forecast;
 * the pace wall refuses in code, with the counter-offer card. Draft resume
 * lives in OnboardingResumeTest (it needs a rule-free activity lifecycle).
 */
@RunWith(AndroidJUnit4::class)
public class OnboardingFlowTest {
    @get:Rule
    public val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    public fun happyPath_completesUnderThreeMinutes_andLandsOnHubWithEstimatedForecast() {
        val startedAt = SystemClock.elapsedRealtime()
        val rule = composeTestRule

        rule.onNodeWithTag("onboarding-step-WELCOME").assertIsDisplayed()
        rule.driveToHub()

        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        assertTrue(
            "the full wizard took ${elapsedMs}ms — must stay under the 3-minute budget",
            elapsedMs < THREE_MINUTES_MS,
        )

        // Hub after onboarding: trend, budget, forecast + the provenance pill
        // (R-A5; WLO-0034 — one chip, the header's, lowercase per the chip
        // vocabulary; the hand-built ESTIMATED stamp is gone).
        rule.onNodeWithTag("hub-trend-card").assertIsDisplayed()
        val forecastRendered =
            rule
                .onAllNodesWithTag("hub-forecast-card", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        assertTrue("the cold-start forecast card must render on the fresh Hub", forecastRendered)
        rule.onAllNodesWithText("estimated").onFirst().assertExists()
    }

    @Test
    public fun goalStep_paceAtTheCap_showsTheWallCounterOffer() {
        val rule = composeTestRule
        rule.onNodeWithTag("onboarding-step-WELCOME").assertIsDisplayed()
        rule.onNodeWithTag("onboarding-next").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("onboarding-step-GOAL").assertIsDisplayed()

        // Drive the stats so the code-level wall lands UNDER the chosen pace:
        // a lighter body burns less, and the calorie floor then forbids the
        // default pace — the plan must counter-offer, never silently accept
        // (F01 §4 fallback b: the refusal is in code, not copy).
        repeat(STAT_TAPS) {
            rule.onNodeWithTag("onboarding-current-weight-minus").performClick()
        }
        rule.waitForIdle()

        rule
            .onNodeWithText("The wall", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private companion object {
        const val THREE_MINUTES_MS: Long = 180_000

        /** 82 → 74 kg: at 74 kg the floor forbids 0.5 %/wk — the wall is live. */
        const val STAT_TAPS: Int = 16
    }
}
