package app.wlo.core.data

import app.wlo.core.common.ClockPort
import app.wlo.core.documents.Cadence
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.documents.TargetsInvariants
import app.wlo.core.documents.TargetsRecord
import app.wlo.core.documents.TargetsViolation
import app.wlo.core.documents.TargetsWriterId
import app.wlo.core.model.ConstantsRegistry
import kotlinx.serialization.Serializable

/**
 * The Targets write path (R-B2, Appendix A.2): writers are EXACTLY TWO,
 * enforced by the Kotlin compiler — [TargetsWriter] is sealed, so only this
 * module can implement it, and the version store below is `internal`, so no
 * other module can persist a version except through these two classes:
 *
 *  - [F01StudioWriter] — plan creation + Studio edits + revert (F01).
 *  - [F07ApplyWriter] — adaptive adjustments, only via explicit Apply (F07).
 *
 * Every save creates immutable `vN+1`; "revert" copies an older version
 * forward. Rejections are hard, never silent clamps (A.2).
 */
public sealed interface TargetsWriter {
    public val writerId: TargetsWriterId
}

/** F07 check-in Apply input. Deliberately carries NO exercise/expenditure-
 * credit field — "context, never credit" is enforced by the type shape plus
 * the [EatBackRejected] bound (A.2 #2), not by convention. */
@Serializable
public data class AdaptiveProposal(
    /** Check-in decision id — ties the applied version to the F07 ledger. */
    public val decisionId: String,
    public val measuredTdeeKcal: Double,
    public val newBudgetKcal: Double? = null,
    public val newWeeklyBudgetKcal: Double? = null,
    public val newSchedule: List<Double>? = null,
    /** Optional goal-pace revision (±1.0 cap still applies). */
    public val pacePctPerWeek: Double? = null,
    /**
     * F07 decision-ledger record (formulaVersion + inputs + output) — its
     * hash lands in provenance rows; export is F13's job.
     */
    public val ledgerHashHex: String? = null,
)

public class F01StudioWriter internal constructor(
    private val store: TargetsVersionStore,
    private val clock: ClockPort,
) : TargetsWriter {
    override val writerId: TargetsWriterId = TargetsWriterId.STUDIO_F01

    /** Plan creation: writes v1 (F01's Start step; R-B2 "F01 writes v1"). */
    public suspend fun writeFirst(
        profileId: String,
        document: TargetsDocument,
    ): TargetsWriteOutcome = write(profileId, baseVersion = null, document = document)

    /** Studio edit: validates the caller saw the current version (diff shown). */
    public suspend fun writeRevision(
        profileId: String,
        baseVersion: Int,
        document: TargetsDocument,
    ): TargetsWriteOutcome = write(profileId, baseVersion = baseVersion, document = document)

    /** Revert = a new version copying [toVersion]; never a history rewrite (A.1). */
    public suspend fun revert(
        profileId: String,
        toVersion: Int,
    ): TargetsWriteOutcome {
        val current =
            store.current(profileId)
                ?: return TargetsWriteOutcome.Rejected(TargetsWriteError.NoActiveTargets(profileId))
        val source =
            if (current.version == toVersion) {
                current
            } else {
                store.history(profileId).firstOrNull { it.version == toVersion }
                    ?: return TargetsWriteOutcome.Rejected(
                        TargetsWriteError.VersionConflict(toVersion, current.version),
                    )
            }
        // Reverting to the current version is still a versioned save (no-op diff).
        return write(profileId, baseVersion = current.version, document = source.document)
    }

    private suspend fun write(
        profileId: String,
        baseVersion: Int?,
        document: TargetsDocument,
    ): TargetsWriteOutcome {
        val failures = validate(profileId, document)
        if (failures.isNotEmpty()) {
            return TargetsWriteOutcome.Rejected(TargetsWriteError.InvariantViolated(failures))
        }
        return store.write(profileId, writerId, baseVersion, document, clock.now().toEpochMilliseconds())
    }

    private suspend fun validate(
        profileId: String,
        document: TargetsDocument,
    ): List<TargetsViolation> {
        val violations = TargetsInvariants.validate(document)
        // Floor override (A.1): below the registry default for this profile's
        // sex requires a persistent acknowledgment — and validation against
        // the (lowered) floor still ran above, never weakened.
        val default = store.defaultFloorKcal(profileId)
        if (document.energy.floorKcal < default && !document.energy.floorOverrideAcknowledged) {
            return listOf(TargetsViolation.FloorViolated(default, document.energy.floorKcal)) + violations
        }
        return violations
    }
}

