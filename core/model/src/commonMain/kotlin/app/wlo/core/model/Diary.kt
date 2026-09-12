package app.wlo.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One diary entry (R-B1: F02 owns the food diary; R-B8: event-level storage,
 * day-level rendering). The computed numbers are derived views over the food
 * item × portion (formula `diary/portion-scale-v1` in the constants registry);
 * the entry also carries a provenance key into the `provenance` table so the
 * "how we got here" sheet can open per entry.
 */
@Serializable
public data class DiaryEntry(
    public val id: String,
    public val profileId: String,
    public val dayEpochDay: Long,
    public val mealSlot: MealSlot,
    /** The catalog item this entry scales; null for kcal-only quick-adds. */
    public val foodItemId: String? = null,
    /** Free-text fallback path (R-U15: the manual path is always there). */
    public val textHint: String? = null,
    /** Portion in [unit] — grams/ml, or grams via a serving preset. */
    public val quantity: Double,
    /** "g" | "ml" | "serving" (v1 portion vocabulary). */
    public val unit: String,
    public val kcal: Double,
    public val proteinG: Double? = null,
    public val carbG: Double? = null,
    public val fatG: Double? = null,
    public val fiberG: Double? = null,
    public val enteredVia: EntryVia,
    /** Provenance-table scalar key for this entry's kcal (`diary/kcal/<entryId>`). */
    public val provenanceScalar: String,
    /** Monotonic per-entry counter: grows by one on every accepted edit. */
    public val revision: Int,
    public val createdAt: Instant,
    public val editedAt: Instant? = null,
    /**
     * Entry "delete" is an archive (hide-not-delete, R-B7): the row stays,
     * day views and projections exclude it, history stays reversible.
     */
    public val archivedAt: Instant? = null,
    /** Fresh Start ledger columns (R-B7, added now so no later migration is needed). */
    public val hiddenAt: Instant? = null,
    public val hiddenReason: String? = null,
)

/**
 * One frozen prior version of a diary entry (correction audit, R-B8 + F02 §3:
 * "the raw estimate + correction delta (ground-truth pair)"). Append-only:
 * an edit inserts the OLD state here and bumps the entry's pointer — the
 * chain is the honest history, never a rewrite.
 */
@Serializable
public data class DiaryRevision(
    public val id: String,
    public val entryId: String,
    public val revision: Int,
    public val mealSlot: MealSlot,
    public val foodItemId: String? = null,
    public val textHint: String? = null,
    public val quantity: Double,
    public val unit: String,
    public val kcal: Double,
    public val proteinG: Double? = null,
    public val carbG: Double? = null,
    public val fatG: Double? = null,
    public val fiberG: Double? = null,
    public val enteredVia: EntryVia,
    /** When this version was superseded by the next edit. */
    public val editedAt: Instant,
)

/** F06 trend smoother selection (F06 §3; R-A2 fixes the default α at 0.15). */
@Serializable
public enum class TrendMethod(
    public val wireName: String,
) {
    /** Trailing EWMA — the default (R-A2). */
    @SerialName("ewma")
    EWMA("ewma"),

    /** Zero-phase (forward-backward) EWMA — near-zero lag, recent values revise. */
    @SerialName("ewma-zero-phase")
    ZERO_PHASE_EWMA("ewma-zero-phase"),

    /** Plain 7-day moving average — maximum simplicity, maximum lag. */
    @SerialName("ma-7d")
    MOVING_AVERAGE_7D("ma-7d"),
    ;

    public companion object {
        public fun fromWireName(name: String): TrendMethod? = entries.firstOrNull { it.wireName == name }
    }
}

/** Body-fat method registry (F06 §3: "a method registry, never one number"). */
@Serializable
public enum class BodyFatMethod(
    public val wireName: String,
) {
    /** US Navy tape method (Hodgdon & Beckett) — neck/waist(/hip) girths. */
    @SerialName("navy-tape")
    NAVY_TAPE("navy-tape"),

    /** RFM (Woolcott & Bergman 2012) — height/waist only, no neck tape. */
    @SerialName("rfm")
    RFM("rfm"),
    ;

    public companion object {
        public fun fromWireName(name: String): BodyFatMethod? = entries.firstOrNull { it.wireName == name }
    }
}
