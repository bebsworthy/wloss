package app.wlo.core.engines

import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.Sex
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafety
import app.wlo.core.model.WeightGoalSafetyInput
import app.wlo.core.testing.GoldenFixtures
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** WLO-0073 deterministic holdout calibration and release-gate snapshot. */
class ForecastCalibrationBenchmarkTest {
    @Serializable
    private data class Corpus(
        val corpusVersion: String,
        val horizonsDays: List<Int>,
        val scenarios: List<Scenario>,
    )

    @Serializable
    private data class Scenario(
        val name: String,
        val sex: String?,
        val ageYears: Int,
        val heightCm: Double,
        val activityLevel: String,
        val startWeightKg: Double,
        val goalWeightKg: Double,
        val intakeKcal: Double,
        val actualTdeeAtStartKcal: Double,
        val actualTdeeWeightCoefficient: Double,
        val dailyAdaptationKcal: Double,
        val measuredTdeeErrorKcal: Double,
    )

    private data class Observation(
        val caseName: String,
        val horizonDays: Int,
        val errorKg: Double,
        val covered: Boolean,
    )

    private data class Metrics(
        val mae28Kg: Double,
        val bias28Kg: Double,
        val coverage28: Double,
        val mae56Kg: Double,
        val bias56Kg: Double,
        val coverage56: Double,
        val meanFinishRevisionDays: Double,
        val maxFinishRevisionDays: Long,
        val finishRevisionCases: List<String>,
        val uncoveredCases: List<String>,
    )

    private val corpus: Corpus by lazy {
        Json.decodeFromString(GoldenFixtures.load("golden/forecast_calibration_v1.json"))
    }

    @Test
    fun releaseQualitySnapshotIsDeterministic() {
        val snapshot = snapshot(metrics())
        println(snapshot)
        assertEquals(EXPECTED_SNAPSHOT, snapshot)
    }

    @Test
    fun releaseQualityGatesExposeTheKnownDateRevisionFailure() {
        val metrics = metrics()
        assertTrue(metrics.mae28Kg <= MAX_MAE_28_KG)
        assertTrue(abs(metrics.bias28Kg) <= MAX_ABS_BIAS_28_KG)
        assertTrue(metrics.coverage28 >= MIN_INTERVAL_COVERAGE)
        assertTrue(metrics.mae56Kg <= MAX_MAE_56_KG)
        assertTrue(abs(metrics.bias56Kg) <= MAX_ABS_BIAS_56_KG)
        assertTrue(metrics.coverage56 >= MIN_INTERVAL_COVERAGE)
        assertTrue(
            metrics.maxFinishRevisionDays > MAX_FINISH_REVISION_DAYS,
            "Cold-start point dates stay release-ineligible while 39 days exceeds the 28-day gate",
        )
    }

    @Test
    fun coldStartGapsOutliersAndBackfillUseTheOneReleaseBoundary() {
        val scenario = corpus.scenarios.first()
        val cold = coldInput(scenario, startDay = 23_000, startWeightKg = scenario.startWeightKg)
        val eligible = eligibility(scenario, scenario.startWeightKg)
        val measured = measuredInput(scenario, cold, scenario.actualTdeeAtStartKcal)

        assertIs<GoalForecastResult.Developing>(
            ForecastEngine.evaluate(cold, eligible, EngineState.Developing(usableDays = 0), measured),
        )
        assertIs<GoalForecastResult.Held>(
            ForecastEngine.evaluate(
                cold,
                eligible,
                EngineState.Held(EngineHoldReason.UNLOGGED_DAYS, magnitude = 3),
                measured,
            ),
        )
        assertIs<GoalForecastResult.Held>(
            ForecastEngine.evaluate(
                cold,
                eligible,
                EngineState.Held(EngineHoldReason.OUTLIER, magnitude = null),
                measured.copy(measuredTdeeKcal = measured.measuredTdeeKcal?.plus(1_000.0)),
            ),
        )

        val beforeBackfill =
            ForecastEngine.evaluate(cold, eligible, EngineState.Developing(usableDays = 9), measured)
        val afterBackfill =
            ForecastEngine.evaluate(cold, eligible, EngineState.Updating(usableDays = 10), measured)
        assertIs<GoalForecastResult.Developing>(beforeBackfill)
        assertIs<GoalForecastResult.Available>(afterBackfill)
    }

    @Test
    fun gainForecastRemainsExplicitlyWithheldForWlo0083() {
        val scenario = corpus.scenarios.first()
        val gainEligibility =
            WeightGoalSafety.evaluate(
                safetyInput(scenario, currentWeightKg = 80.0).copy(
                    mode = WeightGoalMode.GAIN,
                    targetWeightKg = 85.0,
                    requestedPacePctPerWeek = 0.25,
                ),
            )
        val result =
            ForecastEngine.evaluate(
                coldStartInput = coldInput(scenario, 23_000, 80.0).copy(goalWeightKg = 85.0),
                eligibility = gainEligibility,
                engineState = EngineState.Updating(usableDays = 14),
            )

        assertIs<GoalForecastResult.Withheld>(result)
    }