public class F07ApplyWriter internal constructor(
    private val store: TargetsVersionStore,
    private val clock: ClockPort,
) : TargetsWriter {
    override val writerId: TargetsWriterId = TargetsWriterId.APPLY_F07

    /**
     * Check-in Apply: atomic, ledgered ([AdaptiveProposal.decisionId]),
     * reversible through the ledger + version history (F07 §7).
     */
    public suspend fun apply(
        profileId: String,
        proposal: AdaptiveProposal,
    ): TargetsWriteOutcome {
        val current =
            store.current(profileId)
                ?: return TargetsWriteOutcome.Rejected(TargetsWriteError.NoActiveTargets(profileId))

        // A.2 #2 — no eat-back, enforced HERE (write path, not convention):
        // the new eating budget may never exceed measured expenditure
        // (tolerance: rounding to whole kcal).
        val budgets = listOfNotNull(proposal.newBudgetKcal, proposal.newWeeklyBudgetKcal?.div(7.0))
        budgets.maxOrNull()?.let { maxDaily ->
            if (maxDaily > proposal.measuredTdeeKcal + EAT_BACK_TOLERANCE_KCAL) {
                return TargetsWriteOutcome.Rejected(
                    TargetsWriteError.EatBackRejected(maxDaily, proposal.measuredTdeeKcal),
                )
            }
        }

        val old = current.document
        val energy = old.energy
        val newEnergy =
            energy.copy(
                budgetKcal = proposal.newBudgetKcal ?: energy.budgetKcal,
                weeklyBudgetKcal = proposal.newWeeklyBudgetKcal ?: energy.weeklyBudgetKcal,
                schedule =
                    proposal.newSchedule
                        ?.takeIf { energy.cadence == Cadence.WEEKLY && it.size == 7 }
                        ?: energy.schedule,
            )
        val newDocument =
            old.copy(
                goal =
                    proposal.pacePctPerWeek
                        ?.let { pace -> old.goal.copy(pacePctPerWeek = pace) }
                        ?: old.goal,
                energy = newEnergy,
            )

        val violations = TargetsInvariants.validate(newDocument)
        if (violations.isNotEmpty()) {
            return TargetsWriteOutcome.Rejected(TargetsWriteError.InvariantViolated(violations))
        }

        return store.write(
            profileId = profileId,
            writerId = writerId,
            baseVersion = current.version,
            document = newDocument,
            atEpochMs = clock.now().toEpochMilliseconds(),
        )
    }

    public companion object {
        /**
         * Rounding tolerance for the no-eat-back bound (proposals are computed
         * in whole kcal; a 1 kcal float drift must not reject a maintenance
         * budget). DEFAULTED — not fixed by any spec.
         */
        public const val EAT_BACK_TOLERANCE_KCAL: Double = 1.0
    }
}

/**
 * Composition-root entry point (bound in :app's Koin graph): hands out the
 * two — and only two — writer handles. Takes only public types (D5); the
 * version store it builds is module-internal, so no feature can grow a third
 * writer or a raw setter. The concrete writer types return so F01/F07 call
 * their own verb set (writeFirst/writeRevision vs apply); the sealed
 * [TargetsWriter] hierarchy still guarantees exactly these two implementations.
 */
public class TargetsWriters public constructor(
    db: app.wlo.core.database.WloDatabase,
    private val clock: ClockPort,
) {
    private val versionStore: TargetsVersionStore = RoomTargetsVersionStore(db)

    public fun studio(): F01StudioWriter = F01StudioWriter(versionStore, clock)

    public fun apply(): F07ApplyWriter = F07ApplyWriter(versionStore, clock)
}

/**
 * Internal persistence seam — NOT exported (D5/explicitApi): only the two
 * writers above may produce Targets rows (R-B2, structural).
 */
internal interface TargetsVersionStore {
    suspend fun current(profileId: String): TargetsRecord?

    suspend fun history(profileId: String): List<TargetsRecord>

    suspend fun defaultFloorKcal(profileId: String): Double

    suspend fun write(
        profileId: String,
        writerId: TargetsWriterId,
        baseVersion: Int?,
        document: TargetsDocument,
        atEpochMs: Long,
    ): TargetsWriteOutcome
}

/** The provenance fingerprint space for applied deltas (registry-owned version). */
public object TargetsProvenance {
    public const val APPLY_FORMULA_VERSION: String = "targets/apply-${ConstantsRegistry.VERSION}"
}
