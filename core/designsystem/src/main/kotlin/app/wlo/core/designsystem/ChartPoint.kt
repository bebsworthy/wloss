package app.wlo.core.designsystem

/** One chart point: an epoch day and its kg value (unit formatting stays at render). */
public data class ChartPoint(
    public val epochDay: Long,
    public val value: Double,
)
