package app.wlo.core.engines

import app.wlo.core.model.TrendMethod
import app.wlo.core.testing.GoldenFixtures
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WLO-0072's executable benchmark harness. This is deliberately test-only:
 * it compares policy candidates without making one a production default or
 * changing R-B8's event-level storage contract.
 */
class WeightPolicyBenchmarkTest {
    @Serializable
    private data class Corpus(
        val corpusVersion: String,
        val description: String,
        val consistentWindowStartMinute: Int,
        val consistentWindowEndMinuteExclusive: Int,
        val consistentTargetMinute: Int,
        val scenarios: List<Scenario>,
    )

    @Serializable
    private data class Scenario(
        val name: String,
        val stepDay: Long? = null,
        val truth: List<Truth>,
        val events: List<Event>,
    )

    @Serializable
    private data class Truth(
        val day: Long,
        val kg: Double,
    )

    @Serializable
    private data class Event(
        val id: String,
        val effectiveDay: Long,
        val recordedOnDay: Long,
        val localMinute: Int,
        val zoneOffsetMinutes: Int,
        val kg: Double,
        val validFromRevision: Int = 0,
        val validUntilRevisionExclusive: Int? = null,
        val note: String? = null,
    ) {
        fun activeAt(revision: Int): Boolean =
            validFromRevision <= revision &&
                (validUntilRevisionExclusive == null || revision < validUntilRevisionExclusive)
    }

    private enum class Policy(
        val label: String,
    ) {
        MINIMUM("minimum"),
        FIRST("first"),
        MEDIAN("median"),
        CONSISTENT_WINDOW("consistent-window"),
    }

    private data class Point(
        val day: Long,
        val kg: Double,
    )

    private data class Metrics(
        val biasKg: Double,
        val maeKg: Double,
        val rmseKg: Double,
        val stabilityRmsKg: Double,
        val meanRevisionKg: Double,
        val maxRevisionKg: Double,
        val forecast30dErrorKg: Double,
    )

    private data class SmootherMetrics(
        val biasKg: Double,
        val maeKg: Double,
        val rmseKg: Double,
        val stabilityRmsKg: Double,
        val lagDays: Int,
        val meanRevisionKg: Double,
        val maxRevisionKg: Double,
        val forecast30dErrorKg: Double,
    )

    private val corpus: Corpus by lazy {
        Json.decodeFromString(GoldenFixtures.load("golden/weight_policy_benchmark_v1.json"))
    }

    @Test
    fun corpusPinsEveryRequiredFailureModeWithoutCollapsingRawRows() {
        assertEquals("weight-policy-benchmark-v1", corpus.corpusVersion)
        val raw = corpus.scenarios.flatMap { it.events }
        val rawIds = raw.map { it.id }
        assertEquals(rawIds.size, rawIds.distinct().size, "fixture event IDs must be durable and unique")
        assertTrue(raw.groupBy { it.effectiveDay }.any { it.value.size >= 3 }, "multiple same-day attempts")
        assertTrue(raw.any { it.note?.contains("low outlier") == true }, "low outlier")
        assertTrue(
            corpus.scenarios.any { scenario -> scenario.truth.any { truth -> raw.none { it.effectiveDay == truth.day } } },
            "missing day",
        )
        assertTrue(raw.any { it.localMinute > 18 * 60 }, "mixed times")
        assertTrue(raw.any { it.recordedOnDay > it.effectiveDay }, "backfill")
        assertTrue(raw.map { it.zoneOffsetMinutes }.distinct().size > 1, "travel/timezones")
        assertTrue(raw.any { it.validUntilRevisionExclusive != null }, "edits/deletions")

        val countBefore = raw.size
        Policy.entries.forEach { policy -> corpus.scenarios.forEach { select(it, policy, revision = 1) } }
        assertEquals(countBefore, corpus.scenarios.sumOf { it.events.size }, "benchmarking must not mutate raw events")
    }

    @Test
    fun benchmarkSnapshotIsDeterministicAndAllCandidatesRunOnTheSameCorpus() {
        val report = snapshot()
        println(report)

        // Freeze the complete metric surface. An intentional corpus or metric
        // change updates this snapshot and the evidence report together.
        assertEquals(EXPECTED_SNAPSHOT, report)
    }

    @Test
    fun recommendationFollowsMeasuredTradeoffsRatherThanCompetitorPrecedent() {
        val policies = Policy.entries.associateWith(::policyMetrics)
        val consistent = policies.getValue(Policy.CONSISTENT_WINDOW)
        val minimum = policies.getValue(Policy.MINIMUM)
        val first = policies.getValue(Policy.FIRST)
        val median = policies.getValue(Policy.MEDIAN)

        assertTrue(consistent.maeKg < minimum.maeKg)
        assertTrue(consistent.rmseKg < first.rmseKg)
        assertTrue(consistent.forecast30dErrorKg <= median.forecast30dErrorKg)

        val smoothers = TrendMethod.entries.associateWith(::smootherMetrics)
        assertTrue(
            smoothers.getValue(TrendMethod.ZERO_PHASE_EWMA).lagDays <
                smoothers.getValue(TrendMethod.EWMA).lagDays,
            "zero-phase should buy lower step lag",
        )
        assertTrue(
            smoothers.getValue(TrendMethod.EWMA).meanRevisionKg <
                smoothers.getValue(TrendMethod.ZERO_PHASE_EWMA).meanRevisionKg,
            "past-only EWMA should revise less on average than the future-sensitive option",
        )
    }

