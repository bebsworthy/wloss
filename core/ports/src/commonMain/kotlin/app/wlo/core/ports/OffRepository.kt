package app.wlo.core.ports

/**
 * Open Food Facts lookup door (F02 §3 rung 3, R-C4). NAMING: this is a
 * repository-style port (ARCHITECTURE §2.4 "repositories + projections") —
 * it is the app's single door to the OFF catalog, so `OffRepository` (not
 * `OffLookupClient`) keeps the data-spine naming convention: features see the
 * door, `:core:network` owns the wire. Lookups ride [EgressPurpose.OFF_LOOKUP]
 * through the dispatcher (cache-first, per-lookup receipts); nothing here is
 * AI and no consent category applies (R-C4).
 *
 * Second backend, USDA FDC, is intentionally a DOCUMENTED STUB for this
 * milestone: the interface exists (`UsdaRepository` in :core:network's
 * package-private stub + this door's shape is backend-agnostic enough) and
 * the implementation lands later (F02 §3: "USDA FDC second"). The port does
 * NOT carry a `search` API yet — search-across-backends is F02 §3 rung 5
 * work and deserves its own port when it arrives.
 */
public interface OffRepository {
    /**
     * Looks one barcode up, cache-first (R-C4 "aggressive cache"). Never
     * throws: every failure mode is a [OffLookupResult.Miss] so the capture
     * flow can degrade to its R-U15 manual twin with kind copy.
     */
    public suspend fun lookup(barcode: String): OffLookupResult
}

/** The per-100 g nutrition + identity of one OFF product (R-A4 v1 scope). */
public data class OffProduct(
    public val barcode: String,
    /** Product name (OFF `product_name`, trimmed; may be blank for sparse rows). */
    public val name: String,
    /** Brands line (OFF `brands`, e.g. "Kellogg's, Kellogg Company"). */
    public val brand: String? = null,
    public val kcalPer100g: Double? = null,
    public val proteinGPer100g: Double? = null,
    public val carbGPer100g: Double? = null,
    public val fatGPer100g: Double? = null,
    public val fiberGPer100g: Double? = null,
    /** OFF `serving_size` free text ("one glass (250 ml)") — a preset hint only. */
    public val servingSizeText: String? = null,
    /**
     * ODbL attribution line that MUST surface wherever this data renders
     * (the product screen footer, and receipts as provenance input).
     */
    public val attribution: String,
)

/** One lookup outcome: a hit (honestly labeled when served from cache) or a miss with the why. */
public sealed interface OffLookupResult {
    /**
     * A product row. [servedFromCache] is honest UI state: when true the UI
     * shows a "cached" chip and NO network happened for this call.
     */
    public data class Hit(
        public val product: OffProduct,
        public val servedFromCache: Boolean,
    ) : OffLookupResult

    /** No usable product; [reason] drives the degrade copy (never user-blaming). */
    public data class Miss(
        public val reason: MissReason,
        /** The barcode the miss is for (the manual path pre-fills it). */
        public val barcode: String,
    ) : OffLookupResult
}

/** Why an OFF lookup produced nothing (maps 1:1 onto the degrade copy). */
public enum class MissReason {
    /** The barcode resolves to no product (or one with no usable nutrition). */
    NOT_FOUND,

    /** Network unreachable / timed out / server error — airplane mode parity. */
    NETWORK_UNAVAILABLE,

    /** The R-C4 food-database integration toggle is off (the dispatcher denied). */
    DISABLED,
}
