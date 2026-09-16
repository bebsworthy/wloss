package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.datastore.SettingsStoreFactory
import app.wlo.core.engines.WeightSample
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.Provenance
import app.wlo.core.model.TrendMethod
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * WLO-0030 defect 9: the Hub (persisted day projection) and the weight page
 * (repository recompute over its own window) showed two different trend
 * numbers. These tests pin the fix by construction — every surface input goes
 * through [RoomWeighInRepository.currentTrend] — and the seed sanity (defect
 * 10) on a seeded DB: a gentle ~45-day decline must leave EWMA(α=0.15) within
 * ~0.5 kg of the latest raw reading.
 */
class CurrentTrendTest {
    private val dir = Files.createTempDirectory("wlo-trend-test")
    private val db: WloDatabase = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()
    private val settings = SettingsStoreFactory.create(dir.resolve("settings.preferences_pb").toString().toPath())

    private val profiles = RoomProfileRepository(db, settings)
    private val projector = DayProjector(db, clock)
    private val measurements = RoomMeasurementRepository(db, projector)
    private val weighIns = RoomWeighInRepository(db, measurements, projector)
    private val targets = RoomTargetsRepository(db)
    private val projection = RoomDayProjectionRepository(db, projector, targets)

    /** The demo series window: 45 days ending "today" (the seeder's shape). */
    private val today = 20_708L // 2026-09-12
    private val seriesDays: Long = 45

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun <T> WloResult<T>.okOrDie(): T =
        when (this) {
            is WloResult.Ok -> value
            is WloResult.Err -> error("unexpected error: ${error.debugMessage} (cause: ${error.cause})")
        }

    /** Deterministic gentle decline: 78.2 → ~77.0, ±0.35 kg of fixed noise. */
    private fun seededKg(offsetFromStart: Int): Double {
        val decline = 78.2 - offsetFromStart * (1.2 / (seriesDays - 1))
        val noise = NOISE[offsetFromStart % NOISE.size]
        return decline + noise
    }

    /** One weigh-in per day, ~06:30, oldest first — the seeder's parameters. */
    private suspend fun seedSeries(profileId: String) {
        for (day in 0 until seriesDays.toInt()) {
            weighIns
                .appendWeighIn(
                    profileId = profileId,
                    dayEpochDay = today - (seriesDays - 1) + day,
                    weightKg = seededKg(day),
                    capturedAt = Instant.parse("2026-09-12T06:30:00Z"),
                ).okOrDie()
        }
    }

    @Test
    fun hubProjectionAndSharedReadCarryTheIdenticalNumber() =
        runTest {
            val profileId =
                profiles
                    .create(NewProfile(birthYear = 1990, heightCm = 178.0, startWeightKg = 78.2), clock.now())
                    .okOrDie()
                    .id
            seedSeries(profileId)

            // The Hub's input: the persisted day projection scalar...
            val projected = assertNotNull(projection.day(profileId, today).okOrDie().trendWeightKg)
            // ...and the F06 default view's input: the shared read. Identical
            // by construction — both come from currentTrend(profileId, today).
            // (The projection re-chips provenance as projection/event-sum-v1 —
            // the VALUE is what must provably match.)
            val shared = weighIns.currentTrend(profileId, today).okOrDie()
            val current = assertNotNull(shared.current)
            assertEquals(projected.value, current.value, absoluteTolerance = 1e-12)
        }

    @Test
    fun sharedReadUsesTheCanonicalWindowNotTheChartWindow() =
        runTest {
            val profileId =
                profiles
                    .create(NewProfile(birthYear = 1990, heightCm = 178.0, startWeightKg = 78.2), clock.now())
                    .okOrDie()
                    .id
            seedSeries(profileId)

            val shared = weighIns.currentTrend(profileId, today).okOrDie()
            val current = assertNotNull(shared.current)

            // The canonical answer IS the default smoother over the canonical
            // 30-day window — exactly one window, one smoother selection.
            val canonical =
                weighIns
                    .trend(
                        profileId,
                        today - RoomWeighInRepository.TREND_WINDOW_DAYS + 1,
                        today,
                        TrendMethod.EWMA,
                        ConstantsRegistry.EWMA_ALPHA_DEFAULT,
                    ).okOrDie()
            val canonicalPoint = assertNotNull(canonical.points.lastOrNull())
            assertEquals(canonicalPoint.trendKg.value, current.value, absoluteTolerance = 1e-12)

            // Root cause of the old 77.7-vs-77.6 split, pinned: the old F06
            // query (90-day chart window) seeds EWMA differently and lands on
            // a DIFFERENT number than the persisted 30-day answer.
            val chartWindow =
                weighIns
                    .trend(profileId, today - 89, today, TrendMethod.EWMA, ConstantsRegistry.EWMA_ALPHA_DEFAULT)
                    .okOrDie()
            val chartPoint = chartWindow.points.last()
            assertTrue(
                abs(chartPoint.trendKg.value - current.value) > 1e-9,
                "a 90-day window must NOT reproduce the canonical answer (that was the bug)",
            )
        }

