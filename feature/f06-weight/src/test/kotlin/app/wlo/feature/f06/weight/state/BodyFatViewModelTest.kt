package app.wlo.feature.f06.weight.state

import androidx.lifecycle.SavedStateHandle
import app.wlo.core.common.ClockPort
import app.wlo.core.common.MassUnit
import app.wlo.core.common.WloResult
import app.wlo.core.data.BodyMeasurementCommand
import app.wlo.core.data.BodyMeasurementResult
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.NewMeasurement
import app.wlo.core.data.NewProfile
import app.wlo.core.data.ProfileRepository
import app.wlo.core.datastore.SettingsStoreFactory
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.MeasurementAttr
import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.Profile
import app.wlo.core.model.Sex
import app.wlo.core.model.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BodyFatViewModelTest {
    @Test
    fun `unit toggles preserve canonical tape precision`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val directory = Files.createTempDirectory("body-fat-units")
                val settings =
                    SettingsStoreFactory.create(
                        directory.resolve("settings.preferences_pb").toString().toPath(),
                    )
                settings.setMassUnit(MassUnit.POUND)
                val measurements = RecordingMeasurements()
                val viewModel =
                    BodyFatViewModel(FixedBodyClock, BodyProfileRepository, measurements, settings)
                advanceUntilIdle()
                viewModel.onEvent(BodyFatEvent.WaistChange("40"))
                viewModel.onEvent(BodyFatEvent.NeckChange("15"))

                settings.setMassUnit(MassUnit.KILOGRAM)
                advanceUntilIdle()
                settings.setMassUnit(MassUnit.POUND)
                advanceUntilIdle()
                viewModel.onEvent(BodyFatEvent.Compute)
                viewModel.onEvent(BodyFatEvent.SaveToLogbook)
                advanceUntilIdle()

                val waist =
                    measurements.commands
                        .single()
                        .inputs
                        .single { it.name == "waist" }
                assertEquals(101.6, waist.centimeters, 1e-9)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `editing a restored committed draft starts a new operation`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val directory = Files.createTempDirectory("body-fat-replay")
                val settings =
                    SettingsStoreFactory.create(
                        directory.resolve("settings.preferences_pb").toString().toPath(),
                    )
                settings.setMassUnit(MassUnit.POUND)
                val measurements = RecordingMeasurements()
                val handle = SavedStateHandle()

                fun viewModel() =
                    BodyFatViewModel(
                        clock = FixedBodyClock,
                        profiles = BodyProfileRepository,
                        measurements = measurements,
                        settings = settings,
                        savedStateHandle = handle,
                    )

                val first = viewModel()
                advanceUntilIdle()
                first.onEvent(BodyFatEvent.WaistChange("40"))
                first.onEvent(BodyFatEvent.NeckChange("15"))
                first.onEvent(BodyFatEvent.Compute)
                first.onEvent(BodyFatEvent.SaveToLogbook)
                advanceUntilIdle()

                val restored = viewModel()
                advanceUntilIdle()
                restored.onEvent(BodyFatEvent.WaistChange("41"))
                restored.onEvent(BodyFatEvent.Compute)
                restored.onEvent(BodyFatEvent.SaveToLogbook)
                advanceUntilIdle()

                assertEquals(2, measurements.commands.size)
                assertNotEquals(measurements.commands[0].operationId, measurements.commands[1].operationId)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `formula edit invalidates result and inches save once as canonical centimeters`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val directory = Files.createTempDirectory("body-fat-vm")
                val settings =
                    SettingsStoreFactory.create(
                        directory.resolve("settings.preferences_pb").toString().toPath(),
                    )
                settings.setMassUnit(MassUnit.POUND)
                val measurements = RecordingMeasurements()
                val viewModel =
                    BodyFatViewModel(
                        clock = FixedBodyClock,
                        profiles = BodyProfileRepository,
                        measurements = measurements,
                        settings = settings,
                    )
                advanceUntilIdle()

                viewModel.onEvent(BodyFatEvent.WaistChange("40,0"))
                viewModel.onEvent(BodyFatEvent.NeckChange("15"))
                viewModel.onEvent(BodyFatEvent.Compute)
                assertNotNull(viewModel.uiState.value.estimate)
                assertEquals(viewModel.uiState.value.revision, viewModel.uiState.value.computedRevision)

                viewModel.onEvent(BodyFatEvent.WaistChange("41"))
                assertNull(viewModel.uiState.value.estimate)
                assertFalse(viewModel.uiState.value.canSave)
                viewModel.onEvent(BodyFatEvent.SaveToLogbook)
                assertEquals(0, measurements.commands.size)

                viewModel.onEvent(BodyFatEvent.WaistChange("40,0"))
                viewModel.onEvent(BodyFatEvent.Compute)
                viewModel.onEvent(BodyFatEvent.SaveToLogbook)
                viewModel.onEvent(BodyFatEvent.SaveToLogbook)
                advanceUntilIdle()

                assertEquals(1, measurements.commands.size)
                val waist =
                    measurements.commands
                        .single()
                        .inputs
                        .single { it.name == "waist" }
                assertEquals(101.6, waist.centimeters, 1e-6)
            } finally {
                Dispatchers.resetMain()
            }
        }
}

private object FixedBodyClock : ClockPort {
    override fun now(): Instant = Instant.parse("2026-09-16T12:00:00Z")
}

private class RecordingMeasurements : MeasurementRepository {
    val commands = mutableListOf<BodyMeasurementCommand>()

    override suspend fun saveBodyMeasurement(command: BodyMeasurementCommand): WloResult<BodyMeasurementResult> {
        commands += command
        return WloResult.ok(BodyMeasurementResult("estimate", listOf("waist", "neck")))
    }

    override suspend fun append(event: NewMeasurement): WloResult<MeasurementEvent> = error("unused")

    override suspend fun attachAttrs(
        eventId: String,
        attrs: List<MeasurementAttr>,
    ): WloResult<Unit> = error("unused")

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

    override suspend fun delete(eventId: String): WloResult<Unit> = error("unused")

    override suspend fun deleteTrendScalars(
        profileId: String,
        day: Long,
    ): WloResult<Unit> = error("unused")

    override suspend fun attrsOf(eventId: String): WloResult<List<MeasurementAttr>> = WloResult.ok(emptyList())

    override suspend fun attrsInRange(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<MeasurementAttr>> = WloResult.ok(emptyList())
}

private object BodyProfileRepository : ProfileRepository {
    private val profile =
        Profile(
            id = "profile",
            sex = Sex.MALE,
            birthYear = 1990,
            heightCm = 177.8,
            startWeightKg = null,
            activityLevel = ActivityLevel.SEDENTARY,
            unitPreference = UnitSystem.IMPERIAL,
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
        sex: Sex?,
        birthYear: Int,
        heightCm: Double,
        activityLevel: ActivityLevel,
    ): WloResult<Unit> = WloResult.ok(Unit)
}
