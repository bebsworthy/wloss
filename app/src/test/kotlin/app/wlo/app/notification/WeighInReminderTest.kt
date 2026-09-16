package app.wlo.app.notification

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reminder's scheduling math (WLO-0040): the next occurrence of the
 * chosen minute — today when still comfortably ahead, tomorrow once the
 * moment has passed (with a five-minute grace so a just-picked time doesn't
 * fire immediately or double-fire).
 */
class WeighInReminderTest {
    @Test
    fun copyWorksForAnyChosenTime() {
        assertEquals("Time for a weigh-in", WeighInReminderWorker.TITLE)
        assertFalse(WeighInReminderWorker.TITLE.contains("morning", ignoreCase = true))
        assertFalse(WeighInReminderWorker.BODY.contains("morning", ignoreCase = true))
    }

    @Test
    fun laterToday_whenTheMinuteIsStillAhead() {
        val now = LocalDateTime.parse("2026-09-14T06:00:00")
        val delay = WeighInReminder.delayUntilNext(minuteOfDay = 7 * 60 + 30, now = now)
        assertEquals(Duration.ofMinutes(90), delay)
    }

    @Test
    fun tomorrow_onceTheMinutePassed() {
        val now = LocalDateTime.parse("2026-09-14T08:00:00")
        val delay = WeighInReminder.delayUntilNext(minuteOfDay = 7 * 60 + 30, now = now)
        assertEquals(Duration.ofHours(23).plusMinutes(30), delay)
    }

    @Test
    fun graceWindow_schedulesTomorrow_notANearImmediateFire() {
        // 07:28 — the chosen 07:30 is inside the five-minute grace: tomorrow.
        val now = LocalDateTime.parse("2026-09-14T07:28:00")
        val delay = WeighInReminder.delayUntilNext(minuteOfDay = 7 * 60 + 30, now = now)
        assertTrue(delay > Duration.ofHours(23), "a just-passed slot must not fire within minutes")
    }

    @Test
    fun eveningAnchor_landsNextMorning() {
        val now = LocalDateTime.parse("2026-09-14T21:00:00")
        val delay = WeighInReminder.delayUntilNext(minuteOfDay = 20 * 60, now = now)
        assertEquals(Duration.ofHours(23), delay)
    }

    @Test
    fun dstGapUsesTheNextValidLocalOccurrence() {
        val now = ZonedDateTime.of(LocalDateTime.parse("2026-03-28T23:00:00"), ZoneId.of("Europe/Paris"))
        val delay = WeighInReminder.delayUntilNext(minuteOfDay = 2 * 60 + 30, now = now)
        assertEquals(Duration.ofMinutes(210), delay)
    }

    @Test
    fun dstOverlapUsesOneDeterministicOccurrence() {
        val now = ZonedDateTime.of(LocalDateTime.parse("2026-10-24T23:00:00"), ZoneId.of("Europe/Paris"))
        val delay = WeighInReminder.delayUntilNext(minuteOfDay = 2 * 60 + 30, now = now)
        assertEquals(Duration.ofMinutes(210), delay)
    }
}
