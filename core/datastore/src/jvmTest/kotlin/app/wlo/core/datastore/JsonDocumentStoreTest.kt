package app.wlo.core.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tmp-dir rule (ADR-003, see SettingsStoreTest): one fresh file per test,
 * real dispatchers, `runBlocking`.
 */
class JsonDocumentStoreTest {
    @Test
    fun textRoundTrips() =
        runBlocking {
            val store = newStore()

            assertNull(store.readText(KEY))
            store.writeText(KEY, "{\"step\":\"forecast\"}")
            assertEquals("{\"step\":\"forecast\"}", store.readText(KEY))
            assertEquals("{\"step\":\"forecast\"}", store.observeText(KEY).first())
        }

    @Test
    fun overwriteReplacesAndRemoveClears() =
        runBlocking {
            val store = newStore()

            store.writeText(KEY, "v1")
            store.writeText(KEY, "v2")
            assertEquals("v2", store.readText(KEY))
            store.remove(KEY)
            assertNull(store.readText(KEY))
        }

    @Test
    fun flagsDefaultFalseAndRoundTrip() =
        runBlocking {
            val store = newStore()

            assertFalse(store.readFlag(KEY))
            store.writeFlag(KEY, true)
            assertTrue(store.readFlag(KEY))
            assertTrue(store.observeFlag(KEY).first())
        }

    private fun newStore(): JsonDocumentStore =
        JsonDocumentStore.create(
            Files
                .createTempDirectory("wlo-docstore-test")
                .resolve("documents.preferences_pb")
                .toString()
                .toPath(),
        )

    private companion object {
        const val KEY = "onboarding/draft"
    }
}
