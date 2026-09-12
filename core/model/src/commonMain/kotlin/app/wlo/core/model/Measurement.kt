package app.wlo.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The measurement-event kinds the spine stores at event level (R-B8): every
 * timestamped data point is kept verbatim — multiple weigh-ins per day are
 * normal data, never collapsed or overwritten. Day-level values are derived
 * views produced by the projection pipeline, never by overwriting events.
 */
@Serializable
public enum class MeasurementKind(
    public val wireName: String,
    public val unit: String,
) {
    /** A bodyweight event (scale, typed, OCR, import). Unit kg. */
    @SerialName("weight")
    WEIGHT("weight", "kg"),

    /** A trend-weight scalar (computed by F06's smoother; one owner). Unit kg. */
    @SerialName("trend")
    TREND("trend", "kg"),

    /** Daily intake energy (F02/F03 diary). Unit kcal. */
    @SerialName("intake")
    INTAKE("intake", "kcal"),

    /** Daily burn/expenditure context (F05/F13). Unit kcal. */
    @SerialName("burn")
    BURN("burn", "kcal"),

    /**
     * A body-fat % event (F06 §3 method registry: one series per method — the
     * method rides the EAV sidecar as `method=<wire>`). Unit %.
     */
    @SerialName("body-fat")
    BODY_FAT("body-fat", "%"),

    /**
     * An arbitrary user-defined metric (F06 §3 EAV store: "weight and the
     * built-ins are just pre-registered types"). The metric name rides the
     * EAV sidecar as `metric=<name>`; the unit is per-metric and is ALWAYS
     * supplied via the append call's unit override — [unit] is the empty
     * default, meaning "no unit declared".
     */
    @SerialName("custom")
    CUSTOM("custom", ""),
    ;

    public companion object {
        /** Parses a wire name; `null` for unknown strings (callers decide policy). */
        public fun fromWireName(name: String): MeasurementKind? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * One event-level measurement (R-B8). `capturedAt` is the true capture moment;
 * `day` is the calendar day (epoch day, user's zone) used for day-level views.
 * Custom metrics ride the EAV sidecar, not extra columns.
 */
@Serializable
public data class MeasurementEvent(
    public val id: String,
    public val profileId: String,
    public val dayEpochDay: Long,
    public val kind: MeasurementKind,
    public val valueReal: Double,
    public val unit: String,
    public val source: String,
    public val capturedAt: Instant,
    public val note: String? = null,
)

/** One custom-metric attribute attached to a measurement event (EAV sidecar, F13 §3). */
@Serializable
public data class MeasurementAttr(
    public val eventId: String,
    public val attr: String,
    public val valueText: String? = null,
    public val valueReal: Double? = null,
)

/** Event source vocabulary (open set; these are the v1 producers). */
public object MeasurementSource {
    public const val MANUAL: String = "manual"
    public const val SCALE: String = "scale"
    public const val OCR: String = "ocr"
    public const val IMPORT: String = "import"
    public const val HEALTH_CONNECT: String = "health-connect"
    public const val ENGINE: String = "engine"
}