    @Test
    fun delta7ReadsTheSameSeriesSevenDaysBack() =
        runTest {
            val profileId =
                profiles
                    .create(NewProfile(birthYear = 1990, heightCm = 178.0, startWeightKg = 78.2), clock.now())
                    .okOrDie()
                    .id
            seedSeries(profileId)

            val shared = weighIns.currentTrend(profileId, today).okOrDie()
            val delta = assertNotNull(shared.delta7)
            val byDay = assertNotNull(shared.series).points.associate { it.epochDay to it.trendKg.value }
            val last = shared.samples.last().epochDay
            val expected = assertNotNull(byDay[last]) - assertNotNull(byDay[last - RoomWeighInRepository.DELTA_WINDOW_DAYS])
            assertEquals(expected, delta.value, absoluteTolerance = 1e-12)
            // A gentle decline gives a negative weekly delta of a sane size.
            assertTrue(delta.value < 0.0 && delta.value > -1.0, "weekly delta sane: ${delta.value}")
        }

    @Test
    fun delta30ReadsAcrossTheFullCanonicalWindow() =
        runTest {
            val profileId =
                profiles
                    .create(NewProfile(birthYear = 1990, heightCm = 178.0, startWeightKg = 78.2), clock.now())
                    .okOrDie()
                    .id
            seedSeries(profileId)

            val shared = weighIns.currentTrend(profileId, today).okOrDie()
            val series = assertNotNull(shared.series)
            val delta = assertNotNull(shared.delta30)
            val first = series.points.first()
            val last = series.points.last()
            val expected = last.trendKg.value - first.trendKg.value
            assertEquals(
                expected,
                delta.value,
                absoluteTolerance = 1e-12,
            )
            assertEquals(listOf("windowDays=30"), (delta.provenance as Provenance.Derived).inputs)
        }

    @Test
    fun gentleDeclineSeedKeepsEwmaNearTheRawReading() =
        runTest {
            val profileId =
                profiles
                    .create(NewProfile(birthYear = 1990, heightCm = 178.0, startWeightKg = 78.2), clock.now())
                    .okOrDie()
                    .id
            seedSeries(profileId)

            val shared = weighIns.currentTrend(profileId, today).okOrDie()
            val scalars: List<WeightSample> = shared.samples
            assertTrue(scalars.size >= 30, "the canonical window holds the full 30 days")
            val latestRaw = scalars.last().weightKg
            val trendNow = assertNotNull(shared.current).value
            assertTrue(
                abs(trendNow - latestRaw) <= 0.5,
                "EWMA(0.15) $trendNow must sit within 0.5 kg of the raw $latestRaw (WLO-0030 defect 10)",
            )
        }

    @Test
    fun emptyWindowAnswersNullWithoutErroring() =
        runTest {
            val profileId =
                profiles
                    .create(NewProfile(birthYear = 1990, heightCm = 178.0, startWeightKg = 78.2), clock.now())
                    .okOrDie()
                    .id
            val shared = weighIns.currentTrend(profileId, today).okOrDie()
            assertEquals(0, shared.samples.size)
            assertEquals(null, shared.series)
            assertEquals(null, shared.current)
            assertEquals(null, shared.delta7)
            assertEquals(null, shared.delta30)
        }

    @Test
    fun legacyPersistedTrendIsRebuiltBeforeCanonicalReadReturns() =
        runTest {
            val profileId = profiles.create(NewProfile(), clock.now()).okOrDie().id
            measurements
                .append(
                    NewMeasurement(
                        profileId = profileId,
                        dayEpochDay = today,
                        kind = MeasurementKind.WEIGHT,
                        valueReal = 80.0,
                        source = "manual",
                        capturedAt = Instant.parse("2026-09-12T07:00:00Z"),
                    ),
                ).okOrDie()
            measurements
                .append(
                    NewMeasurement(
                        profileId = profileId,
                        dayEpochDay = today,
                        kind = MeasurementKind.WEIGHT,
                        valueReal = 79.0,
                        source = "manual",
                        capturedAt = Instant.parse("2026-09-12T18:00:00Z"),
                    ),
                ).okOrDie()
            measurements
                .append(
                    NewMeasurement(
                        profileId = profileId,
                        dayEpochDay = today,
                        kind = MeasurementKind.TREND,
                        valueReal = 79.0,
                        source = "engine",
                        capturedAt = Instant.parse("2026-09-12T18:00:00Z"),
                    ),
                ).okOrDie()

            val canonical = assertNotNull(weighIns.currentTrend(profileId, today).okOrDie().current).value
            val persisted =
                measurements
                    .rangeOfKind(profileId, MeasurementKind.TREND, today, today)
                    .okOrDie()
                    .single()
                    .valueReal

            assertEquals(canonical, persisted, absoluteTolerance = 1e-12)
            assertEquals(80.0, persisted, absoluteTolerance = 1e-12)
        }

    private companion object {
        /** Fixed ±0.35 kg noise pattern (no randomness — hand-checkable). */
        val NOISE: List<Double> =
            listOf(0.12, -0.31, 0.05, 0.22, -0.18, -0.35, 0.09, 0.27, -0.08, 0.33, -0.24, 0.02, 0.16, -0.29, 0.11)
    }
}
