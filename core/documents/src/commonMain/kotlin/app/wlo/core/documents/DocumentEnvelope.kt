package app.wlo.core.documents

import kotlinx.serialization.Serializable

/**
 * Versioned-document envelope (F13 §3): everything stored or exported wraps
 * its payload with the schema version so the single migration funnel in this
 * module can route old versions through transforming serializers.
 */
@Serializable
public data class DocumentEnvelope<T>(
    public val schemaVersion: Int,
    public val payload: T,
)
