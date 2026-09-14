package app.wlo.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M3 acceptance (a): the 7-day seeded diary renders across Hub → diary —
 * budget/trend correct, the diary card's week-dots row carries one dot per
 * logged day of the CURRENT week (owner review WLO-0030 defect 12: the mock's
 * week dots, not a month heatmap), water lands as a drink entry, and every
 * entry's provenance sheet shows the correction history (F02 §5 / acceptance 5).
 */
@RunWith(AndroidJUnit4::class)
public class M3DiaryHubTest {
    @get:Rule
    public val rule = createAndroidComposeRule<MainActivity>()

    @Before
    public fun seed() {
        SeedingRobot.onboardAndSeedWeek()
    }

    @Test
    public fun seededWeek_hubRendersBudgetTrendWeekDots_andDiaryCarriesTheDay() {
        TestNav.awaitTag(rule, "hub-trend-card")

        // Budget from Targets — the calories ring card (WLO-0033 wave 2
        // anatomy) — and the forecast card (M2); the Day-Model hub is a longer
        // stack now — scroll to the lower cards.
        rule.onNodeWithTag("f10-ring-card", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("hub-forecast-card", useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        // The diary slice: the week-dots row marks the CURRENT week's logged
        // days (seeder days Sep 2–8; only Sep 7 + Sep 8 fall in this week).
        rule.onNodeWithTag("hub-diary-card", useUnmergedTree = true).performScrollTo()
        val loggedDots =
            rule
                .onAllNodesWithContentDescription("— logged", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size
        assertEquals("one filled dot per logged day of the current week", LOGGED_WEEK_DAYS, loggedDots)

        rule.onNodeWithTag("hub-open-diary").performScrollTo().performClick()
        TestNav.awaitTag(rule, "f02-diary-title")

        // Day total is hand-computable from the seeder: oats 223.2 + salmon
        // 270.4 + rye 150 + the even-day apple snack 46.8 + water 0 = 690.4.
        TestNav.awaitText(rule, "690 kcal")
        // Meal slots render (content-rendered, no empty states) — the slot
        // headers are WloCardHeaders, rendered uppercase; match case-blind.
        rule.onAllNodesWithText("breakfast", ignoreCase = true).onFirst().assertExists()
        rule.onAllNodesWithText("drinks", ignoreCase = true).onFirst().assertExists()
        // The day-status marker defaults to logged once entries exist.
        rule.onNodeWithTag("f02-day-status", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    public fun waterQuickAdd_landsAsADrinkEntry_andCorrectionHistoryIsVisible() {
        TestNav.awaitTag(rule, "hub-trend-card")
        rule.onNodeWithTag("hub-open-diary").performScrollTo().performClick()
        TestNav.awaitTag(rule, "f02-diary-title")

        // The one-tap water quick-add: a DRINK-slot, 0-kcal entry.
        rule.onNodeWithTag("f02-water-add").performClick()
        rule.onAllNodesWithText("water").onFirst().assertExists()

        // Open an entry's provenance sheet: the "how we got here" pattern.
        TestNav.awaitText(rule, "Golden oats")
        rule.onAllNodesWithText("Golden oats").onFirst().performClick()
        TestNav.awaitTag(rule, "f02-entry-sheet")
        // The sheet title is a WloCardHeader now (WLO-0032), rendered
        // uppercase — match case-blind, same as the slot headers above.
        rule.onAllNodesWithText("How we got here", ignoreCase = true).onFirst().assertExists()

        // Correct the portion: the prior version stays in the history (R-B8).
        rule.onNodeWithTag("f02-entry-correct").performClick()
        rule.onNodeWithTag("f02-edit-field").performTextClearance()
        rule.onNodeWithTag("f02-edit-field").performTextInput("70")
        rule.onNodeWithTag("f02-edit-save").performClick()
        TestNav.awaitTag(rule, "f02-revision-list")
        assertTrue(
            "the correction keeps its prior version",
            rule.onAllNodesWithText("version 1", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private companion object {
        /** The seeded week runs Sep 2–8; only Sep 7 + Sep 8 sit in the current week (Mon Sep 7–Sun Sep 13). */
        const val LOGGED_WEEK_DAYS: Int = 2
    }
}
