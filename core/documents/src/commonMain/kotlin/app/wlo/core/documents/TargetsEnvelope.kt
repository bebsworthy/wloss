package app.wlo.core.documents

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.serializer

/**
 * Who wrote a Targets version — pinned strings per Appendix A.1
 * (`studio@F01` | `apply@F07`), closed like every polymorphic discriminator
 * (ADR-004 rule 3). Exactly two writers exist (R-B2); the sealed Kotlin API
 * that produces these rows lives in `:core:data` ([TargetsWriter]).
 */
@Serializable
public enum class TargetsWriterId(
    public val wireName: String,
) {
    @SerialName("studio@F01")
    STUDIO_F01("studio@F01"),

    @SerialName("apply@F07")
    APPLY_F07("apply@F07"),
}

/**
 * One immutable Targets version (A.1): identity envelope + the document.
 * `parentVersion` is null only for v1 (plan creation, F01).
 */
@Serializable
public data class TargetsRecord(
    public val version: Int,
    public val parentVersion: Int? = null,
    public val createdAtEpochMs: Long,
    public val createdBy: TargetsWriterId,
    public val document: TargetsDocument,
)

/**
 * Storage funnel for Targets versions (ADR-004 house rules): encode always
 * emits the current schema version; decode routes older versions through
 * transforming serializers. Schema history:
 *
 * - **v1** — the ratified Appendix A shape (initial release).
 */
public object TargetsDocumentIO {
    public const val SCHEMA_VERSION: Int = 1

    /** v1 → v2 placeholder (proves the funnel before any real v2 exists). */
    public object TargetsV1ToV2 : JsonTransformingSerializer<TargetsRecord>(TargetsRecord.serializer()) {
        override fun transformDeserialize(element: JsonElement): JsonElement = element
    }

    public fun encode(record: TargetsRecord): String =
        DocumentCodec.json.encodeToString(
            serializer = serializer<DocumentEnvelope<TargetsRecord>>(),
            value = DocumentEnvelope(SCHEMA_VERSION, record),
        )

    public fun decode(text: String): TargetsRecord {
        val envelope =
            DocumentCodec.json.decodeFromString(
                deserializer = serializer<DocumentEnvelope<JsonObject>>(),
                string = text,
            )
        val payloadSerializer =
            when (val version = envelope.schemaVersion) {
                SCHEMA_VERSION -> TargetsRecord.serializer()
                else -> error("unknown targets schemaVersion: $version (migration funnel owns this, not callers)")
            }
        val record = DocumentCodec.json.decodeFromJsonElement(payloadSerializer, envelope.payload)
        val violations = TargetsInvariants.validate(record.document)
        require(violations.isEmpty()) {
            "decoded targets version ${record.version} violates invariants: " +
                violations.joinToString { it.detail }
        }
        return record
    }
}

/**
 * Appendix A.2 validation invariants — rejections are hard, never silent
 * clamps. Pure: both writers run this before persisting, and decode refuses
 * documents that fail it, so a version in the store is valid by construction.
 */
public object TargetsInvariants {
    /** Weekly-cadence schedules must sum EXACTLY to the weekly budget (A.2 #3). */
    public const val SCHEDULE_SUM_EPSILON: Double = 1e-6

    public fun validate(document: TargetsDocument): List<TargetsViolation> {
        val violations = mutableListOf<TargetsViolation>()
        validateGoal(document, violations)
        validatePaceCap(document, violations)
        validateFloor(document, violations)
        validateSchedule(document, violations)
        validateMacros(document, violations)
        return violations
    }

    public fun isValid(document: TargetsDocument): Boolean = validate(document).isEmpty()

    private fun validateGoal(
        document: TargetsDocument,
        out: MutableList<TargetsViolation>,
    ) {
        val goal = document.goal
        if (!(goal.targetWeightKg > 0.0) || !goal.targetWeightKg.isFinite()) {
            out += TargetsViolation.InvalidGoal("targetWeightKg must be positive and finite")
        }
    }

    /** A.2 #4: pace capped at ±[ConstantsRegistry-free] 1.0 % bodyweight/week. */
    private fun validatePaceCap(
        document: TargetsDocument,
        out: MutableList<TargetsViolation>,
    ) {
        val pace = document.goal.pacePctPerWeek
        if (!pace.isFinite() || kotlin.math.abs(pace) > MAX_PACE_PCT_PER_WEEK) {
            out += TargetsViolation.PaceCapViolated(capPctPerWeek = MAX_PACE_PCT_PER_WEEK, attemptedPct = pace)
        }
    }

