package app.wlo.app.di

import app.wlo.core.common.ClockPort
import kotlinx.datetime.Instant

/**
 * A frozen [ClockPort] for tests that genuinely need determinism (per-test
 * graph overrides — never bound in the main graph, WLO-0049). Engines never
 * see any clock (D7): they take [Instant] parameters.
 */
public class FixedClock(
    private val fixed: Instant,
) : ClockPort {
    override fun now(): Instant = fixed

    public companion object {
        /** A Tuesday morning, 07:12 — the canonical weigh-in hour. */
        public val DEMO_NOW: Instant = Instant.parse("2026-09-08T07:12:00Z")
    }
}
