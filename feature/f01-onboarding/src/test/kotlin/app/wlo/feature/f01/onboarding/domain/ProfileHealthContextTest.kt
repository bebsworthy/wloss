package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.model.SafetyAnswer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileHealthContextTest {
    private val unknown = List(4) { SafetyAnswer.NOT_ANSWERED }

    @Test fun profileSaveEmitsARefreshSignalWithoutReopeningTheEditor() =
        runBlocking {
            val path = Files.createTempDirectory("health-observe-test").resolve("documents.preferences_pb")
            val documents = JsonDocumentStore.create(path.toString().toPath())
            val firstValue = CompletableDeferred<Unit>()
            val observed =
                async(start = CoroutineStart.UNDISPATCHED) {
                    documents
                        .observeText(ProfileHealthContextStore.key("a"))
                        .onEach { firstValue.complete(Unit) }
                        .take(2)
                        .toList()
                }
            withTimeout(5000) { firstValue.await() }
            val answers = List(4) { SafetyAnswer.NO }
            ProfileHealthContextStore(documents).write("a", answers)
            val values = withTimeout(5000) { observed.await() }
            assertEquals(null, values.first())
            assertEquals(answers, IntakeScreeningIO.decode(values.last()!!))
        }

    @Test fun canonicalProfileContextOverridesLegacyAndIsIsolatedPerProfile() =
        runBlocking {
            val path = Files.createTempDirectory("health-context-test").resolve("documents.preferences_pb")
            val documents = JsonDocumentStore.create(path.toString().toPath())
            val store = ProfileHealthContextStore(documents)
            val legacy = List(4) { SafetyAnswer.NO }
            documents.writeText("intake/screening/a", IntakeScreeningIO.encode(legacy))
            assertEquals(legacy, store.read("a").answers)
            store.write("a", unknown)
            assertEquals(unknown, store.read("a").answers)
            assertEquals(unknown, store.read("b").answers)
            assertEquals(legacy, IntakeScreeningIO.decode(documents.readText("intake/screening/a")!!))
            documents.writeText("profile/health-context-v1/a", "unreadable")
            assertTrue(store.read("a").unreadable)
        }

    @Test fun missingAnswersStayUnknown() {
        assertEquals(unknown, ProfileHealthContextStore.merge(null, null).answers)
    }

    @Test fun existingAnswersArePreservedWithoutInferringNegativeAnswers() {
        val existing = listOf(SafetyAnswer.NO, SafetyAnswer.YES, SafetyAnswer.NOT_ANSWERED, SafetyAnswer.NO)
        assertEquals(existing, ProfileHealthContextStore.merge(unknown, existing).answers)
        assertEquals(existing, ProfileHealthContextStore.merge(existing, unknown).answers)
        assertTrue(ProfileHealthContextStore.merge(existing, existing).conflicts.isEmpty())
    }

    @Test fun conflictingLegacyAnswersRequireReview() {
        val no = List(4) { SafetyAnswer.NO }
        val result = ProfileHealthContextStore.merge(no, no.toMutableList().also { it[2] = SafetyAnswer.YES })
        assertEquals(setOf(2), result.conflicts)
        assertEquals(SafetyAnswer.NOT_ANSWERED, result.answers[2])
        assertEquals(SafetyAnswer.NO, result.answers[0])
    }
}
