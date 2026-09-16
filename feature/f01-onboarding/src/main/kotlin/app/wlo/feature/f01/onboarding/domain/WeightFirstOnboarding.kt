package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.common.AppError
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.MassUnit
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.NewProfile
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.WeighInRepository
import app.wlo.core.data.WeighInWriteCommand
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.documents.DocumentCodec
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.UnitSystem
import app.wlo.core.model.WeightGoalEligibility
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafety
import app.wlo.core.model.WeightGoalSafetyInput
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

@Serializable
public enum class WeightFirstStep { WELCOME, UNIT, GOAL, WEIGHT }

@Serializable
public enum class FirstWeightSource { NONE, MANUAL, FILE_IMPORT, HEALTH_CONNECT }

/** Resumable first-run state. Unknown values are null, never synthetic health facts. */
@Serializable
public data class WeightFirstDraft(
    val step: WeightFirstStep = WeightFirstStep.WELCOME,
    val unit: MassUnit? = null,
    val goalMode: WeightGoalMode? = null,
    val targetWeightKg: Double? = null,
    val pacePctPerWeek: Double? = null,
    val weightSource: FirstWeightSource = FirstWeightSource.NONE,
    val firstWeightKg: Double? = null,
) {
    public fun eligibility(): WeightGoalEligibility =
        WeightGoalSafety.evaluate(
            WeightGoalSafetyInput(
                ageYears = null,
                mode = goalMode,
                currentWeightKg = firstWeightKg,
                targetWeightKg = targetWeightKg,
                requestedPacePctPerWeek = pacePctPerWeek,
                // First-run deliberately does not collect sensitive screening or age.
                pregnant = SafetyAnswer.NOT_ANSWERED,
                breastfeeding = SafetyAnswer.NOT_ANSWERED,
                eatingDisorderConcern = SafetyAnswer.NOT_ANSWERED,
                medicallyInfluencedWeight = SafetyAnswer.NOT_ANSWERED,
            ),
        )
}

public class WeightFirstOnboardingStore(
    private val documents: JsonDocumentStore,
) {
    public suspend fun read(): WeightFirstDraft = documents.readText(DRAFT_KEY)?.let(::decodeDraft) ?: EMPTY_DRAFT

    public suspend fun write(draft: WeightFirstDraft) {
        documents.writeText(DRAFT_KEY, DocumentCodec.json.encodeToString(WeightFirstDraft.serializer(), draft))
    }

    public suspend fun finish(draft: WeightFirstDraft) {
        documents.writeText(GOAL_INTENT_KEY, DocumentCodec.json.encodeToString(WeightFirstDraft.serializer(), draft))
        documents.writeText(HANDOFF_KEY, draft.weightSource.name)
        documents.writeFlag(FinishOnboarding.FLAG_COMPLETE, true)
        documents.remove(DRAFT_KEY)
    }

    public suspend fun consumeHandoff() {
        documents.remove(HANDOFF_KEY)
    }

    public companion object {
        private val EMPTY_DRAFT: WeightFirstDraft = WeightFirstDraft()
        public const val DRAFT_KEY: String = "onboarding/weight-first-draft"
        public const val GOAL_INTENT_KEY: String = "weight/goal-intent-v1"
        public const val HANDOFF_KEY: String = "onboarding/post-completion-handoff"

        public fun decodeDraft(text: String): WeightFirstDraft =
            runCatching { DocumentCodec.json.decodeFromString(WeightFirstDraft.serializer(), text) }
                .getOrDefault(WeightFirstDraft())
    }
}

/** Atomic-enough first-run writer: profile/weight first, completion flag last. */
public class FinishWeightFirstOnboarding(
    private val profiles: ProfileRepository,
    private val weighIns: WeighInRepository,
    private val settings: SettingsStore,
    private val store: WeightFirstOnboardingStore,
    private val clock: ClockPort,
) {
    public suspend operator fun invoke(draft: WeightFirstDraft): WloResult<Unit> {
        val unit = draft.unit ?: return WloResult.err(AppError.InvalidInput("Choose kg or lb."))
        settings.setMassUnit(unit)
        val now = clock.now()
        val profile =
            profiles.active().getOrNull()
                ?: profiles
                    .create(
                        NewProfile(
                            startWeightKg = draft.firstWeightKg,
                            unitPreference = if (unit == MassUnit.KILOGRAM) UnitSystem.METRIC else UnitSystem.IMPERIAL,
                        ),
                        now,
                    ).getOrNull()
                ?: return WloResult.err(AppError.Storage(null, "profile.create"))
        draft.firstWeightKg?.let { weight ->
            weighIns
                .commitWeighIn(
                    WeighInWriteCommand.New(
                        operationId = "$OPERATION_PREFIX:${profile.id}",
                        profileId = profile.id,
                        dayEpochDay = DayBoundary.epochDay(now, TimeZone.currentSystemDefault()),
                        weightKg = weight,
                        source = SOURCE,
                        capturedAt = now,
                    ),
                ).getOrNull() ?: return WloResult.err(AppError.Storage(null, "weight.append"))
        }
        store.finish(draft)
        return WloResult.ok(Unit)
    }

    public companion object {
        public const val SOURCE: String = "weight-first-onboarding"
        private const val OPERATION_PREFIX: String = "weight-first"
    }
}
