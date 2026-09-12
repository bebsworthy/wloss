package app.wlo.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Proves Room 3 codegen end-to-end on the JVM driver (codegen + DAO + flow). */
class ProvenanceEventDaoTest {
    @Test
    fun insertAndObserveDay() =
        runTest {
            val dbFile = Files.createTempDirectory("wlo-dao-test").resolve("wlo-test.db")
            val db = jvmDatabaseBuilder(dbFile.toString()).build()

            val dao = db.provenanceEvents()
            dao.insert(
                ProvenanceEventEntity(
                    day = "2026-09-12",
                    kind = "bmi",
                    valueText = "24.2 kg/m2",
                    provenanceJson = """{"kind":"derived","formulaVersion":"bmi/quetelet-v1","inputs":["weightKg","heightCm"]}""",
                    createdAtEpochMs = 42,
                    note = null,
                ),
            )
            dao.insert(
                ProvenanceEventEntity(
                    day = "2026-09-12",
                    kind = "weight",
                    valueText = "81.2 kg",
                    provenanceJson = """{"kind":"measured"}""",
                    createdAtEpochMs = 43,
                    note = "morning weigh-in",
                ),
            )

            val events = dao.forDay("2026-09-12")
            assertEquals(2, events.size)
            assertEquals("weight", events[1].kind)
            assertEquals("morning weigh-in", events[1].note)
            assertEquals(0, dao.forDay("2026-09-11").size)
            assertTrue(dao.observeDay("2026-09-12").first().isNotEmpty())

            db.close()
        }
}
