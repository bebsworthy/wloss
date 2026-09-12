package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.documents.Cadence
import app.wlo.core.documents.Energy
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.documents.TargetsViolation
import app.wlo.core.documents.TargetsWriterId
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.Sex
import app.wlo.core.testing.FakeClock
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The two-writer write path (R-B2, Appendix A.2): F01 creates + revises, F07
 * applies — and nothing else can write. Table-driven invariant rejections
 * live here; generated (property) cases ride beside them.
 */
class TargetsWriterTest {
    private val dir = Files.createTempDirectory("wlo-writer-test")
    private val db: WloDatabase = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()
    private val writers = TargetsWriters(db, clock)
    private val settings =
        app.wlo.core.datastore.SettingsStoreFactory
            .create(dir.resolve("settings.preferences_pb").toString().toPath())
    private val profiles = RoomProfileRepository(db, settings)

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun aProfile(sex: Sex? = Sex.FEMALE): String {
        val profile =
            profiles.create(
                NewProfile(
                    sex = sex,
                    birthYear = 1994,
                    heightCm = 165.0,
                    startWeightKg = 84.2,
                    activityLevel = ActivityLevel.MODERATE,
                ),
                clock.now(),
            )
        return (profile as WloResult.Ok).value.id
    }

    private fun document(
        cadence: Cadence = Cadence.DAILY,
        budgetKcal: Double? = 1_900.0,
        weeklyBudgetKcal: Double? = null,
        schedule: List<Double> = emptyList(),
        floorKcal: Double = 1_200.0,
        pacePctPerWeek: Double = 0.5,
        acknowledged: Boolean = false,
    ): TargetsDocument =
        TargetsDocument(
            goal = Goal(targetWeightKg = 78.0, pacePctPerWeek = pacePctPerWeek),
            energy =
                Energy(
                    cadence = cadence,
                    budgetKcal = budgetKcal,
                    weeklyBudgetKcal = weeklyBudgetKcal,
                    schedule = schedule,
                    floorKcal = floorKcal,
                    floorOverrideAcknowledged = acknowledged,
                ),
            macros = Macros(split = MacroSplit.Preset("balanced")),
        )

    // --- F01 Studio ---

    @Test
    fun studioWritesV1_thenVNPlus1_withDiffs() =
        runTest {
            val id = aProfile()
            val studio = writers.studio() as F01StudioWriter

            val first = studio.writeFirst(id, document())
            assertIs<TargetsWriteOutcome.Written>(first)
            assertEquals(1, first.record.version)
            assertEquals(null, first.record.parentVersion)
            assertEquals(TargetsWriterId.STUDIO_F01, first.record.createdBy)
            assertEquals(listOf("plan created"), first.diff)

            val revision =
                studio.writeRevision(
                    id,
                    baseVersion = 1,
                    document = document(budgetKcal = 1_950.0),
                )
            assertIs<TargetsWriteOutcome.Written>(revision)
            assertEquals(2, revision.record.version)
            assertEquals(1, revision.record.parentVersion)
            assertTrue(revision.diff.any { it.contains("1,900") || it.contains("1900") }, revision.diff.toString())
        }

    @Test
    fun studioRevisionAgainstStaleBaseConflicts() =
        runTest {
            val id = aProfile()
            val studio = writers.studio() as F01StudioWriter
            studio.writeFirst(id, document())
            studio.writeRevision(id, 1, document(budgetKcal = 1_910.0))
            // A revision based on v1 when current is v2 is rejected — no silent overwrite.
            val stale = studio.writeRevision(id, 1, document(budgetKcal = 1_920.0))
            assertIs<TargetsWriteOutcome.Rejected>(stale)
            assertIs<TargetsWriteError.VersionConflict>(stale.error)
        }

    @Test
    fun floorWall_budgetBelowFloorIsRejectedNotClamped() =
        runTest {
            val id = aProfile()
            val studio = writers.studio() as F01StudioWriter
            val rejected = studio.writeFirst(id, document(budgetKcal = 900.0))
            assertIs<TargetsWriteOutcome.Rejected>(rejected)
            val violation = assertIs<TargetsWriteError.InvariantViolated>(rejected.error)
            assertTrue(violation.violations.any { it is TargetsViolation.FloorViolated })
        }

    @Test
    fun floorOverrideNeedsAcknowledgment() =
        runTest {
            val id = aProfile(Sex.FEMALE)
            val studio = writers.studio() as F01StudioWriter
            // 1,100 kcal is below the 1,200 F default: needs the persistent ack.
            val unacknowledged = studio.writeFirst(id, document(budgetKcal = 1_100.0, floorKcal = 1_100.0))
            assertIs<TargetsWriteOutcome.Rejected>(unacknowledged)

            val acknowledged = studio.writeFirst(id, document(budgetKcal = 1_100.0, floorKcal = 1_100.0, acknowledged = true))
            assertIs<TargetsWriteOutcome.Written>(acknowledged)
        }

