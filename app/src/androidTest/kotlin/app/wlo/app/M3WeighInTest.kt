package app.wlo.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.centerLeft
import androidx.compose.ui.test.centerRight
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * M3 acceptance (b): weigh in twice in one day — both events stay listed
 * verbatim (R-B8), the hero shows the CANONICAL trend (the shared read: the
 * default smoother over the canonical 30-day window — the exact number the
 * Hub shows; WLO-0030 defect 9), the smoothing controls move the visible line
 * and mark the headline as a PREVIEW, the outlier guard asks keep-or-correct
 * without dropping the event, and the math documentation screen opens from
 * the chart. The logbook delete is the M3 swipe-to-dismiss — a full swipe
 * fires it — with the undo inline where the row was (WLO-0050).
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
    public fun doubleWeighIn_keepsBothEvents_andHeroMatchesTheCanonicalTrend() {
        awaitWeightSurface()

        // Weigh in #1: today already carries the seeded morning reading — a
        // LOWER re-weigh replaces it as the day's scalar (lowest-of-day).
        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("76.8")
        rule.onNodeWithTag("f06-save-weighin").performClick()
        pollText("76.8 kg")

        // Weigh in #2: the post-bathroom-win re-weigh is normal data.
        rule.onNodeWithTag("f06-open-sheet").performClick()
        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("77.6")
        rule.onNodeWithTag("f06-save-weighin").performClick()

        // The hero equals a hand-rolled EWMA over the canonical 30-day window
        // (the shared read — Hub and Weight page show the same number);
        // today's lowest (76.8) REPLACES the seeded reading — same calendar day.
        val canonicalWindow = SEEDED_SCALARS.takeLast(CANONICAL_WINDOW_DAYS).dropLast(1) + 76.8
        pollTrend(trailingEwmaLast(canonicalWindow, ALPHA))

        // Both events stay listed verbatim in the full logbook (WLO-0055) —
        // the weight surface's history card compresses the day to its
        // canonical closing weight, the feed keeps every reading.
        rule.onNodeWithTag("f06-open-logbook").performScrollTo().performClick()
        TestNav.awaitTag(rule, "f06-logbook-title")
        assertTrue(
            "both weigh-ins are listed",
            rule.onAllNodesWithText("76.8 kg").fetchSemanticsNodes().isNotEmpty() &&
                rule.onAllNodesWithText("77.6 kg").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the lowest-of-day is marked",
            rule.onAllNodesWithText("day's weight", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    public fun smoothingControls_moveTheLine_asLabeledPreview() {
        awaitWeightSurface()

        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("76.8")
        rule.onNodeWithTag("f06-save-weighin").performClick()
        val canonicalWindow = SEEDED_SCALARS.takeLast(CANONICAL_WINDOW_DAYS).dropLast(1) + 76.8
        pollTrend(trailingEwmaLast(canonicalWindow, ALPHA))

        // The α tuner (R-A2: visible, default 0.15): pushing α to its cap
        // makes the PREVIEW trend follow the raw readings — a different last
        // value, and the headline is labeled a preview (the saved trend keeps
        // the default smoother).
        val snappyLast = trailingEwmaLast(SCALARS_WITH_REWEIGH, ALPHA_MAX)
        assertNotEquals(trailingEwmaLast(canonicalWindow, ALPHA), snappyLast, 1e-9)
        rule
            .onNodeWithTag("f06-alpha-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { action -> checkNotNull(action)(ALPHA_MAX.toFloat()) }
        pollTrend(snappyLast)
        pollText("Preview — the saved trend keeps the default smoother", substring = true)

        // The method switch: zero-phase differs from the EWMA at older points,
        // so the weekly delta the preview chip shows changes. Both smoothers
        // run with the α the tuner now holds (0.5 after the slider push).
        val ewmaDelta = ewmaSeries(SCALARS_WITH_REWEIGH, ALPHA_MAX).let { it.last() - it[it.lastIndex - DELTA_LOOKBACK_DAYS] }
        val zeroDelta = zeroPhaseSeries(SCALARS_WITH_REWEIGH, ALPHA_MAX).let { it.last() - it[it.lastIndex - DELTA_LOOKBACK_DAYS] }
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
        // The f06 quick action lands with the weigh-in sheet open (wlo://weight/log);
        // the chart card lives beneath it — dismiss first, as a user would.
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        // The window selector + segment row grew the surface — the math button
        // sits below the fold now; scroll to it like a user would.
        rule.onNodeWithTag("f06-open-math").performScrollTo().performClick()
        TestNav.awaitTag(rule, "f06-math-title")
        // The formula version rides the doc card's provenance chip; the new
        // chip anatomy speaks the value via its content description (the chip
        // never repeats the value as visible text — WLO-0030 defect 15).
        assertTrue(
            "each smoother's formula version is documented",
            rule
                .onAllNodesWithContentDescription("derived, trend/ewma-v1", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
    }

    @Test
    public fun logbookDelete_fullSwipeFires_thenUndoInline() {
        awaitWeightSurface()
        // The sheet rides the quick action; dismiss it, then open the full
        // logbook — the verbatim feed owns the delete door (WLO-0055).
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithTag("f06-open-logbook").performScrollTo().performClick()
        TestNav.awaitTag(rule, "f06-logbook-title")

        // The top row is the one under the finger; its value must come back.
        val deletedValue = firstRowWeight()

        // One full swipe past the trigger IS the delete (WLO-0050) — no
        // dialog, no parked-open row, no page-level banner.
        rule.onAllNodesWithTag("f06-row").onFirst().performTouchInput { swipe(centerRight, centerLeft) }

        // The undo notice lives in the feed, where the row was.
        TestNav.awaitTag(rule, "f06-deleted-banner")
        pollFirstRowChanged(deletedValue)
        rule.onNodeWithTag("f06-undo-delete").performClick()
        pollFirstRow(deletedValue)
    }

    private fun firstRowWeight(): String =
        rule
            .onAllNodesWithTag("f06-row-weight")
            .onFirst()
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .first()
            .toString()

    private fun pollFirstRow(expected: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val nodes = rule.onAllNodesWithTag("f06-row-weight").fetchSemanticsNodes()
            if (nodes.isNotEmpty() && nodes.first().config[SemanticsProperties.Text].first().toString() == expected) {
                return
            }
            Thread.sleep(POLL_MS)
        }
        error("logbook first row never returned to \"$expected\"")
    }

    private fun pollFirstRowChanged(unexpected: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val nodes = rule.onAllNodesWithTag("f06-row-weight").fetchSemanticsNodes()
            if (nodes.isNotEmpty() && nodes.first().config[SemanticsProperties.Text].first().toString() != unexpected) {
                return
            }
            Thread.sleep(POLL_MS)
        }
        error("deleted row \"$unexpected\" never left the top of the feed")
    }

    // --- helpers ---

    private fun awaitWeightSurface() {
        TestNav.awaitTag(rule, "hub-trend-card")
        // The rail's weigh-in quick action (no intent re-delivery — the compose
        // rule's teardown hangs on in-flight deep links, the M1 note's trap).
        rule.onAllNodesWithText("Weigh in").onFirst().performClick()
        TestNav.awaitTag(rule, "f06-title")
    }

    private fun pollTrend(kg: Double) {
        // The hero renders the numeral WITHOUT the unit (defect 14) — poll for
        // the bare numeral run.
        pollText(formatTrend(kg))
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

    private fun formatTrend(kg: Double): String = decimals1(kg)

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

        /** The canonical window the shared trend read computes over (days). */
        const val CANONICAL_WINDOW_DAYS: Int = 30

        /** The weekly-delta lookback (days behind the last point). */
        const val DELTA_LOOKBACK_DAYS: Int = 7
        const val TIMEOUT_MS: Long = 20_000
        const val POLL_MS: Long = 150L

        /**
         * Lowest-of-day scalars the seeder produces, oldest first: one morning
         * reading per day for the whole 45-day series (the evening re-weigh on
         * the double day lands higher, so the morning value is the scalar).
         */
        val SEEDED_SCALARS: List<Double> = List(SeedingRobot.SERIES_DAYS) { SeedingRobot.demoWeightKg(it) }

        /** The scalars after the 76.8 re-weigh replaces today's reading. */
        val SCALARS_WITH_REWEIGH: List<Double> = SEEDED_SCALARS.dropLast(1) + 76.8
    }
}
