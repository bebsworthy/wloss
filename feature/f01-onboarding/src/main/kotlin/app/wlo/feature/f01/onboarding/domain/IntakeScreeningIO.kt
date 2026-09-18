package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.documents.DocumentCodec
import app.wlo.core.documents.DocumentEnvelope
import app.wlo.core.model.SafetyAnswer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Missing, corrupt or future screening never implies a negative answer. */
public object IntakeScreeningIO {
    public fun encode(answers: List<SafetyAnswer>): String {
        val envelope = DocumentEnvelope(1, answers)
        return DocumentCodec.json.encodeToString(envelope)
    }

    public fun decode(text: String): List<SafetyAnswer>? =
        runCatching {
            val document = DocumentCodec.json.decodeFromString<DocumentEnvelope<List<SafetyAnswer>>>(text)
            document.payload.takeIf { document.schemaVersion == 1 && it.size == 4 }
        }.getOrNull()
}