    private fun metrics(): Metrics {
        val observations = mutableListOf<Observation>()
        val finishRevisions = mutableListOf<Pair<String, Long>>()
        corpus.scenarios.forEach { scenario ->
            val truth = simulateTruth(scenario, days = 84)
            val eligible = eligibility(scenario, scenario.startWeightKg)
            val coldAtStart = coldInput(scenario, START_DAY, truth.first())
            val developing =
                assertIs<GoalForecastResult.Developing>(
                    ForecastEngine.evaluate(
                        coldAtStart,
                        eligible,
                        EngineState.Developing(usableDays = 0),
                    ),
                )
            observations += observations(scenario.name, developing.bands, truth, cutoff = 0, scenario.goalWeightKg)

            val cutoff = 14
            val cutoffWeight = truth[cutoff]
            val currentActualTdee = actualTdee(scenario, cutoffWeight, cutoff)
            val coldAtCutoff = coldInput(scenario, START_DAY + cutoff, cutoffWeight)
            val measured =
                measuredInput(
                    scenario,
                    coldAtCutoff,
                    measuredTdeeKcal = currentActualTdee + scenario.measuredTdeeErrorKcal,
                )
            val available =
                assertIs<GoalForecastResult.Available>(
                    ForecastEngine.evaluate(
                        coldAtCutoff,
                        eligibility(scenario, cutoffWeight),
                        EngineState.Updating(usableDays = 14),
                        measured,
                    ),
                )
            observations += observations(scenario.name, available.bands, truth, cutoff, scenario.goalWeightKg)

            val firstFinish = developing.bands.expected.finishEpochDay
            val revisedFinish = available.bands.expected.finishEpochDay
            if (firstFinish != null && revisedFinish != null) {
                finishRevisions += scenario.name to abs(revisedFinish - firstFinish)
            }
        }
        val at28 = observations.filter { it.horizonDays == 28 }
        val at56 = observations.filter { it.horizonDays == 56 }
        return Metrics(
            mae28Kg = at28.map { abs(it.errorKg) }.average(),
            bias28Kg = at28.map { it.errorKg }.average(),
            coverage28 = at28.count { it.covered }.toDouble() / at28.size,
            mae56Kg = at56.map { abs(it.errorKg) }.average(),
            bias56Kg = at56.map { it.errorKg }.average(),
            coverage56 = at56.count { it.covered }.toDouble() / at56.size,
            meanFinishRevisionDays = finishRevisions.map { it.second.toDouble() }.average(),
            maxFinishRevisionDays = finishRevisions.maxOfOrNull { it.second } ?: 0,
            finishRevisionCases = finishRevisions.map { (name, days) -> "$name:$days" },
            uncoveredCases = observations.filterNot { it.covered }.map { it.caseName },
        )
    }

    private fun observations(
        scenarioName: String,
        bands: ForecastBands,
        truth: List<Double>,
        cutoff: Int,
        goalWeightKg: Double,
    ): List<Observation> =
        corpus.horizonsDays.map { horizon ->
            val actual = truth[cutoff + horizon]
            val expected = weightAt(bands.expected, horizon, bands.expected.trajectoryKg.firstOrNull() ?: actual, goalWeightKg)
            val optimistic = weightAt(bands.optimistic, horizon, expected, goalWeightKg)
            val pessimistic = weightAt(bands.pessimistic, horizon, expected, goalWeightKg)
            Observation(
                caseName = "$scenarioName@d$cutoff+$horizon",
                horizonDays = horizon,
                errorKg = expected - actual,
                covered = actual in minOf(optimistic, pessimistic)..maxOf(optimistic, pessimistic),
            )
        }

    private fun weightAt(
        band: ForecastBand,
        horizonDays: Int,
        fallbackWeightKg: Double,
        goalWeightKg: Double,
    ): Double {
        val index = horizonDays / 7 - 1
        return band.trajectoryKg.getOrNull(index) ?: band.trajectoryKg.lastOrNull() ?: fallbackWeightKg.coerceAtLeast(goalWeightKg)
    }

    private fun simulateTruth(
        scenario: Scenario,
        days: Int,
    ): List<Double> {
        val weights = ArrayList<Double>(days + 1)
        var weight = scenario.startWeightKg
        weights += weight
        repeat(days) { day ->
            val deficit = actualTdee(scenario, weight, day) - scenario.intakeKcal
            weight -= deficit / 7_700.0
            weights += weight
        }
        return weights
    }

