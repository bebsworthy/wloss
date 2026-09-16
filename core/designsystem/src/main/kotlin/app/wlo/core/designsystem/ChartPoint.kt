package app.wlo.core.designsystem

/** The semantic role of a point, independent of how the chart paints it. */
public enum class ChartSeriesRole {
    RAW,
    DAILY,
    TREND,
    FORECAST,
    BODY_METHOD,
}

/**
 * One chart point and the provenance needed by accessible detail/list views.
 * Defaults preserve callers that only have the historical day/value pair.
 */
public data class ChartPoint(
    public val epochDay: Long,
    public val value: Double,
    public val stableKey: String = "$epochDay:$value",
    public val captureTimeEpochMs: Long? = null,
    public val displayUnit: String = "",
    public val role: ChartSeriesRole = ChartSeriesRole.DAILY,
    public val sourceEventIds: List<String> = emptyList(),
    public val provenance: String? = null,
    public val holdReason: String? = null,
    public val methodLabel: String? = null,
)
