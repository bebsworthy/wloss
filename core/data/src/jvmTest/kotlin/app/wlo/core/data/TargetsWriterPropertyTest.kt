package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.datastore.SettingsStoreFactory
import app.wlo.core.documents.Cadence
import app.wlo.core.documents.Energy
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.documents.TargetsRecord
import app.wlo.core.documents.TargetsViolation
import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.Sex
import app.wlo.core.testing.FakeClock
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Property tests for the A.2 write-path invariants: every generated document
 * that breaks an invariant is REJECTED (never clamped); every valid one is
 * written as version+1 (Appendix A.2, T-J "property tests for reconciliation/
 * invariant math").
 */
class TargetsWriterPropertyTest {
    private val dir = Files.createTempDirectory("wlo-writer-prop")
    private val db: WloDatabase = jvmDatabaseBuilder(dir.resolve("wlo.db").toString()).build()
    private val clock = FakeClock()
    private val writers = TargetsWriters(db, clock)
    private val profiles =
        RoomProfileRepository(db, SettingsStoreFactory.create(dir.resolve("settings.preferences_pb").toString().toPath()))

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private val pace = Arb.numericDouble(-2.0, 2.0)
    private val budget = Arb.numericDouble(400.0, 4_000.0)
    private val floor = Arb.numericDouble(800.0, 2_000.0)
    private val acknowledged = Arb.boolean()

    @Test
    fun generatedDocuments_roundTripThroughValidation() =
        runTest {
            checkAll(iterations = 150, pace, budget, floor, acknowledged) { pacePct, kcal, floorKcal, ack ->
                val doc =
                    TargetsDocument(
                        goal = Goal(targetWeightKg = 78.0, pacePctPerWeek = pacePct),
                        energy =
                            Energy(
                                cadence = Cadence.DAILY,
                                budgetKcal = kcal,
                                floorKcal = floorKcal,
                                floorOverrideAcknowledged = ack,
                            ),
                        macros = Macros(split = MacroSplit.Preset("balanced")),
                    )
                val violations =
                    app.wlo.core.documents.TargetsInvariants
                        .validate(doc) +
                        // The writer's acknowledgment rule, mirrored for the oracle.
                        floorAckViolations(doc)
                val id = newProfile()
                val outcome = (writers.studio() as F01StudioWriter).writeFirst(id, doc)
                if (violations.isEmpty()) {
                    assertIs<TargetsWriteOutcome.Written>(outcome, "must accept: $violations for $doc")
                } else {
                    assertIs<TargetsWriteOutcome.Rejected>(outcome, "must reject ($violations): $doc")
                }
            }
        }

    /** Mirrors F01StudioWriter's acknowledgment rule as the property oracle. */
    private fun floorAckViolations(doc: TargetsDocument): List<TargetsViolation> =
        if (doc.energy.floorKcal < 1_500.0 && !doc.energy.floorOverrideAcknowledged) {
            listOf(TargetsViolation.FloorViolated(1_500.0, doc.energy.floorKcal))
        } else {
            emptyList()
        }

    @Test
    fun validWritesAlwaysIncrementTheVersion() =
        runTest {
            checkAll(iterations = 40, Arb.int(1, 6), Arb.numericDouble(1_300.0, 2_200.0)) { revisions, kcal ->
                val id = newProfile()
                val studio = writers.studio() as F01StudioWriter
                var written = 0
                var base: Int? = null
                repeat(revisions) {
                    // Coerce above the floor so the fixture document is always valid.
                    val safeKcal = maxOf(kcal, 1_500.0)
                    val outcome =
                        if (base ==
                            null
                        ) {
                            studio.writeFirst(id, validDoc(safeKcal))
                        } else {
                            studio.writeRevision(id, base!!, validDoc(safeKcal))
                        }
                    val record = outcome.okWrittenOrNull() ?: error("write unexpectedly rejected: $outcome")
                    assertEquals(written + 1, record.version, "versions are strictly vN+1")
                    written = record.version
                    base = record.version
                }
            }
        }

    @Test
    fun applyNeverLandsAboveMeasuredTdee() =
        runTest {
            checkAll(iterations = 150, Arb.numericDouble(1_200.0, 3_500.0), Arb.numericDouble(1_000.0, 4_000.0)) { rawTdee, rawProposed ->
                // Coerce above the plan floor so only the eat-back rule decides.
                val tdee = maxOf(rawTdee, 1_500.0)
                val proposed = maxOf(rawProposed, 1_500.0)
                val id = newProfile()
                (writers.studio() as F01StudioWriter).writeFirst(id, validDoc(1_500.0))
                val outcome =
                    (writers.apply() as F07ApplyWriter).apply(
                        id,
                        AdaptiveProposal(
                            decisionId = "prop",
                            measuredTdeeKcal = tdee,
                            newBudgetKcal = proposed,
                        ),
                    )
                if (proposed > tdee + F07ApplyWriter.EAT_BACK_TOLERANCE_KCAL) {
                    assertIs<TargetsWriteOutcome.Rejected>(outcome, "eat-back must be rejected: $proposed > $tdee")
                    assertIs<TargetsWriteError.EatBackRejected>((outcome as TargetsWriteOutcome.Rejected).error)
                } else {
                    assertIs<TargetsWriteOutcome.Written>(outcome)
                }
            }
        }

    private suspend fun newProfile(sex: Sex? = Sex.MALE): String =
        profiles
            .create(
                NewProfile(sex = sex, birthYear = 1990, heightCm = 180.0, startWeightKg = 95.0, activityLevel = ActivityLevel.LIGHT),
                clock.now(),
            ).okOrDie()
            .id

    private fun TargetsWriteOutcome.okWrittenOrNull(): TargetsRecord? = (this as? TargetsWriteOutcome.Written)?.record

    private fun <T> WloResult<T>.okOrDie(): T =
        when (this) {
            is WloResult.Ok -> value
            is WloResult.Err -> error("unexpected storage error: ${error.debugMessage} (cause: ${error.cause})")
        }

    private fun validDoc(budgetKcal: Double): TargetsDocument =
        TargetsDocument(
            goal = Goal(targetWeightKg = 85.0, pacePctPerWeek = 0.5),
            energy = Energy(cadence = Cadence.DAILY, budgetKcal = budgetKcal, floorKcal = 1_500.0),
            macros = Macros(split = MacroSplit.Preset("balanced")),
        )
}
