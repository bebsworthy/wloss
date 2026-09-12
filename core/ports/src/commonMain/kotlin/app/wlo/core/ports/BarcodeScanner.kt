package app.wlo.core.ports

import app.wlo.core.model.Analysis

/**
 * Barcode scanner for packaged-food entry (F02 §capture) and pantry stock-take
 * (F04); one shared scan stack (R-U12 discipline, DRY §2.5).
 */
public interface BarcodeScanner {
    public suspend fun scanNext(): Analysis<Barcode>?
}

/** A decoded code; [format] is an informational vendor string (EAN-13, QR...). */
public data class Barcode(
    public val value: String,
    public val format: String,
)
