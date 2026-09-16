package app.wlo.feature.f10.hub.state

import app.wlo.core.common.AppError
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.MassUnit
import app.wlo.core.common.WloResult
import app.wlo.core.data.CurrentTrend
import app.wlo.core.data.DayDiary
import app.wlo.core.data.DayDiaryTotals
import app.wlo.core.data.DayView
import app.wlo.core.documents.Energy
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.documents.TargetsRecord
import app.wlo.core.documents.TargetsWriterId
import app.wlo.core.engines.DayPhase
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.Profile
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.Sex
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafetyInput
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelReliabilityTest {
    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun repositoryFailureIsRecoverableAndRetryRecovers() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture()
            fixture.rangeResult = failure("range")
            val viewModel = fixture.viewModel()

            advanceUntilIdle()
            assertIs<HubUiState.RecoverableError>(viewModel.uiState.value)

            fixture.rangeResult = WloResult.ok(emptyList())
            viewModel.refresh()
            advanceUntilIdle()
            assertIs<HubUiState.Ready>(viewModel.uiState.value)
        }

    @Test
    fun profileFailureNeverLooksLikeFreshSetup() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture()
            fixture.profile.value = failure("profile")
            val viewModel = fixture.viewModel()

            advanceUntilIdle()

            assertIs<HubUiState.RecoverableError>(viewModel.uiState.value)
        }

    @Test
    fun refreshCrossesPhaseAndLocalMidnight() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture(at = "2026-09-16T10:29:00Z")
            val viewModel = fixture.viewModel()
            advanceUntilIdle()
            val morning = assertIs<HubUiState.Ready>(viewModel.uiState.value)
            assertEquals(DayPhase.MORNING, morning.phase)
            val firstDay = fixture.observedDays.last()

            fixture.clock.setTo(Instant.parse("2026-09-16T10:30:00Z").toEpochMilliseconds())
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(DayPhase.MIDDAY, assertIs<HubUiState.Ready>(viewModel.uiState.value).phase)

            fixture.clock.setTo(Instant.parse("2026-09-17T00:00:00Z").toEpochMilliseconds())
            viewModel.refresh()
            advanceUntilIdle()
            assertNotEquals(firstDay, fixture.observedDays.last())
        }

    @Test
    fun timezoneTravelRebindsTheLocalDay() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture(at = "2026-09-16T23:30:00Z")
            val viewModel = fixture.viewModel()
            advanceUntilIdle()
            val utcDay = fixture.observedDays.last()

            fixture.zone = TimeZone.of("Europe/Paris")
            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(utcDay + 1, fixture.observedDays.last())
        }

    @Test
    fun rapidRefreshesPublishOnlyTheLatestGeneration() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture(at = "2026-09-16T08:00:00Z")
            val viewModel = fixture.viewModel()
            advanceUntilIdle()

            fixture.clock.setTo(Instant.parse("2026-09-17T08:00:00Z").toEpochMilliseconds())
            viewModel.refresh()
            fixture.clock.setTo(Instant.parse("2026-09-18T08:00:00Z").toEpochMilliseconds())
            viewModel.refresh()
            advanceUntilIdle()

            val ready = assertIs<HubUiState.Ready>(viewModel.uiState.value)
            assertEquals(2L, ready.refreshGeneration)
            assertEquals(
                DayBoundary.epochDay(fixture.clock.now(), TimeZone.UTC),
                fixture.observedDays.last(),
            )
        }

    @Test
    fun slowOldDayCannotOverwriteFastRefreshedDay() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture(at = "2026-09-16T08:00:00Z")
            fixture.blockedDay = DayBoundary.epochDay(fixture.clock.now(), TimeZone.UTC)
            val viewModel = fixture.viewModel()
            advanceUntilIdle()
            assertIs<HubUiState.Loading>(viewModel.uiState.value)

            fixture.clock.setTo(Instant.parse("2026-09-17T08:00:00Z").toEpochMilliseconds())
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(1L, assertIs<HubUiState.Ready>(viewModel.uiState.value).refreshGeneration)

            fixture.releaseBlockedDay.complete(Unit)
            advanceUntilIdle()
            val afterOldCompletes = assertIs<HubUiState.Ready>(viewModel.uiState.value)
            assertEquals(1L, afterOldCompletes.refreshGeneration)
            assertEquals(
                DayBoundary.epochDay(fixture.clock.now(), TimeZone.UTC),
                fixture.observedDays.last(),
            )
        }

    @Test
    fun processRecreationDerivesStateFromCurrentClockAndRepositories() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture(at = "2026-09-16T18:59:00Z")
            val first = fixture.viewModel()
            advanceUntilIdle()
            assertEquals(DayPhase.MIDDAY, assertIs<HubUiState.Ready>(first.uiState.value).phase)

            fixture.clock.setTo(Instant.parse("2026-09-16T19:00:00Z").toEpochMilliseconds())
            val recreated = fixture.viewModel()
            advanceUntilIdle()

            assertEquals(DayPhase.EVENING, assertIs<HubUiState.Ready>(recreated.uiState.value).phase)
        }

    @Test
    fun targetWithoutSafetyAttestationNeverProducesAHubDate() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture()
            fixture.profile.value = WloResult.ok(completeProfile())
            fixture.targetsRecord = targetsRecord()

            val viewModel = fixture.viewModel()
            advanceUntilIdle()

            assertNull(assertIs<HubUiState.Ready>(viewModel.uiState.value).forecast)
        }

    @Test
    fun eligibleLossShowsDevelopingRangeWithoutCentralDate() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val fixture = Fixture()
            fixture.profile.value = WloResult.ok(completeProfile())
            fixture.targetsRecord = targetsRecord()
            fixture.attestation = eligibleAttestation()

            val viewModel = fixture.viewModel()
            advanceUntilIdle()

            val forecast = assertNotNull(assertIs<HubUiState.Ready>(viewModel.uiState.value).forecast)
            assertTrue(forecast.bands.expectedKg.isNotEmpty())
            assertTrue(forecast.bands.optimisticKg.isNotEmpty())
            assertTrue(forecast.bands.pessimisticKg.isNotEmpty())
            assertFalse(forecast.bands.pointDateEligible)
            assertNull(forecast.bands.expectedFinishEpochDay)
        }

    private class Fixture(
        at: String = "2026-09-16T08:00:00Z",
    ) {
        val clock = FakeClock(Instant.parse(at).toEpochMilliseconds())
        var zone: TimeZone = TimeZone.UTC
        val profile = MutableStateFlow<WloResult<Profile?>>(WloResult.ok(profile()))
        val observedDays = mutableListOf<Long>()
        var rangeResult: WloResult<List<DayView>> = WloResult.ok(emptyList())
        var targetsRecord: TargetsRecord? = null
        var attestation: WeightGoalSafetyInput? = null
        var blockedDay: Long? = null
        val releaseBlockedDay = CompletableDeferred<Unit>()

        fun viewModel(): HubViewModel =
            HubViewModel(
                clock = clock,
                profiles = proxy { name, _ -> if (name == "observeActive") profile else unused(name) },
                dayProjection =
                    proxy { name, args ->
                        when (name) {
                            "observeDay" -> {
                                val day = args[1] as Long
                                observedDays += day
                                flow {
                                    if (day == blockedDay) releaseBlockedDay.await()
                                    emit(WloResult.ok(dayView(day)))
                                }
                            }
                            "range" -> rangeResult
                            else -> unused(name)
                        }
                    },
                targets =
                    proxy { name, _ ->
                        when (name) {
                            "observeCurrent" -> flowOf(WloResult.ok(targetsRecord))
                            else -> unused(name)
                        }
                    },
                readGoalSafetyInput = { attestation },
                weighIns =
                    proxy { name, _ ->
                        when (name) {
                            "currentTrend" -> WloResult.ok(CurrentTrend(emptyList(), null, null, null))
                            "dayWeighIns" -> WloResult.ok(emptyList<Any>())
                            else -> unused(name)
                        }
                    },
                diary =
                    proxy { name, args ->
                        when (name) {
                            "day" -> emptyDiary(args[1] as Long)
                            else -> unused(name)
                        }
                    },
                planner =
                    proxy { name, _ ->
                        when (name) {
                            "currentPlan" -> WloResult.ok(null)
                            else -> unused(name)
                        }
                    },
                massUnit = flowOf(MassUnit.KILOGRAM),
                currentTimeZone = { zone },
            )

        private fun dayView(day: Long): DayView =
            DayView(
                profileId = PROFILE_ID,
                dayEpochDay = day,
                budgetKcal = null,
                proteinG = null,
                carbG = null,
                fatG = null,
                fiberG = null,
                waterMl = null,
                trendWeightKg = null,
                intakeKcal = null,
                burnKcal = null,
            )

        private fun emptyDiary(day: Long): WloResult<DayDiary> =
            WloResult.ok(
                DayDiary(
                    profileId = PROFILE_ID,
                    dayEpochDay = day,
                    slots = emptyMap(),
                    entries = emptyList(),
                    totals = DayDiaryTotals(0.0, null, null, null, null),
                ),
            )
    }

    companion object {
        private const val PROFILE_ID = "profile"

        private fun profile(): Profile =
            Profile(
                id = PROFILE_ID,
                sex = null,
                birthYear = null,
                heightCm = null,
                startWeightKg = 80.0,
                activityLevel = ActivityLevel.SEDENTARY,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            )

        private fun completeProfile(): Profile =
            profile().copy(
                sex = Sex.FEMALE,
                birthYear = 1990,
                heightCm = 170.0,
            )

        private fun targetsRecord(): TargetsRecord =
            TargetsRecord(
                version = 1,
                createdAtEpochMs = 0,
                createdBy = TargetsWriterId.STUDIO_F01,
                document =
                    TargetsDocument(
                        goal = Goal(targetWeightKg = 74.0, pacePctPerWeek = 0.5),
                        energy = Energy(budgetKcal = 1_900.0, floorKcal = 1_200.0),
                        macros = Macros(split = MacroSplit.Preset("balanced")),
                    ),
            )

        private fun eligibleAttestation(): WeightGoalSafetyInput =
            WeightGoalSafetyInput(
                ageYears = 36,
                mode = WeightGoalMode.LOSS,
                currentWeightKg = 82.0,
                targetWeightKg = 74.0,
                requestedPacePctPerWeek = 0.5,
                plannedDailyEnergyKcal = 1_900.0,
                minimumDailyEnergyKcal = 1_200.0,
                pregnant = SafetyAnswer.NO,
                breastfeeding = SafetyAnswer.NO,
                eatingDisorderConcern = SafetyAnswer.NO,
                medicallyInfluencedWeight = SafetyAnswer.NO,
            )

        private fun failure(detail: String): WloResult.Err = WloResult.Err(AppError.Storage(null, detail))

        private fun unused(name: String): Nothing = error("Unexpected repository call: $name")

        private inline fun <reified T : Any> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                call(method.name, args.orEmpty())
            } as T
    }
}
