package app.wlo.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.wlo.core.consent.ConsentChain
import app.wlo.core.database.WloDatabase
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.vault.RestoreCommitter
import app.wlo.core.vault.StagedRestorer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M6 PART A acceptance, LEG 2 — THE MONEY TEST (acceptance 1): after the host
 * script has UNINSTALLED and REINSTALLED the app, this test holds nothing but
 * the backup bytes the script passes back in (`backupBase64` instrumentation
 * arg, or `/data/local/tmp/wlo-e2e/backup.wlo`). It stages the restore (the
 * report!), commits it through the single Room transaction, and asserts the
 * logical state EQUALS the seeded spec: counts per section, spot values, the
 * consent ledger chain (still verifiable), and the recomputed day projection.
 *
 * A fresh install has an empty ledger, so the backup chain must land
 * genesis-rooted and verify with [ConsentChain.verify] — no tampering in
 * flight.
 */
@RunWith(AndroidJUnit4::class)
public class M6RestoreAssertTest {
    @Test
    public fun restoreFromSurvivingBackup_assertLogicalEquality() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val bytes: ByteArray =
            args.getString("backupBase64")?.let {
                java.util.Base64
                    .getDecoder()
                    .decode(it)
            } ?: File("/data/local/tmp/wlo-e2e/backup.wlo").takeIf(File::exists)?.readBytes()
                // PART B: outside the host E2E script the leg-2 test has nothing
                // to restore — skip honestly instead of failing the plain run
                // (scripts/e2e-wipe-restore.sh drives this test with the backup).
                ?: return

        val koin = GlobalContext.get()
        val db = koin.get<WloDatabase>()
        val settings = koin.get<SettingsStore>()
        val restorer = koin.get<StagedRestorer>()
        val committer = koin.get<RestoreCommitter>()

        // The restore trip, exactly as PART B's UI will drive it:
        // stage (validate + report) → read it → commit.
        val staged = runBlocking { restorer.stage(bytes, M6E2eSpec.PASSPHRASE.toCharArray()) }
        assertTrue(staged.totalRows > 0)
        assertTrue(staged.schemaVersionRead <= 1)
        val sectionNames = staged.sections.map { it.name }.toSet()
        assertTrue("profiles" in sectionNames && "consent_ledger" in sectionNames)

        val result = runBlocking { committer.commit(staged) }
        println(
            "M6E2E restore: inserted=${result.inserted} skipped=${result.skipped} " +
                "warnings=${result.warnings}",
        )

        // Logical equality with the seeded spec.
        M6E2eSpec.assertCounts(db)

        // Spot values.
        val profile = runBlocking { db.profiles().all().single() }
        assertEquals(M6E2eSpec.BIRTH_YEAR, profile.birthYear)
        assertEquals(M6E2eSpec.HEIGHT_CM, checkNotNull(profile.heightCm), 0.0001)
        assertEquals(M6E2eSpec.START_WEIGHT_KG, checkNotNull(profile.startWeightKg), 0.0001)

        val weighIns = runBlocking { db.measurementEvents().range(profile.id, 0, Long.MAX_VALUE) }
        assertTrue(weighIns.any { it.valueReal == M6E2eSpec.SPOT_WEIGH_IN_KG }, "the 76.9 weigh-in survives")
        assertEquals(M6E2eSpec.EXPECT_MEASUREMENTS, weighIns.size, "R-B8: all raw points ride along")

        val food = runBlocking { db.foodItems().all().single() }
        assertEquals(M6E2eSpec.FOOD_NAME, food.name)
        assertEquals(M6E2eSpec.FOOD_KCAL, food.kcalPer100g!!, 0.0001)
        // FTS mirror rebuilt: the restored catalog is searchable.
        assertTrue(runBlocking { db.foodSearch().countMatches("e2e*") } > 0)

        val recipe = runBlocking { db.recipes().all().single() }
        assertEquals(M6E2eSpec.RECIPE_NAME, recipe.name)
        val listItem = runBlocking { db.listItems().all().single() }
        assertEquals(M6E2eSpec.LIST_ITEM_NAME, listItem.name)
        val pantry = runBlocking { db.pantryItems().all().single() }
        assertEquals(M6E2eSpec.PANTRY_ITEM_NAME, pantry.name)

        // The consent ledger: restored rows + chain STILL VERIFIES.
        val ledger = runBlocking { db.consentLedger().all() }
        assertEquals(M6E2eSpec.EXPECT_CONSENT_ENTRIES, ledger.size)
        val entries =
            ledger.map {
                app.wlo.core.consent.ConsentEntry(
                    seq = it.seq,
                    atEpochMs = it.atEpochMs,
                    capability =
                        app.wlo.core.model.ConsentCapability.entries
                            .first { c -> c.wireName == it.capability },
                    decision = app.wlo.core.consent.ConsentDecision.GRANT,
                    prevHashHex = it.prevHashHex,
                    hashHex = it.hashHex,
                )
            }
        // Room's ledger is 1-BASED (autoGenerate treats 0 as "generate"), so
        // the audit anchors the chain at the first stored seq — the PART B
        // wiring must do the same (HashChain.verify takes seqBase for this).
        val seqBase = entries.first().seq
        assertTrue(
            app.wlo.core.consent.HashChain.verify(
                entries = entries,
                seqBase = seqBase,
                seqOf = app.wlo.core.consent.ConsentEntry::seq,
                prevHashOf = app.wlo.core.consent.ConsentEntry::prevHashHex,
                canonicalOf = { e ->
                    "${e.seq}|${e.atEpochMs}|${e.capability.wireName}|${e.decision.name}|${e.prevHashHex}"
                },
                hashOf = app.wlo.core.consent.ConsentEntry::hashHex,
            ),
            "the hash chain survives device migration",
        )

        // Derived views recomputed after commit (day_records never traveled).
        val dayRecord = runBlocking { db.dayRecords().day(profile.id, weighIns.first().dayEpochDay) }
        assertNotNull(dayRecord)

        // Settings section rode the backup.
        val unit = runBlocking { settings.exportKnownSettings()["unit_system"] }
        assertNotNull(unit)

        println("M6E2E restore: PASS — logical state equals the seeded spec after wipe+reinstall")
    }
}
