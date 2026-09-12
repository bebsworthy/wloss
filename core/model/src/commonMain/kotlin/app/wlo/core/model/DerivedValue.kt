package app.wlo.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A number the user sees is never a bare value: it carries its provenance so
 * UI can render the provenance chip (ARCHITECTURE.md §2.4, invariant C6, D6).
 * There is deliberately no `toString`-to-UI shortcut here; rendering goes
 * through `:core:designsystem` components.
 */
@Serializable
public data class DerivedValue<T : Any>(
    public val value: T,
    public val provenance: Provenance,
)

/**
 * Provenance = measured / estimated / derived(formulaVersion, inputs) / held
 * (§2.4). AI-assisted estimates additionally carry the model id and consent
 * state. Discriminators are pinned strings, never class names (ADR-004 rule 3).
 */
@Serializable
public sealed interface Provenance {
    /** A direct measurement (scale, tape, BP cuff...). */
    @Serializable
    @SerialName("measured")
    public data class Measured(
        public val at: Instant,
        public val instrument: String,
    ) : Provenance

    /**
     * An estimate: heuristic, rule engine, or AI-assisted. `modelId` and
     * `consentGranted` are set when the estimate came from an AI capability.
     */
    @Serializable
    @SerialName("estimated")
    public data class Estimated(
        public val at: Instant,
        public val method: String,
        public val confidence: Double? = null,
        public val modelId: String? = null,
        public val consentGranted: Boolean? = null,
    ) : Provenance

    /** Deterministic engine output; `formulaVersion` ties back to the constants registry. */
    @Serializable
    @SerialName("derived")
    public data class Derived(
        public val formulaVersion: String,
        public val inputs: List<String>,
    ) : Provenance

    /** Weak data is held, never guessed (FEATURES §2.1). */
    @Serializable
    @SerialName("held")
    public data class Held(
        public val reason: HoldReason,
    ) : Provenance
}

/** Why a value is held rather than shown as fact. */
@Serializable
public enum class HoldReason {
    @SerialName("insufficient-data")
    INSUFFICIENT_DATA,

    @SerialName("conflicting-inputs")
    CONFLICTING_INPUTS,

    @SerialName("consent-denied")
    CONSENT_DENIED,

    @SerialName("stale")
    STALE,
}

/**
 * Data-quality states are part of the domain, not UI strings (FEATURES §2.1):
 * a day is `developing` while being logged, `updating` while an engine run is
 * in flight, `held` when data is too weak to render as fact.
 */
@Serializable
public enum class DataQuality {
    @SerialName("developing")
    DEVELOPING,

    @SerialName("updating")
    UPDATING,

    @SerialName("held")
    HELD,
}
