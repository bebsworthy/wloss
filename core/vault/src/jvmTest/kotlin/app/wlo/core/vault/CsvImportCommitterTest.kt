package app.wlo.core.vault

import app.wlo.core.common.ClockPort
import app.wlo.core.data.DayProjector
import app.wlo.core.database.ProfileEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CsvImportCommitterTest {
    private val dir = Files.createTempDirectory("wlo-csv-commit")
    private lateinit var db: WloDatabase
    private val clock = ClockPort { Instant.fromEpochMilliseconds(1_760_000_000_000) }

    @Before
    fun setUp() =
        runTest {
            db = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
            db.profiles().upsert(
                ProfileEntity(
                    id = "profile",
                    sex = null,
                    birthYear = 1990,
                    heightCm = 180.0,
                    startWeightKg = 90.0,
                    activityLevel = "sedentary",
                    unitPreference = "metric",
                    createdAtEpochMs = 1,
                ),
            )
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun sameStagedCsv_isAtomicAndIdempotent() =
        runTest {
            val committer = CsvImportCommitter(db, DayProjector(db, clock), clock)
            val rows = rows()

            val first = committer.commit("profile", rows)
            val second = committer.commit("profile", rows)

            assertEquals(2, first.inserted)
            assertEquals(0, second.inserted)
            assertEquals(2, second.skipped)
            assertEquals(2, db.measurementEvents().all().size)
            assertEquals(1, db.measurementEventAttrs().all().size)
        }

    @Test
    fun failureInsideRoomPhase_rollsBackEveryCsvRow() =
        runTest {
            val committer =
                CsvImportCommitter(
                    db = db,
                    projector = DayProjector(db, clock),
                    clock = clock,
                    transactionHook = { throw IllegalStateException("forced") },
                )

            assertFailsWith<IllegalStateException> { committer.commit("profile", rows()) }

            assertEquals(0, db.measurementEvents().all().size)
            assertEquals(0, db.measurementEventAttrs().all().size)
        }

    @Test
    fun cancellationInsideRoomPhase_rollsBackEveryCsvRow() =
        runTest {
            val committer =
                CsvImportCommitter(
                    db = db,
                    projector = DayProjector(db, clock),
                    clock = clock,
                    transactionHook = { throw CancellationException("forced") },
                )

            assertFailsWith<CancellationException> { committer.commit("profile", rows()) }

            assertEquals(0, db.measurementEvents().all().size)
            assertEquals(0, db.measurementEventAttrs().all().size)
        }

    private fun rows(): List<CsvMeasurementImporter.StagedMeasurement> =
        listOf(
            CsvMeasurementImporter.StagedMeasurement("row-1", 20_000, "weight", 84.2, "kg"),
            CsvMeasurementImporter.StagedMeasurement("row-2", 20_001, "custom", 1.2, "mmol", "ketones"),
        )
}
