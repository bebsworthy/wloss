package app.wlo.core.ports

import app.wlo.core.model.ConsentCapability
import okio.Sink

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
 * WHY a packet leaves the device (F12 §3.8, F13 §9). Every egress request
 * declares exactly one purpose; enforcement follows the purpose, not the
 * caller's word:
 *
 *  - [ZOO_DOWNLOAD] — R-S14 model zoo: never consent-gated (on-device
 *    inference is always allowed), but only served when the request is
 *    user-initiated (the ModelManager flow with size disclosure).
 *  - [OFF_LOOKUP] — R-C4, verbatim: "F13 integration toggle (not an F12 AI
 *    category), default on, cached, per-lookup audit trail". Served cache-first
 *    while the integration toggle is on; every lookup gets a receipt row.
 *  - [DIAGNOSTICS] — T-K4 (q-000026): opt-in, CONTENT-FREE crash reports.
 *    NOT an F12 AI category (the taxonomy is frozen at six, R-C1) — an
 *    explicit settings toggle backed by [DiagnosticsPolicy], default OFF.
 *    Every report is scrubbed to a field whitelist before dispatch (see
 *    :core:network AcraReportSender) and rides the dispatcher with receipts.
 *  - [FUTURE_*] — every other packet class fails CLOSED unless the mapped
 *    [ConsentCapability] currently holds a grant (F12 §3.1 matrix).
 */
public enum class EgressPurpose(
    public val wireName: String,
    /** The consent category that must be granted, or null for non-AI purposes. */
    public val requiresCapability: ConsentCapability?,
) {
    ZOO_DOWNLOAD("zoo-download", requiresCapability = null),
    OFF_LOOKUP("off-lookup", requiresCapability = null),
    DIAGNOSTICS("diagnostics", requiresCapability = null),
    FUTURE_CLOUD_VISION("future-cloud-vision", ConsentCapability.FOOD_PHOTO),
    FUTURE_CLOUD_STT("future-cloud-stt", ConsentCapability.VOICE_INPUT),
    FUTURE_CLOUD_MEAL_PLAN("future-cloud-meal-plan", ConsentCapability.MEAL_PLANNING),
    FUTURE_CLOUD_SILHOUETTE("future-cloud-silhouette", ConsentCapability.SILHOUETTE),
    FUTURE_CLOUD_POOP("future-cloud-poop", ConsentCapability.POOP_PHOTO),
    FUTURE_CLOUD_CHAT("future-cloud-chat", ConsentCapability.INSIGHTS_CHAT),
}

/**
 * A consent-gated, receipt-emitting egress operation (F12 §3.8): the single
 * socket door. Every request carries its purpose, the host it claims to reach
 * and the expected payload size (receipted; download responses larger than the
 * declared expectation are cut). [cacheKey] opts into the dispatcher's
 * cache-first store (R-C4: OFF lookups are "cached"); [userInitiated] is
 * required true for [EgressPurpose.ZOO_DOWNLOAD].
 */
public class EgressRequest<R : Any>(
    public val purpose: EgressPurpose,
    public val host: String,
    public val operation: String,
    public val expectedBytes: Long? = null,
    public val cacheKey: String? = null,
    public val userInitiated: Boolean = false,
    public val block: suspend () -> R,
)

/**
 * A streaming body download (R-S14 zoo). The dispatcher performs the HTTP GET
 * itself and pumps the bytes into the caller's [Sink] — the caller never sees
 * a socket — reporting progress through [onProgress]. [expectedBytes] is both
 * receipted and enforced as a hard cap.
 */
public class EgressDownload(
    public val modelId: String,
    public val url: String,
    public val expectedBytes: Long,
    public val userInitiated: Boolean,
    public val onProgress: (bytesSoFar: Long) -> Unit = {},
)

/** Why a dispatched call was refused (D8: sealed failures ride Result). */
public class EgressDeniedException(
    public val purpose: EgressPurpose,
    public val reason: String,
) : IllegalStateException("egress denied [$purpose]: $reason")

/**
 * The single network choke point (F12 §3.8): every egress call carries its
 * [EgressPurpose], passes enforcement (consent grant, R-C4 allowance or
 * user-initiated zoo flow), and issues a hash-chained receipt — success OR
 * failure. Implemented ONLY by `:core:network` (D1: features cannot even see
 * the implementation; `:app` binds it).
 */
public interface EgressPort {
    public suspend fun <R : Any> dispatch(request: EgressRequest<R>): Result<R>

    /** Stream [EgressDownload.url] into [sink]; returns the bytes written. */
    public suspend fun download(
        request: EgressDownload,
        sink: Sink,
    ): Result<Long>
}

/**
 * The R-C4 food-database integration toggle, injected into the dispatcher.
 * Ruling wording (FEATURES.md §3): "F13 integration toggle (not an F12 AI
 * category), default on, cached, per-lookup audit trail." Default-on until F13
 * ships the settings surface (M6); the dispatcher never grants OFF lookups
 * through the six AI categories.
 */
public fun interface OffLookupPolicy {
    public suspend fun isFoodDbLookupAllowed(): Boolean
}

/**
 * The T-K4 diagnostics toggle (q-000026): opt-in, content-free crash reports —
 * deliberately NOT a [ConsentCapability] (that taxonomy is frozen at six AI
 * categories, R-C1; ACRA is not an AI feature). Default implementation is
 * deny-by-default; production wiring reads the explicit settings toggle.
 */
public fun interface DiagnosticsPolicy {
    public suspend fun isDiagnosticsEgressAllowed(): Boolean
}