    /** A.2 #1: floor — no version may budget below floorKcal. */
    private fun validateFloor(
        document: TargetsDocument,
        out: MutableList<TargetsViolation>,
    ) {
        val energy = document.energy
        val floor = energy.floorKcal
        when (energy.cadence) {
            Cadence.DAILY ->
                energy.budgetKcal?.let { budget ->
                    if (budget < floor) out += TargetsViolation.FloorViolated(floor, budget)
                }

            Cadence.WEEKLY -> {
                energy.weeklyBudgetKcal?.let { weekly ->
                    if (weekly < floor * 7) out += TargetsViolation.FloorViolated(floor, weekly / 7)
                }
                energy.schedule.forEachIndexed { index, day ->
                    if (day < floor) out += TargetsViolation.FloorViolated(floor, day, dayIndex = index)
                }
            }
        }
    }

    /** A.2 #3: weekly schedules sum exactly to the weekly budget, seven entries. */
    private fun validateSchedule(
        document: TargetsDocument,
        out: MutableList<TargetsViolation>,
    ) {
        val energy = document.energy
        if (energy.cadence != Cadence.WEEKLY) return
        if (energy.schedule.size != 7) {
            out += TargetsViolation.ScheduleShapeInvalid(size = energy.schedule.size)
            return
        }
        val weekly = energy.weeklyBudgetKcal ?: return
        val sum = energy.schedule.sum()
        if (kotlin.math.abs(sum - weekly) > SCHEDULE_SUM_EPSILON) {
            out += TargetsViolation.ScheduleSumMismatch(expectedKcal = weekly, actualKcal = sum)
        }
    }

    private fun validateMacros(
        document: TargetsDocument,
        out: MutableList<TargetsViolation>,
    ) {
        val split = document.macros.split
        if (split is MacroSplit.Custom) {
            val pcts = listOfNotNull(split.proteinPct, split.carbPct, split.fatPct)
            if (pcts.size == 3 && kotlin.math.abs(pcts.sum() - 100.0) > 0.01) {
                out += TargetsViolation.MacroSplitInvalid("custom percent split sums to ${pcts.sum()}, expected 100")
            }
            val negative = listOfNotNull(split.proteinG, split.proteinPct, split.carbPct, split.fatPct).any { it < 0.0 }
            if (negative) out += TargetsViolation.MacroSplitInvalid("macro values must be non-negative")
        }
    }

    /** A.2 #4 cap: ±1.0 % bodyweight/week (F07 §3 pace cap; ruling §3 Algorithm constants). */
    public const val MAX_PACE_PCT_PER_WEEK: Double = 1.0
}

/** Machine-readable invariant failures (A.2); mapped to copy in exactly one place. */
public sealed interface TargetsViolation {
    public val detail: String

    public data class FloorViolated(
        public val floorKcal: Double,
        public val attemptedKcal: Double,
        public val dayIndex: Int? = null,
    ) : TargetsViolation {
        override val detail: String
            get() =
                "budget ${attemptedKcal}kcal below the ${floorKcal}kcal floor" +
                    (dayIndex?.let { " (day ${it + 1})" } ?: "")
    }

    public data class PaceCapViolated(
        public val capPctPerWeek: Double,
        public val attemptedPct: Double,
    ) : TargetsViolation {
        override val detail: String
            get() = "pace $attemptedPct%/week exceeds the ±$capPctPerWeek%/week cap"
    }

    public data class ScheduleSumMismatch(
        public val expectedKcal: Double,
        public val actualKcal: Double,
    ) : TargetsViolation {
        override val detail: String
            get() = "schedule sums to ${actualKcal}kcal, weekly budget is ${expectedKcal}kcal"
    }

    public data class ScheduleShapeInvalid(
        public val size: Int,
    ) : TargetsViolation {
        override val detail: String
            get() = "weekly cadence needs exactly 7 schedule entries, got $size"
    }

    public data class InvalidGoal(
        override val detail: String,
    ) : TargetsViolation

    public data class MacroSplitInvalid(
        override val detail: String,
    ) : TargetsViolation
}
