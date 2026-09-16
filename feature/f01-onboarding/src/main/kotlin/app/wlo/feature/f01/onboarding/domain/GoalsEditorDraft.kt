package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.common.MassUnit
import app.wlo.core.documents.DocumentCodec
import app.wlo.core.documents.DocumentEnvelope
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.WeightGoalMode
import app.wlo.core.model.WeightGoalSafetyInput
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/** Canonical unsaved Goals-editor values, safe across unit changes and recreation. */
@Serializable
public data class GoalsEditorDraft(
    public val profileId: String,
    public val baseVersion: Int? = null,
    public val massUnit: MassUnit,
    public val targetWeightText: String,
    public val paceText: String,
    public val targetDate: String,
    public val budgetText: String,
    public val mode: WeightGoalMode,
    public val pregnant: SafetyAnswer,
    public val breastfeeding: SafetyAnswer,
    public val eatingDisorderConcern: SafetyAnswer,
    public val medicallyInfluencedWeight: SafetyAnswer,
)

/** Recoverable bridge between the immutable Targets write and safety metadata write. */
@Serializable
public data class GoalSaveJournal(
    public val operationId: String,
    public val profileId: String,
    public val baseVersion: Int?,
    public val document: TargetsDocument,
    public val safetyInput: WeightGoalSafetyInput,
    public val committedVersion: Int? = null,
)

public object GoalsEditorDraftIO {
    private const val SCHEMA_VERSION: Int = 2

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

public object GoalSaveJournalIO {
    private const val SCHEMA_VERSION: Int = 1

    public fun encode(journal: GoalSaveJournal): String =
        DocumentCodec.json.encodeToString(
            serializer<DocumentEnvelope<GoalSaveJournal>>(),
            DocumentEnvelope(SCHEMA_VERSION, journal),
        )

    public fun decode(text: String): GoalSaveJournal? =
        runCatching {
            val envelope =
                DocumentCodec.json.decodeFromString(
                    serializer<DocumentEnvelope<JsonObject>>(),
                    text,
                )
            if (envelope.schemaVersion != SCHEMA_VERSION) return null
            DocumentCodec.json.decodeFromJsonElement(GoalSaveJournal.serializer(), envelope.payload)
        }.getOrNull()
}
