package app.wlo.core.datastore

import app.wlo.core.common.MassUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tmp-dir rule (ADR-003): no dedicated DataStore testing artifact is published
 * (verified 2026-09-12), so tests use a real temp file — one DataStore per
 * file per process, so each test gets its own fresh path. DataStore runs its
 * own scope on real dispatchers, so these tests use `runBlocking` (real IO +
 * real time) rather than the virtual-time test scheduler.
 */
class SettingsStoreTest {
    @Test
    fun suspendSetThenFlowRead() =
        runBlocking {
            val store = SettingsStoreFactory.create(tempFile())

            assertEquals(MassUnit.KILOGRAM, store.massUnit.first()) // house default, R-D10
            store.setMassUnit(MassUnit.POUND)
            assertEquals(MassUnit.POUND, store.massUnit.first())
        }

    @Test
    fun booleanFlagSetAndReadBack() =
        runBlocking {
            val store = SettingsStoreFactory.create(tempFile())

            assertEquals(false, store.consentIntroSeen.first())
            store.setConsentIntroSeen(true)
            assertEquals(true, store.consentIntroSeen.first())
        }

    @Test
    fun writesLandOnDisk() =
        runBlocking {
            val file = tempFile()
            val store = SettingsStoreFactory.create(file)

            store.setMassUnit(MassUnit.POUND)
            store.consentIntroSeen.first() // drain an edit to ensure the file is flushed
            assertTrue(java.io.File(file.toString()).length() > 0, "preferences file must be written")
        }

    private fun tempFile(): okio.Path =
        Files
            .createTempDirectory("wlo-datastore-test")
            .resolve("settings.preferences_pb")
            .toString()
            .toPath()
}
