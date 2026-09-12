package app.wlo.core.testing

import app.wlo.core.common.ClockPort
import kotlinx.datetime.Instant

/**
 * Frozen/steppable clock for tests (EWMA windows, quiet hours 21:30–07:30 —
 * anything time-sensitive in the loops is deterministic under this clock).
 */
public class FakeClock(
    startEpochMs: Long = DEFAULT_START_EPOCH_MS,
) : ClockPort {
    public var nowEpochMs: Long = startEpochMs
        private set

    override fun now(): Instant = Instant.fromEpochMilliseconds(nowEpochMs)

    public fun advanceBy(millis: Long) {
        nowEpochMs += millis
    }

    public fun setTo(epochMs: Long) {
        nowEpochMs = epochMs
    }

    public companion object {
        /** 2026-09-12T06:00:00Z — the canonical 6 a.m. weigh-in moment. */
        public const val DEFAULT_START_EPOCH_MS: Long = 1_788_482_400_000
    }
}
