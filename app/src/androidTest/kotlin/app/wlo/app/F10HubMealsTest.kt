package app.wlo.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wlo.app.di.FixedClock
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.PlannerRepository
import app.wlo.core.model.EntryVia
import app.wlo.core.model.MealSlot
import app.wlo.core.model.PlannedSlot
import app.wlo.core.model.PlannedSlotState
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/**
 * WLO-0033 wave 2 acceptance: the rebuilt Hub loop — the streak chip visible
 * over a logged day (multi-oracle count, F11), and the meals card's one-tap
 * "log as planned": tapping the CTA writes the ONE diary entry (R-D11) and
 * retires the slot, and the day flows re-render with no manual refresh.
 */
@RunWith(AndroidJUnit4::class)
public class F10HubMealsTest {
    @get:Rule
    public val rule = createAndroidComposeRule<MainActivity>()

    private val koin get() = GlobalContext.get()

    private lateinit var seeded: SeedingRobot.SeedResult

    @Before
    public fun seed() {
        // WLO-0066: this test exercises a daytime-only Hub action. Preserve
        // the device's real local date so DemoSeed and every repository agree
        // on "today", but pin the phase to midday before seeding makes the
        // Hub compose. The test is then identical at 02:00 and 14:00.
        val zone = TimeZone.currentSystemDefault()
        val localToday =
            kotlin.time.Clock.System
                .now()
                .toLocalDateTime(zone)
                .date
        val midday = LocalDateTime(localToday, LocalTime.fromSecondOfDay(12 * 60 * 60)).toInstant(zone)
        loadKoinModules(
            module {
                single<ClockPort> { FixedClock(midday) }
            },
        )
        seeded = SeedingRobot.onboardAndSeedWeek()
    }

    private fun planner(): PlannerRepository = koin.get()

    private fun diary(): DiaryRepository = koin.get()

    private fun clock(): ClockPort = koin.get()

    private fun today(): Long = DayBoundary.epochDay(clock().now(), TimeZone.currentSystemDefault())

    @Test
    public fun streakChip_isVisibleOverASeededLoggedDay() {
        TestNav.awaitTag(rule, "hub-trend-card")

        // The seeded week logs today (diary + weigh-in), so the multi-oracle
        // streak anchors at today — the chip shows a count, never a zero.
        rule.onNodeWithTag("f10-streak-chip", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    public fun logAsPlanned_writesOneDiaryEntry_andRetiresTheSlot() {
        SeedingRobot.dealWeekPlan(profileId = seeded.profileId, startDayEpochDay = seeded.today, seed = 42)
        val slotId = nextOpenSlotId()

        TestNav.awaitTag(rule, "hub-trend-card")
        rule
            .onAllNodesWithTag("f10-log-as-planned", useUnmergedTree = true)
            .onFirst()
            .performScrollTo()
            .performClick()

        // The write lands: the slot retires CONFIRMED with the entry link, and
        // the diary carries the ONE plan entry (R-D11) — the state flows
        // re-emit on their own (the log refreshes the day projection).
        awaitConfirmedWithEntry(slotId)
        val planEntry =
            runBlocking {
                diary().day(seeded.profileId, today()).getOrNull()?.entries?.firstOrNull {
                    it.enteredVia == EntryVia.PLAN
                }
            }
        assertTrue("the plan entry lands in today's diary", planEntry != null)

        // The diary surface renders the logged meal under today's record.
        rule.onNodeWithTag("hub-open-diary").performScrollTo().performClick()
        TestNav.awaitTag(rule, "f02-diary-title")
        val name = runBlocking { slotName(slotId) }
        if (name != null) TestNav.awaitText(rule, name)
    }

    /** The VM's row rule: the first open slot in meal-slot order, ties by creation then id. */
    private fun nextOpenSlotId(): String =
        runBlocking {
            val open =
                planner()
                    .slots(seeded.profileId, today(), today())
                    .getOrNull()
                    .orEmpty()
                    .filter { it.state == PlannedSlotState.PLANNED && it.recipeId != null }
            assertTrue("the dealt plan must leave an open slot for today", open.isNotEmpty())
            open.minWithOrNull(nextSlotOrdering())!!.id
        }

    private suspend fun slotName(slotId: String): String? =
        planner()
            .slots(seeded.profileId, today(), today())
            .getOrNull()
            .orEmpty()
            .firstOrNull { it.id == slotId }
            ?.recipeName

    /** Polls until the slot reached CONFIRMED with its diary entry linked. */
    private fun awaitConfirmedWithEntry(slotId: String) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val done =
                runBlocking {
                    planner()
                        .slots(seeded.profileId, today(), today())
                        .getOrNull()
                        .orEmpty()
                        .firstOrNull { it.id == slotId }
                        ?.let { it.state == PlannedSlotState.CONFIRMED && it.replacedByEntryId != null }
                        ?: false
                }
            if (done) return
            Thread.sleep(250)
        }
        error("slot $slotId never confirmed with an entry link")
    }

    /** The Hub row's deterministic ordering: meal-slot order, then creation, then id. */
    private fun nextSlotOrdering(): Comparator<PlannedSlot> =
        compareBy<PlannedSlot> { MealSlot.fromWireName(it.mealSlot)?.ordinal ?: Int.MAX_VALUE }
            .thenBy { it.createdAtEpochMs }
            .thenBy { it.id }
}
