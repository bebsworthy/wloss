package app.wlo.core.vault

import androidx.room3.withWriteTransaction
import app.wlo.core.consent.ConsentChain
import app.wlo.core.consent.ConsentEntry
import app.wlo.core.data.DayProjector
import app.wlo.core.database.AisleCorrectionEntity
import app.wlo.core.database.ConsentLedgerEntity
import app.wlo.core.database.DiaryEntryEntity
import app.wlo.core.database.DiaryEntryRevisionEntity
import app.wlo.core.database.FoodItemEntity
import app.wlo.core.database.FoodSearchEntity
import app.wlo.core.database.GroceryItemEntity
import app.wlo.core.database.HealthConnectImportLogEntity
import app.wlo.core.database.HealthConnectRecordEntity
import app.wlo.core.database.HealthConnectSyncStateEntity
import app.wlo.core.database.ListItemEntity
import app.wlo.core.database.MeasurementEventAttrEntity
import app.wlo.core.database.MeasurementEventEntity
import app.wlo.core.database.PantryItemEntity
import app.wlo.core.database.PlanSlotEntity
import app.wlo.core.database.PlanVersionEntity
import app.wlo.core.database.ProfileEntity
import app.wlo.core.database.ProvenanceEntity
import app.wlo.core.database.RecipeEntity
import app.wlo.core.database.TargetsVersionEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.datastore.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Staged-and-validated restore (F13 §4 flows 1/3): parse → verify →
 * [StagedRestorer.stage] a REPORT → explicit user confirmation →
 * [RestoreCommitter.commit]. Hostile or corrupt input is rejected before any
 * write. Once Apply begins, Room changes are atomic and a persisted journal
 * makes the remaining DataStore and projection phases resumable and
 * idempotent across cancellation, failure, or process death.
 *
 * ## Reconcile semantics (R-B7 interplay, as implemented)
 *
 * Restore APPENDS/RECONCILES — it never destroys:
 *  - a row whose primary key already exists locally is SKIPPED (the local
 *    copy wins; history is never overwritten — R-B7's hide-not-delete
 *    discipline applied to imports);
 *  - rows new to this install are inserted;
 *  - NOTHING is deleted: local rows absent from the backup stay (a restore
 *    is not a wipe; Fresh Start's ritual owner F01 hides history, F13 never
 *    does);
 *  - hidden/archived rows restore verbatim so the R-B7 ledger survives
 *    device migration;
 *  - the consent LEDGER appends only when the backup chain continues the
 *    local head (or local is empty and the backup chain verifies from
 *    genesis); a forked chain is kept local with a report warning instead
 *    of silently breaking the tamper evidence;
 *  - `day_records` (derived) recompute for the restored union range after
 *    commit; `food_search` (FTS mirror) rebuilds inside the transaction.
 */
