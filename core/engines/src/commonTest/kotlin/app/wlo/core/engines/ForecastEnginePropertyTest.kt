package app.wlo.core.engines

import app.wlo.core.model.ActivityLevel
import app.wlo.core.model.Sex
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property tests (T-J) for the forecast + TDEE invariants: bands decelerate
 * monotonically, band ordering holds, and the closed-form window math is
 * exact. Generated cases ride beside the frozen golden scenarios. Kotest's
 * Double arb emits NaN/∞ edges, so every generator filters to finite values.
 */
class ForecastEnginePropertyTest {
    private val startDay = 20_700L

    private fun finite(
        min: Double,
        max: Double,
    ): Arb<Double> = Arb.numericDouble(min, max)

    private fun coldStartInput(
        sex: Sex,
        age: Int,
        heightCm: Double,
        startKg: Double,
        goalKg: Double,
        level: ActivityLevel,
        intake: Double,
    ): ColdStartInput =
        ColdStartInput(
            sex = sex,
            ageYears = age,
            heightCm = heightCm,
            startTrendKg = startKg,
            goalWeightKg = goalKg,
            activityLevel = level,
            intakeKcal = intake,
            startEpochDay = startDay,
            startInstant = Instant.fromEpochMilliseconds(0),
        )

    @Test
    fun bandsFinishInOrder_optimisticThenExpectedThenPessimistic() =
        runTest {
            checkAll(
                Arb.int(20..70),
                finite(150.0, 200.0),
                finite(60.0, 130.0),
                finite(0.05, 0.9),
            ) { age, heightCm, startKg, lossFraction ->
                val goal = startKg * (1.0 - lossFraction)
                val tdee =
                    ForecastEngine.bmrMifflinStJeor(Sex.FEMALE, startKg, heightCm, age) *
                        1.55 // MODERATE
                // Intake a quarter under TDEE: a real, sustainable deficit.
                val intake = tdee * 0.75
                val bands =
                    ForecastEngine.coldStart(
                        coldStartInput(Sex.FEMALE, age, heightCm, startKg, goal, ActivityLevel.MODERATE, intake),
                    )
                val optimistic = bands.optimistic.finishEpochDay
                val expected = bands.expected.finishEpochDay
                val pessimistic = bands.pessimistic.finishEpochDay
                assertTrue(
                    listOfNotNull(optimistic, expected, pessimistic).zipWithNext().all { (a, b) -> a <= b },
                    "band finishes must be ordered optimistic ≤ expected ≤ pessimistic " +
                        "(got $optimistic, $expected, $pessimistic)",
                )
            }
        }

    @Test
    fun weeklyRatesDecelerateMonotonically() =
        runTest {
            checkAll(
                Arb.int(20..70),
                finite(150.0, 200.0),
                finite(70.0, 130.0),
                finite(0.03, 0.5),
            ) { age, heightCm, startKg, lossFraction ->
                val bands =
                    ForecastEngine.coldStart(
                        coldStartInput(
                            sex = Sex.MALE,
                            age = age,
                            heightCm = heightCm,
                            startKg = startKg,
                            goalKg = startKg * (1.0 - lossFraction),
                            level = ActivityLevel.MODERATE,
                            intake = ForecastEngine.bmrMifflinStJeor(Sex.MALE, startKg, heightCm, age) * 1.55 * 0.8,
                        ),
                    )
                bands.expected.weeklyRatesKg.zipWithNext().forEach { (a, b) ->
                    assertTrue(
                        b <= a + 1e-12,
                        "deceleration core: weekly rate must never grow (got $a → $b)",
                    )
                }
                assertTrue(bands.expected.weeklyRatesKg.first() > 0.0, "deficit path starts positive")
            }
        }

    @Test
    fun gainBandsAreOrderedMonotonicAndDecelerating() =
        runTest {
            checkAll(
                Arb.int(20..70),
                finite(150.0, 200.0),
                finite(60.0, 120.0),
                finite(1.0, 10.0),
            ) { age, heightCm, startKg, gainKg ->
                val tdee =
                    ForecastEngine.bmrMifflinStJeor(Sex.MALE, startKg, heightCm, age) *
                        1.375
                val bands =
                    ForecastEngine.coldStart(
                        coldStartInput(
                            sex = Sex.MALE,
                            age = age,
                            heightCm = heightCm,
                            startKg = startKg,
                            goalKg = startKg + gainKg,
                            level = ActivityLevel.LIGHT,
                            intake = tdee + 400.0,
                        ),
                    )
                val finishes =
                    listOf(
                        assertNotNull(bands.optimistic.finishEpochDay),
                        assertNotNull(bands.expected.finishEpochDay),
                        assertNotNull(bands.pessimistic.finishEpochDay),
                    )
                assertTrue(finishes.zipWithNext().all { (a, b) -> a <= b })
                assertTrue(
                    bands.expected.trajectoryKg
                        .zipWithNext()
                        .all { (a, b) -> b >= a },
                )
                assertTrue(bands.expected.weeklyRatesKg.all { it <= 0.0 })
                assertTrue(
                    bands.expected.weeklyRatesKg
                        .zipWithNext()
                        .all { (a, b) -> b >= a - 1e-12 },
                )
            }
        }

