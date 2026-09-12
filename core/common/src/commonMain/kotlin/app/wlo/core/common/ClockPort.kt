package app.wlo.core.common

import kotlinx.datetime.Instant

/**
 * Time sourcing port. Engines never see this (D7 — they take `Instant`
 * parameters); state holders and repositories do, so tests can freeze time.
 */
public fun interface ClockPort {
    public fun now(): Instant
}
