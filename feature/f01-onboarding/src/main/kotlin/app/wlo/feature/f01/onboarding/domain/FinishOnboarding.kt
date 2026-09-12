package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.common.AppError
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.F01StudioWriter
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.NewMeasurement
import app.wlo.core.data.NewProfile
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.TargetsWriters
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.documents.Cadence
import app.wlo.core.documents.ConstraintApplier
import app.wlo.core.documents.DietTemplate
import app.wlo.core.documents.DietTemplateApplier
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.Sex
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * The Start-step write (F01 §3 step 8 / Appendix A.2 writer #1, R-B2): create
 * the (single, R-B9) profile, anchor the first trend scalar, write Targets v1
 * through the sealed studio writer, then mark onboarding complete and retire
 * the draft. Every step is fallible as a value (D8); a rejection surfaces
 * honestly and leaves no partial state behind (profile + trend only land when
 * the whole write can proceed; a targets rejection aborts before the flag).
 */
public class FinishOnboarding(
    private val profiles: ProfileRepository,
    private val measurements: MeasurementRepository,
    private val writers: TargetsWriters,
    private val documents: JsonDocumentStore,
    private val clock: ClockPort,
) {
    /**
     * @param template the picked template (caller guarantees non-null; the
     *   default template is the fallback upstream)
     * @param draftTargets the applier draft shown on the review screen
     * @param draftWeightKg the current-weight entry, appended as the anchor
     *   trend scalar (event-level, R-B8)
     */
    public suspend operator fun invoke(
        profile: NewProfile,
        template: DietTemplate,
        draftTargets: TargetsDocument,
        draftWeightKg: Double?,
        timeZone: TimeZone,
    ): WloResult<Unit> {
        val now: Instant = clock.now()

        val created =
            profiles.create(profile, now).getOrNull()
                ?: return WloResult.err(AppError.Storage(cause = null, detail = "profile.create"))

        if (draftWeightKg != null) {
            measurements
                .append(
                    NewMeasurement(
                        profileId = created.id,
                        dayEpochDay = DayBoundary.epochDay(now, timeZone),
                        kind = MeasurementKind.TREND,
                        valueReal = draftWeightKg,
                        source = SOURCE_ONBOARDING,
                        capturedAt = now,
                    ),
                ).getOrNull()
                ?: return WloResult.err(AppError.Storage(cause = null, detail = "trend.append"))
        }

        val studioWriter: F01StudioWriter = writers.studio()
        val writeOutcome = studioWriter.writeFirst(profileId = created.id, document = draftTargets)
        val writeError =
            when (writeOutcome) {
                is app.wlo.core.data.TargetsWriteOutcome.Written -> null
                is app.wlo.core.data.TargetsWriteOutcome.Rejected ->
                    AppError.InvalidInput(detail = writeOutcome.error.toString())
            }
        if (writeError != null) return WloResult.err(writeError)

        documents.writeFlag(FLAG_COMPLETE, true)
        documents.remove(DRAFT_KEY)
        return WloResult.ok(Unit)
    }

    public companion object {
        public const val SOURCE_ONBOARDING: String = "onboarding"
        public const val FLAG_COMPLETE: String = "onboarding/complete"
        public const val DRAFT_KEY: String = "onboarding/draft"

        /**
         * Builds the applier context from wizard state (formula TDEE from the
         * wizard's own Mifflin-St Jeor × activity math; the deficit fraction
         * derives from the chosen pace so budget follows pace, not a fixed %).
         */
        public fun applierContext(
            sex: Sex?,
            birthYear: Int?,
            heightCm: Double?,
            currentWeightKg: Double?,
            goalWeightKg: Double,
            pacePctPerWeek: Double,
            formulaTdeeKcal: Double?,
        ): DietTemplateApplier.Context =
            DietTemplateApplier.Context(
                sex = sex?.wireName,
                birthYear = birthYear,
                heightCm = heightCm,
                currentWeightKg = currentWeightKg,
                goalWeightKg = goalWeightKg,
                pacePctPerWeek = pacePctPerWeek,
                formulaTdeeKcal = formulaTdeeKcal,
                deficitFraction = deficitFraction(pacePctPerWeek, currentWeightKg, formulaTdeeKcal),
            )

        /** Pace-deficit fraction of TDEE; null-safe (applier falls back to its default budget). */
        public fun deficitFraction(
            pacePctPerWeek: Double,
            currentWeightKg: Double?,
            formulaTdeeKcal: Double?,
        ): Double {
            val tdee = formulaTdeeKcal ?: return FALLBACK_DEFICIT_FRACTION
            val weight = currentWeightKg ?: return FALLBACK_DEFICIT_FRACTION
            val dailyDeficit = pacePctPerWeek / 100.0 * weight * ConstantsRegistry.KCAL_PER_KG_FAT / 7.0
            return (dailyDeficit / tdee).coerceIn(0.0, 0.5)
        }

        /**
         * Merges the deterministic constraint application into the applier's
         * draft (F01 §3 v1 deterministic path, R-S10): protein grams, schedule
         * (weekly cadence, pinned sum), eating window. Unresolved phrasings
         * are NOT guessed into fields — they stayed `held` in the preview.
         */
        public fun applyConstraints(
            draft: TargetsDocument,
            application: ConstraintApplier.Application,
        ): TargetsDocument {
            var result = draft

            application.proteinGrams?.let { grams ->
                val split = result.macros.split
                result =
                    result.copy(
                        macros =
                            result.macros.copy(
                                split =
                                    when (split) {
                                        is MacroSplit.Custom -> split.copy(proteinG = grams)
                                        is MacroSplit.Preset ->
                                            MacroSplit.Custom(proteinG = grams)
                                    },
                            ),
                    )
            }

            val schedule = application.weekSchedule
            if (schedule != null && schedule.size == 7) {
                val weekly = schedule.sum()
                result =
                    result.copy(
                        energy =
                            result.energy.copy(
                                cadence = Cadence.WEEKLY,
                                budgetKcal = null,
                                weeklyBudgetKcal = weekly,
                                schedule = schedule,
                            ),
                    )
            }

            application.eatingWindow?.let { window ->
                result = result.copy(macros = result.macros.copy(eatingWindow = window))
            }

            return result
        }
    }
}

/** Private constants holder (keeps the companion's public API tight). */
private const val FALLBACK_DEFICIT_FRACTION: Double = 0.2
