package app.wlo.core.consent

/**
 * Consent-local time source; keeps `:core:consent` dependent on nothing but
 * `:core:model` (ADR-005 graph) while letting tests freeze time deterministically.
 */
public fun interface ConsentTimeSource {
    public fun nowEpochMs(): Long
}
