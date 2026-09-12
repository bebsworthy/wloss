package app.wlo.core.documents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The deterministic constraint→field applier (F01 §3, R-S10): table-driven —
 * each recognized phrase family maps to exactly one field; unknown phrasings
 * are returned unresolved and held, never guessed.
 */
class ConstraintApplierTest {
    @Test
    fun proteinGramPhrasings() {
        assertEquals(120.0, ConstraintApplier.proteinGram("~120 g protein"))
        assertEquals(120.0, ConstraintApplier.proteinGram("120g protein"))
        assertEquals(135.0, ConstraintApplier.proteinGram("protein 135 g"))
        assertNull(ConstraintApplier.proteinGram("protein"))
        assertNull(ConstraintApplier.proteinGram("5000 g protein"), "sanity rail: out-of-range is not mapped")
    }

    @Test
    fun weekdayNoCookingTrimsAndSpreads() {
        val app = ConstraintApplier.apply(listOf("No cooking Wednesdays"), dailyBaseKcal = 2_000.0)
        val schedule = app.weekSchedule!!
        assertEquals(7, schedule.size)
        // Wednesday (index 2) trimmed to 70%; the 600 kcal spread over 6 days.
        assertEquals(1_400.0, schedule[2])
        assertEquals(2_100.0, schedule[0])
        // Pinned weekly total: nothing created, nothing destroyed.
        assertEquals(14_000.0, schedule.sum())
    }

    @Test
    fun foodRulesRouteByBucket() {
        val app =
            ConstraintApplier.apply(
                listOf("hate cottage cheese", "allergic to shellfish", "no mustard"),
            )
        assertEquals(listOf("cottage cheese"), app.addedDislikes)
        assertEquals(listOf("shellfish"), app.addedAllergies)
        assertEquals(listOf("mustard"), app.addedExclusions)
        assertTrue(app.unresolved.isEmpty())
    }

    @Test
    fun eatingWindowPhrasings() {
        val window = ConstraintApplier.apply(listOf("eating window 12-20")).eatingWindow
        assertEquals(12, window?.startHour)
        assertEquals(20, window?.endHour)
        val if168 = ConstraintApplier.apply(listOf("16:8 fasting")).eatingWindow
        assertEquals("16:8", if168?.pattern)
    }

    @Test
    fun householdPhrasing() {
        assertEquals(4, ConstraintApplier.apply(listOf("household of 4")).householdSize)
    }

    @Test
    fun unknownPhrasingsAreHeldNotGuessed() {
        val app = ConstraintApplier.apply(listOf("mostly pescatarian-ish vibes", "cheat Sundays"))
        assertEquals(listOf("mostly pescatarian-ish vibes", "cheat sundays"), app.unresolved)
        assertNull(app.proteinGrams)
        assertNull(app.weekSchedule)
        assertNull(app.eatingWindow)
    }

    @Test
    fun constraintsComposeInOrder() {
        val app =
            ConstraintApplier.apply(
                listOf("~120 g protein", "household of 2", "no gluten"),
            )
        assertEquals(120.0, app.proteinGrams)
        assertEquals(2, app.householdSize)
        assertEquals(listOf("gluten"), app.addedExclusions)
    }
}
