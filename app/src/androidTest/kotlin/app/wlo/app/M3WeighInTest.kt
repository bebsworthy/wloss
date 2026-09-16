package app.wlo.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.centerLeft
import androidx.compose.ui.test.centerRight
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.MassUnit
import app.wlo.core.common.getOrNull
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.WeighInRepository
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.model.MeasurementKind
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
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
    public fun weightSurface_activityRecreationRefreshesWithoutAnErrorState() {
        TestNav.awaitTag(rule, "f06-open-sheet")
        rule.onNodeWithTag("f06-goal-progress", useUnmergedTree = true).assertExists()

        rule.activityRule.scenario.recreate()

        TestNav.awaitTag(rule, "f06-open-sheet")
        rule.onNodeWithTag("f06-goal-progress", useUnmergedTree = true).assertExists()
        assertTrue(rule.onAllNodesWithTag("f06-load-error").fetchSemanticsNodes().isEmpty())
    }

    @Test
    public fun prefilledEntry_usesMaterialPickers_andSavesWithoutTypingWeight() {
        val koin = GlobalContext.get()
        awaitWeightSurface()
        val before = todayEvents(koin).size

        val weightText =
            rule
                .onNodeWithTag("f06-weight-field", useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text
        assertTrue("seeded history prefills the first daily entry", weightText.isNotBlank())
        assertTrue(
            rule
                .onNodeWithTag("f06-step-up", useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .single()
                .startsWith("Increase weight by 0.1"),
        )

        rule.onNodeWithTag("f06-day-field", useUnmergedTree = true).performClick()
        TestNav.awaitTag(rule, "f06-day-field-picker")
        rule.onNodeWithTag("f06-day-field-confirm", useUnmergedTree = true).performClick()

        rule.onNodeWithTag("f06-time-field", useUnmergedTree = true).performClick()
        TestNav.awaitTag(rule, "f06-time-field-picker")
        rule.onNodeWithTag("f06-time-field-confirm", useUnmergedTree = true).performClick()

        rule.onNodeWithTag("f06-save-weighin", useUnmergedTree = true).performScrollTo().performClick()
        pollTodayEventCount(koin, before + 1)
        TestNav.awaitTag(rule, "f06-confirmation-card")
        val announcement = confirmationAnnouncement()
        assertTrue(announcement.indexOf("Weight trend") < announcement.indexOf("Raw reading"))
        rule.onNodeWithTag("f06-confirmation-done").performClick()
        assertTrue(rule.onAllNodesWithTag("f06-confirmation-card").fetchSemanticsNodes().isEmpty())
    }

    @Test
    public fun compactLargeFontSheet_keepsSaveReachableWithIme_andCapturesEvidence() {
        val ui = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            OnboardingRobot.shell("settings put system font_scale 2.0")
            ui.setOrientationLeft()
            rule.activityRule.scenario.recreate()
            awaitWeightSurface()

            rule.onNodeWithTag("f06-weight-field", useUnmergedTree = true).performTextClearance()
            rule.onNodeWithTag("f06-weight-field", useUnmergedTree = true).performTextInput("78.4")
            rule.onNodeWithTag("f06-save-weighin", useUnmergedTree = true).performScrollTo()
            OnboardingRobot.shell("screencap -p /sdcard/wlo0069-weighin-large-font-landscape-ime.png")
            assertTrue(
                rule
                    .onAllNodesWithTag("f06-save-weighin", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty(),
            )
        } finally {
            OnboardingRobot.shell("settings put system font_scale 1.0")
            ui.setOrientationNatural()
            ui.unfreezeRotation()
        }
    }

    @Test
    public fun doubleWeighIn_keepsBothEvents_andHeroMatchesTheCanonicalTrend() {
        awaitWeightSurface()

        // Weigh in #1: today already carries the seeded morning reading — a
        // LOWER re-weigh replaces it as the day's scalar (lowest-of-day).
        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("76.8")
        rule.onNodeWithTag("f06-save-weighin").performClick()
        pollText("Raw reading 76.8 kg · manual")
        dismissConfirmation()

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
        dismissConfirmation()

        // Both events stay listed verbatim in the full logbook (WLO-0055) —
        // the weight surface's history card compresses the day to its
        // canonical closing weight, the feed keeps every reading.
        rule.onNodeWithTag("f06-open-logbook").performScrollTo().performClick()
        TestNav.awaitTag(rule, "f06-logbook-list")
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
    public fun poundInput_isStoredAsCanonicalKilograms_andRenderedAsPounds() {
        val koin = GlobalContext.get()
        runBlocking { koin.get<SettingsStore>().setMassUnit(MassUnit.POUND) }
        awaitWeightSurface()

        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("170.0")
        rule.onNodeWithTag("f06-save-weighin").performClick()
        pollText("Raw reading 170.0 lb · manual")
        TestNav.awaitTag(rule, "f06-confirmation-card")
        assertTrue(
            rule
                .onAllNodesWithText("Raw reading 170.0 lb · manual", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        assertTrue("active unit is announced", "lb" in confirmationAnnouncement())

        val expectedKg = MassUnit.POUND.toKilograms(170.0)
        val storedKg =
            runBlocking {
                val profile = checkNotNull(koin.get<ProfileRepository>().active().getOrNull())
                val clock = koin.get<ClockPort>()
                val today = DayBoundary.epochDay(clock.now(), TimeZone.currentSystemDefault())
                koin
                    .get<WeighInRepository>()
                    .dayWeighIns(profile.id, today)
                    .getOrNull()
                    .orEmpty()
                    .map { it.valueReal }
                    .single { abs(it - expectedKg) < 1e-9 }
            }
        assertEquals(expectedKg, storedKg, 1e-9)
    }

    @Test
    public fun firstSavedReading_showsHonestSparseConfirmation() {
        val koin = GlobalContext.get()
        awaitWeightSurface()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()

        runBlocking {
            val profile = checkNotNull(koin.get<ProfileRepository>().active().getOrNull())
            val clock = koin.get<ClockPort>()
            val today = DayBoundary.epochDay(clock.now(), TimeZone.currentSystemDefault())
            val repository = koin.get<WeighInRepository>()
            koin
                .get<MeasurementRepository>()
                .rangeOfKind(profile.id, MeasurementKind.WEIGHT, today - 365, today)
                .getOrNull()
                .orEmpty()
                .forEach { repository.deleteWeighIn(it.id, clock.now()) }
        }

        rule.onNodeWithTag("f06-open-sheet").performClick()
        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("80.0")
        rule.onNodeWithTag("f06-save-weighin").performClick()

        TestNav.awaitTag(rule, "f06-confirmation-sparse")
        pollText("Trend is still learning")
        pollText("Add 2 more to establish a trend.")
        assertTrue("sparse state is announced before raw data", confirmationAnnouncement().startsWith("Weight saved. Trend"))
    }

    @Test
    public fun invalidInput_staysInTheSheet_withFieldSpecificError() {
        awaitWeightSurface()

        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-save-weighin").performClick()
        pollText("Enter a weight.")
        TestNav.awaitTag(rule, "f06-weighin-sheet")

        rule.onNodeWithTag("f06-weight-field").performTextInput("not-a-number")
        rule.onNodeWithTag("f06-save-weighin").performClick()
        pollText("Enter one number", substring = true)
        TestNav.awaitTag(rule, "f06-weighin-sheet")
    }

    @Test
    public fun rapidDoubleSave_createsOneEvent() {
        val koin = GlobalContext.get()
        awaitWeightSurface()
        val before = todayEvents(koin).size
        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("76,8")

        // Invoke the semantics action twice before Compose can recompose the
        // disabled button. The ViewModel's synchronous SAVING transition is
        // the actual duplicate-write guard.
        rule.onNodeWithTag("f06-save-weighin").performSemanticsAction(SemanticsActions.OnClick) { click ->
            checkNotNull(click)()
            checkNotNull(click)()
        }
        pollText("Raw reading 76.8 kg · manual")
        assertEquals(before + 1, todayEvents(koin).size)
    }

    @Test
    public fun smoothingControls_moveTheLine_asLabeledPreview() {
        awaitWeightSurface()

        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("76.8")
        rule.onNodeWithTag("f06-save-weighin").performClick()
        val canonicalWindow = SEEDED_SCALARS.takeLast(CANONICAL_WINDOW_DAYS).dropLast(1) + 76.8
        pollTrend(trailingEwmaLast(canonicalWindow, ALPHA))
        dismissConfirmation()

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
    public fun emptyThirtyDayWindow_offersSmallestWiderWindow_andHidesTuner() {
        val koin = GlobalContext.get()
        awaitWeightSurface()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()

        runBlocking {
            val profile = checkNotNull(koin.get<ProfileRepository>().active().getOrNull())
            val clock = koin.get<ClockPort>()
            val today = DayBoundary.epochDay(clock.now(), TimeZone.currentSystemDefault())
            val repository = koin.get<WeighInRepository>()
            koin
                .get<MeasurementRepository>()
                .rangeOfKind(profile.id, MeasurementKind.WEIGHT, today - 365, today)
                .getOrNull()
                .orEmpty()
                .forEach { repository.deleteWeighIn(it.id, clock.now()) }
            repository.appendWeighIn(
                profileId = profile.id,
                dayEpochDay = today - 40,
                weightKg = 81.0,
                capturedAt = clock.now(),
                source = "test",
                note = null,
            )
        }

        rule.onNodeWithTag("f06-window-d30", useUnmergedTree = true).performScrollTo().performClick()
        pollText("No weigh-ins in the last 30 days", substring = true)
        pollText("Show 90 d")
        assertTrue(rule.onAllNodesWithTag("f06-alpha-slider", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        OnboardingRobot.shell("screencap -p /sdcard/wlo0053-empty-window.png")

        rule.onNodeWithText("Show 90 d", useUnmergedTree = true).performClick()
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithTag("f06-alpha-slider", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        pollText("the trend resumes when you do", substring = true)
        assertTrue(
            rule
                .onAllNodesWithContentDescription("90 d window, 1 daily weigh-ins", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        OnboardingRobot.shell("screencap -p /sdcard/wlo0053-lapsed-window.png")
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
        dismissConfirmation()
        // "Keep" clears the question; prove the raw event survives by reading
        // it from the verbatim logbook (the weight hero intentionally shows
        // the lower daily scalar rather than every reading).
        rule.onNodeWithTag("f06-open-logbook").performScrollTo().performClick()
        TestNav.awaitTag(rule, "f06-logbook-list")
        pollText("95.0 kg")
    }

    @Test
    public fun outlierCorrect_deletesFlaggedEvent_andReopensWithSafePriorValue() {
        val koin = GlobalContext.get()
        awaitWeightSurface()
        rule.onNodeWithTag("f06-weight-field").performTextClearance()
        rule.onNodeWithTag("f06-weight-field").performTextInput("95.0")
        rule.onNodeWithTag("f06-save-weighin").performClick()
        pollText("keep or correct?", substring = true)

        rule.onNodeWithTag("f06-outlier-correct").performClick()
        TestNav.awaitTag(rule, "f06-weighin-sheet")
        val correctedPrefill =
            rule
                .onNodeWithTag("f06-weight-field")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text
        val expectedPrefill =
            runBlocking {
                val profile = checkNotNull(koin.get<ProfileRepository>().active().getOrNull())
                val clock = koin.get<ClockPort>()
                val today = DayBoundary.epochDay(clock.now(), TimeZone.currentSystemDefault())
                val current =
                    checkNotNull(
                        koin
                            .get<WeighInRepository>()
                            .currentTrend(profile.id, today)
                            .getOrNull()
                            ?.current,
                    )
                MassUnit.KILOGRAM.formatNumber(current.value)
            }
        assertEquals(expectedPrefill, correctedPrefill)
        assertNotEquals("95.0", correctedPrefill)
        assertTrue(todayEvents(koin).none { abs(it.valueReal - 95.0) < 1e-9 })
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
        TestNav.awaitTag(rule, "f06-math-ewma")
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
        TestNav.awaitTag(rule, "f06-logbook-list")

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

    @Test
    public fun logbookEdit_tapOpensSheet_saveReplacesTheEntry() {
        awaitWeightSurface()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.onNodeWithTag("f06-open-logbook").performScrollTo().performClick()
        TestNav.awaitTag(rule, "f06-logbook-list")

        // The tap door (F06 §5 inline edit) carries the same feedback as
        // every other list row: tap opens the prefilled sheet.
        rule.onAllNodesWithTag("f06-row").onFirst().performClick()
        TestNav.awaitTag(rule, "f06-edit-sheet")
        rule.onNodeWithTag("f06-edit-weight").performTextClearance()
        rule.onNodeWithTag("f06-edit-weight").performTextInput("80.4")
        rule.onNodeWithTag("f06-edit-save").performClick()

        // The entry is replaced in place, and the log says so.
        pollFirstRow("80.4 kg")
        pollText("edited", substring = true)
    }

    private fun confirmationAnnouncement(): String =
        rule
            .onAllNodesWithContentDescription("Weight saved", substring = true)
            .onFirst()
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .single()

    private fun dismissConfirmation() {
        TestNav.awaitTag(rule, "f06-confirmation-done")
        rule.onNodeWithTag("f06-confirmation-done").performClick()
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithTag("f06-confirmation-card").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun firstRowWeight(): String {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val actual =
                rule
                    .onAllNodesWithTag("f06-row-weight", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .firstOrNull()
                    ?.config
                    ?.get(SemanticsProperties.Text)
                    ?.first()
                    ?.toString()
            if (actual != null) return actual
            Thread.sleep(POLL_MS)
        }
        error("logbook never rendered its first weigh-in row")
    }

    private fun pollFirstRow(expected: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val nodes = rule.onAllNodesWithTag("f06-row-weight", useUnmergedTree = true).fetchSemanticsNodes()
            val actual =
                nodes
                    .firstOrNull()
                    ?.config
                    ?.get(SemanticsProperties.Text)
                    ?.first()
                    ?.toString()
            if (actual == expected) {
                return
            }
            Thread.sleep(POLL_MS)
        }
        val notice =
            runCatching {
                rule
                    .onNodeWithTag("f06-logbook-notice", useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.Text]
                    .first()
            }.getOrNull()
        error("logbook first row never became \"$expected\"; session notice: $notice")
    }

    private fun pollFirstRowChanged(unexpected: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val nodes = rule.onAllNodesWithTag("f06-row-weight", useUnmergedTree = true).fetchSemanticsNodes()
            val actual =
                nodes
                    .firstOrNull()
                    ?.config
                    ?.get(SemanticsProperties.Text)
                    ?.first()
                    ?.toString()
            if (actual != null && actual != unexpected) {
                return
            }
            Thread.sleep(POLL_MS)
        }
        error("deleted row \"$unexpected\" never left the top of the feed")
    }

    // --- helpers ---

    private fun awaitWeightSurface() {
        // Weight is the primary post-onboarding destination. This also makes
        // the suite independent of Hub's day-phase quick-action rail.
        TestNav.awaitTag(rule, "f06-open-sheet")
        rule.onNodeWithTag("f06-open-sheet", useUnmergedTree = true).performClick()
        TestNav.awaitTag(rule, "f06-weighin-sheet")
    }

    private fun todayEvents(koin: org.koin.core.Koin) =
        runBlocking {
            val profile = checkNotNull(koin.get<ProfileRepository>().active().getOrNull())
            val clock = koin.get<ClockPort>()
            val today = DayBoundary.epochDay(clock.now(), TimeZone.currentSystemDefault())
            koin
                .get<WeighInRepository>()
                .dayWeighIns(profile.id, today)
                .getOrNull()
                .orEmpty()
        }

    private fun pollTodayEventCount(
        koin: org.koin.core.Koin,
        expected: Int,
    ) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (todayEvents(koin).size == expected) return
            Thread.sleep(POLL_MS)
        }
        error("today's weigh-in count never became $expected; actual=${todayEvents(koin).size}")
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
            if (
                rule
                    .onAllNodesWithText(text, substring = substring, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            ) {
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

    private fun formatTrend(kg: Double): String = MassUnit.KILOGRAM.formatNumber(kg)

    private fun formatDelta(kg: Double): String {
        val sign = if (kg < 0) "− " else "+ "
        return "$sign${MassUnit.KILOGRAM.formatNumber(abs(kg))} kg / 7 d"
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