    private fun actualTdee(
        scenario: Scenario,
        weightKg: Double,
        day: Int,
    ): Double =
        scenario.actualTdeeAtStartKcal +
            scenario.actualTdeeWeightCoefficient * (weightKg - scenario.startWeightKg) +
            scenario.dailyAdaptationKcal * day

    private fun coldInput(
        scenario: Scenario,
        startDay: Long,
        startWeightKg: Double,
    ): ColdStartInput =
        ColdStartInput(
            sex = scenario.sex?.let { value -> Sex.entries.first { it.wireName == value } },
            ageYears = scenario.ageYears,
            heightCm = scenario.heightCm,
            startTrendKg = startWeightKg,
            goalWeightKg = scenario.goalWeightKg,
            activityLevel = ActivityLevel.entries.first { it.wireName == scenario.activityLevel },
            intakeKcal = scenario.intakeKcal,
            startEpochDay = startDay,
            startInstant = Instant.fromEpochMilliseconds(0),
        )

    private fun measuredInput(
        scenario: Scenario,
        cold: ColdStartInput,
        measuredTdeeKcal: Double,
    ): MeasuredInput {
        val pace = (measuredTdeeKcal - scenario.intakeKcal) * 7.0 / 7_700.0
        return MeasuredInput(
            sex = cold.sex,
            ageYears = cold.ageYears,
            heightCm = cold.heightCm,
            startTrendKg = cold.startTrendKg,
            goalWeightKg = cold.goalWeightKg,
            intakeKcal = cold.intakeKcal,
            measuredTdeeKcal = measuredTdeeKcal,
            fastPaceKgPerWeek = pace * 1.35,
            slowPaceKgPerWeek = pace * 0.65,
            startEpochDay = cold.startEpochDay,
            startInstant = cold.startInstant,
        )
    }

    private fun eligibility(
        scenario: Scenario,
        currentWeightKg: Double,
    ) = WeightGoalSafety.evaluate(safetyInput(scenario, currentWeightKg))

    private fun safetyInput(
        scenario: Scenario,
        currentWeightKg: Double,
    ): WeightGoalSafetyInput =
        WeightGoalSafetyInput(
            ageYears = scenario.ageYears,
            mode = WeightGoalMode.LOSS,
            currentWeightKg = currentWeightKg,
            targetWeightKg = scenario.goalWeightKg,
            requestedPacePctPerWeek = 0.4,
            plannedDailyEnergyKcal = scenario.intakeKcal,
            minimumDailyEnergyKcal = 1_200.0,
            pregnant = SafetyAnswer.NO,
            breastfeeding = SafetyAnswer.NO,
            eatingDisorderConcern = SafetyAnswer.NO,
            medicallyInfluencedWeight = SafetyAnswer.NO,
        )

    private fun snapshot(metrics: Metrics): String =
        "corpus=${corpus.corpusVersion}; scenarios=${corpus.scenarios.size}; " +
            "mae28=${q(metrics.mae28Kg)}; bias28=${q(metrics.bias28Kg)}; coverage28=${q(metrics.coverage28)}; " +
            "mae56=${q(metrics.mae56Kg)}; bias56=${q(metrics.bias56Kg)}; coverage56=${q(metrics.coverage56)}; " +
            "finishRevisionMeanDays=${q(metrics.meanFinishRevisionDays)}; " +
            "finishRevisionMaxDays=${metrics.maxFinishRevisionDays}; " +
            "finishRevisions=${metrics.finishRevisionCases.joinToString(",")}; " +
            "uncovered=${metrics.uncoveredCases.joinToString(",")}"

    private fun q(value: Double): String = (round(value * 10_000.0) / 10_000.0).toString()

    private companion object {
        const val START_DAY: Long = 23_000
        const val MAX_MAE_28_KG: Double = 0.75
        const val MAX_ABS_BIAS_28_KG: Double = 0.50
        const val MAX_MAE_56_KG: Double = 1.50
        const val MAX_ABS_BIAS_56_KG: Double = 1.00
        const val MIN_INTERVAL_COVERAGE: Double = 0.80
        const val MAX_FINISH_REVISION_DAYS: Long = 28
        const val EXPECTED_SNAPSHOT: String =
            "corpus=forecast-calibration-v1; scenarios=4; mae28=0.4733; bias28=0.415; " +
                "coverage28=0.875; mae56=0.9154; bias56=0.7835; coverage56=0.875; " +
                "finishRevisionMeanDays=22.5; finishRevisionMaxDays=39; " +
                "finishRevisions=female-steady-loss:6,male-higher-deficit:39; " +
                "uncovered=other-shallow-loss@d0+28,other-shallow-loss@d0+56"
    }
}