public class StagedRestorer(
    private val db: WloDatabase,
) {
    /**
     * Parses + decrypts + verifies + fully decodes a backup into the staged
     * report. NO WRITE touches any store — this is the "preview before
     * apply" step (F13 §4 flow 1).
     *
     * @throws BackupContainerException wrong magic / truncated / wrong passphrase
     * @throws BackupDocumentException  wrong format / future version / manifest mismatch
     */
    public suspend fun stage(
        container: ByteArray,
        passphrase: CharArray,
    ): StagedRestore {
        val decrypted = BackupContainer.decrypt(container, passphrase)
        val verified = BackupCodec.decodeVerified(decrypted.plaintextJson)
        val sections = verified.sections

        val sectionReports =
            BackupSchema.SECTION_ORDER.map { name ->
                val element = sections[name] ?: JsonArray(emptyList())
                SectionReport(
                    name = name,
                    rows = rowsIn(element),
                    schemaVersion = verified.schemaVersionRead,
                    sha256 = BackupCodec.sha256(BackupCodec.canonical(element)),
                    ok = true,
                )
            }

        // FULL typed decode of every section: a row that fails its schema
        // fails the STAGE here, before any commit exists to poison.
        val payload = decodePayloadSections(sections)
        validateReferences(payload)

        val warnings =
            buildList {
                if (verified.schemaVersionWritten < verified.schemaVersionRead) {
                    add("migrated backup schema v${verified.schemaVersionWritten} → v${verified.schemaVersionRead}")
                }
                if (payload.consentLedger.isNotEmpty() && db.consentLedger().last() != null) {
                    add("existing consent ledger detected: backup entries apply only if the hash chain continues it")
                }
            }
        return StagedRestore(
            schemaVersionWritten = verified.schemaVersionWritten,
            schemaVersionRead = verified.schemaVersionRead,
            sections = sectionReports,
            payload = payload,
            warnings = warnings,
        )
    }

    private fun decodeAll(sections: JsonObject): BackupPayload = decodePayloadSections(sections)

    internal fun rowsIn(element: JsonElement): Int =
        when (element) {
            is JsonArray -> element.size
            is JsonObject -> (element["values"] as? JsonArray)?.size ?: element.size
            else -> 0
        }

    /**
     * Referential integrity at STAGING (F13 §4 "rows parsed / skipped /
     * warnings" — here: reference breakage is a whole-restore rejection, not
     * a skip): rows referencing profiles/plans/entries missing from BOTH the
     * backup and the local store never reach the commit. The DB-level FKs are
     * the brace; this is the belt — Room's KMP drivers keep the FK pragma
     * connection-scoped, so the honest guarantee lives in this check plus the
     * single-transaction commit.
     */
    private suspend fun validateReferences(payload: BackupPayload) {
        val localProfiles =
            db
                .profiles()
                .all()
                .map { it.id }
                .toSet()
        val profileIds = payload.profiles.map { it.id }.toSet() + localProfiles
        val failures =
            buildList {
                val orphanMeasurements = payload.measurements.filter { it.profileId !in profileIds }.map { it.id }
                if (orphanMeasurements.isNotEmpty()) add("measurements reference missing profiles: $orphanMeasurements")
                val eventIds =
                    payload.measurements.map { it.id }.toSet() +
                        db
                            .measurementEvents()
                            .all()
                            .map { it.id }
                            .toSet()
                val orphanHealthRecords =
                    payload.healthConnect.records
                        .filter { it.profileId !in profileIds || it.measurementEventId !in eventIds }
                        .map { it.recordId }
                if (orphanHealthRecords.isNotEmpty()) {
                    add("Health Connect records reference missing profiles or events: $orphanHealthRecords")
                }
                val orphanHealthStateProfiles =
                    (payload.healthConnect.syncStates.map { it.profileId } + payload.healthConnect.logs.map { it.profileId })
                        .filterNot { it in profileIds }
                        .distinct()
                if (orphanHealthStateProfiles.isNotEmpty()) {
                    add("Health Connect state references missing profiles: $orphanHealthStateProfiles")
                }
                val orphanDiary = payload.diary.filter { it.profileId !in profileIds }.map { it.id }
                if (orphanDiary.isNotEmpty()) add("diary entries reference missing profiles: $orphanDiary")
                val badRevisions =
                    payload.diary
                        .flatMap { e -> e.revisions.filter { it.entryId != e.id } }
                        .map { it.id }
                if (badRevisions.isNotEmpty()) add("diary revisions reference foreign entries: $badRevisions")
                val planIds =
                    payload.plans.map { it.id }.toSet() +
                        db
                            .planVersions()
                            .all()
                            .map { it.id }
                            .toSet()
                val orphanSlots = payload.planSlots.filter { it.planId !in planIds }.map { it.id }
                if (orphanSlots.isNotEmpty()) add("plan slots reference missing plans: $orphanSlots")
                val localFoods =
                    db
                        .foodItems()
                        .all()
                        .map { it.id }
                        .toSet()
                val foodIds = payload.foodItems.map { it.id }.toSet() + localFoods
                val orphanDiaryFoods =
                    payload.diary
                        .filter { it.foodItemId != null && it.foodItemId !in foodIds }
                        .map { it.id }
                if (orphanDiaryFoods.isNotEmpty()) add("diary entries reference missing food items: $orphanDiaryFoods")
            }
        if (failures.isNotEmpty()) {
            throw BackupDocumentException(
                BackupDocumentException.Reason.REFERENCE_BROKEN,
                "referential integrity failed: ${failures.joinToString("; ")}",
            )
        }
    }
}

