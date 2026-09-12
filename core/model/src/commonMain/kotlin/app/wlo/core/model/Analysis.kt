package app.wlo.core.model

import kotlinx.serialization.Serializable

/**
 * The uniform AI-port result shape (ARCHITECTURE.md §2.4 "capability
 * analyzers"): value + confidence + provenance + `held` flag. Sanity rails run
 * in interactors *after* a port returns, so rails stay SDK-independent.
 */
@Serializable
public data class Analysis<T : Any>(
    public val value: T,
    public val confidence: Double? = null,
    public val provenance: Provenance,
    public val held: Boolean,
)
