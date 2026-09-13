package app.wlo.core.ports

import app.wlo.core.model.Analysis

/**
 * Barcode scanner for packaged-food entry (F02 §3 rung 3) and pantry
 * stock-take (F04); one shared scan stack (R-U12 discipline, DRY §2.5).
 * Push-pull shape: the camera surface streams memory-only preview frames in
 * through [observe] (R-U14 pipeline; nothing persists), the caller awaits the
 * next decode via [scanNext]. Implementations must fall back internally when
 * their primary engine is unavailable (the WLO stack: ML Kit bundled → ZXing
 * — see the audit cards under docs/tech/audit/) and must never throw on a
 * bad frame: a frame that decodes to nothing is silence, not an error.
 */
public interface BarcodeScanner {
    /** Feeds one preview frame; fire-and-forget, safe at analysis frame rate. */
    public fun observe(frame: CapturedFrame)

    /**
     * Suspends until the next barcode decodes (one result per call, then wait
     * for the next); null only when cancelled — callers own their timeouts.
     */
    public suspend fun scanNext(): Analysis<Barcode>?
}

/** A decoded code; [format] is an informational vendor string (EAN-13, QR...). */
public data class Barcode(
    public val value: String,
    public val format: String,
)
