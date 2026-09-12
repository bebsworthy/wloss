package app.wlo.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.FoodRepository
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.NewCustomFood
import app.wlo.core.data.NewDiaryEntry
import app.wlo.core.data.NewMeasurement
import app.wlo.core.data.NewProfile
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

            // The seeder's double weigh-in day, verbatim (R-B8).
            for (weighIn in DiarySeeder.weighIns()) {
                val day = today - (6 - weighIn.dayOffset)
                val at = DayBoundary.startOfDay(day, zone) + weighIn.hourOfDay.hours + weighIn.minuteOfHour.minutes
                weighIns.appendWeighIn(
                    profileId = profileId,
                    dayEpochDay = day,
                    weightKg = weighIn.weightKg,
                    capturedAt = at,
                    source = weighIn.source,
                )
            }
            // Quiet weigh-ins on every other day (plus two days before the
            // seeder week, so the 7-day delta has a lookback point): the
            // trailing-7-day trend gate (F06 §4 / F10 §4) passes and the
            // trend has data.
            val base =
                linkedMapOf(
                    -2 to 82.10,
                    -1 to 82.00,
                    0 to 81.90,
                    1 to 81.75,
                    2 to 81.60,
                    4 to 81.35,
                    5 to 81.15,
                    6 to 81.05,
                )
            for ((offset, kg) in base) {
                val day = today - (6 - offset)
                weighIns.appendWeighIn(
                    profileId = profileId,
                    dayEpochDay = day,
                    weightKg = kg,
                    capturedAt = DayBoundary.startOfDay(day, zone) + 7.hours,
                    source = MeasurementSource.MANUAL,
                )
            }
        }
}
