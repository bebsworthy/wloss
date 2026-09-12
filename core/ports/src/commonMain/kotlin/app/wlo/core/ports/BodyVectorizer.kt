package app.wlo.core.ports

import app.wlo.core.model.Analysis

/**
 * Silhouette capture (F08). Output is a VECTOR OUTLINE ONLY — body photographs
 * are never stored (owner ruling R-U16); camera frames stay memory-only and
 * the persistence path rejects anything but [VectorOutline].
 */
public interface BodyVectorizer {
    public suspend fun vectorize(frame: CapturedFrame): Analysis<VectorOutline>
}

/** Normalized outline points (x,y pairs in 0..1), renderable and storable. */
public data class VectorOutline(
    public val points: List<Double>,
)
