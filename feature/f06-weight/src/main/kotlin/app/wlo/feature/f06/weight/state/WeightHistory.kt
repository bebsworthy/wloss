package app.wlo.feature.f06.weight.state

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/**
 * The compressed weight history (WLO-0055): one bucket per row, the further
 * back the coarser — each day for the last [DAILY_DAYS] days, then
 * [WEEKLY_WEEKS] Mon–Sun weeks, then [MONTHLY_MONTHS] calendar months, then
 * quarter by quarter up to [QUARTERLY_QUARTERS]. The verbatim feed lives on
 * the logbook screen; this is the weight surface's summary of the same data.
 */
public enum class HistoryTier(
    public val label: String,
) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
}

/** One raw weigh-in reduced to what the compression reads. */
public data class WeightSample(
    public val dayEpochDay: Long,
    public val kg: Double,
)

/**
 * One history bucket: its tier and date span, the raw weigh-in count, the
 * closing day's weight (the lowest reading of the bucket's last day with
 * data — the same lowest-of-day canonical the trend uses) and the change vs
 * the previous (older) bucket. An empty bucket keeps its row with a null
 * weight — gaps are data, not absence of UI.
 */
public data class HistoryBucket(
    public val tier: HistoryTier,
    public val startDay: Long,
    public val endDay: Long,
    public val count: Int,
    public val endValueKg: Double?,
    public val deltaKg: Double?,
)

public object WeightHistory {
    public const val DAILY_DAYS: Int = 7
    public const val WEEKLY_WEEKS: Int = 4
    public const val MONTHLY_MONTHS: Int = 4
    public const val QUARTERLY_QUARTERS: Int = 12

    /**
     * Builds the buckets, newest first. Buckets tile the span from [floorDay]
     * to [today] without overlap; anything older than the floor is the full
     * logbook's business. Deltas chain bucket-to-bucket, carrying across an
     * empty bucket (the change then spans the gap).
     */
    public fun build(
        samples: List<WeightSample>,
        today: Long,
        rawCountByDay: Map<Long, Int>? = null,
        dailyDays: Int = DAILY_DAYS,
        weeklyWeeks: Int = WEEKLY_WEEKS,
        monthlyMonths: Int = MONTHLY_MONTHS,
        quarterlyQuarters: Int = QUARTERLY_QUARTERS,
    ): List<HistoryBucket> {
        if (samples.isEmpty()) return emptyList()
        val floor = floorDay(today, quarterlyQuarters)
        val byDay = samples.groupBy { it.dayEpochDay }
        val canonical = byDay.mapValues { (_, day) -> day.minOf { it.kg } }.filterKeys { it in floor..today }

        // Date ranges, newest first — daily, then weeks, months, quarters,
        // each clipped so the tiers partition the span.
        val ranges = ArrayList<Range>()
        var end = today
        repeat(dailyDays) {
            if (end < floor) return@repeat
            ranges += Range(HistoryTier.DAILY, end, end)
            end -= 1
        }
        repeat(weeklyWeeks) {
            if (end < floor) return@repeat
            val monday = startOfWeek(end)
            val start = maxOf(monday, floor)
            val last = minOf(end, monday + 6)
            if (start <= last) ranges += Range(HistoryTier.WEEKLY, start, last)
            end = monday - 1
        }
        repeat(monthlyMonths) {
            if (end < floor) return@repeat
            val monthStart = monthStart(end)
            val next = monthStart.plus(DatePeriod(months = 1))
            val start = maxOf(monthStart.toEpochDays(), floor)
            val last = minOf(end, next.toEpochDays() - 1)
            if (start <= last) ranges += Range(HistoryTier.MONTHLY, start, last)
            end = monthStart.toEpochDays() - 1
        }
        while (end >= floor) {
            val quarterStart = quarterStart(end)
            val next = quarterStart.plus(DatePeriod(months = 3))
            val start = maxOf(quarterStart.toEpochDays(), floor)
            val last = minOf(end, next.toEpochDays() - 1)
            if (start <= last) ranges += Range(HistoryTier.QUARTERLY, start, last)
            end = quarterStart.toEpochDays() - 1
        }

        // Oldest first while chaining deltas, then flip to display order.
        var previous: Double? = null
        return ranges
            .asReversed()
            .map { range ->
                val days = (range.start..range.end).filter { canonical.containsKey(it) }
                val count = days.sumOf { day -> rawCountByDay?.get(day) ?: byDay.getValue(day).size }
                val endValue = days.maxOrNull()?.let { canonical.getValue(it) }
                val delta = if (endValue != null && previous != null) endValue - previous!! else null
                previous = endValue ?: previous
                HistoryBucket(range.tier, range.start, range.end, count, endValue, delta)
            }.asReversed()
    }

