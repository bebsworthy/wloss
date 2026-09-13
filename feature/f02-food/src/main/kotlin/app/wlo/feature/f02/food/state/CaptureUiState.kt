package app.wlo.feature.f02.food.state

import app.wlo.core.model.MealSlot
import app.wlo.core.ports.MissReason
import app.wlo.core.ports.OffProduct
import app.wlo.feature.f02.food.domain.NutritionLabelParser
import app.wlo.feature.f02.food.domain.ScanCorrectionLoop

/**
 * The capture flow's render state (F02 §3 rungs 1–3): viewfinder → analyzing
 * → the editable result card; the barcode/label assist stages; every failure
 * lands on a stage that KEEPS the manual twin one tap away (R-U15).
 */
public data class CaptureUiState(
    public val mode: CaptureLensMode = CaptureLensMode.PHOTO,
    public val stage: CaptureStage = CaptureStage.Viewfinder,
    public val slot: MealSlot = MealSlot.SNACK,
    /** Search results for the swap / add-item sheets. */
    public val searchResults: List<FoodHitUi> = emptyList(),
    public val searchQuery: String = "",
    public val searching: Boolean = false,
    /** The id of the item being swapped; null = "add missed item" sheet. */
    public val swappingItemId: Int? = null,
    public val notice: NoticeUi? = null,
) {
    /** The camera stays bound only while the viewfinder is front-and-center. */
    public val viewfinderActive: Boolean
        get() = stage is CaptureStage.Viewfinder

    public companion object {
        public val LOADING: CaptureUiState = CaptureUiState()
    }
}

/** The lens assists (F02 §3 rungs 1 and 3); voice is rung 2, lands later. */
public enum class CaptureLensMode {
    PHOTO,
    BARCODE,
    LABEL,
}

/** One capture-flow stage; the UI renders exactly one at a time. */
public sealed interface CaptureStage {
    /** The viewfinder: frame the plate, shutter, barcode stream, or label. */
    public data object Viewfinder : CaptureStage

    /** 600–900 ms shimmer as chips spring in (F02 §4) — analyzer in flight. */
    public data object Analyzing : CaptureStage

    /**
     * The editable result card — everything is a DRAFT until save (F02 §1:
     * "100% of AI-estimated values are editable before save; nothing
     * auto-commits").
     */
    public data class Result(
        public val items: List<ScanCorrectionLoop.ScanItem>,
        public val scanConfidence: Double?,
        /** Analyzer-level held (low top-1 confidence): amber strip, save still works (F02 §4). */
        public val held: Boolean,
        public val modelId: String,
        /** At least one item tripped a sanity rail (the marker + copy shows per item). */
        public val railTripped: Boolean,
    ) : CaptureStage {
        public val visibleItems: List<ScanCorrectionLoop.ScanItem>
            get() = items.filterNot(ScanCorrectionLoop.ScanItem::removed)
    }

    /**
     * The zoo model is absent (R-S14 download-on-first-use): the flow offers
     * the model manager AND the manual twin — never a dead end, never a crash.
     */
    public data class MissingModel(
        public val modelId: String,
        public val reason: String,
    ) : CaptureStage

    /** An OFF product for the scanned barcode (cached hits are labeled honestly). */
    public data class Product(
        public val product: OffProduct,
        public val servedFromCache: Boolean,
    ) : CaptureStage

    /** No OFF product (or OFF disabled): degrade to manual search / custom entry (R-C4, R-U15). */
    public data class ProductMiss(
        public val reason: MissReason,
        public val barcode: String,
    ) : CaptureStage

    /** OCR read the label: the prefilled DRAFT waits for explicit confirmation (R-U15). */
    public data class OcrDraft(
        public val parsed: NutritionLabelParser.ParsedLabel,
        public val draft: CustomFoodDraft,
    ) : CaptureStage

    /** Saving in flight (entries writing; the UI blocks double-saves). */
    public data object Saving : CaptureStage

    /** Saved: the honest end card (photo discarded at save — R-U14 default). */
    public data class Saved(
        public val entryCount: Int,
        public val kcal: Double,
    ) : CaptureStage
}

/** Adapter so the shared [NoticeUi] keeps its INFO/RAIL semantics. */
public fun MissReason.userCopy(): String =
    when (this) {
        MissReason.NOT_FOUND ->
            "no product under that barcode yet — search your catalog or type it in, both are right here"
        MissReason.NETWORK_UNAVAILABLE ->
            "Open Food Facts is out of reach right now (offline?) — catalog search " +
                "and manual entry work exactly as always"
        MissReason.DISABLED ->
            "food-database lookups are switched off — search and manual entry are unaffected"
    }
