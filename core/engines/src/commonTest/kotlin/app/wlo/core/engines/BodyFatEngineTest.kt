package app.wlo.core.engines

import app.wlo.core.model.BodyFatMethod
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.Provenance
import app.wlo.core.model.Sex
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Body-fat method registry against published worked examples (F06 §3).
 *
 * FLAGGED: the F06 spec carries no worked examples; canonical published
 * examples are used instead and pinned here (and in the golden fixture) so a
 * formula regression is a test failure, not a silent drift.
 *
 * Sources:
 *  - Navy: Hodgdon & Beckett (1984) circumference equations as published in
 *    the DoD body-composition methodology — the canonical "6 ft male, 36 in
 *    waist, 15 in neck ≈ 20%" example (182.88 / 91.44 / 38.1 cm).
 *  - RFM: Woolcott & Bergman 2012 (PLoS One) — closed form
 *    `64 − 20·(height/waist)` (male), `76 − 20·(height/waist)` (female);
 *    expected values are hand-checkable rationals.
 */
class BodyFatEngineTest {
    private val at = Instant.fromEpochMilliseconds(1_788_482_400_000)

    // --- Navy (tape) ---

    @Test
    fun navyMale_publishedExample() {
        // 6 ft (182.88 cm), waist 36 in (91.44 cm), neck 15 in (38.1 cm) → ≈ 20.3%.
        val bf = BodyFatEngine.navyMale(waistCm = 91.44, neckCm = 38.1, heightCm = 182.88)
        assertEquals(20.27728475087423, bf, absoluteTolerance = 1e-9)
    }

    @Test
    fun navyMale_secondExample() {
        // 180 cm, waist 85, neck 38 → ≈ 16.1% (matches published calculator math).
        val bf = BodyFatEngine.navyMale(waistCm = 85.0, neckCm = 38.0, heightCm = 180.0)
        assertEquals(16.106606138198572, bf, absoluteTolerance = 1e-9)
    }

    @Test
    fun navyFemale_publishedExample() {
        // 5'6" (167.64 cm), waist 30 in (76.2), hips 40 in (101.6), neck 13 in (33.02) → ≈ 30.1%.
        val bf =
            BodyFatEngine.navyFemale(
                waistCm = 76.2,
                hipCm = 101.6,
                neckCm = 33.02,
                heightCm = 167.64,
            )
        assertEquals(30.09635330947583, bf, absoluteTolerance = 1e-9)
    }

    @Test
    fun navy_rejectsImpossibleGirths() {
        assertFailsWith<IllegalArgumentException> {
            BodyFatEngine.navyMale(waistCm = 30.0, neckCm = 40.0, heightCm = 180.0)
        }
        assertFailsWith<IllegalArgumentException> {
            BodyFatEngine.navyFemale(waistCm = 70.0, hipCm = 90.0, neckCm = 180.0, heightCm = 165.0)
        }
    }

    // --- RFM ---

    @Test
    fun rfm_maleClosedForm() {
        // 64 − 20·(175/90) = 64 − 38.888… = 25.111…
        assertEquals(25.111111111111114, BodyFatEngine.rfm(Sex.MALE, 175.0, 90.0), absoluteTolerance = 1e-12)
    }

    @Test
    fun rfm_femaleClosedForm() {
        // 76 − 20·(162/78) = 76 − 41.538… = 34.4615…
        assertEquals(34.46153846153846, BodyFatEngine.rfm(Sex.FEMALE, 162.0, 78.0), absoluteTolerance = 1e-12)
    }

    @Test
    fun rfm_undisclosedSexUsesThePublishedMidpointDefault() {
        // DEFAULTED (not from the paper): midpoint constant 70, published in the registry.
        assertEquals(70.0 - 20.0 * (170.0 / 85.0), BodyFatEngine.rfm(null, 170.0, 85.0), absoluteTolerance = 1e-12)
        assertEquals(30.0, BodyFatEngine.rfm(Sex.OTHER, 170.0, 85.0), absoluteTolerance = 1e-12)
    }

    // --- Registry door: provenance shape ---

    @Test
    fun estimate_carriesEstimatedProvenanceAndInputs() {
        val result =
            BodyFatEngine.estimate(
                method = BodyFatMethod.NAVY_TAPE,
                sex = Sex.MALE,
                heightCm = 182.88,
                waistCm = 91.44,
                neckCm = 38.1,
                at = at,
            )
        assertEquals(BodyFatMethod.NAVY_TAPE, result.method)
        assertEquals(ConstantsRegistry.BODY_FAT_NAVY_FORMULA_VERSION, result.formulaVersion)
        val provenance = assertIs<Provenance.Estimated>(result.estimate.provenance)
        assertEquals(at, provenance.at)
        assertTrue(result.inputs.any { it.startsWith("waistCm=91.44") })
        assertTrue(abs(result.estimate.value - 20.27728475087423) < 1e-9)
    }

    @Test
    fun estimate_rfmNeedsNoNeck() {
        val result =
            BodyFatEngine.estimate(
                method = BodyFatMethod.RFM,
                sex = Sex.FEMALE,
                heightCm = 162.0,
                waistCm = 78.0,
                at = at,
            )
        assertEquals(34.46153846153846, result.estimate.value, absoluteTolerance = 1e-12)
        assertEquals(ConstantsRegistry.BODY_FAT_RFM_FORMULA_VERSION, result.formulaVersion)
    }

    @Test
    fun estimate_navyWithoutNeckIsAContractError() {
        assertFailsWith<IllegalArgumentException> {
            BodyFatEngine.estimate(
                method = BodyFatMethod.NAVY_TAPE,
                sex = Sex.MALE,
                heightCm = 180.0,
                waistCm = 85.0,
                at = at,
            )
        }
    }

    @Test
    fun estimate_femaleNavyRequiresHip() {
        assertFailsWith<IllegalArgumentException> {
            BodyFatEngine.estimate(
                method = BodyFatMethod.NAVY_TAPE,
                sex = Sex.FEMALE,
                heightCm = 167.64,
                waistCm = 76.2,
                neckCm = 33.02,
                at = at,
            )
        }
    }
}
