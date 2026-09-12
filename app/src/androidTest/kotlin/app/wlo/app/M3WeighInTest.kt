package app.wlo.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * M3 acceptance (b): weigh in twice in one day — both events stay listed
 * verbatim (R-B8), the trend matches a hand-rolled EWMA over the daily
 * scalars, the smoothing controls move the visible line, the outlier guard
 * asks keep-or-correct without dropping the event, and the math
 * documentation screen opens from the chart.
 */
@RunWith(AndroidJUnit4::class)
public class M3WeighInTest {
    @get:Rule
    public val rule = createAndroidComposeRule<MainActivity>()

    @Before
    public fun seed() {
        SeedingRobot.onboardAndSeedWeek()
    }

    @Test
    public fun doubleWeighIn_keepsBothEvents_andTrendMatchesHandRolledEwma() {
        awaitWeightSurface()

        // Weigh in #1 (today already carries the seeded 81.05 reading).
        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("79.6")
        rule.onNodeWithTag("f06-save-weighin").performClick()
        pollText("79.6 kg")

        // Weigh in #2: the post-bathroom-win re-weigh is normal data.
        rule.onNodeWithTag("f06-open-sheet").performClick()
        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("80.4")
        rule.onNodeWithTag("f06-save-weighin").performClick()
        pollText("80.4 kg")

        // Both events stay listed verbatim; the lowest is marked as the day's weight.
        assertTrue(
            "both weigh-ins are listed",
            rule.onAllNodesWithText("79.6 kg").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText("80.4 kg").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the lowest-of-day is marked",
            rule.onAllNodesWithText("day's weight").fetchSemanticsNodes().isNotEmpty(),
        )

        // The trend value equals a hand-rolled EWMA over the daily scalars;
        // today's lowest (79.6) REPLACES the seeded 81.05 — same calendar day.
        val scalars = SEEDED_SCALARS.dropLast(1) + 79.6
        pollText(formatTrend(trailingEwmaLast(scalars, ALPHA)))
    }

    @Test
    public fun smoothingControls_moveTheLine() {
        awaitWeightSurface()

        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("79.6")
        rule.onNodeWithTag("f06-save-weighin").performClick()
        val scalars = SEEDED_SCALARS.dropLast(1) + 79.6
        pollText(formatTrend(trailingEwmaLast(scalars, ALPHA)))

        // The α tuner (R-A2: visible, default 0.15): pushing α to its cap
        // makes the trend follow the raw readings — a different last value.
        val snappyLast = trailingEwmaLast(scalars, ALPHA_MAX)
        assertNotEquals(trailingEwmaLast(scalars, ALPHA), snappyLast, 1e-9)
        rule
            .onNodeWithTag("f06-alpha-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { action -> checkNotNull(action)(ALPHA_MAX.toFloat()) }
        pollText(formatTrend(snappyLast))

        // The method switch: zero-phase differs from the EWMA at older points,
        // so the weekly delta the hero chip shows changes. Both smoothers run
        // with the α the tuner now holds (0.5 after the slider push).
        val ewmaDelta = ewmaSeries(scalars, ALPHA_MAX).let { it.last() - it[DELTA_LOOKBACK_INDEX] }
        val zeroDelta = zeroPhaseSeries(scalars, ALPHA_MAX).let { it.last() - it[DELTA_LOOKBACK_INDEX] }
        assertNotEquals("zero-phase must re-shape the series", ewmaDelta, zeroDelta, 1e-9)
        rule.onNodeWithTag("f06-method-ewma-zero-phase").performClick()
        pollText(formatDelta(zeroDelta))
    }

    @Test
    public fun outlierFlagged_showsKeepOrCorrect_andKeepKeepsTheEvent() {
        awaitWeightSurface()

        // A reading far outside the seeded σ band trips the ±3σ guard — but
        // the event is stored either way (R-B8); the banner only asks.
        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("95.0")
        rule.onNodeWithTag("f06-save-weighin").performClick()
        pollText("keep or correct?", substring = true)

        rule.onNodeWithTag("f06-outlier-keep").performClick()
        // "Keep" clears the question; the event itself stays in the day list.
        pollText("95.0 kg")
    }

    @Test
    public fun mathDocScreen_opensFromTheChart() {
        awaitWeightSurface()
        rule.onNodeWithTag("f06-open-math").performClick()
        TestNav.awaitTag(rule, "f06-math-title")
        assertTrue(
            "each smoother's formula version is documented",
            rule.onAllNodesWithText("trend/ewma-v1", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    // --- helpers ---

    private fun awaitWeightSurface() {
        TestNav.awaitTag(rule, "hub-trend-card")
        // The rail's weigh-in quick action (no intent re-delivery — the compose
        // rule's teardown hangs on in-flight deep links, the M1 note's trap).
        rule.onAllNodesWithText("weigh in").onFirst().performClick()
        TestNav.awaitTag(rule, "f06-title")
    }

    private fun pollText(
        text: String,
        substring: Boolean = false,
    ) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()) {
                return
            }
            Thread.sleep(POLL_MS)
        }
        val kgTexts =
            OnboardingRobot
                .device()
                .findObjects(
                    androidx.test.uiautomator.By
                        .textContains("kg"),
                ).mapNotNull { it.text }
                .distinct()
        error("text \"$text\" never appeared; on screen kg texts: $kgTexts")
    }

    /** Trailing EWMA series (hand-rolled, independent of the engine). */
    private fun ewmaSeries(
        values: List<Double>,
        alpha: Double,
    ): List<Double> {
        val out = ArrayList<Double>(values.size)
        var s = values.first()
        for (x in values) {
            s = alpha * x + (1 - alpha) * s
            out += s
        }
        return out
    }

    private fun trailingEwmaLast(
        values: List<Double>,
        alpha: Double,
    ): Double = ewmaSeries(values, alpha).last()

    /** Forward-then-backward EWMA (the zero-phase filter, hand-rolled). */
    private fun zeroPhaseSeries(
        values: List<Double>,
        alpha: Double,
    ): List<Double> {
        val forward = ewmaSeries(values, alpha)
        val backward = ArrayList<Double>(forward.size)
        var r = forward.last()
        backward.add(r)
        for (i in forward.size - 2 downTo 0) {
            r = alpha * forward[i] + (1 - alpha) * r
            backward.add(r)
        }
        return backward.asReversed()
    }

    private fun formatTrend(kg: Double): String = "${decimals1(kg)} kg"

    private fun formatDelta(kg: Double): String {
        val sign = if (kg < 0) "− " else "+ "
        return "$sign${decimals1(abs(kg))} kg / 7 d"
    }

    private fun decimals1(value: Double): String {
        val tenths = (value * 10).toLong()
        val whole = tenths / 10
        val tenth = tenths % 10
        return "$whole.$tenth"
    }

    private companion object {
        const val ALPHA: Double = 0.15
        const val ALPHA_MAX: Double = 0.5

        /** The weekly-delta lookback inside the seeded series (index of today-7). */
        const val DELTA_LOOKBACK_INDEX: Int = 1
        const val TIMEOUT_MS: Long = 20_000
        const val POLL_MS: Long = 150L

        /**
         * Lowest-of-day scalars the seeder produces, oldest first: two quiet
         * days before the seeder week, then the seven seeded days.
         */
        val SEEDED_SCALARS: List<Double> =
            listOf(82.10, 82.00, 81.90, 81.75, 81.60, 81.20, 81.35, 81.15, 81.05)
    }
}
