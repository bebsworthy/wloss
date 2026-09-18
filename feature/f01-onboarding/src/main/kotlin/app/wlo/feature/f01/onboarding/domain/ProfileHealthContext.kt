package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.documents.DocumentCodec
import app.wlo.core.model.SafetyAnswer
import app.wlo.core.model.WeightGoalSafetyInput

/** Profile-owned answers. Legacy records remain untouched until the user saves their profile context. */
public class ProfileHealthContextStore(
    private val documents: JsonDocumentStore,
) {
    public suspend fun read(profileId: String): ProfileHealthContext {
        val canonical = documents.readText(key(profileId))
        if (canonical != null) {
            return IntakeScreeningIO.decode(canonical)?.let { ProfileHealthContext(it) }
                ?: ProfileHealthContext(unreadable = true)
        }
        val intakeText = documents.readText("intake/screening/$profileId")
        val intake = intakeText?.let(IntakeScreeningIO::decode)
        val goalText = documents.readText("weight/goal-safety-v1/$profileId")
        val goal =
            goalText
                ?.let { text ->
                    runCatching {
                        DocumentCodec.json.decodeFromString(WeightGoalSafetyInput.serializer(), text)
                    }.getOrNull()
                }?.let { listOf(it.pregnant, it.breastfeeding, it.eatingDisorderConcern, it.medicallyInfluencedWeight) }
        return merge(intake, goal).copy(
            unreadable = (intakeText != null && intake == null) || (goalText != null && goal == null),
        )
    }

    public suspend fun write(
        profileId: String,
        answers: List<SafetyAnswer>,
    ) {
        require(answers.size == 4)
        documents.writeText(key(profileId), IntakeScreeningIO.encode(answers))
    }

    public companion object {
        public fun key(profileId: String): String = "profile/health-context-v1/$profileId"

        /** Legacy records have no comparable answer timestamps: conflicting answers need explicit review. */
        public fun merge(
            first: List<SafetyAnswer>?,
            second: List<SafetyAnswer>?,
        ): ProfileHealthContext {
            val conflicts = mutableSetOf<Int>()
            val answers =
                (0..3).map { index ->
                    val known =
                        listOfNotNull(first?.getOrNull(index), second?.getOrNull(index))
                            .filter { it != SafetyAnswer.NOT_ANSWERED }
                            .distinct()
                    if (known.size > 1) conflicts.add(index)
                    known.singleOrNull() ?: SafetyAnswer.NOT_ANSWERED
                }
            return ProfileHealthContext(answers, conflicts)
        }
    }
}

public data class ProfileHealthContext(
    val answers: List<SafetyAnswer> = List(4) { SafetyAnswer.NOT_ANSWERED },
    val conflicts: Set<Int> = emptySet(),
    val unreadable: Boolean = false,
)
