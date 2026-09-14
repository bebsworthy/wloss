package app.wlo.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.FoodRepository
import app.wlo.core.data.GenerateWeekPlan
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.NewCustomFood
import app.wlo.core.data.NewDiaryEntry
import app.wlo.core.data.NewMeasurement
import app.wlo.core.data.NewProfile
import app.wlo.core.data.PlanView
import app.wlo.core.data.PlannerRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.TargetsWriteOutcome
import app.wlo.core.data.TargetsWriters
import app.wlo.core.data.WeighInRepository
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.documents.DietTemplate
import app.wlo.core.documents.DietTemplateApplier
import app.wlo.core.documents.OnboardingTemplates
import app.wlo.core.model.EntryVia
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import app.wlo.core.model.Sex
import app.wlo.core.testing.DiarySeeder
import app.wlo.feature.f01.onboarding.domain.FinishOnboarding
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Deterministic seeding for the M3 instrumented tests: onboards programmatically
 * (the same write the wizard's Start step performs — no wizard driving), then
 * seeds the 7-day [DiarySeeder] week through the REAL repositories in the app's
 * own Koin graph (the instrumentation runs in the app process). Runs after the
 * orchestrator's clearPackageData, so every test starts from a cold install
 * plus exactly this data.
 */
public object SeedingRobot {
    /** One seeded week: profile id + the epoch day of seeder day-offset 6 (today). */
    public data class SeedResult(
        public val profileId: String,
        public val today: Long,
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
        runBlocking {
            val planner = koin().get<PlannerRepository>()
            val result =
                planner.generateWeek(
                    GenerateWeekPlan(
                        profileId = profileId,
                        startDayEpochDay = startDayEpochDay,
                        days = 7,
                        seed = seed,
                    ),
                )
            check(result is WloResult.Ok) { "plan deal failed: $result" }
            result.value
        }

    /** The app's own Koin (the Application started it in this process). */
    private fun koin(): org.koin.core.Koin =
        org.koin.core.context.GlobalContext
            .get()

    /** Onboards like the wizard's Start write, then seeds the seeder week. */
    public fun onboardAndSeedWeek(): SeedResult =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val clock = koin().get<ClockPort>()
            val zone = TimeZone.currentSystemDefault()
            val now = clock.now()
            val today = DayBoundary.epochDay(now, zone)

            val profiles = koin().get<ProfileRepository>()
            val measurements = koin().get<MeasurementRepository>()
            val writers = koin().get<TargetsWriters>()
            val documents = koin().get<JsonDocumentStore>()

            val assets = context.assets
            val templates: List<DietTemplate> =
                OnboardingTemplates.load { path ->
                    assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            val template = templates.firstOrNull { it.isDefault } ?: templates.first()
            val targetsDoc =
                DietTemplateApplier.toTargetsDocument(
                    template,
                    FinishOnboarding.applierContext(
                        sex = Sex.FEMALE,
                        birthYear = 1990,
                        heightCm = 168.0,
                        currentWeightKg = 82.0,
                        goalWeightKg = 74.0,
                        pacePctPerWeek = 0.5,
                        formulaTdeeKcal = null,
                    ),
                )
            val created =
                profiles.create(NewProfile(sex = Sex.FEMALE, birthYear = 1990, heightCm = 168.0, startWeightKg = 82.0), now)
            check(created is WloResult.Ok) { "profile create failed" }
            val profileId = created.value.id

            // The wizard's anchor trend scalar + Targets v1 + the completion flag.
            measurements.append(
                NewMeasurement(
                    profileId = profileId,
                    dayEpochDay = today,
                    kind = MeasurementKind.TREND,
                    valueReal = 82.0,
                    source = "onboarding",
                    capturedAt = now,
                ),
            )
            val write = writers.studio().writeFirst(profileId = profileId, document = targetsDoc)
            check(write is TargetsWriteOutcome.Written) { "targets write failed" }
            documents.writeFlag(FinishOnboarding.FLAG_COMPLETE, true)

            seedWeek(profileId, today)
            SeedResult(profileId, today)
        }

    /** Seeds the [DiarySeeder] week so seeder day-offset 6 lands on [today]. */
    public fun seedWeek(
        profileId: String,
        today: Long,
    ): Unit =
        runBlocking {
            val clock = koin().get<ClockPort>()
            val zone = TimeZone.currentSystemDefault()
            val foodsRepo = koin().get<FoodRepository>()
            val diary = koin().get<DiaryRepository>()
            val weighIns = koin().get<WeighInRepository>()
            val now = clock.now()

            val foodIds =
                DiarySeeder
                    .foods()
                    .mapNotNull { seed ->
                        val outcome =
                            foodsRepo.createCustomFood(
                                NewCustomFood(
                                    profileId = profileId,
                                    name = seed.name,
                                    brand = seed.brand,
                                    kcalPer100g = seed.kcalPer100g,
                                    proteinGPer100g = seed.proteinGPer100g,
                                    carbGPer100g = seed.carbGPer100g,
                                    fatGPer100g = seed.fatGPer100g,
                                    fiberGPer100g = seed.fiberGPer100g,
                                    macrosVerified = true,
                                ),
                                now,
                            )
                        (outcome as? WloResult.Ok)?.value?.id?.let { id -> seed.key to id }
                    }.toMap()

            for (entry in DiarySeeder.entries()) {
                val day = today - (6 - entry.dayOffset)
                val isWater = entry.foodKey == "water"
                diary.logEntry(
                    entry =
                        NewDiaryEntry(
                            profileId = profileId,
                            dayEpochDay = day,
                            mealSlot = entry.slot,
                            foodItemId = if (isWater) null else foodIds[entry.foodKey],
                            textHint = entry.textHint.takeIf { isWater },
                            quantity = entry.quantity,
                            unit = entry.unit,
                            kcalOnly = if (isWater) 0.0 else null,
                            enteredVia = if (isWater) EntryVia.QUICK_ADD else EntryVia.MANUAL_SEARCH,
                        ),
                    at = now,
                )
            }

            // The demo weigh-in series (WLO-0030 defects 10 + 13): ~45 days of
            // gentle decline with bounded noise — the trend sits WITHIN ~0.5 kg
            // of the latest raw reading, so the hero never reads as nonsense.
            // Includes one double-weigh-in day, verbatim (R-B8): the evening
            // re-weigh lands HIGHER, so lowest-of-day stays the morning value.
            for (weighIn in demoWeighInSeries()) {
                val day = today - weighIn.daysAgo
                val at =
                    DayBoundary.startOfDay(day, zone) +
                        weighIn.hourOfDay.hours +
                        weighIn.minuteOfHour.minutes
                weighIns.appendWeighIn(
                    profileId = profileId,
                    dayEpochDay = day,
                    weightKg = weighIn.weightKg,
                    capturedAt = at,
                    source = MeasurementSource.MANUAL,
                )
            }
        }

    /**
     * One row of the demo weigh-in series: how many days ago it was captured,
     * the reading, and its morning-window time (the double day adds an evening
     * re-weigh).
     */
    public data class SeriesWeighIn(
        public val daysAgo: Long,
        public val weightKg: Double,
        public val hourOfDay: Int,
        public val minuteOfHour: Int,
    )

    /** The series length (days) — a season of daily morning weigh-ins. */
    public const val SERIES_DAYS: Int = 45

    /** Series endpoints: 78.2 kg declining to ~77.0 kg (~0.027 kg/day). */
    public const val SERIES_START_KG: Double = 78.2
    public const val SERIES_END_KG: Double = 77.0

    /** The morning weigh-in time — the same conditions every day. */
    public const val MORNING_HOUR: Int = 6
    public const val MORNING_MINUTE: Int = 30

    /** The double-weigh-in day (R-B8): an evening re-weigh +0.3 kg above it. */
    public const val DOUBLE_DAY_DAYS_AGO: Long = 3
    public const val EVENING_DELTA_KG: Double = 0.3

    /**
     * Deterministic bounded noise (±0.35 kg), 15-entry cycle; the LAST
     * entry is 0.0 so today's raw reading sits exactly on the trend line —
     * no randomness anywhere, expectations stay hand-checkable.
     */
    public val NOISE_KG: List<Double> =
        listOf(
            0.12,
            -0.31,
            0.05,
            0.22,
            -0.18,
            -0.35,
            0.09,
            0.27,
            -0.08,
            0.33,
            -0.24,
            0.02,
            0.16,
            -0.29,
            0.0,
        )

    /**
     * The demo series' morning reading for the day [dayOffsetFromStart]
     * days after the series start (0 = first day). Gentle decline + noise.
     */
    public fun demoWeightKg(dayOffsetFromStart: Int): Double {
        val span = SERIES_DAYS - 1
        val decline = SERIES_START_KG - (SERIES_START_KG - SERIES_END_KG) * dayOffsetFromStart / span
        return decline + NOISE_KG[dayOffsetFromStart % NOISE_KG.size]
    }

    /**
     * The full demo weigh-in series, newest last: one morning reading per
     * day for [SERIES_DAYS] days plus the evening re-weigh on the double
     * day (higher — lowest-of-day keeps the morning value).
     */
    public fun demoWeighInSeries(): List<SeriesWeighIn> {
        val mornings =
            (0 until SERIES_DAYS).map { offset ->
                SeriesWeighIn(
                    daysAgo = (SERIES_DAYS - 1).toLong() - offset,
                    weightKg = demoWeightKg(offset),
                    hourOfDay = MORNING_HOUR,
                    minuteOfHour = MORNING_MINUTE,
                )
            }
        val doubleDay =
            SeriesWeighIn(
                daysAgo = DOUBLE_DAY_DAYS_AGO,
                weightKg = demoWeightKg(SERIES_DAYS - 1 - DOUBLE_DAY_DAYS_AGO.toInt()) + EVENING_DELTA_KG,
                hourOfDay = 19,
                minuteOfHour = 45,
            )
        return mornings + doubleDay
    }
}
