package app.wlo.core.vault

import androidx.room3.withWriteTransaction
import app.wlo.core.common.ClockPort
import app.wlo.core.data.DayProjector
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.jvmDatabaseBuilder
import app.wlo.core.datastore.SettingsStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.jsonObject
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The staged restore contract on the JVM Room driver (F13 §3 "a corrupt file
 * can never merge garbage into live data", §4 flow 3 "nothing was changed"):
 * hostile inputs are rejected with existing data untouched; a valid restore
 * reconciles (append, keep-local, never delete — R-B7) and commits
 * atomically (a poisoned commit rolls back whole).
 */
class StagedRestoreJvmTest {
    private val dir = Files.createTempDirectory("wlo-vault-test")
    private lateinit var db: WloDatabase
    private lateinit var settings: app.wlo.core.datastore.SettingsStore
    private lateinit var documents: app.wlo.core.datastore.JsonDocumentStore
    private lateinit var restorer: StagedRestorer
    private lateinit var committer: RestoreCommitter
    private val passphrase = "e2e-passphrase".toCharArray()
    private val clock =
        object : ClockPort {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_760_000_000_000)
        }

    @Before
    fun setUp() {
        db =
            jvmDatabaseBuilder(dir.resolve("wlo.db").toString())
                .addCallback(
                    object : androidx.room3.RoomDatabase.Callback() {
                        override suspend fun onOpen(connection: androidx.sqlite.SQLiteConnection) {
                            connection.prepare("PRAGMA foreign_keys = ON").use { it.step() }
                        }
                    },
                ).build()
        settings = SettingsStoreFactory.create(dir.resolve("settings.preferences_pb").toString().toPath())
        documents =
            app.wlo.core.datastore.JsonDocumentStore
                .create(dir.resolve("docs.preferences_pb").toString().toPath())
        restorer = StagedRestorer(db)
        committer =
            RestoreCommitter(
                db = db,
                settings = settings,
                documents = documents,
                projector = DayProjector(db, clock),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- fixture helpers ----------------------------------------------------

    private fun payload(): BackupPayload =
        BackupPayload(
            profiles =
                listOf(
                    ProfileRow(
                        id = "prof-1",
                        sex = "male",
                        birthYear = 1990,
                        heightCm = 180.0,
                        startWeightKg = 90.0,
                        activityLevel = "sedentary",
                        unitPreference = "metric",
                        createdAtEpochMs = 1_000,
                    ),
                ),
            measurements =
                listOf(
                    MeasurementRow(
                        id = "meas-1",
                        profileId = "prof-1",
                        dayEpochDay = 20_000,
                        kind = "weight",
                        valueReal = 89.4,
                        unit = "kg",
                        source = "manual",
                        capturedAtEpochMs = 5_000,
                    ),
                    // R-B8: two weigh-ins the same day ride verbatim.
                    MeasurementRow(
                        id = "meas-2",
                        profileId = "prof-1",
                        dayEpochDay = 20_000,
                        kind = "weight",
                        valueReal = 89.1,
                        unit = "kg",
                        source = "scale",
                        capturedAtEpochMs = 9_000,
                    ),
                ),
            diary =
                listOf(
                    DiaryEntryRow(
                        id = "entry-1",
                        profileId = "prof-1",
                        dayEpochDay = 20_000,
                        mealSlot = "lunch",
                        quantity = 200.0,
                        unit = "g",
                        computedKcal = 320.0,
                        enteredVia = "manual-search",
                        provenanceScalar = "diary/kcal/entry-1",
                        revision = 0,
                        createdAtEpochMs = 6_000,
                    ),
                ),
            foodItems =
                listOf(
                    FoodItemRow(
                        id = "food-1",
                        profileId = "prof-1",
                        name = "Lentils",
                        kcalPer100g = 116.0,
                        proteinGPer100g = 9.0,
                        carbGPer100g = 20.0,
                        fatGPer100g = 0.4,
                        createdAtEpochMs = 2_000,
                    ),
                ),
            consentLedger = listOf(ledgerRow(atEpochMs = 3_000, capability = "food-photo")),
            settings = SettingsSection(values = mapOf("unit_system" to "KILOGRAM", "lock_timeout" to "5min")),
            documents = DocumentsSection(values = mapOf("onboarding/complete" to "true")),
        )

    /** A consent row whose hash is the REAL canonical hash (chain-verifiable). */
    private fun ledgerRow(
        atEpochMs: Long,
        capability: String,
    ): ConsentLedgerRow {
        val cap =
            app.wlo.core.model.ConsentCapability.entries
                .first { it.wireName == capability }
        val prev = app.wlo.core.consent.ConsentEntry.GENESIS_PREV_HASH
        val hash =
            app.wlo.core.consent.ConsentChain.hashOf(
                app.wlo.core.consent.ConsentEntry(
                    seq = 0,
                    atEpochMs = atEpochMs,
                    capability = cap,
                    decision = app.wlo.core.consent.ConsentDecision.GRANT,
                    prevHashHex = prev,
                    hashHex = "",
                ),
            )
        return ConsentLedgerRow(
            seq = 0,
            profileId = "prof-1",
            atEpochMs = atEpochMs,
            capability = capability,
            decision = "grant",
            prevHashHex = prev,
            hashHex = hash,
        )
    }

    private fun ledgerChain(vararg capabilities: String): List<ConsentLedgerRow> {
        var previous = app.wlo.core.consent.ConsentEntry.GENESIS_PREV_HASH
        return capabilities.mapIndexed { index, capability ->
            val cap =
                app.wlo.core.model.ConsentCapability.entries
                    .first { it.wireName == capability }
            val entry =
                app.wlo.core.consent.ConsentEntry(
                    seq = index.toLong(),
                    atEpochMs = 3_000L + index * 1_000L,
                    capability = cap,
                    decision = app.wlo.core.consent.ConsentDecision.GRANT,
                    prevHashHex = previous,
                    hashHex = "",
                )
            val hash =
                app.wlo.core.consent.ConsentChain
                    .hashOf(entry)
            ConsentLedgerRow(
                seq = entry.seq,
                profileId = "prof-1",
                atEpochMs = entry.atEpochMs,
                capability = capability,
                decision = "grant",
                prevHashHex = previous,
                hashHex = hash,
            ).also { previous = hash }
        }
    }

    private fun containerFor(
        payload: BackupPayload,
        passphrase: CharArray = this.passphrase,
    ): ByteArray =
        BackupContainer.encrypt(
            BackupCodec.encode(payload, createdAtEpochMs = 77),
            BackupKdf.argon2idDefaults("0123456789abcdef".toByteArray()),
            passphrase,
        )

    @Test
    fun agendaItemsAndUnknownDiaryNutritionSurviveEncryptedRestore() =
        runTest {
            val source = payload()
            val snapshot = "{\"name\":\"Office lunch\",\"unit\":\"portion\"}"
            val agenda =
                PlanSlotRow(
                    id = "independent",
                    planId = "",
                    profileId = "prof-1",
                    dayEpochDay = 20_000,
                    mealSlot = "lunch",
                    recipeName = "Office lunch",
                    servings = 1.0,
                    createdAtEpochMs = 1234,
                    itemJson = snapshot,
                )
            val document =
                source.copy(
                    diary = source.diary.map { it.copy(computedKcal = null, itemJson = snapshot) },
                    planSlots = listOf(agenda),
                )
            val staged = restorer.stage(containerFor(document), passphrase)
            committer.commit(staged)
            assertEquals(snapshot, db.planSlots().byId("independent")?.itemJson)
            val actual = db.diaryEntries().all().single()
            assertEquals(null, actual.computedKcal)
            assertEquals(snapshot, actual.itemJson)
            assertEquals(null, db.dayRecords().day("prof-1", 20_000)?.intakeKcal)
        }

    // --- happy path ---------------------------------------------------------

    @Test
    fun stageAndCommit_restoresIntoEmptyStore() =
        runTest {
            val staged = restorer.stage(containerFor(payload()), passphrase)
            assertEquals(BackupSchema.SCHEMA_VERSION, staged.schemaVersionRead)
            assertEquals(BackupSchema.SECTION_ORDER.size, staged.sections.size)
            assertTrue(staged.warnings.isEmpty())

            val result = committer.commit(staged)
            assertEquals(5, result.inserted) // profile + 2 measurements + diary + food
            assertEquals(0, result.skipped)

            assertNotNull(db.profiles().byId("prof-1"))
            assertEquals(2, db.measurementEvents().range("prof-1", 0, 99_999).size)
            assertEquals(1, db.diaryEntries().range("prof-1", 0, 99_999).size)
            assertEquals(1, db.foodItems().countAll())
            // FTS mirror rebuilt: the restored catalog is searchable.
            assertEquals(1, db.foodSearch().countMatches("lent*"))
            // Consent ledger restored with its chain intact (empty local → genesis ok).
            assertEquals(1, db.consentLedger().all().size)
            // Settings + documents applied.
            assertEquals("5min", settings.lockTimeout.first())
            assertEquals(true, documents.readFlag("onboarding/complete"))
            // Derived views recomputed (day_records was NOT in the backup).
            val day = db.dayRecords().day("prof-1", 20_000)
            assertNotNull(day)
            assertEquals(320.0, day!!.intakeKcal)
        }

    @Test
    fun restore_isReconcile_keepsLocalNeverDeletes() =
        runTest {
            committer.commit(restorer.stage(containerFor(payload()), passphrase))
            // Second restore of the SAME payload: every PK exists → all skipped,
            // nothing duplicated, nothing removed.
            val second = committer.commit(restorer.stage(containerFor(payload()), passphrase))
            assertEquals(0, second.inserted)
            assertEquals(5, second.skipped)
            assertEquals(2, db.measurementEvents().range("prof-1", 0, 99_999).size)
        }

    @Test
    fun consentLedger_forkIsHeld_notAppended() =
        runTest {
            committer.commit(restorer.stage(containerFor(payload()), passphrase))
            val head = db.consentLedger().last()
            assertNotNull(head)
            // A DIFFERENT genesis-rooted chain cannot continue the local history.
            val forked =
                payload().copy(
                    consentLedger =
                        listOf(ledgerRow(atEpochMs = 4_000, capability = "insights-chat")),
                )
            val result = committer.commit(restorer.stage(containerFor(forked), passphrase))
            assertTrue(result.warnings.single().contains("fork"))
            assertEquals(1, db.consentLedger().all().size, "local ledger untouched")
        }

    @Test
    fun consentLedger_matchingLocalPrefix_appendsOnlyStrictSuffix() =
        runTest {
            val chain = ledgerChain("food-photo", "insights-chat")
            committer.commit(restorer.stage(containerFor(payload().copy(consentLedger = chain.take(1))), passphrase))

            val staged = restorer.stage(containerFor(payload().copy(consentLedger = chain)), passphrase)
            val result = committer.commit(staged)

            assertTrue(result.warnings.isEmpty())
            assertEquals(chain.map { it.hashHex }, db.consentLedger().all().map { it.hashHex })
        }

    @Test
    fun crashAfterRoomCommit_newCommitterRollsJournalForwardIdempotently() =
        runTest {
            var failAfterRoom = true
            val crashing =
                RestoreCommitter(
                    db = db,
                    settings = settings,
                    documents = documents,
                    projector = DayProjector(db, clock),
                    phaseHook = { phase ->
                        if (phase == "room" && failAfterRoom) {
                            failAfterRoom = false
                            error("simulated process death")
                        }
                    },
                )
            assertFailsWith<IllegalStateException> {
                crashing.commit(restorer.stage(containerFor(payload()), passphrase))
            }
            assertNotNull(db.profiles().byId("prof-1"), "Room transaction committed before the simulated death")
            assertEquals("1min", settings.lockTimeout.first(), "later stores have not run yet")

            val recovered = committer.recoverPending()

            assertNotNull(recovered)
            assertEquals(5, recovered.inserted)
            assertEquals("5min", settings.lockTimeout.first())
            assertEquals(true, documents.readFlag("onboarding/complete"))
            assertNotNull(db.dayRecords().day("prof-1", 20_000))
            assertEquals(null, committer.recoverPending(), "completed journal is removed")
            assertEquals(2, db.measurementEvents().all().size, "Room replay inserted no duplicates")
        }

    // --- hostile inputs: rejected, data untouched ---------------------------

    @Test
    fun hostile_wrongPassphrase_rejectedAndUntouched() =
        runTest {
            committer.commit(restorer.stage(containerFor(payload()), passphrase))
            val before = snapshotCounts()
            val failure =
                assertFailsWith<BackupContainerException> {
                    restorer.stage(containerFor(payload()), "wrong".toCharArray())
                }
            assertEquals(BackupContainerException.Reason.WRONG_PASSPHRASE, failure.reason)
            assertEquals(before, snapshotCounts())
        }

    @Test
    fun hostile_truncated_rejectedAndUntouched() =
        runTest {
            committer.commit(restorer.stage(containerFor(payload()), passphrase))
            val before = snapshotCounts()
            val container = containerFor(payload())
            assertFailsWith<BackupContainerException> {
                restorer.stage(container.copyOfRange(0, container.size / 3), passphrase)
            }
            assertEquals(before, snapshotCounts())
        }

    @Test
    fun hostile_wrongMagic_rejectedAndUntouched() =
        runTest {
            committer.commit(restorer.stage(containerFor(payload()), passphrase))
            val before = snapshotCounts()
            val junk = "not a backup at all, just text".toByteArray()
            assertFailsWith<BackupContainerException> { restorer.stage(junk, passphrase) }
            assertEquals(before, snapshotCounts())
        }

    @Test
    fun hostile_manifestTamper_rejectedAndUntouched() =
        runTest {
            committer.commit(restorer.stage(containerFor(payload()), passphrase))
            val before = snapshotCounts()
            val container = containerFor(payload())
            val decrypted = BackupContainer.decrypt(container, passphrase)
            val tampered =
                decrypted.plaintextJson
                    .toString(Charsets.UTF_8)
                    .replace("89.4", "1.0") // flip a value without fixing the manifest
                    .toByteArray()
            val resealed =
                BackupContainer.encrypt(
                    tampered,
                    BackupKdf.argon2idDefaults("0123456789abcdef".toByteArray()),
                    passphrase,
                )
            val failure =
                assertFailsWith<BackupDocumentException> { restorer.stage(resealed, passphrase) }
            assertEquals(BackupDocumentException.Reason.MANIFEST_MISMATCH, failure.reason)
            assertEquals(before, snapshotCounts())
        }

    @Test
    fun hostile_schemaRowGarbage_failsStageBeforeAnyWrite() =
        runTest {
            committer.commit(restorer.stage(containerFor(payload()), passphrase))
            val before = snapshotCounts()
            val container = containerFor(payload())
            val decrypted = BackupContainer.decrypt(container, passphrase)
            // Manifest-consistent tampering: rebuild the manifest over a bad row.
            val badSections =
                decrypted.plaintextJson
                    .toString(Charsets.UTF_8)
                    .replace("\"kind\":\"weight\"", "\"kind\":true") // wrong-typed field
            val root =
                app.wlo.core.documents.DocumentCodec.json
                    .parseToJsonElement(badSections)
            val sections = root.jsonObject["sections"]!!.jsonObject
            val rebuilt =
                app.wlo.core.documents.DocumentCodec.json
                    .encodeToString(
                        kotlinx.serialization.json.JsonElement
                            .serializer(),
                        kotlinx.serialization.json.buildJsonObject {
                            put("format", kotlinx.serialization.json.JsonPrimitive("wlo-backup"))
                            put("createdAtEpochMs", kotlinx.serialization.json.JsonPrimitive(77))
                            put("schemaVersion", kotlinx.serialization.json.JsonPrimitive(1))
                            put("manifest", BackupCodec.manifestFor(sections.jsonObject, 1))
                            put("sections", sections.jsonObject)
                        },
                    ).toByteArray()
            val resealed =
                BackupContainer.encrypt(
                    rebuilt,
                    BackupKdf.argon2idDefaults("0123456789abcdef".toByteArray()),
                    passphrase,
                )
            val failure =
                assertFailsWith<BackupDocumentException> { restorer.stage(resealed, passphrase) }
            assertEquals(BackupDocumentException.Reason.SCHEMA_INVALID, failure.reason)
            assertEquals(before, snapshotCounts())
        }

    @Test
    fun hostile_brokenReferences_rejectedAtStage_nothingWritten() =
        runTest {
            committer.commit(restorer.stage(containerFor(payload()), passphrase))
            val before = snapshotCounts()
            // A measurement referencing a profile missing from BOTH the backup and
            // the local store is rejected at STAGING — the hostile file never
            // reaches a commit (the report is the user's "nothing was changed").
            val poisoned =
                payload().copy(
                    measurements =
                        listOf(
                            MeasurementRow(
                                id = "orphan",
                                profileId = "missing-profile",
                                dayEpochDay = 20_001,
                                kind = "weight",
                                valueReal = 1.0,
                                unit = "kg",
                                source = "manual",
                                capturedAtEpochMs = 1,
                            ),
                        ),
                    diary = emptyList(),
                    foodItems = emptyList(),
                    consentLedger = emptyList(),
                )
            val failure =
                assertFailsWith<BackupDocumentException> {
                    restorer.stage(containerFor(poisoned), passphrase)
                }
            assertEquals(BackupDocumentException.Reason.REFERENCE_BROKEN, failure.reason)
            assertEquals(before, snapshotCounts())
        }

    @Test
    fun commit_transactionIsAtomicForcedFailureRollsBackWhole() =
        runTest {
            // The mechanism RestoreCommitter leans on, proven directly: a write
            // batch inside db.withWriteTransaction that throws midway leaves NO
            // trace — the Room transaction is the commit-or-nothing unit.
            val forced =
                assertFailsWith<IllegalStateException> {
                    db.withWriteTransaction {
                        db.profiles().upsert(
                            app.wlo.core.database.ProfileEntity(
                                id = "tx-p",
                                sex = null,
                                birthYear = 1990,
                                heightCm = 180.0,
                                startWeightKg = 90.0,
                                activityLevel = "sedentary",
                                unitPreference = "metric",
                                createdAtEpochMs = 1,
                            ),
                        )
                        db.diaryEntries().insert(
                            app.wlo.core.database.DiaryEntryEntity(
                                id = "tx-d",
                                profileId = "tx-p",
                                dayEpochDay = 20_000,
                                mealSlot = "lunch",
                                quantity = 1.0,
                                unit = "g",
                                computedKcal = 1.0,
                                enteredVia = "manual-search",
                                provenanceScalar = "diary/kcal/tx-d",
                                revision = 0,
                                createdAtEpochMs = 1,
                            ),
                        )
                        throw IllegalStateException("boom mid-transaction")
                    }
                }
            assertEquals("boom mid-transaction", forced.message)
            assertEquals(0, db.profiles().all().size, "rolled back: no profile")
            assertEquals(0, db.diaryEntries().all().size, "rolled back: no diary row")
        }

    // --- helpers ------------------------------------------------------------

    private data class Counts(
        val profiles: Int,
        val measurements: Int,
        val diary: Int,
        val foods: Int,
        val ledger: Int,
    )

    private suspend fun snapshotCounts(): Counts =
        Counts(
            profiles = db.profiles().all().size,
            measurements = db.measurementEvents().all().size,
            diary = db.diaryEntries().all().size,
            foods = db.foodItems().countAll(),
            ledger = db.consentLedger().all().size,
        )
}
