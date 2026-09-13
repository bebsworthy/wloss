package app.wlo.feature.f02.food.state

import app.wlo.core.ports.CapturedFrame
import app.wlo.core.ports.OffProduct

/**
 * Capture-flow intents (MVI-lite): one channel in, [CaptureUiState] out. The
 * frame events carry memory-only [CapturedFrame]s straight from the camera
 * surface (R-U14 pipeline; the ViewModel holds no reference after analysis).
 */
public sealed interface CaptureEvent {
    /** Lens chip change (photo / barcode / label). */
    public data class ModeChange(
        public val mode: CaptureLensMode,
    ) : CaptureEvent

    /** One throttled preview frame from the viewfinder (barcode decode feed). */
    public data class PreviewFrame(
        public val frame: CapturedFrame,
    ) : CaptureEvent

    /** The shutter took one still (photo or label analysis input). */
    public data class StillCaptured(
        public val frame: CapturedFrame,
    ) : CaptureEvent

    /** The typed-barcode manual twin (R-U15) — same lookup as a scan. */
    public data class ManualBarcode(
        public val barcode: String,
    ) : CaptureEvent

    /** (Re)arms the barcode scan loop when the barcode viewfinder shows. */
    public data object StartScanning : CaptureEvent

    public data object StopScanning : CaptureEvent

    // --- correction-loop edits (everything editable before save, F02 §1) ---

    public data class ItemGramsChange(
        public val itemId: Int,
        public val text: String,
    ) : CaptureEvent

    public data class ItemRemove(
        public val itemId: Int,
    ) : CaptureEvent

    /** Opens the swap search for one chip (null = "add missed item"). */
    public data class OpenSwap(
        public val itemId: Int?,
    ) : CaptureEvent

    public data object DismissSearch : CaptureEvent

    public data class SwapQueryChange(
        public val text: String,
    ) : CaptureEvent

    public data class PickSwap(
        public val hit: FoodHitUi,
    ) : CaptureEvent

    /** Saves every visible chip to the diary (the loop's hero action). */
    public data object Save : CaptureEvent

    /** Back to the viewfinder (retake / next scan). */
    public data object Retake : CaptureEvent

    // --- product stage ---

    public data class ProductAddToDiary(
        public val product: OffProduct,
        public val grams: Double,
    ) : CaptureEvent

    public data class ProductSaveAsFood(
        public val product: OffProduct,
    ) : CaptureEvent

    // --- label draft stage ---

    public data class OcrDraftChange(
        public val draft: CustomFoodDraft,
    ) : CaptureEvent

    /** The explicit confirm on the prefilled draft (R-U15: never auto-saves). */
    public data object OcrSave : CaptureEvent

    public data object DismissNotice : CaptureEvent
}

/** OFF product → catalog row (source = OFF; R-C4). */
internal fun app.wlo.core.ports.OffProduct.toNewCustomFood(profileId: String): app.wlo.core.data.NewCustomFood =
    app.wlo.core.data.NewCustomFood(
        profileId = profileId,
        name = name,
        brand = brand,
        kcalPer100g = kcalPer100g,
        proteinGPer100g = proteinGPer100g,
        carbGPer100g = carbGPer100g,
        fatGPer100g = fatGPer100g,
        fiberGPer100g = fiberGPer100g,
        source = app.wlo.core.model.FoodSource.OFF,
    )
