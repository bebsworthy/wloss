package app.wlo.core.ports

import app.wlo.core.model.Analysis
import app.wlo.core.model.BristolType

/**
 * On-device Bristol classifier for stool photos (F09); the cloud path is gated
 * by the `poop-photo` capability and the one-tap manual override is always
 * equal-status (R-U15).
 */
public interface StoolClassifier {
    public suspend fun classify(frame: CapturedFrame): Analysis<BristolType>
}
