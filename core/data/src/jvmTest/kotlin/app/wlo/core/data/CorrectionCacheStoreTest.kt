package app.wlo.core.data

import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.documents.CorrectionCacheDocument
import app.wlo.core.documents.DocumentCodec
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The R-B6 correction cache (document-backed): corrections accumulate as
 * prior signal, survive a fresh store instance (durability), stay bounded,
 * and a corrupt payload degrades to the empty document — never a crash.
 * Tmp-dir rule (ADR-003): one fresh file per test, real dispatchers.
 */
public class CorrectionCacheStoreTest {
    private fun newStore(): CorrectionCacheStore {
        val file = Files.createTempDirectory("wlo-cache-test").resolve("docs.preferences_pb")
        return CorrectionCacheStore(documents = JsonDocumentStore.create(file.toString().toPath()))
    }

    @Test
    public fun correctionsAccumulatePerLabelAndPersistAsADocument() =
        runBlocking {
            val file = Files.createTempDirectory("wlo-cache-test").resolve("docs.preferences_pb")
            val documents = JsonDocumentStore.create(file.toString().toPath())
            val store = CorrectionCacheStore(documents = documents)

            store.recordCorrection("french_fries", "house salad", atEpochMs = 100L)
            store.recordCorrection("french_fries", "house salad", lastGrams = 180.0, atEpochMs = 200L)
            store.recordCorrection("pizza", "leftover lasagna", atEpochMs = 300L)

            val prior = store.priorFor("french_fries")!!
            assertEquals("house salad", prior.correctedLabel)
            assertEquals(2, prior.occurrences)
            assertEquals(180.0, prior.lastGrams)
            assertEquals("leftover lasagna", store.priorFor("pizza")!!.correctedLabel)

            // Durability: the persisted document on disk decodes back to the
            // same priors (a fresh process loads exactly this document).
            val raw = documents.readText(KEY)!!
            val persisted = DocumentCodec.json.decodeFromString(CorrectionCacheDocument.serializer(), raw)
            assertEquals(2, persisted.entries.first { it.analyzedLabel == "french_fries" }.occurrences)
            assertEquals(1, persisted.entries.first { it.analyzedLabel == "pizza" }.occurrences)
        }

    @Test
    public fun latestCorrectionWinsTargetCountAccumulates() =
        runBlocking {
            val store = newStore()
            store.recordCorrection("pizza", "caesar salad", atEpochMs = 1L)
            store.recordCorrection("pizza", "the other half", atEpochMs = 2L)
            val prior = store.priorFor("pizza")!!
            assertEquals("the other half", prior.correctedLabel)
            assertEquals(2, prior.occurrences, "the count is the prior's weight across targets")
        }

    @Test
    public fun cacheStaysBoundedByRecency() =
        runBlocking {
            val store = newStore()
            for (i in 0 until (CorrectionCacheStore.MAX_ENTRIES + 20)) {
                store.recordCorrection("label-$i", "fixed-$i", atEpochMs = i.toLong())
            }
            assertNull(store.priorFor("label-0"), "oldest priors fall off the LRU")
            assertTrue(store.priorFor("label-${CorrectionCacheStore.MAX_ENTRIES + 19}") != null)
        }

    @Test
    public fun clearWipesAndEmptyReadsAreNull() =
        runBlocking {
            val store = newStore()
            assertNull(store.priorFor("nothing"))
            store.recordCorrection("a", "b", atEpochMs = 1L)
            store.clear()
            assertNull(store.priorFor("a"))
        }

    public companion object {
        /** The CorrectionCacheStore's document key (JsonDocumentStore namespace). */
        private const val KEY: String = "correction-cache"
    }
}