    private fun snapshot(): String =
        buildString {
            appendLine("corpus=${corpus.corpusVersion}; rawEvents=${corpus.scenarios.sumOf { it.events.size }}")
            Policy.entries.forEach { policy ->
                val m = policyMetrics(policy)
                appendLine(
                    "policy=${policy.label}; bias=${q(m.biasKg)}; mae=${q(m.maeKg)}; rmse=${q(m.rmseKg)}; " +
                        "stabilityRms=${q(m.stabilityRmsKg)}; " +
                        "revisionMean=${q(m.meanRevisionKg)}; revisionMax=${q(m.maxRevisionKg)}; " +
                        "forecast30d=${q(m.forecast30dErrorKg)}; " + mutationSummary(policy),
                )
            }
            TrendMethod.entries.forEach { method ->
                val m = smootherMetrics(method)
                appendLine(
                    "smoother=${method.wireName}; bias=${q(m.biasKg)}; mae=${q(m.maeKg)}; rmse=${q(m.rmseKg)}; " +
                        "stabilityRms=${q(m.stabilityRmsKg)}; " +
                        "lagDays=${m.lagDays}; revisionMean=${q(m.meanRevisionKg)}; revisionMax=${q(m.maxRevisionKg)}; " +
                        "forecast30d=${q(m.forecast30dErrorKg)}",
                )
            }
        }.trimEnd()

    private fun policyMetrics(policy: Policy): Metrics {
        val errors = mutableListOf<Double>()
        val stability = mutableListOf<Double>()
        val revisions = mutableListOf<Double>()
        val forecasts = mutableListOf<Double>()
        corpus.scenarios.forEach { scenario ->
            val final = select(scenario, policy, revision = 1)
            val before = select(scenario, policy, revision = 0).associateBy { it.day }
            val truth = scenario.truth.associate { it.day to it.kg }
            val scenarioErrors = final.map { it.kg - truth.getValue(it.day) }
            errors += scenarioErrors
            stability += scenarioErrors.zipWithNext { a, b -> b - a }
            final.forEach { point -> before[point.day]?.let { revisions += abs(point.kg - it.kg) } }
            forecasts += forecastError(final, scenario.truth.map { Point(it.day, it.kg) })
        }
        return Metrics(
            biasKg = errors.average(),
            maeKg = errors.map(::abs).average(),
            rmseKg = sqrt(errors.map { it * it }.average()),
            stabilityRmsKg = sqrt(stability.map { it * it }.average()),
            meanRevisionKg = revisions.average(),
            maxRevisionKg = revisions.maxOrNull() ?: 0.0,
            forecast30dErrorKg = forecasts.average(),
        )
    }

    private fun mutationSummary(policy: Policy): String {
        val scenario = corpus.scenarios.first { it.name == "noisy-decline-with-mutations" }
        val before = select(scenario, policy, revision = 0).associateBy { it.day }
        val after = select(scenario, policy, revision = 1).associateBy { it.day }

        fun delta(day: Long): Double = abs(after.getValue(day).kg - before.getValue(day).kg)

        return "editDelta=${q(delta(21012))}; deleteDelta=${q(delta(21013))}; " +
            "backfillCoverageGain=${after.keys.minus(before.keys).size}"
    }

    private fun smootherMetrics(method: TrendMethod): SmootherMetrics {
        val errors = mutableListOf<Double>()
        val stability = mutableListOf<Double>()
        val revisions = mutableListOf<Double>()
        val forecasts = mutableListOf<Double>()
        var lagDays = 0
        corpus.scenarios.forEach { scenario ->
            val finalInput = select(scenario, Policy.CONSISTENT_WINDOW, revision = 1)
            val beforeInput = select(scenario, Policy.CONSISTENT_WINDOW, revision = 0)
            val final = smooth(finalInput, method)
            val before = smooth(beforeInput, method).associateBy { it.day }
            val truth = scenario.truth.associate { it.day to it.kg }
            val scenarioErrors = final.map { it.kg - truth.getValue(it.day) }
            errors += scenarioErrors
            stability += scenarioErrors.zipWithNext { a, b -> b - a }
            final.forEach { point -> before[point.day]?.let { revisions += abs(point.kg - it.kg) } }
            forecasts += forecastError(final, scenario.truth.map { Point(it.day, it.kg) })
            scenario.stepDay?.let { step ->
                val newLevel = scenario.truth.first { it.day == step }.kg
                lagDays =
                    final
                        .firstOrNull { it.day >= step && abs(it.kg - newLevel) <= 0.2 }
                        ?.let { (it.day - step).toInt() }
                        ?: (scenario.truth.last().day - step + 1).toInt()
            }
        }
        return SmootherMetrics(
            biasKg = errors.average(),
            maeKg = errors.map(::abs).average(),
            rmseKg = sqrt(errors.map { it * it }.average()),
            stabilityRmsKg = sqrt(stability.map { it * it }.average()),
            lagDays = lagDays,
            meanRevisionKg = revisions.average(),
            maxRevisionKg = revisions.maxOrNull() ?: 0.0,
            forecast30dErrorKg = forecasts.average(),
        )
    }

