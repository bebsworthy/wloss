package app.wlo.core.engines

/**
 * Current multi-oracle streak (F11 "Gamification engine", frozen: a day
 * counts via weigh-in OR meal-log OR workout, user-tunable). PURE and total
 * — values in, values out; no clock (the caller owns "today") and no gate
 * (D7: nothing is guessed — the function counts exactly the days the caller
 * proved, so it always returns, never refuses).
 *
 * The engine is deliberately **oracle-agnostic**: the CALLER reduces the
 * diary to [countedDays] — the set of epoch days on which at least one
 * enabled oracle fired (weigh-in, meal-log, or workout); this object only
 * walks the set. The later gamification ledger (freeze tokens, badges,
 * repair — F11 "State owned") will supersede but not change this counting
 * rule; it consumes it.
 *
 * Anchoring rule (the definition this object exists to freeze): the streak
 * is the run of consecutive counted days ending at [asOfDay] — or, when
 * [asOfDay] itself has not counted yet, ending at `asOfDay - 1` (the intact
 * streak shown on a morning before the first log; the Hub chip "ticks after
 * the log"). If neither `asOfDay` nor `asOfDay - 1` is in [countedDays],
 * the streak is 0 — a missed yesterday already broke it.
 *
 * Edge rules: empty history → 0; a gap in the set breaks the run naturally;
 * never negative; days in the set after [asOfDay] are ignored (future plans
 * don't tick the streak). The walk is a backwards while loop — O(streak),
 * no allocation beyond the counter.
 */
public object StreakMetrics {
    public fun currentStreak(
        countedDays: Set<Long>,
        asOfDay: Long,
    ): Int {
        val anchor =
            when {
                asOfDay in countedDays -> asOfDay
                asOfDay - 1 in countedDays -> asOfDay - 1
                // Neither today nor yesterday counted: the last run is dead.
                else -> return 0
            }
        var day = anchor
        var streak = 0
        while (day in countedDays) {
            streak++
            day--
        }
        return streak
    }
}
