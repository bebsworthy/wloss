package app.wlo.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.assertEquals

/** Polls + URI helpers shared by the M3 instrumented tests. */
public object TestNav {
    /** Concrete URI for a registry pattern ({arg} placeholders become dummies). */
    public fun concreteUri(uriPattern: String): String =
        uriPattern
            .replace("{entry}", "test-entry")
            .replace("{slot}", "lunch")
            .replace("{proposal}", "p1")

    /** Delivers a wlo:// link to the running activity (singleTask re-delivery). */
    public fun deliver(
        scenario: ActivityScenario<MainActivity>,
        uri: String,
    ) {
        scenario.onActivity { activity ->
            activity.deliverNewIntentForVerification(
                Intent(activity, MainActivity::class.java).setData(Uri.parse(uri)),
            )
        }
    }

    /**
     * Delivers a wlo:// link under a COMPOSE RULE and polls for the route
     * while PUMPING the test frame clock. The compose test rule parks the
     * activity's MonotonicFrameClock between test actions, so the intent's
     * `LaunchedEffect(newIntent) → handleDeepLink` recomposition never runs
     * on its own — under `am start`/in-process re-delivery the route would
     * stay on the start destination forever (the app is correct: rule-free
     * tests and production frames deliver instantly). Advancing the test
     * clock lets the pending recomposition run, exactly like the compose
     * APIs do for click-driven navigation.
     */
    public fun deliverPumpingClock(
        rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        uri: String,
        expected: String,
    ) {
        deliver(rule.activityRule.scenario, uri)
        awaitRoutePumpingClock(rule, expected)
    }

    /** [awaitRoute], pumping the compose test frame clock while polling. */
    public fun awaitRoutePumpingClock(
        rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
        expected: String,
    ) {
        val scenario = rule.activityRule.scenario
        val clock = rule.mainClock
        val wasAutoAdvance = clock.autoAdvance
        clock.autoAdvance = false
        try {
            val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
            var actual: String? = null
            while (SystemClock.elapsedRealtime() < deadline) {
                clock.advanceTimeBy(POLL_MS)
                scenario.onActivity { activity -> actual = activity.currentDestinationForVerification }
                if (actual == expected) return
                SystemClock.sleep(POLL_MS / 2)
            }
            assertEquals(expected, actual)
            return
        } finally {
            clock.autoAdvance = wasAutoAdvance
        }
    }

    /** Polls the activity's destination hook until the expected nav route shows. */
    public fun awaitRoute(
        scenario: ActivityScenario<MainActivity>,
        expected: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        var actual: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> actual = activity.currentDestinationForVerification }
            if (actual == expected) return
            SystemClock.sleep(POLL_MS)
        }
        assertEquals(expected, actual)
    }

    /** Polls WHAT the hub route renders ("onboarding" while fresh, "hub" after). */
    public fun awaitSurface(
        scenario: ActivityScenario<MainActivity>,
        expected: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        var actual: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> actual = activity.currentSurfaceForVerification }
            if (actual == expected) return
            SystemClock.sleep(POLL_MS)
        }
        assertEquals(expected, actual)
    }

    /** Polls until a compose node with [tag] (or [alternativeTag]) exists. */
    public fun awaitTag(
        rule: ComposeTestRule,
        tag: String,
        alternativeTag: String? = null,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            // The activity may still be composing when the first poll lands
            // (the test starts before the first frame on slow processes) —
            // "no compose hierarchies" is retryable within the window.
            val found =
                runCatching {
                    rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
                        (
                            alternativeTag != null &&
                                rule.onAllNodesWithTag(alternativeTag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
                        )
                }.getOrDefault(false)
            if (found) return
            Thread.sleep(POLL_MS)
        }
        val route = currentRoute(rule)
        val texts =
            OnboardingRobot
                .device()
                .findObjects(
                    androidx.test.uiautomator.By
                        .textContains(" "),
                ).mapNotNull { it.text }
                .filter { it.length in 3..40 }
                .distinct()
                .take(12)
        error("node \"$tag\" never appeared; route=$route; screen texts: $texts")
    }

    /** Polls until a text exists; on timeout dumps the visible texts. */
    public fun awaitText(
        rule: ComposeTestRule,
        text: String,
    ) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(POLL_MS)
        }
        val texts =
            OnboardingRobot
                .device()
                .findObjects(
                    androidx.test.uiautomator.By
                        .textContains(" "),
                ).mapNotNull { it.text }
                .filter { it.length in 3..60 }
                .distinct()
                .take(25)
        error("text \"$text\" never appeared; screen texts: $texts")
    }

    private fun currentRoute(rule: ComposeTestRule): String? {
        val android = rule as? AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
        var route: String? = null
        android?.activityRule?.scenario?.onActivity { activity -> route = activity.currentDestinationForVerification }
        return route
    }

    private const val TIMEOUT_MS: Long = 20_000
    private const val POLL_MS: Long = 150
}