/**
 * FULL typed decode of every section (the single decode funnel shared by
 * staged restore and export import): a row that fails its schema throws
 * [BackupDocumentException] with SCHEMA_INVALID — no partial payloads.
 */
internal fun decodePayloadSections(sections: JsonObject): BackupPayload {
    fun <T> rows(
        name: String,
        serializer: KSerializer<T>,
    ): List<T> {
        val element = sections[name] ?: return emptyList()
        return runCatching { BackupCodec.decodeRows(element, serializer) }
            .getOrElse {
                throw BackupDocumentException(
                    BackupDocumentException.Reason.SCHEMA_INVALID,
                    "section $name has a row failing schema validation: ${it.message}",
                )
            }
    }

    fun <T> section(
        element: JsonElement?,
        serializer: KSerializer<T>,
    ): T {
        element ?: throw BackupDocumentException(BackupDocumentException.Reason.SCHEMA_INVALID, "missing required section")
        return runCatching { BackupCodec.json.decodeFromString(serializer, element.toString()) }
            .getOrElse {
                throw BackupDocumentException(
                    BackupDocumentException.Reason.SCHEMA_INVALID,
                    "section fails schema validation: ${it.message}",
                )
            }
    }

    return BackupPayload(
        profiles = rows(BackupSchema.SECTION_PROFILES, ProfileRow.serializer()),
        measurements = rows(BackupSchema.SECTION_MEASUREMENTS, MeasurementRow.serializer()),
        diary = rows(BackupSchema.SECTION_DIARY, DiaryEntryRow.serializer()),
        targets = rows(BackupSchema.SECTION_TARGETS, TargetsVersionRow.serializer()),
        provenance = rows(BackupSchema.SECTION_PROVENANCE, ProvenanceRow.serializer()),
        consentLedger = rows(BackupSchema.SECTION_CONSENT_LEDGER, ConsentLedgerRow.serializer()),
        foodItems = rows(BackupSchema.SECTION_FOOD_ITEMS, FoodItemRow.serializer()),
        recipes = rows(BackupSchema.SECTION_RECIPES, RecipeRow.serializer()),
        groceryItems = rows(BackupSchema.SECTION_GROCERY, GroceryItemRow.serializer()),
        plans = rows(BackupSchema.SECTION_PLANS, PlanVersionRow.serializer()),
        planSlots = rows(BackupSchema.SECTION_PLAN_SLOTS, PlanSlotRow.serializer()),
        listItems = rows(BackupSchema.SECTION_LIST, ListItemRow.serializer()),
        pantryItems = rows(BackupSchema.SECTION_PANTRY, PantryItemRow.serializer()),
        aisleCorrections = rows(BackupSchema.SECTION_AISLE_CORRECTIONS, AisleCorrectionRow.serializer()),
        healthConnect =
            sections[BackupSchema.SECTION_HEALTH_CONNECT]?.let {
                section(it, HealthConnectSection.serializer())
            } ?: HealthConnectSection(),
        settings = section(sections[BackupSchema.SECTION_SETTINGS], SettingsSection.serializer()),
        documents = section(sections[BackupSchema.SECTION_DOCUMENTS], DocumentsSection.serializer()),
        vaultBlobs = rows(BackupSchema.SECTION_VAULT, VaultBlobRow.serializer()),
    )
}

/** One section's line of the staged report (F13 §4 "per-stage ticks"). */
@Serializable
public data class SectionReport(
    public val name: String,
    public val rows: Int,
    public val schemaVersion: Int,
    public val sha256: String,
    public val ok: Boolean,
)

/**
 * The staged report shown before commit: counts per section, schema versions
 * (migrated-from → migrated-to) and warnings — the "preview diff" PART B renders.
 */
@Serializable
public data class StagedRestore(
    public val schemaVersionWritten: Int,
    public val schemaVersionRead: Int,
    public val sections: List<SectionReport>,
    public val payload: BackupPayload,
    public val warnings: List<String>,
) {
    public val totalRows: Int get() = sections.sumOf { it.rows }
}

