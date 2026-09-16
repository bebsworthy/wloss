package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.documents.DocumentCodec
import app.wlo.core.documents.DocumentEnvelope
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.WeightGoalMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/** Canonical unsaved Goals-editor values, safe across unit changes and recreation. */
@Serializable
public data class GoalsEditorDraft(
    public val baseVersion: Int,
    public val targetWeightKg: Double?,
    public val pacePctPerWeek: Double?,
    public val targetDate: String,
    public val budgetKcal: Double?,
    public val mode: WeightGoalMode,
    public val pregnant: SafetyAnswer,
    public val breastfeeding: SafetyAnswer,
    public val eatingDisorderConcern: SafetyAnswer,
    public val medicallyInfluencedWeight: SafetyAnswer,
)

public object GoalsEditorDraftIO {
    private const val SCHEMA_VERSION: Int = 1

    public fun encode(draft: GoalsEditorDraft): String =
        DocumentCodec.json.encodeToString(
            serializer<DocumentEnvelope<GoalsEditorDraft>>(),
            DocumentEnvelope(SCHEMA_VERSION, draft),
        )

    public fun decode(text: String): GoalsEditorDraft? =
        runCatching {
            val envelope =
                DocumentCodec.json.decodeFromString(
                    serializer<DocumentEnvelope<JsonObject>>(),
                    text,
                )
            if (envelope.schemaVersion != SCHEMA_VERSION) return null
            DocumentCodec.json.decodeFromJsonElement(GoalsEditorDraft.serializer(), envelope.payload)
        }.getOrNull()
}
