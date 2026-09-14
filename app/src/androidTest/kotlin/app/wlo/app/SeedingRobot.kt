package app.wlo.app

import app.wlo.app.demo.DemoSeed
import app.wlo.core.data.PlanView

/**
 * Deterministic seeding for the M3 instrumented tests — a thin delegate to the
 * debug source set's [DemoSeed] (androidTest compiles against the debug
 * variant), so the demo dataset is defined exactly once. `just demo`'s
 * DemoSeedReceiver drives the same object for a fresh install on the emulator.
 *
 * Semantics unchanged: onboards programmatically (the same write the wizard's
 * Start step performs — no wizard driving), then seeds the 7-day DiarySeeder
 * week through the REAL repositories in the app's own Koin graph. Runs after
 * the orchestrator's clearPackageData, so every test starts from a cold
 * install plus exactly this data. (The nested [SeedResult] / [SeriesWeighIn]
 * types mirror DemoSeed's so call sites keep spelling `SeedingRobot.` — the
 * seeding logic itself lives only in DemoSeed.)
 */
public object SeedingRobot {
    /** One seeded week: profile id + the epoch day of seeder day-offset 6 (today). */
    public data class SeedResult(
        public val profileId: String,
        public val today: Long,
    )

    /**
     * One row of the demo weigh-in series: how many days ago it was captured,
     * the reading, and its morning-window time (the double day adds an evening
     * re-weigh). Mirror of [DemoSeed.SeriesWeighIn].
     */
    public data class SeriesWeighIn(
        public val daysAgo: Long,
        public val weightKg: Double,
        public val hourOfDay: Int,
        public val minuteOfHour: Int,
    )

    /**
     * Deals a deterministic week plan through the real planner door (F03),
     * WLO-0033 wave 2: the Hub meals card + "log as planned" tests need slots
     * with recipes; the seeded targets cover the week's budgets.
     */
    public fun dealWeekPlan(
        profileId: String,
        startDayEpochDay: Long,
        seed: Long,
    ): PlanView =
        DemoSeed.dealWeekPlan(
            profileId = profileId,
            startDayEpochDay = startDayEpochDay,
            seed = seed,
        )

    /** Onboards like the wizard's Start write, then seeds the seeder week. */
    public fun onboardAndSeedWeek(): SeedResult {
        val seeded = DemoSeed.onboardAndSeedWeek()
        return SeedResult(profileId = seeded.profileId, today = seeded.today)
    }

    /** Seeds the DiarySeeder week so seeder day-offset 6 lands on [today]. */
    public fun seedWeek(
        profileId: String,
        today: Long,
    ): Unit =
        DemoSeed.seedWeek(
            profileId = profileId,
            today = today,
        )

    /** The demo series' morning reading — [DemoSeed.demoWeightKg]. */
    public fun demoWeightKg(dayOffsetFromStart: Int): Double = DemoSeed.demoWeightKg(dayOffsetFromStart)

    /** The full demo weigh-in series, newest last — [DemoSeed.demoWeighInSeries]. */
    public fun demoWeighInSeries(): List<SeriesWeighIn> =
        DemoSeed
            .demoWeighInSeries()
            .map { SeriesWeighIn(it.daysAgo, it.weightKg, it.hourOfDay, it.minuteOfHour) }

    /** The series length (days) — a season of daily morning weigh-ins. */
    public const val SERIES_DAYS: Int = DemoSeed.SERIES_DAYS

    /** Series endpoints: 78.2 kg declining to ~77.0 kg (~0.027 kg/day). */
    public const val SERIES_START_KG: Double = DemoSeed.SERIES_START_KG
    public const val SERIES_END_KG: Double = DemoSeed.SERIES_END_KG

    /** The morning weigh-in time — the same conditions every day. */
    public const val MORNING_HOUR: Int = DemoSeed.MORNING_HOUR
    public const val MORNING_MINUTE: Int = DemoSeed.MORNING_MINUTE

    /** The double-weigh-in day (R-B8): an evening re-weigh +0.3 kg above it. */
    public const val DOUBLE_DAY_DAYS_AGO: Long = DemoSeed.DOUBLE_DAY_DAYS_AGO
    public const val EVENING_DELTA_KG: Double = DemoSeed.EVENING_DELTA_KG

    /** Deterministic bounded noise (±0.35 kg), 15-entry cycle — [DemoSeed.NOISE_KG]. */
    public val NOISE_KG: List<Double>
        get() = DemoSeed.NOISE_KG
}
