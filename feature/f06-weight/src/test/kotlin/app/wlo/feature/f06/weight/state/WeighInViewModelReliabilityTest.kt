package app.wlo.feature.f06.weight.state

import app.wlo.core.common.AppError
import app.wlo.core.common.ClockPort
import app.wlo.core.common.MassUnit
import app.wlo.core.common.WloResult
import app.wlo.core.data.CurrentTrend
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.DayView
import app.wlo.core.data.DeletedWeighIn
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.NewMeasurement
import app.wlo.core.data.NewProfile
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.ReplacedWeighIn
import app.wlo.core.data.TargetsRepository
import app.wlo.core.data.WeighInOutcome
import app.wlo.core.data.WeighInRepository
import app.wlo.core.documents.Energy
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.documents.TargetsRecord
import app.wlo.core.documents.TargetsWriterId
import app.wlo.core.engines.OutlierVerdict
import app.wlo.core.engines.SmoothingEngine
import app.wlo.core.engines.WeightSample
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.MeasurementAttr
import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import app.wlo.core.model.Profile
import app.wlo.core.model.Provenance
import app.wlo.core.model.TrendMethod
import app.wlo.core.model.UnitSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WeighInViewModelReliabilityTest {
    @Test
    fun `goal progress handles no goal forming trend loss maintenance and gain`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val noGoal = viewModel()
                advanceUntilIdle()
                assertEquals(GoalProgressState.NO_GOAL, noGoal.uiState.value.goalProgress.state)

                val forming = viewModel(targets = StaticTargets(targetsRecord(75.0)))
                advanceUntilIdle()
                assertEquals(GoalProgressState.TREND_FORMING, forming.uiState.value.goalProgress.state)

                suspend fun stateFor(targetKg: Double): GoalProgressUi {
                    val weighIns = FakeWeighIns().apply { canonicalTrendKg = 80.0 }
                    val viewModel = viewModel(weighIns = weighIns, targets = StaticTargets(targetsRecord(targetKg)))
                    advanceUntilIdle()
                    return viewModel.uiState.value.goalProgress
                }

                val loss = stateFor(72.0)
                assertEquals(GoalProgressState.LOSS, loss.state)
                assertTrue(loss.rungs.size in 4..8)
                assertTrue(loss.rungs.all { it.rangeEpochDays == null })
                assertEquals(8.0, loss.remainingKg)
                assertEquals(GoalProgressState.MAINTENANCE, stateFor(80.0).state)
                assertEquals(GoalProgressState.GAIN, stateFor(85.0).state)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `successful save confirms persisted raw event and recomputed canonical trend`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val today =
                    java.time.LocalDate
                        .of(2026, 9, 16)
                        .toEpochDay()
                val weighIns =
                    FakeWeighIns().apply {
                        appendSucceeds = true
                        scalarSamples =
                            listOf(
                                WeightSample(today - 2, 81.0),
                                WeightSample(today - 1, 80.5),
                                WeightSample(today, 80.0),
                            )
                        canonicalTrendKg = 80.25
                        canonicalDeltaKg = -0.75
                    }
                val viewModel = viewModel(weighIns = weighIns, initialSheetOpen = true)
                advanceUntilIdle()

                viewModel.onEvent(WeighInEvent.WeightChange("79.8"))
                viewModel.onEvent(WeighInEvent.Save)
                advanceUntilIdle()

                val confirmation = assertNotNull(viewModel.confirmationState.value)
                assertEquals("saved-event", confirmation.eventId)
                assertEquals(79.8, confirmation.rawWeightKg)
                assertEquals(MeasurementSource.MANUAL, confirmation.source)
                assertEquals(80.25, confirmation.trend?.value)
                assertEquals(-0.75, confirmation.delta7?.value)
                assertEquals(3, confirmation.sampleCount)
                assertTrue(confirmation.hapticPending)
                assertEquals(WeighInSubmissionState.SUCCESS, viewModel.submissionState.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `confirmation haptic acknowledgement is one shot and Done clears the receipt`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val weighIns = FakeWeighIns().apply { appendSucceeds = true }
                val viewModel = viewModel(weighIns = weighIns, initialSheetOpen = true)
                advanceUntilIdle()
                viewModel.onEvent(WeighInEvent.WeightChange("79.8"))
                viewModel.onEvent(WeighInEvent.Save)
                advanceUntilIdle()
                val eventId = assertNotNull(viewModel.confirmationState.value).eventId

                viewModel.onEvent(WeighInEvent.ConfirmationHapticConsumed(eventId))
                assertFalse(assertNotNull(viewModel.confirmationState.value).hapticPending)
                viewModel.onEvent(WeighInEvent.ConfirmationHapticConsumed(eventId))
                assertFalse(assertNotNull(viewModel.confirmationState.value).hapticPending)

                viewModel.onEvent(WeighInEvent.DismissConfirmation)
                assertEquals(null, viewModel.confirmationState.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `failed save produces neither confirmation nor successful submission`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = viewModel(initialSheetOpen = true)
                advanceUntilIdle()
                viewModel.onEvent(WeighInEvent.WeightChange("79.8"))

                viewModel.onEvent(WeighInEvent.Save)
                advanceUntilIdle()

                assertEquals(null, viewModel.confirmationState.value)
                assertEquals(WeighInSubmissionState.FAILURE, viewModel.submissionState.value)
                assertNotNull(viewModel.sheetState.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `cancelled entry produces no confirmation`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = viewModel(initialSheetOpen = true)
                advanceUntilIdle()
                viewModel.onEvent(WeighInEvent.WeightChange("79.8"))

                viewModel.onEvent(WeighInEvent.DismissSheet)

                assertEquals(null, viewModel.confirmationState.value)
                assertEquals(null, viewModel.sheetState.value)
                assertEquals(WeighInSubmissionState.IDLE, viewModel.submissionState.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `sparse successful save keeps canonical raw value without inventing a trend`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val weighIns =
                    FakeWeighIns().apply {
                        appendSucceeds = true
                        scalarSamples = listOf(WeightSample(20_000, 80.0))
                        canonicalTrendKg = 80.0
                    }
                val viewModel =
                    viewModel(
                        weighIns = weighIns,
                        massUnits = flowOf(MassUnit.POUND),
                        initialSheetOpen = true,
                    )
                advanceUntilIdle()
                viewModel.onEvent(WeighInEvent.WeightChange("176.4"))

                viewModel.onEvent(WeighInEvent.Save)
                advanceUntilIdle()

                val confirmation = assertNotNull(viewModel.confirmationState.value)
                assertEquals(MassUnit.POUND.toKilograms(176.4), confirmation.rawWeightKg, 1e-9)
                assertEquals(1, confirmation.sampleCount)
                assertEquals(80.0, confirmation.trend?.value)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `first daily entry prefills from canonical trend in the active unit`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val weighIns = FakeWeighIns().apply { canonicalTrendKg = 81.6 }
                val viewModel = viewModel(weighIns = weighIns, massUnits = flowOf(MassUnit.POUND))
                advanceUntilIdle()

                viewModel.onEvent(WeighInEvent.OpenSheet())

                assertEquals(MassUnit.POUND.formatNumber(81.6), viewModel.sheetState.value?.weightText)
                assertEquals("Prefilled from your latest trend.", viewModel.sheetState.value?.prefillContext)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `empty history opens blank with explicit context`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = viewModel()
                advanceUntilIdle()

                viewModel.onEvent(WeighInEvent.OpenSheet())

                assertEquals("", viewModel.sheetState.value?.weightText)
                assertEquals("No previous reading yet.", viewModel.sheetState.value?.prefillContext)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `initial open sheet backfills after load but never overwrites typed input`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val trendGate = CompletableDeferred<Unit>()
                val weighIns = FakeWeighIns(currentTrendGate = trendGate).apply { canonicalTrendKg = 80.4 }
                val untouched = viewModel(weighIns = weighIns, initialSheetOpen = true)
                testScheduler.runCurrent()
                assertEquals("", untouched.sheetState.value?.weightText)

                trendGate.complete(Unit)
                advanceUntilIdle()
                assertEquals("80.4", untouched.sheetState.value?.weightText)

                val secondGate = CompletableDeferred<Unit>()
                val typedWeighIns = FakeWeighIns(currentTrendGate = secondGate).apply { canonicalTrendKg = 82.0 }
                val typed = viewModel(weighIns = typedWeighIns, initialSheetOpen = true)
                testScheduler.runCurrent()
                typed.onEvent(WeighInEvent.WeightChange("79.3"))
                secondGate.complete(Unit)
                advanceUntilIdle()

                assertEquals("79.3", typed.sheetState.value?.weightText)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `storage failure is explicit and retry recovers to successful empty`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val weighIns = FakeWeighIns().apply { currentTrendFails = true }
                val viewModel = viewModel(weighIns = weighIns)

                advanceUntilIdle()
                assertIs<WeightLoadState.Error>(viewModel.uiState.value.loadState)

                weighIns.currentTrendFails = false
                viewModel.onEvent(WeighInEvent.Refresh)
                advanceUntilIdle()

                assertIs<WeightLoadState.Empty>(viewModel.uiState.value.loadState)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `settings storage failure is explicit`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = viewModel(massUnits = flow { error("settings unavailable") })
                advanceUntilIdle()

                assertIs<WeightLoadState.Error>(viewModel.uiState.value.loadState)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `slow stale refresh cannot replace a newer snapshot`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val firstRead = CompletableDeferred<Unit>()
                val weighIns = FakeWeighIns(firstDailyScalarsGate = firstRead)
                val viewModel = viewModel(weighIns = weighIns)
                testScheduler.runCurrent()

                viewModel.onEvent(WeighInEvent.Refresh)
                testScheduler.runCurrent()
                assertIs<WeightLoadState.Empty>(viewModel.uiState.value.loadState)

                firstRead.complete(Unit)
                advanceUntilIdle()

                assertIs<WeightLoadState.Empty>(viewModel.uiState.value.loadState)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `refresh failure retains the last good weight content`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val weighIns =
                    FakeWeighIns().apply {
                        scalarSamples = listOf(WeightSample(20_000, 80.0))
                    }
                val viewModel = viewModel(weighIns = weighIns)
                advanceUntilIdle()
                assertIs<WeightLoadState.Content>(viewModel.uiState.value.loadState)
                val samples =
                    viewModel.uiState.value.trend
                        ?.samples

                weighIns.currentTrendFails = true
                viewModel.onEvent(WeighInEvent.Refresh)
                advanceUntilIdle()

                assertIs<WeightLoadState.Error>(viewModel.uiState.value.loadState)
                assertEquals(
                    samples,
                    viewModel.uiState.value.trend
                        ?.samples,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `refresh crosses local midnight without recreating the view model`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val clock = MutableClock(Instant.parse("2026-09-16T23:59:00Z"))
                val weighIns = FakeWeighIns()
                val viewModel = viewModel(clock, weighIns)
                advanceUntilIdle()
                val firstDay = weighIns.requestedDays.last()

                clock.instant = Instant.parse("2026-09-17T00:01:00Z")
                viewModel.onEvent(WeighInEvent.BoundaryCheck)
                advanceUntilIdle()

                assertEquals(firstDay + 1, weighIns.requestedDays.last())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `refresh and recreation derive the day from the current timezone`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val clock = MutableClock(Instant.parse("2026-09-16T00:30:00Z"))
                var zone: TimeZone = TimeZone.UTC
                val weighIns = FakeWeighIns()
                val first = viewModel(clock, weighIns) { zone }
                advanceUntilIdle()
                val utcDay = weighIns.requestedDays.last()

                zone = TimeZone.of("America/Los_Angeles")
                first.onEvent(WeighInEvent.BoundaryCheck)
                advanceUntilIdle()
                val travelledDay = weighIns.requestedDays.last()
                assertEquals(utcDay - 1, travelledDay)

                viewModel(clock, weighIns) { zone }
                advanceUntilIdle()
                assertEquals(travelledDay, weighIns.requestedDays.last())
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `three old points in selected window render trend despite no trailing week points`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val today =
                    java.time.LocalDate
                        .of(2026, 9, 16)
                        .toEpochDay()
                val weighIns =
                    FakeWeighIns().apply {
                        scalarSamples =
                            listOf(
                                WeightSample(today - 60, 81.0),
                                WeightSample(today - 45, 80.4),
                                WeightSample(today - 30, 79.8),
                            )
                    }
                val viewModel = viewModel(weighIns = weighIns)
                advanceUntilIdle()

                val trend = assertNotNull(viewModel.uiState.value.trend)
                assertTrue(trend.trendLineVisible)
                assertTrue(trend.trend.isNotEmpty())
                assertEquals(today - 89, trend.windowStartDay)
                assertEquals(today, trend.windowEndDay)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `two stale points hold trend and reference with lapsed copy`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val today =
                    java.time.LocalDate
                        .of(2026, 9, 16)
                        .toEpochDay()
                val weighIns =
                    FakeWeighIns().apply {
                        scalarSamples = listOf(WeightSample(today - 30, 81.0), WeightSample(today - 20, 80.0))
                    }
                val viewModel = viewModel(weighIns = weighIns)
                advanceUntilIdle()

                val trend = assertNotNull(viewModel.uiState.value.trend)
                assertFalse(trend.trendLineVisible)
                assertTrue(trend.trend.isEmpty())
                assertTrue(trend.reference.isEmpty())
                assertEquals("Last entry 27 Aug — the trend resumes when you do.", trend.stateCopy)
                assertTrue("latest 80.0 kg" in trend.description)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `empty selected window offers the smallest wider window containing data`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val today =
                    java.time.LocalDate
                        .of(2026, 9, 16)
                        .toEpochDay()
                val weighIns =
                    FakeWeighIns().apply {
                        filterScalarsByRange = true
                        scalarSamples = listOf(WeightSample(today - 40, 81.0))
                    }
                val viewModel = viewModel(weighIns = weighIns)
                advanceUntilIdle()

                viewModel.onEvent(WeighInEvent.WindowChange(ChartWindowUi.D30))
                advanceUntilIdle()

                val trend = assertNotNull(viewModel.uiState.value.trend)
                assertTrue(trend.samples.isEmpty())
                assertEquals(ChartWindowUi.D90, trend.emptyActionWindow)
                assertEquals("No weigh-ins in the last 30 days · latest entries 7 Aug", trend.stateCopy)
                assertEquals(today - 29, trend.windowStartDay)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `reference is gated identically with the selected window trend`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val today =
                    java.time.LocalDate
                        .of(2026, 9, 16)
                        .toEpochDay()
                val weighIns =
                    FakeWeighIns().apply {
                        filterScalarsByRange = true
                        scalarSamples =
                            listOf(
                                WeightSample(today - 60, 82.0),
                                WeightSample(today - 30, 81.0),
                                WeightSample(today, 80.0),
                            )
                    }
                val viewModel = viewModel(weighIns = weighIns)
                advanceUntilIdle()
                assertTrue(assertNotNull(viewModel.uiState.value.trend).reference.isNotEmpty())

                viewModel.onEvent(WeighInEvent.WindowChange(ChartWindowUi.D30))
                advanceUntilIdle()

                val held = assertNotNull(viewModel.uiState.value.trend)
                assertFalse(held.trendLineVisible)
                assertTrue(held.reference.isEmpty())
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun viewModel(
        clock: MutableClock = MutableClock(Instant.parse("2026-09-16T12:00:00Z")),
        weighIns: FakeWeighIns = FakeWeighIns(),
        massUnits: Flow<MassUnit> = flowOf(MassUnit.KILOGRAM),
        targets: TargetsRepository = EmptyTargets,
        initialSheetOpen: Boolean = false,
        zoneProvider: () -> TimeZone = { TimeZone.UTC },
    ): WeighInViewModel =
        WeighInViewModel(
            clock = clock,
            profiles = FakeProfiles,
            weighIns = weighIns,
            measurements = EmptyMeasurements,
            goalProgressLoader =
                GoalProgressLoader(
                    clock = clock,
                    targets = targets,
                    dayProjection = EmptyDayProjection,
                    readGoalSafetyInput = { null },
                    zoneProvider = zoneProvider,
                ),
            massUnits = massUnits,
            initialSheetOpen = initialSheetOpen,
            zoneProvider = zoneProvider,
        )
}

private class StaticTargets(
    private val record: TargetsRecord,
) : TargetsRepository {
    override suspend fun current(profileId: String): WloResult<TargetsRecord?> = WloResult.ok(record)

    override fun observeCurrent(profileId: String): Flow<WloResult<TargetsRecord?>> = flowOf(WloResult.ok(record))

    override suspend fun history(profileId: String): WloResult<List<TargetsRecord>> = WloResult.ok(listOf(record))
}

private fun targetsRecord(targetKg: Double): TargetsRecord =
    TargetsRecord(
        version = 1,
        createdAtEpochMs = 0,
        createdBy = TargetsWriterId.STUDIO_F01,
        document =
            TargetsDocument(
                goal = Goal(targetWeightKg = targetKg, pacePctPerWeek = 0.5),
                energy = Energy(budgetKcal = 1_900.0, floorKcal = 1_200.0),
                macros = Macros(MacroSplit.Preset("balanced")),
            ),
    )

private object EmptyTargets : TargetsRepository {
    override suspend fun current(profileId: String): WloResult<TargetsRecord?> = WloResult.ok(null)

    override fun observeCurrent(profileId: String): Flow<WloResult<TargetsRecord?>> = flowOf(WloResult.ok(null))

    override suspend fun history(profileId: String): WloResult<List<TargetsRecord>> = WloResult.ok(emptyList())
}

private object EmptyDayProjection : DayProjectionRepository {
    override fun observeDay(
        profileId: String,
        day: Long,
    ): Flow<WloResult<DayView>> = flow { error("unused") }

    override fun observeRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<WloResult<List<DayView>>> = flow { error("unused") }

    override suspend fun day(
        profileId: String,
        day: Long,
    ): WloResult<DayView> = error("unused")

    override suspend fun range(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<DayView>> = WloResult.ok(emptyList())

    override suspend fun recompute(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<Unit> = error("unused")
}

private class MutableClock(
    var instant: Instant,
) : ClockPort {
    override fun now(): Instant = instant
}

private object FakeProfiles : ProfileRepository {
    private val profile =
        Profile(
            id = "profile",
            sex = null,
            birthYear = null,
            heightCm = 170.0,
            startWeightKg = null,
            activityLevel = ActivityLevel.SEDENTARY,
            unitPreference = UnitSystem.METRIC,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

    override fun observeActive(): Flow<WloResult<Profile?>> = flowOf(WloResult.ok(profile))

    override suspend fun active(): WloResult<Profile?> = WloResult.ok(profile)

    override suspend fun byId(profileId: String): WloResult<Profile?> = WloResult.ok(profile)

    override suspend fun create(
        profile: NewProfile,
        at: Instant,
    ): WloResult<Profile> = WloResult.ok(this.profile)

    override suspend fun archive(
        profileId: String,
        at: Instant,
    ): WloResult<Unit> = WloResult.ok(Unit)

    override suspend fun setUnitPreference(
        profileId: String,
        unit: UnitSystem,
    ): WloResult<Unit> = WloResult.ok(Unit)

    override suspend fun updateFacts(
        profileId: String,
        sex: app.wlo.core.model.Sex?,
        birthYear: Int,
        heightCm: Double,
        activityLevel: ActivityLevel,
    ): WloResult<Unit> = WloResult.ok(Unit)
}

private object EmptyMeasurements : MeasurementRepository {
    override suspend fun append(event: NewMeasurement): WloResult<MeasurementEvent> = failure()

    override suspend fun attachAttrs(
        eventId: String,
        attrs: List<MeasurementAttr>,
    ): WloResult<Unit> = failure()

    override suspend fun range(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<MeasurementEvent>> = WloResult.ok(emptyList())

    override suspend fun rangeOfKind(
        profileId: String,
        kind: MeasurementKind,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<MeasurementEvent>> = WloResult.ok(emptyList())

    override suspend fun countOfKind(
        profileId: String,
        kind: MeasurementKind,
        fromDay: Long,
        toDay: Long,
    ): WloResult<Int> = WloResult.ok(0)

    override suspend fun byId(eventId: String): WloResult<MeasurementEvent?> = WloResult.ok(null)

    override fun observeRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<WloResult<List<MeasurementEvent>>> = flowOf(WloResult.ok(emptyList()))

    override suspend fun delete(eventId: String): WloResult<Unit> = failure()

    override suspend fun deleteTrendScalars(
        profileId: String,
        day: Long,
    ): WloResult<Unit> = failure()

    override suspend fun attrsOf(eventId: String): WloResult<List<MeasurementAttr>> = WloResult.ok(emptyList())

    override suspend fun attrsInRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<MeasurementAttr>> = WloResult.ok(emptyList())
}

private class FakeWeighIns(
    private val firstDailyScalarsGate: CompletableDeferred<Unit>? = null,
    private val currentTrendGate: CompletableDeferred<Unit>? = null,
) : WeighInRepository {
    var appendSucceeds: Boolean = false
    var currentTrendFails: Boolean = false
    var scalarSamples: List<WeightSample> = emptyList()
    var filterScalarsByRange: Boolean = false
    var canonicalTrendKg: Double? = null
    var canonicalDeltaKg: Double? = null
    val requestedDays = mutableListOf<Long>()
    private var dailyScalarCalls = 0

    override suspend fun appendWeighIn(
        profileId: String,
        dayEpochDay: Long,
        weightKg: Double,
        capturedAt: Instant,
        source: String,
        note: String?,
    ): WloResult<WeighInOutcome> =
        if (appendSucceeds) {
            WloResult.ok(
                WeighInOutcome(
                    event =
                        MeasurementEvent(
                            id = "saved-event",
                            profileId = profileId,
                            dayEpochDay = dayEpochDay,
                            kind = MeasurementKind.WEIGHT,
                            valueReal = weightKg,
                            unit = MassUnit.KILOGRAM.symbol,
                            source = source,
                            capturedAt = capturedAt,
                            note = note,
                        ),
                    verdict = OutlierVerdict.Quiet(residualKg = 0.0, sigma = 0.0),
                ),
            )
        } else {
            failure()
        }

    override suspend fun dayWeighIns(
        profileId: String,
        day: Long,
    ): WloResult<List<MeasurementEvent>> {
        requestedDays += day
        return WloResult.ok(emptyList())
    }

    override suspend fun lowestOfDay(
        profileId: String,
        day: Long,
    ): WloResult<MeasurementEvent?> = WloResult.ok(null)

    override suspend fun dailyScalars(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<WeightSample>> {
        dailyScalarCalls += 1
        if (dailyScalarCalls == 1 && firstDailyScalarsGate != null) {
            withContext(NonCancellable) { firstDailyScalarsGate.await() }
            return WloResult.ok(listOf(WeightSample(toDay, 80.0)))
        }
        return WloResult.ok(
            if (filterScalarsByRange) scalarSamples.filter { it.epochDay in fromDay..toDay } else scalarSamples,
        )
    }

    override suspend fun trend(
        profileId: String,
        fromDay: Long,
        toDay: Long,
        method: TrendMethod,
        alpha: Double,
    ) = WloResult.ok(
        SmoothingEngine.trend(
            if (filterScalarsByRange) scalarSamples.filter { it.epochDay in fromDay..toDay } else scalarSamples,
            method,
            alpha,
        ),
    )

    override suspend fun currentTrend(
        profileId: String,
        toDay: Long,
    ): WloResult<CurrentTrend> {
        currentTrendGate?.await()
        if (currentTrendFails) return failure()
        val current =
            canonicalTrendKg?.let { value ->
                DerivedValue(value, Provenance.Derived(formulaVersion = "test", inputs = emptyList()))
            }
        val delta =
            canonicalDeltaKg?.let { value ->
                DerivedValue(value, Provenance.Derived(formulaVersion = "test", inputs = emptyList()))
            }
        return WloResult.ok(CurrentTrend(scalarSamples, null, current, delta))
    }

    override suspend fun deleteWeighIn(
        eventId: String,
        at: Instant,
    ): WloResult<DeletedWeighIn> = failure()

    override suspend fun replaceWeighIn(
        eventId: String,
        dayEpochDay: Long,
        weightKg: Double,
        capturedAt: Instant,
        editedDescription: String,
    ): WloResult<ReplacedWeighIn> = failure()

    override suspend fun restoreWeighIn(snapshot: DeletedWeighIn): WloResult<MeasurementEvent> = failure()
}

private fun <T> failure(): WloResult<T> = WloResult.err(AppError.Storage(cause = null, detail = "test failure"))