    @Test
    fun revertCreatesANewVersionCopyingTheOld() =
        runTest {
            val id = aProfile()
            val studio = writers.studio() as F01StudioWriter
            studio.writeFirst(id, document(budgetKcal = 1_900.0))
            studio.writeRevision(id, 1, document(budgetKcal = 1_600.0))

            val revert = studio.revert(id, toVersion = 1)
            assertIs<TargetsWriteOutcome.Written>(revert)
            assertEquals(3, revert.record.version, "revert is v3 copying v1 — history intact")
            assertEquals(1_900.0, revert.record.document.energy.budgetKcal)
            assertEquals(3, targetsHistoryCount(id))
        }

    private suspend fun targetsHistoryCount(profileId: String): Int = db.targetsVersions().history(profileId).size

    // --- F07 Apply ---

    @Test
    fun applyWritesAnApplyAuthoredVersion() =
        runTest {
            val id = aProfile()
            (writers.studio() as F01StudioWriter).writeFirst(id, document())
            val f07 = writers.apply() as F07ApplyWriter

            val outcome =
                f07.apply(
                    id,
                    AdaptiveProposal(
                        decisionId = "checkin-2026-W37",
                        measuredTdeeKcal = 2_410.0,
                        newBudgetKcal = 1_950.0,
                        ledgerHashHex = "abc123",
                    ),
                )
            assertIs<TargetsWriteOutcome.Written>(outcome)
            assertEquals(2, outcome.record.version)
            assertEquals(TargetsWriterId.APPLY_F07, outcome.record.createdBy)
            assertEquals(1_950.0, outcome.record.document.energy.budgetKcal)
        }

    @Test
    fun noEatBack_budgetAboveMeasuredTdeeIsRejected() =
        runTest {
            val id = aProfile()
            (writers.studio() as F01StudioWriter).writeFirst(id, document())
            val f07 = writers.apply() as F07ApplyWriter

            // Exercise expenditure is structurally incapable of raising eating
            // targets: the proposal carries no exercise field, and a budget
            // above MEASURED burn is rejected outright (A.2 #2).
            val outcome =
                f07.apply(
                    id,
                    AdaptiveProposal(
                        decisionId = "checkin-2026-W38",
                        measuredTdeeKcal = 2_000.0,
                        newBudgetKcal = 2_600.0,
                    ),
                )
            assertIs<TargetsWriteOutcome.Rejected>(outcome)
            assertIs<TargetsWriteError.EatBackRejected>(outcome.error)

            // Maintenance-at-goal (budget == measured TDEE) is allowed.
            val maintenance =
                f07.apply(
                    id,
                    AdaptiveProposal(
                        decisionId = "checkin-2026-W39",
                        measuredTdeeKcal = 2_000.0,
                        newBudgetKcal = 2_000.0,
                    ),
                )
            assertIs<TargetsWriteOutcome.Written>(maintenance)
        }

    @Test
    fun applyAdaptsUpwardWhenMeasuredTdeeRises_plateauReframe() =
        runTest {
            val id = aProfile()
            (writers.studio() as F01StudioWriter).writeFirst(id, document(budgetKcal = 1_900.0))
            val f07 = writers.apply() as F07ApplyWriter
            // Flat trend raised measured TDEE 2,290 → 2,410; the plan follows upward.
            val outcome =
                f07.apply(
                    id,
                    AdaptiveProposal(
                        decisionId = "checkin-2026-W40",
                        measuredTdeeKcal = 2_410.0,
                        newBudgetKcal = 1_950.0,
                        pacePctPerWeek = 0.55,
                    ),
                )
            assertIs<TargetsWriteOutcome.Written>(outcome)
            assertTrue(outcome.diff.any { it.contains("budget") }, outcome.diff.toString())
            assertTrue(outcome.diff.any { it.contains("pace") }, outcome.diff.toString())
        }

    @Test
    fun applyRequiresAnExistingPlan() =
        runTest {
            val id = aProfile()
            val outcome =
                (writers.apply() as F07ApplyWriter).apply(
                    id,
                    AdaptiveProposal(decisionId = "x", measuredTdeeKcal = 2_000.0, newBudgetKcal = 1_800.0),
                )
            assertIs<TargetsWriteOutcome.Rejected>(outcome)
            assertIs<TargetsWriteError.NoActiveTargets>(outcome.error)
        }

    @Test
    fun readsSeeCurrentAndFullHistory() =
        runTest {
            val id = aProfile()
            val repo = RoomTargetsRepository(db)
            assertEquals(null, repo.current(id).getOrNull())
            (writers.studio() as F01StudioWriter).writeFirst(id, document())
            (writers.studio() as F01StudioWriter).writeRevision(id, 1, document(budgetKcal = 1_910.0))

            assertEquals(2, repo.current(id).getOrNull()?.version)
            val history = repo.history(id).getOrNull()!!
            assertEquals(listOf(1, 2), history.map { it.version })
            // v1 is superseded (kept for the timeline, not the active plan).
            assertEquals(
                1_910.0,
                repo
                    .current(id)
                    .getOrNull()
                    ?.document
                    ?.energy
                    ?.budgetKcal,
            )
        }
}