/**
 * The commit phase: applies a staged payload in ONE Room transaction
 * (commit-or-nothing) under the module's append/reconcile rules, then
 * recomputes derived views. Counts are computed from PK presence BEFORE the
 * transaction, so "inserted vs skipped (kept local)" is exact.
 */
public class RestoreCommitter(
    private val db: WloDatabase,
    private val settings: SettingsStore,
    private val documents: JsonDocumentStore,
    private val projector: DayProjector,
    private val phaseHook: suspend (String) -> Unit = {},
) {
    private val commitMutex = Mutex()

    public data class CommitResult(
        public val inserted: Int,
        public val skipped: Int,
        public val warnings: List<String>,
    )

    /**
     * Persists the complete staged operation before the first mutation, then
     * rolls each idempotent phase forward. A crash after Room commits can
     * safely replay Room and continue the DataStore/projection phases.
     */
    public suspend fun commit(staged: StagedRestore): CommitResult =
        commitMutex.withLock {
            // Never replace an older durable operation. Finish it first, then
            // journal and apply the operation the caller explicitly confirmed.
            recoverPendingUnlocked()
            val journal =
                RestoreJournal(
                    staged = staged,
                    existingBefore = countBackingKeys(staged.payload),
                )
            writeJournal(journal)
            requireNotNull(recoverPendingUnlocked())
        }

    /** Resumes a persisted restore, or returns null when no recovery is due. */
    public suspend fun recoverPending(): CommitResult? = commitMutex.withLock { recoverPendingUnlocked() }

    private suspend fun recoverPendingUnlocked(): CommitResult? {
        var journal = readJournal() ?: return null
        val payload = journal.staged.payload
        if (journal.phase == RestorePhase.ROOM) {
            val roomWarnings = commitRoom(payload)
            phaseHook("room")
            journal = journal.copy(phase = RestorePhase.SETTINGS, warnings = roomWarnings)
            writeJournal(journal)
        }
        if (journal.phase == RestorePhase.SETTINGS) {
            if (payload.settings.values.isNotEmpty()) settings.importSettings(payload.settings.values)
            phaseHook("settings")
            journal = journal.copy(phase = RestorePhase.DOCUMENTS)
            writeJournal(journal)
        }
        if (journal.phase == RestorePhase.DOCUMENTS) {
            if (payload.documents.values.isNotEmpty()) documents.importDocuments(payload.documents.values)
            phaseHook("documents")
            journal = journal.copy(phase = RestorePhase.PROJECTIONS)
            writeJournal(journal)
        }
        if (journal.phase == RestorePhase.PROJECTIONS) {
            for ((profileId, range) in restoredDays(payload)) {
                projector.refresh(profileId, range.first, range.second)
            }
            phaseHook("projections")
            journal = journal.copy(phase = RestorePhase.COMPLETE)
            writeJournal(journal)
        }

        val existingAfter = countBackingKeys(payload)
        val inserted = existingAfter - journal.existingBefore
        val result =
            CommitResult(
                inserted = inserted,
                skipped = countStagedKeys(payload) - inserted,
                warnings = journal.warnings,
            )
        documents.remove(RESTORE_JOURNAL_KEY)
        return result
    }

    private suspend fun commitRoom(payload: BackupPayload): List<String> {
        val warnings = mutableListOf<String>()
        db.withWriteTransaction {
            // FK-safe order: profiles first, then events, then dependents.
            db.profiles().insertAllIgnoring(payload.profiles.map { it.toEntity() })
            db.measurementEvents().insertAllIgnoring(payload.measurements.map { it.toEntity() })
            db.measurementEventAttrs().insertAllIgnoring(
                payload.measurements.flatMap { m -> m.attrs.map { a -> a.toEntity(m.id) } },
            )
            db.diaryEntries().insertAllIgnoring(payload.diary.map { it.toEntity() })
            db.diaryEntryRevisions().insertAllIgnoring(
                payload.diary.flatMap { e -> e.revisions.map { r -> r.toEntity() } },
            )
            db.targetsVersions().insertAllIgnoring(payload.targets.map { it.toEntity() })
            db.provenance().insertAllIgnoring(payload.provenance.map { it.toEntity() })
            db.foodItems().insertAllIgnoring(payload.foodItems.map { it.toEntity() })
            // FTS mirror rebuild for restored catalog rows (in-transaction).
            db.foodSearch().insertAll(payload.foodItems.map { it.toSearchEntity() })
            // (recipeId, version) rows are immutable — insert-only semantics
            // via IGNORE keeps local versions in place.
            db.recipes().insertAllIgnoring(payload.recipes.map { it.toEntity() })
            db.groceryItems().insertAllIgnoring(payload.groceryItems.map { it.toEntity() })
            db.planVersions().insertAllIgnoring(payload.plans.map { it.toEntity() })
            db.planSlots().insertAllIgnoring(payload.planSlots.map { it.toEntity() })
            db.listItems().insertAllIgnoring(payload.listItems.map { it.toEntity() })
            db.pantryItems().insertAllIgnoring(payload.pantryItems.map { it.toEntity() })
            db.aisleCorrections().insertAllIgnoring(payload.aisleCorrections.map { it.toEntity() })
            db.healthConnect().insertRecordsIgnoring(payload.healthConnect.records.map { it.toEntity() })
            db.healthConnect().insertSyncStatesIgnoring(payload.healthConnect.syncStates.map { it.toEntity() })
            db.healthConnect().insertLogsIgnoring(payload.healthConnect.logs.map { it.toEntity() })

            warnings += reconcileConsentLedger(payload.consentLedger)
        }
        return warnings
    }

    /** Backup and local ledgers must share an exact genesis-rooted prefix. */
    private suspend fun reconcileConsentLedger(backup: List<ConsentLedgerRow>): List<String> {
        if (backup.isEmpty()) return emptyList()
        if (!chainVerifies(backup)) return listOf("consent ledger in backup fails its hash chain — nothing appended")
        if (!backup.first().isGenesis()) return listOf("consent ledger does not start from genesis — nothing appended")
        val local = db.consentLedger().all()
        val shared = minOf(local.size, backup.size)
        val prefixMatches = (0 until shared).all { index -> local[index].hashHex == backup[index].hashHex }
        if (!prefixMatches) {
            return listOf("consent ledger forked from local history — local ledger kept")
        }
        if (backup.size > local.size) {
            for (row in backup.drop(local.size)) {
                db.consentLedger().appendRestoredIgnoring(
                    seq = row.seq,
                    profileId = row.profileId.ifBlank { CONSENT_PROFILE_WIRE },
                    capability = row.capability,
                    decision = row.decision,
                    atEpochMs = row.atEpochMs,
                    prevHashHex = row.prevHashHex,
                    hashHex = row.hashHex,
                )
            }
        }
        return emptyList()
    }

    private suspend fun readJournal(): RestoreJournal? =
        documents.readText(RESTORE_JOURNAL_KEY)?.let { encoded ->
            BackupCodec.json.decodeFromString(RestoreJournal.serializer(), encoded)
        }

    private suspend fun writeJournal(journal: RestoreJournal) {
        documents.writeText(RESTORE_JOURNAL_KEY, BackupCodec.json.encodeToString(journal))
    }

    @Serializable
    private data class RestoreJournal(
        val staged: StagedRestore,
        val existingBefore: Int,
        val phase: RestorePhase = RestorePhase.ROOM,
        val warnings: List<String> = emptyList(),
    )

    @Serializable
    private enum class RestorePhase { ROOM, SETTINGS, DOCUMENTS, PROJECTIONS, COMPLETE }

    private companion object {
        const val RESTORE_JOURNAL_KEY: String = "__internal/restore-journal-v1"
    }

    /** Rows whose (consent-ledger chain linked and) PKs are now in the store. */
    private suspend fun countBackingKeys(payload: BackupPayload): Int {
        var present = 0
        for (row in payload.profiles) if (db.profiles().byId(row.id) != null) present++
        for (row in payload.measurements) if (db.measurementEvents().byId(row.id) != null) present++
        for (row in payload.diary) if (db.diaryEntries().byId(row.id) != null) present++
        for (row in payload.foodItems) if (db.foodItems().byId(row.id) != null) present++
        for (row in payload.recipes) if (db.recipes().latest(row.recipeId)?.version == row.version) present++
        for (row in payload.groceryItems) if (db.groceryItems().byId(row.id) != null) present++
        for (row in payload.plans) if (db.planVersions().byId(row.id) != null) present++
        for (row in payload.planSlots) if (db.planSlots().byId(row.id) != null) present++
        for (row in payload.listItems) if (db.listItems().byId(row.id) != null) present++
        for (row in payload.pantryItems) if (db.pantryItems().byId(row.id) != null) present++
        for (row in payload.healthConnect.records) if (db.healthConnect().record(row.recordId) != null) present++
        return present
    }

    private fun countStagedKeys(payload: BackupPayload): Int =
        payload.profiles.size +
            payload.measurements.size +
            payload.diary.size +
            payload.foodItems.size +
            payload.recipes.size +
            payload.groceryItems.size +
            payload.plans.size +
            payload.planSlots.size +
            payload.listItems.size +
            payload.pantryItems.size +
            payload.healthConnect.records.size

    private fun chainVerifies(rows: List<ConsentLedgerRow>): Boolean {
        var prev: String? = rows.firstOrNull()?.prevHashHex
        for (row in rows) {
            if (row.prevHashHex != prev) return false
            // Hash recompute per entry — the tamper evidence must hold on
            // the bytes we are about to append (ConsentChain's canonical form).
            // An unknown capability wire name fails the chain (fail closed).
            val capability =
                app.wlo.core.model.ConsentCapability.entries
                    .firstOrNull { it.wireName == row.capability }
                    ?: return false
            val decision =
                when (row.decision) {
                    "grant" -> app.wlo.core.consent.ConsentDecision.GRANT
                    "revoke" -> app.wlo.core.consent.ConsentDecision.REVOKE
                    else -> return false
                }
            val expected =
                ConsentChain.hashOf(
                    ConsentEntry(
                        seq = row.seq,
                        atEpochMs = row.atEpochMs,
                        capability = capability,
                        decision = decision,
                        prevHashHex = row.prevHashHex,
                        hashHex = row.hashHex,
                    ),
                )
            if (expected != row.hashHex) return false
            prev = row.hashHex
        }
        return true
    }

    private fun ConsentLedgerRow.isGenesis(): Boolean = prevHashHex == ConsentEntry.GENESIS_PREV_HASH

    private fun restoredDays(payload: BackupPayload): Map<String, Pair<Long, Long>> {
        val byProfile = mutableMapOf<String, MutableList<Long>>()
        for (m in payload.measurements) byProfile.getOrPut(m.profileId) { mutableListOf() }.add(m.dayEpochDay)
        for (d in payload.diary) byProfile.getOrPut(d.profileId) { mutableListOf() }.add(d.dayEpochDay)
        return byProfile.mapValues { (_, days) -> days.min() to days.max() }
    }
}

