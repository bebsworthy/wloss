package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.documents.DocumentCodec
import app.wlo.core.documents.DocumentEnvelope
import app.wlo.core.documents.PreferenceProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * The persisted onboarding draft (F01 §1: killing the wizard mid-flow resumes,
 * never corrupts, never restarts the user's typing). Encoded through the
 * versioned-document house rules (ADR-004): schema envelope, defaults on new
 * fields, unknown keys ignored — an old or torn draft decodes to a refusal
 * ([OnboardingDraftIO.decode] returns null), which the caller treats as a
 * clean restart at step one.
 */
@Serializable
public data class OnboardingDraft(
    /** The step the user was on; restore lands exactly there. */
    public val step: String = "WELCOME",
    public val sex: String? = null,
    public val birthYear: Int? = null,
    public val heightCm: Double? = null,
    public val currentWeightKg: Double? = null,
    public val goalWeightKg: Double? = null,
    public val pacePctPerWeek: Double? = null,
    public val activityLevel: String? = null,
    public val templateId: String? = null,
    public val constraints: List<String> = emptyList(),
    public val preferences: PreferenceProfile = PreferenceProfile(),
    /** Custom weekday schedule (7 kcal values); null = flat, not touched. */
    public val schedule: List<Double>? = null,
    public val startedAtEpochMs: Long? = null,
)

/** Storage funnel for the draft (same envelope rules as Targets/DietPlan). */
public object OnboardingDraftIO {
    public const val SCHEMA_VERSION: Int = 1

    public fun encode(draft: OnboardingDraft): String =
        DocumentCodec.json.encodeToString(
            serializer = serializer<DocumentEnvelope<OnboardingDraft>>(),
            value = DocumentEnvelope(SCHEMA_VERSION, draft),
        )

    /** Null on any decode failure — a corrupt draft restarts cleanly, never crashes. */
    public fun decode(text: String): OnboardingDraft? =
        try {
            val envelope =
                DocumentCodec.json.decodeFromString(
                    deserializer = serializer<DocumentEnvelope<JsonObject>>(),
                    string = text,
                )
            when (envelope.schemaVersion) {
                SCHEMA_VERSION ->
                    DocumentCodec.json.decodeFromJsonElement(OnboardingDraft.serializer(), envelope.payload)
                else -> null
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: kotlinx.serialization.SerializationException) {
            null
        }

    /** Internal helper kept for tests of the envelope contract (JSON element path). */
    internal fun decodeElement(element: JsonElement): OnboardingDraft? =
        try {
            DocumentCodec.json.decodeFromJsonElement(OnboardingDraft.serializer(), element)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: kotlinx.serialization.SerializationException) {
            null
        }
}
