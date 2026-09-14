package app.wlo.core.common

import kotlinx.datetime.Instant

/**
 * Time sourcing port. Engines never see this (D7 — they take `Instant`
 * parameters); state holders and repositories do, so tests can freeze time.
 */
public fun interface ClockPort {
    public fun now(): Instant
}

/**
 * The production clock (WLO-0049): the device's wall clock via
 * `kotlinx.datetime.Clock`. Tests that need determinism construct a fake
 * ([app.wlo.core.testing.FakeClock]) or override the binding per test —
 * nothing in the main graph may pin time.
 */
public data object SystemClock : ClockPort {
    override fun now(): Instant =
        kotlin.time.Clock
            .System
            .now()
}
