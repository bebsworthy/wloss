package app.wlo.core.common

import app.wlo.core.model.ConsentCapability

/**
 * D8: domain errors are sealed `AppError` subtypes returned in `Result`-style
 * values — no exceptions cross module boundaries. Library exceptions are
 * wrapped at repository edges. UI copy mapping happens in exactly one place.
 */
public sealed class AppError(
    public open val debugMessage: String,
    public open val cause: Throwable? = null,
) {
    /** A caller passed something the API contract rejects. */
    public data class InvalidInput(
        val detail: String,
    ) : AppError(debugMessage = "invalid input: $detail")

    /** Local persistence failed (database/datastore/file). */
    public data class Storage(
        override val cause: Throwable?,
        val detail: String,
    ) : AppError(debugMessage = "storage failure: $detail", cause = cause)

    /** A document/payload could not be decoded (schema drift beyond the migration funnel). */
    public data class Parse(
        val detail: String,
        override val cause: Throwable? = null,
    ) : AppError(debugMessage = "parse failure: $detail", cause = cause)

    /** A consent-gated path was reached without a grant (F12). */
    public data class ConsentDenied(
        val capability: ConsentCapability,
    ) : AppError(debugMessage = "consent denied: ${capability.wireName}")

    /** A capability is temporarily unavailable (offline fallback exhausted, model missing...). */
    public data class Unavailable(
        val detail: String,
    ) : AppError(debugMessage = "unavailable: $detail")

    /** Anything from outside the app that we cannot further classify. */
    public data class External(
        override val cause: Throwable,
    ) : AppError(debugMessage = cause.message ?: "external failure", cause = cause)
}
