package app.wlo.core.engines

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * F07 decision-ledger record (F07 §3 "[v1] Decision ledger — every check-in's
 * inputs and decision recorded and exportable"; §2 moment 3: Apply/Keep appended
 * to the check-in ledger). Shape is aligned with the provenance table
 * (F13 §3: formulaVersion + inputs + computedAt) so a ledger row feeds the
 * "how we got here" trail without re-modeling.
 *
 * Payloads are canonical `DocumentCodec`-style JSON: data-class serialization
 * is field-ordered and deterministic, so equal inputs produce equal
 * [inputsHashHex] fingerprints.
 */
@Serializable
public data class EngineDecision(
    public val decisionId: String,
    public val atEpochMs: Long,
    public val engineVersion: String,
    public val formulaVersion: String,
    /** Full input snapshot (canonical JSON) — exportable, re-runnable. */
    public val inputSnapshotJson: String,
    /** Deterministic fingerprint of [inputSnapshotJson] (see [InputsHash]). */
    public val inputsHashHex: String,
    /** Canonical JSON of the decision output. */
    public val outputJson: String,
    /** One-line human summary, e.g. "tdee 2410 · apply +50". */
    public val summary: String,
)

public object DecisionLedger {
    /** Compact JSON used for ledger payloads (field order = declaration order). */
    public val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

    /**
     * Builds a ledger record from serializable input/output values. Pure —
     * `atEpochMs` and `decisionId` come from the caller (the check-in flow
     * owns the clock; engines stay D7-pure).
     */
    public inline fun <reified I : Any, reified O : Any> record(
        decisionId: String,
        atEpochMs: Long,
        engineVersion: String,
        formulaVersion: String,
        inputs: I,
        output: O,
        summary: String,
    ): EngineDecision {
        val inputsJson = json.encodeToString(serializer<I>(), inputs)
        val outputJson = json.encodeToString(serializer<O>(), output)
        return EngineDecision(
            decisionId = decisionId,
            atEpochMs = atEpochMs,
            engineVersion = engineVersion,
            formulaVersion = formulaVersion,
            inputSnapshotJson = inputsJson,
            inputsHashHex = InputsHash.fnv1a64(inputsJson),
            outputJson = outputJson,
            summary = summary,
        )
    }
}

/**
 * Deterministic input-set fingerprint for provenance rows and ledger records.
 * FNV-1a 64-bit — deliberately NOT cryptographic: its job is change detection
 * ("did the inputs to this number move?"), not secrecy. Stable across
 * platforms (pure byte math over UTF-8).
 */
public object InputsHash {
    private const val FNV_OFFSET: Long = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val FNV_PRIME: Long = 0x100000001b3L

    public fun fnv1a64(text: String): String {
        var hash = FNV_OFFSET
        for (byte in text.encodeToByteArray()) {
            hash = hash xor (byte.toLong() and 0xffL)
            hash *= FNV_PRIME
        }
        val hex = CharArray(16)
        for (i in 0 until 16) {
            val shift = 60 - i * 4
            hex[i] = HEX_DIGITS[((hash ushr shift) and 0xFL).toInt()]
        }
        return hex.concatToString()
    }

    private const val HEX_DIGITS: String = "0123456789abcdef"
}
