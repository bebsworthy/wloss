package app.wlo.core.ports

import app.wlo.core.model.ConsentCapability

/**
 * A camera/memory frame handed to capture ports. Frames are MEMORY-ONLY:
 * persistence paths accept only derived, user-visible artifacts — for the
 * silhouette pipeline, vector outlines exclusively (R-U16).
 */
public class CapturedFrame(
    public val bytes: ByteArray,
    public val widthPx: Int,
    public val heightPx: Int,
)

/**
 * The single network choke point (F12 §3.8): every egress call carries its
 * [ConsentCapability], needs a current grant, and issues a hash-chained
 * receipt. Implemented ONLY by `:core:network` (D1: features cannot even see
 * the implementation; `:app` binds it).
 */
public interface EgressPort {
    public suspend fun <R : Any> dispatch(request: EgressRequest<R>): Result<R>
}

/** A consent-gated, receipt-emitting egress operation. */
public class EgressRequest<R : Any>(
    public val capability: ConsentCapability,
    public val operation: String,
    public val block: suspend () -> R,
)
