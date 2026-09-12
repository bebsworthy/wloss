package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.testing.GoldenFixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden-file harness for the F10 fixed-rules Day Model (F10 §3): a full-day
 * timeline of scenarios in `resources/golden/day_model_timeline.json` runs
 * unchanged in CI; the rules and the fixture evolve together.
 */
class DayModelEngineGoldenTest {
    @Serializable
    private data class Scenario(
        val name: String,
        val minutesOfDay: Int,
        val weighInLogged: Boolean = false,
        val weighInReminderMuted: Boolean = false,
        val checkInDueToday: Boolean = false,
        val hasOpenPlannedMeal: Boolean = false,
        val workoutDueToday: Boolean = false,
        val workoutDone: Boolean = false,
        val trendAvailable: Boolean = false,
        val isPlanner: Boolean = false,
        val planTomorrowPending: Boolean = false,
        val expectedPhase: String,
        val expectedHero: String,
        val expectedCards: List<String>,
        val expectedQuickActions: List<String>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val scenarios: List<Scenario> by lazy {
        json.decodeFromString<List<Scenario>>(GoldenFixtures.load("golden/day_model_timeline.json"))
    }

    @Test
    fun goldenTimelineMatchesTheFixedRules() {
        assertTrue(scenarios.size >= 10, "fixture should carry a real day of scenarios")
        scenarios.forEach { scenario ->
            val model =
                DayModelEngine.resolve(
                    DayModelInput(
                        minutesOfDay = scenario.minutesOfDay,
                        weighInLogged = scenario.weighInLogged,
                        weighInReminderMuted = scenario.weighInReminderMuted,
                        checkInDueToday = scenario.checkInDueToday,
                        hasOpenPlannedMeal = scenario.hasOpenPlannedMeal,
                        workoutDueToday = scenario.workoutDueToday,
                        workoutDone = scenario.workoutDone,
                        trendAvailable = scenario.trendAvailable,
                        isPlanner = scenario.isPlanner,
                        planTomorrowPending = scenario.planTomorrowPending,
                    ),
                )
            assertEquals(
                scenario.expectedPhase,
                model.phase.wireName,
                "${scenario.name}: phase",
            )
            assertEquals(
                scenario.expectedHero,
                model.heroCard.wireName,
                "${scenario.name}: hero slot",
            )
            assertEquals(
                scenario.expectedCards,
                model.cards.map { it.card.wireName },
                "${scenario.name}: card order",
            )
            assertEquals(
                scenario.expectedQuickActions,
                model.quickActions.map { it.wireName },
                "${scenario.name}: quick-action rail",
            )
        }
    }

    @Test
    fun mutedWeighInCardCarriesMutedState() {
        val model =
            DayModelEngine.resolve(
                DayModelInput(minutesOfDay = 400, weighInLogged = false, weighInReminderMuted = true),
            )
        val weighIn = model.cards.first { it.card == HubCard.WEIGH_IN }
        assertEquals(CardState.MUTED_BY_CHOICE, weighIn.state)
    }

    @Test
    fun loggedWeighInDisappearsSilently() {
        // R-D14: absence is silent — the card exists only while the weigh-in is open.
        val model = DayModelEngine.resolve(DayModelInput(minutesOfDay = 400, weighInLogged = true))
        assertEquals(null, model.cards.firstOrNull { it.card == HubCard.WEIGH_IN })
    }

    @Test
    fun versionStampsFromTheRegistry() {
        assertEquals(ConstantsRegistry.DAY_MODEL_VERSION, DayModelEngine.VERSION)
    }
}