    /**
     * The oldest day the card reads: the start of the quarter
     * [quarterlyQuarters] back, counting the current partial one. The bounded
     * query's `fromDay`.
     */
    public fun floorDay(
        today: Long,
        quarterlyQuarters: Int,
    ): Long {
        val quarterStart = quarterStart(today)
        return quarterStart
            .plus(DatePeriod(months = -3 * (quarterlyQuarters - 1)))
            .toEpochDays()
    }

    private fun startOfWeek(day: Long): Long = day - (LocalDate.fromEpochDays(day).dayOfWeek.isoDayNumber - 1)

    private fun monthStart(day: Long): LocalDate {
        val date = LocalDate.fromEpochDays(day)
        return LocalDate(date.year, date.monthNumber, 1)
    }

    private fun quarterStart(day: Long): LocalDate {
        val date = LocalDate.fromEpochDays(day)
        return LocalDate(date.year, (date.monthNumber - 1) / 3 * 3 + 1, 1)
    }

    private data class Range(
        val tier: HistoryTier,
        val start: Long,
        val end: Long,
    )
}

/** Bucket and month label formatting, shared by the card and the logbook feed. */
public object HistoryLabels {
    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTHS_SHORT =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTHS_FULL =
        listOf(
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December",
        )

    /** The history card's bucket label ("Today", "Thu 11", "Aug 25 – 31", "Sep 2026", "Q2 2026"). */
    public fun bucketLabel(
        bucket: HistoryBucket,
        today: Long,
    ): String =
        when (bucket.tier) {
            HistoryTier.DAILY -> rowDayLabel(bucket.endDay, today)
            HistoryTier.WEEKLY -> spanLabel(bucket.startDay, bucket.endDay)
            HistoryTier.MONTHLY -> {
                val date = LocalDate.fromEpochDays(bucket.startDay)
                "${MONTHS_SHORT[date.monthNumber - 1]} ${date.year}"
            }
            HistoryTier.QUARTERLY -> {
                val date = LocalDate.fromEpochDays(bucket.startDay)
                "Q${(date.monthNumber - 1) / 3 + 1} ${date.year}"
            }
        }

    /** The logbook's sticky month header, uppercase — the month the rows omit. */
    public fun monthHeader(monthStartDay: Long): String {
        val date = LocalDate.fromEpochDays(monthStartDay)
        return "${MONTHS_FULL[date.monthNumber - 1].uppercase()} ${date.year}"
    }

    /** The logbook row's date label — no month on the row, the header carries it. */
    public fun rowDayLabel(
        day: Long,
        today: Long,
    ): String =
        when (day) {
            today -> "Today"
            today - 1L -> "Yesterday"
            else -> {
                val date = LocalDate.fromEpochDays(day)
                "${WEEKDAYS[date.dayOfWeek.isoDayNumber - 1]} ${date.dayOfMonth}"
            }
        }

    private fun spanLabel(
        start: Long,
        end: Long,
    ): String {
        val from = LocalDate.fromEpochDays(start)
        val to = LocalDate.fromEpochDays(end)
        return if (from.monthNumber == to.monthNumber) {
            "${MONTHS_SHORT[from.monthNumber - 1]} ${from.dayOfMonth} – ${to.dayOfMonth}"
        } else {
            val fromLabel = "${MONTHS_SHORT[from.monthNumber - 1]} ${from.dayOfMonth}"
            val toLabel = "${MONTHS_SHORT[to.monthNumber - 1]} ${to.dayOfMonth}"
            "$fromLabel – $toLabel"
        }
    }
}
