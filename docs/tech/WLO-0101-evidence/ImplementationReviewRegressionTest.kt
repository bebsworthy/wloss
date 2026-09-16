package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.datastore.SettingsStoreFactory
import app.wlo.core.model.MeasurementKind
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ImplementationReviewRegressionTest {
    @Test
    fun legacyPersistedTrendMustMatchCanonicalAfterPolicyInitialization() = runTest {
        val dir = Files.createTempDirectory("wlo0101-migration")
        val db = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
        try {
            val settings = SettingsStoreFactory.create(dir.resolve("settings.preferences_pb").toString().toPath())
            val profiles = RoomProfileRepository(db, settings)
            val projector = DayProjector(db, FakeClock())
            val measurements = RoomMeasurementRepository(db, projector)
            val weights = RoomWeighInRepository(db, measurements, projector)
            val profile = profiles.create(NewProfile(), Instant.parse("2026-09-12T00:00:00Z")).okOrDie()
            val day = LocalDate.parse("2026-09-12").toEpochDays().toLong()
            suspend fun seed(kind: MeasurementKind, value: Double, time: String) {
                measurements.append(NewMeasurement(profile.id, day, kind, value, "manual", Instant.parse(time)))
            }
            seed(MeasurementKind.WEIGHT, 80.0, "2026-09-12T07:00:00Z")
            seed(MeasurementKind.WEIGHT, 79.0, "2026-09-12T18:00:00Z")
            seed(MeasurementKind.TREND, 79.0, "2026-09-12T18:00:00Z")
            db.profiles().initializeWeightPolicy(profile.id, "UTC", DailyWeightPolicy.VERSION)
            val canonical = weights.currentTrend(profile.id, day).okOrDie().current!!.value
            val persisted = measurements.rangeOfKind(profile.id, MeasurementKind.TREND, day, day).okOrDie().single().valueReal
            println("REVIEW EVIDENCE: canonical=$canonical persisted=$persisted")
            assertEquals(canonical, persisted, "Policy upgrade must not retain minimum-policy trend rows")
        } finally { db.close() }
    }
}

private fun <T> WloResult<T>.okOrDie(): T = (this as WloResult.Ok).value