    @Test
    fun trajectoryStaysBetweenGoalAndStart() =
        runTest {
            checkAll(
                Arb.int(20..70),
                finite(155.0, 195.0),
                finite(65.0, 120.0),
                Arb.positiveInt(12).filter { it <= 40 },
            ) { age, heightCm, startKg, weeksIn ->
                val goal = startKg - weeksIn * 0.4
                val bands =
                    ForecastEngine.coldStart(
                        coldStartInput(Sex.FEMALE, age, heightCm, startKg, goal, ActivityLevel.LIGHT, 1_500.0),
                    )
                bands.expected.trajectoryKg.forEach { w ->
                    assertTrue(
                        w in goal..startKg,
                        "projected weight $w escaped the [goal, start] cone (goal=$goal start=$startKg)",
                    )
                }
            }
        }

    @Test
    fun bmrRespondsToWeightAtThePublishedCoefficient() =
        runTest {
            checkAll(Arb.int(18..90), finite(140.0, 210.0)) { age, heightCm ->
                val light = ForecastEngine.bmrMifflinStJeor(Sex.MALE, 80.0, heightCm, age)
                val heavy = ForecastEngine.bmrMifflinStJeor(Sex.MALE, 90.0, heightCm, age)
                assertEquals(100.0, heavy - light, absoluteTolerance = 1e-9)
                val female = ForecastEngine.bmrMifflinStJeor(Sex.FEMALE, 80.0, heightCm, age)
                assertEquals(-166.0, female - light, absoluteTolerance = 1e-9)
            }
        }

    @Test
    fun tdeeWindowMathIsExactForSyntheticWindows() =
        runTest {
            checkAll(
                finite(1_200.0, 3_500.0),
                finite(-1.5, 1.5),
                Arb.int(2..13),
            ) { intake, lossOverWindow, halfWindow ->
                val days = halfWindow * 2
                val start = 90_000L
                val window =
                    (0 until days).map { d ->
                        EnergyDay(
                            epochDay = start + d.toLong(),
                            intakeKcal = intake,
                            status = DayStatus.LOGGED,
                            trendWeightKg = 80.0 + lossOverWindow * d / (days - 1),
                        )
                    }
                val result = assertNotNull(EnergyEngine.measuredTdee(window))
                assertEquals(intake, result.avgIntakeKcal, absoluteTolerance = 1e-9)
                // Span is (days − 1) day gaps; the weekly rate rescales by 7/span.
                val expectedWeekly = lossOverWindow * 7.0 / (days - 1)
                assertEquals(expectedWeekly, result.weeklyTrendChangeKg, absoluteTolerance = 1e-9)
                assertEquals(
                    intake - expectedWeekly * 7_700.0 / 7.0,
                    result.tdeeKcal,
                    absoluteTolerance = 1e-9,
                )
            }
        }

    @Test
    fun ewmaIsBoundedByInputExtremesAndMonotoneInAlpha() =
        runTest {
            checkAll(finite(40.0, 160.0), finite(40.0, 160.0)) { a, b ->
                // A constant series has no direction to track: the monotonicity
                // property is vacuous there (both alphas reproduce the input).
                if (kotlin.math.abs(a - b) < 1e-9) return@checkAll
                val series = listOf(a, b, a, b, a, b)
                val out = EnergyEngine.ewma(series)
                // FP rounding can step 1 ulp past an input at the α-blend seam.
                assertTrue(
                    out.all { it in minOf(a, b) - 1e-9..maxOf(a, b) + 1e-9 },
                    "EWMA cannot overshoot its inputs",
                )
                val stiff = EnergyEngine.ewma(series, alpha = 0.15)
                val loose = EnergyEngine.ewma(series, alpha = 0.9)
                assertTrue(
                    kotlin.math.abs(loose[1] - b) < kotlin.math.abs(stiff[1] - b),
                    "larger alpha must track the input faster",
                )
            }
        }
}