    private fun select(
        scenario: Scenario,
        policy: Policy,
        revision: Int,
    ): List<Point> =
        scenario.events
            .filter { it.activeAt(revision) }
            .groupBy { it.effectiveDay }
            .map { (day, events) ->
                val ordered = events.sortedWith(compareBy<Event> { it.localMinute }.thenBy { it.id })
                val kg =
                    when (policy) {
                        Policy.MINIMUM -> ordered.minOf { it.kg }
                        Policy.FIRST -> ordered.first().kg
                        Policy.MEDIAN -> median(ordered.map { it.kg })
                        Policy.CONSISTENT_WINDOW -> {
                            val inWindow =
                                ordered.filter {
                                    it.localMinute >= corpus.consistentWindowStartMinute &&
                                        it.localMinute < corpus.consistentWindowEndMinuteExclusive
                                }
                            inWindow
                                .minWithOrNull(
                                    compareBy<Event> { abs(it.localMinute - corpus.consistentTargetMinute) }
                                        .thenBy { it.localMinute }
                                        .thenBy { it.id },
                                )?.kg ?: median(ordered.map { it.kg })
                        }
                    }
                Point(day, kg)
            }.sortedBy { it.day }

    private fun smooth(
        points: List<Point>,
        method: TrendMethod,
    ): List<Point> =
        SmoothingEngine
            .trend(points.map { WeightSample(it.day, it.kg) }, method)
            .points
            .map { Point(it.epochDay, it.trendKg.value) }

    /** Absolute 30-day endpoint error from an OLS slope over the last 14 available points. */
    private fun forecastError(
        actual: List<Point>,
        truth: List<Point>,
    ): Double {
        val actualWindow = actual.takeLast(14)
        val truthWindow = truth.filter { point -> actualWindow.any { it.day == point.day } }
        val anchorDay = actualWindow.last().day
        val actualForecast = actualWindow.last().kg + slope(actualWindow) * 30.0
        val truthForecast = truthWindow.last().kg + slope(truthWindow) * 30.0
        assertEquals(anchorDay, truthWindow.last().day)
        return abs(actualForecast - truthForecast)
    }

    private fun slope(points: List<Point>): Double {
        val xMean = points.map { it.day.toDouble() }.average()
        val yMean = points.map { it.kg }.average()
        val denominator = points.sumOf { (it.day - xMean).pow(2) }
        return if (denominator == 0.0) 0.0 else points.sumOf { (it.day - xMean) * (it.kg - yMean) } / denominator
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private fun q(value: Double): String = (round(value * 10_000.0) / 10_000.0).toString()

    private companion object {
        // Populated from the deterministic harness; kept here rather than in
        // production so WLO-0072 cannot silently alter a governing default.
        const val EXPECTED_SNAPSHOT: String =
            """corpus=weight-policy-benchmark-v1; rawEvents=54
policy=minimum; bias=-0.0145; mae=0.101; rmse=0.2579; stabilityRms=0.4695; revisionMean=0.2902; revisionMax=9.92; forecast30d=0.6273; editDelta=9.92; deleteDelta=4.01; backfillCoverageGain=1
policy=first; bias=-2.0E-4; mae=0.0916; rmse=0.2448; stabilityRms=0.4238; revisionMean=0.2902; revisionMax=9.92; forecast30d=0.6273; editDelta=9.92; deleteDelta=4.01; backfillCoverageGain=1
policy=median; bias=0.0149; mae=0.0765; rmse=0.1671; stabilityRms=0.2896; revisionMean=0.2484; revisionMax=9.92; forecast30d=0.3102; editDelta=9.92; deleteDelta=2.005; backfillCoverageGain=1
policy=consistent-window; bias=0.03; mae=0.0635; rmse=0.1352; stabilityRms=0.1864; revisionMean=0.2067; revisionMax=9.92; forecast30d=0.0632; editDelta=9.92; deleteDelta=0.0; backfillCoverageGain=1
smoother=ewma; bias=0.2909; mae=0.2909; rmse=0.3704; stabilityRms=0.1351; lagDays=9; revisionMean=0.1745; revisionMax=1.54; forecast30d=0.9319
smoother=ewma-zero-phase; bias=0.0681; mae=0.1891; rmse=0.2248; stabilityRms=0.1391; lagDays=6; revisionMean=0.1895; revisionMax=0.8704; forecast30d=0.9796
smoother=ma-7d; bias=0.1833; mae=0.1841; rmse=0.2767; stabilityRms=0.1426; lagDays=5; revisionMean=0.2341; revisionMax=2.0; forecast30d=1.1008"""
    }
}
