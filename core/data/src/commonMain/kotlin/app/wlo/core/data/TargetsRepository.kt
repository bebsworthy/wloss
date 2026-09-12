package app.wlo.core.data

import app.wlo.core.common.WloResult
import app.wlo.core.documents.TargetsRecord
import app.wlo.core.documents.TargetsWriterId
import kotlinx.coroutines.flow.Flow

/**
 * Read door for Targets versions (Appendix A.1). Writes have exactly two
 * doors — see [TargetsWriter]; this interface is read-only, and the internal
 * store the writers use is not exported from this module.
 */
public interface TargetsRepository {
    /** The active version (never superseded), null before the first write. */
    public suspend fun current(profileId: String): WloResult<TargetsRecord?>

    public fun observeCurrent(profileId: String): Flow<WloResult<TargetsRecord?>>

    /** Full immutable history, oldest first — powers the version timeline + revert. */
    public suspend fun history(profileId: String): WloResult<List<TargetsRecord>>
}

/** Machine-readable rejection reasons for the write path (A.2). */
public sealed class TargetsWriteError {
    /** A.2 invariants 1–4 (+ goal/macro shape): hard rejections, never clamps. */
    public data class InvariantViolated(
        public val violations: List<app.wlo.core.documents.TargetsViolation>,
    ) : TargetsWriteError()

    /** Overriding the default floor needs a persistent acknowledgment (A.1). */
    public data class FloorOverrideUnacknowledged(
        public val floorKcal: Double,
        public val defaultKcal: Double,
    ) : TargetsWriteError()

    /**
     * A.2 #2 — no eat-back: the proposed eating target exceeds measured
     * expenditure (F05/F13 context is structurally incapable of raising
     * targets). v1 models loss/maintenance; a TDEE raise flows in through
     * the measured term, never through exercise credit.
     */
    public data class EatBackRejected(
        public val budgetKcal: Double,
        public val measuredTdeeKcal: Double,
    ) : TargetsWriteError()

    /** The base version moved under the writer (Studio edit vs. Apply race). */
    public data class VersionConflict(
        public val expectedBaseVersion: Int,
        public val actualCurrentVersion: Int?,
    ) : TargetsWriteError()

    /** Apply with no active plan — F01 must write v1 first (R-B2). */
    public data class NoActiveTargets(
        public val profileId: String,
    ) : TargetsWriteError()

    /** The persistence layer failed while committing the version (D8 wrap). */
    public data class StorageFailure(
        public val cause: Throwable,
    ) : TargetsWriteError()
}

/** A writer outcome: a written immutable version, or a sealed rejection. */
public sealed interface TargetsWriteOutcome {
    public data class Written(
        public val record: TargetsRecord,
        /** Human-readable change lines ("budget 1900 → 1950 kcal") for the diff ribbon. */
        public val diff: List<String>,
        public val writtenBy: TargetsWriterId,
    ) : TargetsWriteOutcome

    public data class Rejected(
        public val error: TargetsWriteError,
    ) : TargetsWriteOutcome
}
