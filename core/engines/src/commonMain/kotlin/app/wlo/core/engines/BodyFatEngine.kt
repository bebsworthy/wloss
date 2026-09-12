package app.wlo.core.engines

import app.wlo.core.model.BodyFatMethod
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Provenance
import app.wlo.core.model.Sex
import kotlinx.datetime.Instant
import kotlin.math.log10

/**
 * F06 body-fat method registry (F06 §3: "a method registry, never one
 * number") — v1 ships the two tape formulas: US Navy (Hodgdon & Beckett
 * circumference equations) and RFM (Woolcott & Bergman 2012). Pure, D7 (the
 * capture instant enters as a parameter).
 *
 * Each method keeps its own series (the caller persists one event per method
 * with `method=<wireName>` on the EAV sidecar); the headline pick is the
 * user's, never the engine's. Every result is `estimated` provenance —
 * F06 §5: girth formulas are estimates with formula + inputs + citation,
 * never measurements.
 *
 * Worked examples (the F06 spec carries none — FLAGGED in M3; canonical
 * published examples used instead, see BodyFatEngineTest):
 *  - Navy, male, 182.88 cm / neck 38.1 / waist 91.44 → 20.277…% (the widely
 *    published "6 ft, 36 in waist, 15 in neck ≈ 20%" DoD example).
 *  - RFM is closed-form: `64 − 20·(height/waist)` (male) — hand-checkable.
 */
public object BodyFatEngine {
    public const val NAVY_VERSION: String = ConstantsRegistry.BODY_FAT_NAVY_FORMULA_VERSION
    public const val RFM_VERSION: String = ConstantsRegistry.BODY_FAT_RFM_FORMULA_VERSION

    /**
     * US Navy, male (circumferences + height in cm, all > 0, waist > neck):
     * `495 / (1.0324 − 0.19077·log10(waist − neck) + 0.15456·log10(height)) − 450`.
     */
    public fun navyMale(
        waistCm: Double,
        neckCm: Double,
        heightCm: Double,
    ): Double {
        require(waistCm > 0.0 && neckCm > 0.0 && heightCm > 0.0) { "girths/height must be positive" }
        require(waistCm > neckCm) { "waist must exceed neck (log of a non-positive number)" }
        return 495.0 / (1.0324 - 0.19077 * log10(waistCm - neckCm) + 0.15456 * log10(heightCm)) - 450.0
    }

    /**
     * US Navy, female (cm; waist + hip > neck):
     * `495 / (1.29579 − 0.35004·log10(waist + hip − neck) + 0.22100·log10(height)) − 450`.
     */
    public fun navyFemale(
        waistCm: Double,
        hipCm: Double,
        neckCm: Double,
        heightCm: Double,
    ): Double {
        require(waistCm > 0.0 && hipCm > 0.0 && neckCm > 0.0 && heightCm > 0.0) {
            "girths/height must be positive"
        }
        require(waistCm + hipCm > neckCm) { "waist + hip must exceed neck" }
        return 495.0 /
            (1.29579 - 0.35004 * log10(waistCm + hipCm - neckCm) + 0.22100 * log10(heightCm)) -
            450.0
    }

    /**
     * RFM (Woolcott & Bergman 2012, PLoS One): male `64 − 20·(height/waist)`,
     * female `76 − 20·(height/waist)`. Undisclosed/other sex has no published
     * constant — DEFAULTED to the midpoint 70 (published here, amendable in
     * one place; surfaced honestly as an estimate either way).
     */
    public fun rfm(
        sex: Sex?,
        heightCm: Double,
        waistCm: Double,
    ): Double {
        require(heightCm > 0.0 && waistCm > 0.0) { "height/waist must be positive" }
        val constant =
            when (sex) {
                Sex.MALE -> 64.0
                Sex.FEMALE -> 76.0
                Sex.OTHER, null -> 70.0
            }
        return constant - 20.0 * (heightCm / waistCm)
    }

    /**
     * Registry door: one estimate with its method, formula version, and exact
     * inputs spelled out (F06 §5 provenance rule). Navy needs the neck (plus
     * hip for female profiles); RFM needs height + waist only. [at] stamps
     * the provenance (the caller owns the clock, D7).
     */
    public fun estimate(
        method: BodyFatMethod,
        sex: Sex?,
        heightCm: Double,
        waistCm: Double,
        neckCm: Double? = null,
        hipCm: Double? = null,
        at: Instant,
    ): BodyFatEstimate {
        val value: Double
        val version: String
        val inputs: List<String>
        when (method) {
            BodyFatMethod.NAVY_TAPE -> {
                val neck = requireNotNull(neckCm) { "Navy method needs a neck measurement" }
                version = NAVY_VERSION
                inputs =
                    listOf(
                        "waistCm=$waistCm",
                        "neckCm=$neck",
                        "heightCm=$heightCm",
                        "sex=${sex?.wireName ?: "undisclosed"}",
                    ) +
                    (hipCm?.let { listOf("hipCm=$it") } ?: emptyList())
                value =
                    if (sex == Sex.FEMALE) {
                        navyFemale(
                            waistCm = waistCm,
                            hipCm = requireNotNull(hipCm) { "Navy (female) needs a hip measurement" },
                            neckCm = neck,
                            heightCm = heightCm,
                        )
                    } else {
                        navyMale(waistCm = waistCm, neckCm = neck, heightCm = heightCm)
                    }
            }
            BodyFatMethod.RFM -> {
                version = RFM_VERSION
                value = rfm(sex, heightCm, waistCm)
                inputs =
                    listOf(
                        "heightCm=$heightCm",
                        "waistCm=$waistCm",
                        "sex=${sex?.wireName ?: "undisclosed"}",
                    )
            }
        }
        return BodyFatEstimate(
            method = method,
            formulaVersion = version,
            inputs = inputs,
            estimate =
                DerivedValue(
                    value = value,
                    provenance = Provenance.Estimated(at = at, method = version),
                ),
        )
    }
}

/** One registry result: the value plus everything the "how we got here" sheet needs. */
public data class BodyFatEstimate(
    public val method: BodyFatMethod,
    public val formulaVersion: String,
    public val inputs: List<String>,
    public val estimate: DerivedValue<Double>,
)
