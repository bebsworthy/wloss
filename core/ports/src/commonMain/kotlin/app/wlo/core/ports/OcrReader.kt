package app.wlo.core.ports

import app.wlo.core.model.Analysis

/**
 * On-device OCR for crumpled labels and screenshot imports (F02); cloud OCR
 * would ride the `food-photo` capability (R-C3).
 */
public interface OcrReader {
    public suspend fun read(frame: CapturedFrame): Analysis<String>
}