// --- row → entity mappers (commit side; mechanical mirrors of the snapshot) --

internal fun ProfileRow.toEntity(): ProfileEntity =
    ProfileEntity(
        id = id,
        sex = sex,
        birthYear = birthYear,
        heightCm = heightCm,
        startWeightKg = startWeightKg,
        activityLevel = activityLevel,
        unitPreference = unitPreference,
        createdAtEpochMs = createdAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun MeasurementRow.toEntity(): MeasurementEventEntity =
    MeasurementEventEntity(
        id = id,
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        kind = kind,
        valueReal = valueReal,
        unit = unit,
        source = source,
        capturedAtEpochMs = capturedAtEpochMs,
        note = note,
        hiddenAtEpochMs = hiddenAtEpochMs,
        hiddenReason = hiddenReason,
    )

internal fun MeasurementAttrRow.toEntity(eventId: String): MeasurementEventAttrEntity =
    MeasurementEventAttrEntity(
        eventId = eventId,
        attr = attr,
        valueText = valueText,
        valueReal = valueReal,
    )

internal fun HealthConnectRecordRow.toEntity(): HealthConnectRecordEntity =
    HealthConnectRecordEntity(
        recordId,
        profileId,
        measurementEventId,
        dataOriginPackage,
        clientRecordId,
        clientRecordVersion,
        recordingMethod,
        lastModifiedAtEpochMs,
        capturedAtEpochMs,
        zoneOffsetSeconds,
        metric,
        canonicalValue,
    )

internal fun HealthConnectSyncStateRow.toEntity(): HealthConnectSyncStateEntity =
    HealthConnectSyncStateEntity(profileId, metric, changeToken, lastSyncAtEpochMs)

internal fun HealthConnectLogRow.toEntity(): HealthConnectImportLogEntity =
    HealthConnectImportLogEntity(id, profileId, atEpochMs, outcome, inserted, updated, deleted, skipped, conflicts, retryable, detail)

internal fun DiaryEntryRow.toEntity(): DiaryEntryEntity =
    DiaryEntryEntity(
        id = id,
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        mealSlot = mealSlot,
        foodItemId = foodItemId,
        textHint = textHint,
        quantity = quantity,
        unit = unit,
        computedKcal = computedKcal,
        computedProteinG = computedProteinG,
        computedCarbG = computedCarbG,
        computedFatG = computedFatG,
        computedFiberG = computedFiberG,
        enteredVia = enteredVia,
        provenanceScalar = provenanceScalar,
        revision = revision,
        createdAtEpochMs = createdAtEpochMs,
        editedAtEpochMs = editedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
        hiddenAtEpochMs = hiddenAtEpochMs,
        hiddenReason = hiddenReason,
    )

internal fun DiaryRevisionRow.toEntity(): DiaryEntryRevisionEntity =
    DiaryEntryRevisionEntity(
        id = id,
        entryId = entryId,
        revision = revision,
        mealSlot = mealSlot,
        foodItemId = foodItemId,
        textHint = textHint,
        quantity = quantity,
        unit = unit,
        computedKcal = computedKcal,
        computedProteinG = computedProteinG,
        computedCarbG = computedCarbG,
        computedFatG = computedFatG,
        computedFiberG = computedFiberG,
        enteredVia = enteredVia,
        editedAtEpochMs = editedAtEpochMs,
    )

internal fun TargetsVersionRow.toEntity(): TargetsVersionEntity =
    TargetsVersionEntity(
        id = id,
        profileId = profileId,
        version = version,
        documentJson = documentJson,
        writtenBy = writtenBy,
        createdAtEpochMs = createdAtEpochMs,
        supersededAtEpochMs = supersededAtEpochMs,
    )

internal fun ProvenanceRow.toEntity(): ProvenanceEntity =
    ProvenanceEntity(
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        scalar = scalar,
        method = method,
        formulaVersion = formulaVersion,
        inputsHash = inputsHash,
        computedAtEpochMs = computedAtEpochMs,
    )

internal fun ConsentLedgerRow.toEntity(): ConsentLedgerEntity =
    ConsentLedgerEntity(
        seq = seq,
        profileId = profileId.ifBlank { CONSENT_PROFILE_WIRE },
        capability = capability,
        decision = decision,
        atEpochMs = atEpochMs,
        prevHashHex = prevHashHex,
        hashHex = hashHex,
    )

internal fun FoodItemRow.toEntity(): FoodItemEntity =
    FoodItemEntity(
        id = id,
        profileId = profileId,
        name = name,
        brand = brand,
        aliases = aliases,
        kcalPer100g = kcalPer100g,
        proteinGPer100g = proteinGPer100g,
        carbGPer100g = carbGPer100g,
        fatGPer100g = fatGPer100g,
        fiberGPer100g = fiberGPer100g,
        servingPresetsJson = servingPresetsJson,
        source = source,
        macrosVerified = macrosVerified,
        verifiedAtEpochMs = verifiedAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun FoodItemRow.toSearchEntity(): FoodSearchEntity = FoodSearchEntity(name = name, brand = brand, aliases = aliases, foodId = id)

internal fun RecipeRow.toEntity(): RecipeEntity =
    RecipeEntity(
        recipeId = recipeId,
        version = version,
        profileId = profileId,
        name = name,
        servingsBase = servingsBase,
        cuisine = cuisine,
        slotsJson = slotsJson,
        tagsJson = tagsJson,
        fodmapTagsJson = fodmapTagsJson,
        ingredientsJson = ingredientsJson,
        stepsJson = stepsJson,
        kcalPerServing = kcalPerServing,
        proteinGPerServing = proteinGPerServing,
        carbGPerServing = carbGPerServing,
        fatGPerServing = fatGPerServing,
        fiberGPerServing = fiberGPerServing,
        nutritionBasis = nutritionBasis,
        source = source,
        license = license,
        rating = rating,
        lastPlannedAtEpochMs = lastPlannedAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun GroceryItemRow.toEntity(): GroceryItemEntity =
    GroceryItemEntity(
        id = id,
        profileId = profileId,
        name = name,
        aisle = aisle,
        defaultUnit = defaultUnit,
        densityGPerMl = densityGPerMl,
        gramsPerPiece = gramsPerPiece,
        aliasesJson = aliasesJson,
        createdAtEpochMs = createdAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun PlanVersionRow.toEntity(): PlanVersionEntity =
    PlanVersionEntity(
        id = id,
        profileId = profileId,
        version = version,
        startDayEpochDay = startDayEpochDay,
        endDayEpochDay = endDayEpochDay,
        seed = seed,
        settingsJson = settingsJson,
        reportJson = reportJson,
        createdAtEpochMs = createdAtEpochMs,
        supersededAtEpochMs = supersededAtEpochMs,
    )

internal fun PlanSlotRow.toEntity(): PlanSlotEntity =
    PlanSlotEntity(
        id = id,
        planId = planId,
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        mealSlot = mealSlot,
        recipeId = recipeId,
        recipeVersion = recipeVersion,
        recipeName = recipeName,
        servings = servings,
        state = state,
        replacedByEntryId = replacedByEntryId,
        successorSlotId = successorSlotId,
        replacesSlotId = replacesSlotId,
        parentSlotId = parentSlotId,
        isCookEvent = isCookEvent,
        batchServings = batchServings,
        kcalPerServing = kcalPerServing,
        proteinGPerServing = proteinGPerServing,
        carbGPerServing = carbGPerServing,
        fatGPerServing = fatGPerServing,
        fiberGPerServing = fiberGPerServing,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

internal fun ListItemRow.toEntity(): ListItemEntity =
    ListItemEntity(
        id = id,
        profileId = profileId,
        listId = listId,
        groceryItemId = groceryItemId,
        name = name,
        qty = qty,
        unit = unit,
        aisle = aisle,
        state = state,
        checkedAtEpochMs = checkedAtEpochMs,
        deltaQty = deltaQty,
        sourcesJson = sourcesJson,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun PantryItemRow.toEntity(): PantryItemEntity =
    PantryItemEntity(
        id = id,
        profileId = profileId,
        groceryItemId = groceryItemId,
        name = name,
        qty = qty,
        unit = unit,
        expiryEpochDay = expiryEpochDay,
        addedAtEpochMs = addedAtEpochMs,
        lastPurchasedAtEpochMs = lastPurchasedAtEpochMs,
        purchaseCount = purchaseCount,
        isStaple = isStaple,
        outOfStock = outOfStock,
        updatedAtEpochMs = updatedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun AisleCorrectionRow.toEntity(): AisleCorrectionEntity =
    AisleCorrectionEntity(
        profileId = profileId,
        groceryItemId = groceryItemId,
        aisle = aisle,
        updatedAtEpochMs = updatedAtEpochMs,
    )

/**
 * Restores the documents section (text drafts + boolean marks) into the
 * DataStore. Unknown keys are ignored — the key lists are the contract.
 */
public suspend fun JsonDocumentStore.importDocuments(values: Map<String, String>) {
    val textValues = values.filterKeys { it in TEXT_DOCUMENT_KEYS }
    val flagValues =
        values
            .filterKeys { it in FLAG_DOCUMENT_KEYS }
            .mapNotNull { (key, value) -> value.toBooleanStrictOrNull()?.let { key to it } }
            .toMap()
    importBatch(textValues, flagValues)
}

/**
 * The R-B9 single-profile fallback for consent-ledger rows with a blank
 * profileId: v1 ledger rows carry one profile; multi-profile (v1.x)
 * re-partitions them.
 */
public const val CONSENT_PROFILE_WIRE: String = "default"
