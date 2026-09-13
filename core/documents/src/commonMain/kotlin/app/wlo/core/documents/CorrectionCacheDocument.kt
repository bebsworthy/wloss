package app.wlo.core.documents

import kotlinx.serialization.Serializable

/**
 * The R-B6 correction cache as a VERSIONED DOCUMENT (ADR-004 house rules).
 *
 * Mechanism (R-B6): user corrections over a FROZEN on-device model are
 * "prior signal" (F02 §3) — a small structured map `analyzed label → what the
 * user actually logged`, consulted at the next scan to pre-suggest the user's
 * usual correction. ONE mechanism is shared by F02 (dish priors) and F09
 * (classifier personalization): this document type is the shared shape, the
 * store wrapper lives in `:core:data` ([app.wlo.core.data.CorrectionCacheStore]).
 *
 * OWNER FLAG (M4): the R-B6 correction cache currently lives as a DataStore
 * document (JSON envelope here) plus an in-process mirror. A dedicated table
 * would need schema v6 — deliberately AVOIDED this milestone; if F09's
 * personalization needs queries the document cannot serve, schema v6 should
 * formalize these columns as-is.
 */
@Serializable
public data class CorrectionCacheDocument(
    public val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    public val revision: String = "",
    public val entries: List<CorrectionCacheEntry> = emptyList(),
) {
    public companion object {
        public const val CURRENT_SCHEMA_VERSION: Int = 1
    }
}

/**
 * One prior: the analyzer label the user corrected, the label/food they
 * actually keep, how often, and their last portion. `updatedAtEpochMs`
 * supports LRU trimming (the cache is a prior, not an archive — the diary
 * revision chain stays the audit history).
 */
@Serializable
public data class CorrectionCacheEntry(
    /** The model's original suggestion (Food-101 wire label, e.g. `french_fries`). */
    public val analyzedLabel: String,
    /** The corrected target label (display name of the swapped-in food). */
    public val correctedLabel: String,
    /** Corrected target food id (catalog row), when the swap hit the local catalog. */
    public val correctedFoodId: String? = null,
    /** How many times this correction repeated (the prior's weight). */
    public val occurrences: Int = 1,
    /** The user's last accepted portion in grams (portion prior). */
    public val lastGrams: Double? = null,
    public val updatedAtEpochMs: Long = 0L,
)
