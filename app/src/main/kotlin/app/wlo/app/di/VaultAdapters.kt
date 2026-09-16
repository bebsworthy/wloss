package app.wlo.app.di

import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.database.WloDatabase
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.network.EgressLedger
import app.wlo.core.network.EgressReceipt
import app.wlo.core.network.ReceiptChain
import app.wlo.core.network.RoomEgressLedger
import app.wlo.core.ports.DataVaultPort
import app.wlo.core.ports.ReceiptAuditLog
import app.wlo.core.ports.ReceiptChainVerdict
import app.wlo.core.ports.ReceiptEntryView
import app.wlo.core.ports.VaultBackupFileInfo
import app.wlo.core.ports.VaultBackupOutcome
import app.wlo.core.ports.VaultBackupState
import app.wlo.core.ports.VaultCsvMappingView
import app.wlo.core.ports.VaultCsvPreview
import app.wlo.core.ports.VaultCsvRender
import app.wlo.core.ports.VaultCsvStagedReport
import app.wlo.core.ports.VaultExportOutcome
import app.wlo.core.ports.VaultFailure
import app.wlo.core.ports.VaultFreshStartReport
import app.wlo.core.ports.VaultOperationException
import app.wlo.core.ports.VaultPartitionUsage
import app.wlo.core.ports.VaultRestoreCommitReport
import app.wlo.core.ports.VaultRestoreSectionReport
import app.wlo.core.ports.VaultStagedRestoreReport
import app.wlo.core.vault.AutoBackupKeyVault
import app.wlo.core.vault.BackupContainerException
import app.wlo.core.vault.BackupDocumentException
import app.wlo.core.vault.BackupKdf
import app.wlo.core.vault.BackupManager
import app.wlo.core.vault.BackupOptions
import app.wlo.core.vault.BackupSchema
import app.wlo.core.vault.BackupStoreFactory
import app.wlo.core.vault.CsvColumnMapping
import app.wlo.core.vault.CsvImportCommitter
import app.wlo.core.vault.CsvMappingSniffer
import app.wlo.core.vault.CsvMeasurementImporter
import app.wlo.core.vault.CsvTable
import app.wlo.core.vault.CsvTarget
import app.wlo.core.vault.ExportBundle
import app.wlo.core.vault.MetricCsv
import app.wlo.core.vault.RestoreCommitter
import app.wlo.core.vault.SnapshotAssembler
import app.wlo.core.vault.StagedRestore
import app.wlo.core.vault.StagedRestorer
import app.wlo.core.vault.VaultFileStore
import app.wlo.core.vault.VaultKeys
import app.wlo.feature.f01.onboarding.domain.FinishOnboarding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

/**
 * The M6 PART B port implementations (D1: this is the ONLY module that can see
 * `:core:vault`/`:core:network`, so the feature-facing ports get their
 * implementations here, next to the Koin wiring in WloModule).
 *
 * [ReceiptAuditLog] over the Room egress ledger is the F12 receipt viewer's
 * read door; [VaultPortAdapter] is the F13 surfaces' single door.
 */
public class RoomReceiptAudit(
    private val ledger: EgressLedger,
) : ReceiptAuditLog {
    override suspend fun recent(limit: Long): List<ReceiptEntryView> = ledger.recent(limit).map { it.toView() }

    override suspend fun countByPurpose(): Map<String, Long> = ledger.countByPurpose().mapKeys { it.key.wireName }

    override suspend fun totalBytes(): Long = ledger.totalBytes()

    override fun observe(): Flow<List<ReceiptEntryView>> = ledger.observe().map { list -> list.map { it.toView() } }

    /**
     * Full-chain audit: seqs consecutive from 1, links intact, every hash
     * recomputed. [brokenAtSeq] names the first failing receipt when one does.
     */
    override suspend fun verifyChain(): ReceiptChainVerdict {
        val receipts = ledger.recent(RECEIPT_SCAN_LIMIT)
        if (receipts.isEmpty()) return ReceiptChainVerdict.intact(checked = 0)
        var expectedPrev = RoomEgressLedger.GENESIS_HASH
        receipts.forEachIndexed { index, receipt ->
            val seq = index + 1L
            val linked =
                receipt.seq == seq &&
                    receipt.prevHashHex == expectedPrev &&
                    receipt.hashHex == ReceiptChain.hashOf(receipt)
            if (!linked) {
                return ReceiptChainVerdict.broken(checked = receipts.size, atSeq = seq)
            }
            expectedPrev = receipt.hashHex
        }
        return ReceiptChainVerdict.intact(receipts.size)
    }

    private fun EgressReceipt.toView(): ReceiptEntryView =
        ReceiptEntryView(
            seq = seq,
            purposeWire = purpose.wireName,
            host = host,
            operation = operation,
            bytes = bytes,
            outcomeWire = outcome.wireName,
            atEpochMs = atEpochMs,
        )

    private companion object {
        /** The whole ledger, practically (a LONG-tail cap keeps the DAO honest). */
        const val RECEIPT_SCAN_LIMIT: Long = 1_000_000L
    }
}

/**
 * [DataVaultPort] over the PART A pipeline — the F13 surfaces' single door.
 * Staging discipline lives here: the stage* operations only REPORT (the
 * pending payload is held in memory); [commitStaged] applies whichever stage
 * is pending, once — a commit without a stage in this session fails.
 */
public class VaultPortAdapter(
    private val manager: BackupManager,
    private val assembler: SnapshotAssembler,
    private val restorer: StagedRestorer,
    private val committer: RestoreCommitter,
    private val vaultFiles: VaultFileStore,
    private val autoKeys: AutoBackupKeyVault,
    private val storeFactory: BackupStoreFactory,
    private val settings: SettingsStore,
    private val documents: JsonDocumentStore,
    private val db: WloDatabase,
    private val csvCommitter: CsvImportCommitter,
    private val diaries: DiaryRepository,
    private val profiles: ProfileRepository,
    private val clock: app.wlo.core.common.ClockPort,
) : DataVaultPort {
    private var pendingRestore: StagedRestore? = null
    private var pendingCsv: List<CsvMeasurementImporter.StagedMeasurement> = emptyList()
    private var pendingCsvToken: String? = null
    private val restorePending = MutableStateFlow(false)

    // --- storage dashboard ---------------------------------------------------

    override suspend fun storageUsage(): List<VaultPartitionUsage> =
        VaultKeys.Partition.entries.map { partition ->
            val usage = vaultFiles.accounting(partition)
            VaultPartitionUsage(partition = usage.partition, bytes = usage.bytes, count = usage.count)
        }

    override suspend fun reclaimPartition(partition: String) {
        VaultKeys.Partition.byDirName(partition)?.let(vaultFiles::purgePartition)
    }

    // --- backup controls -------------------------------------------------------

    override suspend fun backupState(): VaultBackupState {
        val folder = settings.backupFolderUriOnce()
        val files =
            if (folder == null) {
                emptyList()
            } else {
                runCatching {
                    storeFactory
                        .create(folder)
                        .list()
                        .map { ref -> VaultBackupFileInfo(name = ref.name, sizeBytes = ref.sizeBytes) }
                }.getOrDefault(emptyList())
            }
        return VaultBackupState(folderUri = folder, autoEnabled = settings.backupAutoEnabled.first(), files = files)
    }

    override suspend fun backupNow(passphrase: CharArray?): VaultBackupOutcome {
        val folder =
            settings.backupFolderUriOnce()
                ?: throw VaultOperationException(
                    VaultFailure.NO_BACKUP_FOLDER,
                    "Choose a backup folder first — your data stays yours.",
                )
        return try {
            val outcome = manager.backupNow(destination = folder, passphrase = passphrase)
            VaultBackupOutcome(fileName = outcome.fileName, sizeBytes = outcome.sizeBytes, retired = outcome.retired)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (noKey: IllegalStateException) {
            // BackupManager: "no auto-backup key stored — set up … first".
            throw VaultOperationException(
                VaultFailure.NO_AUTO_KEY,
                noKey.message ?: "no stored auto-backup key",
                noKey,
            )
        }
    }

    override suspend fun configureAutoBackupKey(passphrase: CharArray) {
        val salt = ByteArray(BackupKdf.SALT_BYTES).also(SecureRandom()::nextBytes)
        autoKeys.store(passphrase, salt)
    }

    override suspend fun autoBackupKeyConfigured(): Boolean = autoKeys.load() != null

    // --- staged restore ---------------------------------------------------------

    override suspend fun stageRestore(
        bytes: ByteArray,
        passphrase: CharArray,
    ): VaultStagedRestoreReport {
        val staged =
            try {
                restorer.stage(bytes, passphrase)
            } catch (container: BackupContainerException) {
                throw VaultOperationException(
                    container.reason.toPort(),
                    container.message ?: "unreadable container",
                    container,
                )
            } catch (document: BackupDocumentException) {
                throw VaultOperationException(
                    document.reason.toPort(),
                    document.message ?: "unreadable document",
                    document,
                )
            }
        pendingRestore = staged
        restorePending.value = true
        return staged.toReport()
    }

    override suspend fun stageBundleImport(bytes: ByteArray): VaultStagedRestoreReport {
        // An encrypted .wlo container passed to the BUNDLE door gets pointed at
        // the restore wizard (which owns passphrases) instead of failing cryptic.
        val head = bytes.take(4).map { byte -> byte.toInt() and 0xFF }
        if (head == WLOB_MAGIC) {
            throw VaultOperationException(
                VaultFailure.WRONG_PASSPHRASE,
                "this is an encrypted backup (.wlo) — use Restore to bring it back",
            )
        }
        val (version, payload) =
            try {
                ExportBundle.decodeBundle(bytes)
            } catch (document: BackupDocumentException) {
                throw VaultOperationException(
                    document.reason.toPort(),
                    document.message ?: "unreadable export bundle",
                    document,
                )
            }
        // Bundles ride the SAME commit door as restores: wrap the decoded
        // payload as a staged restore. decodeBundle already ran the typed
        // section decode (the single decode funnel); rows-per-section are
        // recounted from the payload for the report.
        val staged =
            StagedRestore(
                schemaVersionWritten = version,
                schemaVersionRead = BackupSchema.SCHEMA_VERSION,
                sections =
                    BackupSchema.SECTION_ORDER.map { name ->
                        sectionReportFor(name, bundleRowCount(payload, name), version)
                    },
                payload = payload,
                warnings = emptyList(),
            )
        pendingRestore = staged
        restorePending.value = true
        return staged.toReport()
    }

    private fun bundleRowCount(
        payload: app.wlo.core.vault.BackupPayload,
        name: String,
    ): Int =
        when (name) {
            BackupSchema.SECTION_PROFILES -> payload.profiles.size
            BackupSchema.SECTION_MEASUREMENTS -> payload.measurements.size
            BackupSchema.SECTION_DIARY -> payload.diary.size
            BackupSchema.SECTION_TARGETS -> payload.targets.size
            BackupSchema.SECTION_PROVENANCE -> payload.provenance.size
            BackupSchema.SECTION_CONSENT_LEDGER -> payload.consentLedger.size
            BackupSchema.SECTION_FOOD_ITEMS -> payload.foodItems.size
            BackupSchema.SECTION_RECIPES -> payload.recipes.size
            BackupSchema.SECTION_GROCERY -> payload.groceryItems.size
            BackupSchema.SECTION_PLANS -> payload.plans.size
            BackupSchema.SECTION_PLAN_SLOTS -> payload.planSlots.size
            BackupSchema.SECTION_LIST -> payload.listItems.size
            BackupSchema.SECTION_PANTRY -> payload.pantryItems.size
            BackupSchema.SECTION_AISLE_CORRECTIONS -> payload.aisleCorrections.size
            BackupSchema.SECTION_VAULT -> payload.vaultBlobs.size
            else -> 0
        }

    /**
     * Restore phases cross Room, DataStore, and projection APIs, so there is no
     * narrower shared exception type. Preserve cancellation and retain the
     * original cause while translating other failures into the recovery-aware
     * port contract.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun commitStaged(): VaultRestoreCommitReport {
        val staged =
            pendingRestore
                ?: throw VaultOperationException(VaultFailure.BAD_HEADER, "nothing staged — validate a file first")
        val result =
            try {
                committer.commit(staged)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                throw VaultOperationException(
                    VaultFailure.RECOVERY_PENDING,
                    "restore paused after a durable journal was saved; WLO will safely resume: ${failure.message}",
                    failure,
                )
            }
        pendingRestore = null
        restorePending.value = false
        return VaultRestoreCommitReport(
            inserted = result.inserted,
            skipped = result.skipped,
            warnings = result.warnings,
        )
    }

    override fun stagedRestorePending(): Flow<Boolean> = restorePending.asStateFlow()

    // --- export -------------------------------------------------------------------

    override suspend fun renderJsonBundle(): ByteArray =
        ExportBundle.encode(assembler.assemble(BackupOptions()), System.currentTimeMillis())

    override suspend fun renderMetricCsv(): VaultCsvRender {
        val payload = assembler.assemble(BackupOptions())
        val events =
            payload.measurements.map { row ->
                MetricCsv.CsvEvent(
                    eventId = row.id,
                    kind = row.kind,
                    valueReal = row.valueReal,
                    unit = row.unit,
                    capturedAtEpochMs = row.capturedAtEpochMs,
                    metricName = row.attrs.firstOrNull { it.attr == METRIC_ATTR }?.valueText,
                )
            }
        val output = MetricCsv.write(events)
        return VaultCsvRender(csv = output.csv, dataRows = events.size, warnings = output.warnings)
    }

    override suspend fun writeToDestination(
        destination: String,
        fileName: String,
        bytes: ByteArray,
    ): VaultExportOutcome {
        val ref = storeFactory.create(destination).write(fileName, bytes)
        return VaultExportOutcome(fileName = ref.name, sizeBytes = ref.sizeBytes)
    }

    // --- CSV import -------------------------------------------------------------------

    override suspend fun previewCsv(bytes: ByteArray): VaultCsvPreview {
        val table = parseTable(bytes)
        return VaultCsvPreview(
            columns = table.header,
            rowCount = table.rows.size,
            proposed = CsvMappingSniffer.sniff(table.header).toViews(),
            samples =
                table.header.associateWith { column ->
                    val index = table.column(column)
                    table.rows.mapNotNull { row -> row.getOrNull(index)?.takeIf(String::isNotBlank) }.take(3)
                },
        )
    }

    override suspend fun stageCsvImport(
        bytes: ByteArray,
        mapping: List<VaultCsvMappingView>,
    ): VaultCsvStagedReport {
        val table = parseTable(bytes)
        val parsed = CsvMeasurementImporter.parse(table, mapping.toCore())
        pendingCsv = parsed.rows
        pendingCsvToken =
            app.wlo.core.vault.BackupCodec.sha256(
                bytes + mapping.joinToString("|").toByteArray(),
            )
        return VaultCsvStagedReport(
            stagedRows = parsed.rows.size,
            warnings = parsed.warnings,
            sourceRows = table.rows.size,
            rejectedRows =
                parsed.warnings
                    .mapNotNull(::warningRowNumber)
                    .distinct()
                    .size,
            reviewToken = pendingCsvToken,
        )
    }

    override suspend fun commitCsvImport(reviewToken: String): VaultCsvStagedReport {
        if (reviewToken != pendingCsvToken) {
            throw VaultOperationException(
                VaultFailure.BAD_HEADER,
                "This review is no longer current. Review the mapping again before importing.",
            )
        }
        val rows = pendingCsv
        if (rows.isEmpty()) {
            throw VaultOperationException(VaultFailure.BAD_HEADER, "nothing staged — validate a CSV first")
        }
        val profilesAll = db.profiles().all()
        val activeProfile = profilesAll.firstOrNull { it.archivedAtEpochMs == null } ?: profilesAll.firstOrNull()
        val profileId =
            activeProfile?.id
                ?: throw VaultOperationException(VaultFailure.BAD_HEADER, "no profile to import against")
        val result = csvCommitter.commit(profileId, rows)
        pendingCsv = emptyList()
        pendingCsvToken = null
        return VaultCsvStagedReport(
            stagedRows = result.inserted,
            warnings =
                if (result.skipped == 0) {
                    emptyList()
                } else {
                    listOf("${result.skipped} row(s) already present or unsupported")
                },
        )
    }

    private suspend fun parseTable(bytes: ByteArray): CsvTable =
        try {
            CsvTable.parse(bytes.toString(Charsets.UTF_8))
        } catch (failure: IllegalArgumentException) {
            throw VaultOperationException(
                VaultFailure.WRONG_FORMAT,
                failure.message ?: "not a CSV table",
                failure,
            )
        }

    private fun warningRowNumber(warning: String): Int? {
        val rowToken = warning.removePrefix("row ").substringBefore(':')
        return rowToken.toIntOrNull()
    }

    // --- Fresh Start (R-B7) -------------------------------------------------------------

    override suspend fun freshStartPreview(): VaultFreshStartReport =
        VaultFreshStartReport(
            archivedDiaryEntries =
                db
                    .diaryEntries()
                    .all()
                    .count { entry -> entry.hiddenAtEpochMs == null && entry.archivedAtEpochMs == null },
        )

    override suspend fun freshStartHide(): VaultFreshStartReport {
        val now = clock.now()
        var archived = 0
        for (entry in db.diaryEntries().all()) {
            if (entry.hiddenAtEpochMs != null || entry.archivedAtEpochMs != null) continue
            val result = diaries.archiveEntry(entry.id, now)
            if (result is WloResult.Ok) archived++
        }
        // Retire the active profile: the shell gate re-arms onboarding (F01
        // owns the ritual; this is the F13 data-mechanics half).
        val active = profiles.active().getOrNull()
        active?.let { profiles.archive(it.id, now) }
        // The completion flag must go too — the gate stays Onboarded on the
        // flag alone (ShellViewModel: (profile && targets) || flag).
        documents.writeFlag(FinishOnboarding.FLAG_COMPLETE, false)
        return VaultFreshStartReport(archivedDiaryEntries = archived)
    }

    private fun StagedRestore.toReport(): VaultStagedRestoreReport =
        VaultStagedRestoreReport(
            schemaVersionFrom = schemaVersionWritten,
            schemaVersionTo = schemaVersionRead,
            sections = sections.map { VaultRestoreSectionReport(name = it.name, rows = it.rows, ok = it.ok) },
            warnings = warnings,
            totalRows = totalRows,
        )

    /** Bridges a payload count into a :core:vault SectionReport (bundle path). */
    private fun sectionReportFor(
        name: String,
        rows: Int,
        version: Int,
    ): app.wlo.core.vault.SectionReport =
        app.wlo.core.vault
            .SectionReport(name = name, rows = rows, schemaVersion = version, sha256 = "", ok = true)

    private companion object {
        const val METRIC_ATTR: String = "metric"

        val WLOB_MAGIC: List<Int> = listOf(0x57, 0x4C, 0x4F, 0x42) // "WLOB"
    }
}

private fun BackupContainerException.Reason.toPort(): VaultFailure =
    when (this) {
        BackupContainerException.Reason.WRONG_MAGIC -> VaultFailure.WRONG_FORMAT
        BackupContainerException.Reason.UNSUPPORTED_FORMAT -> VaultFailure.UNSUPPORTED_FORMAT
        BackupContainerException.Reason.TRUNCATED -> VaultFailure.TRUNCATED
        BackupContainerException.Reason.WRONG_PASSPHRASE -> VaultFailure.WRONG_PASSPHRASE
    }

private fun BackupDocumentException.Reason.toPort(): VaultFailure =
    when (this) {
        BackupDocumentException.Reason.NOT_JSON,
        BackupDocumentException.Reason.WRONG_FORMAT,
        -> VaultFailure.WRONG_FORMAT
        BackupDocumentException.Reason.BAD_HEADER -> VaultFailure.BAD_HEADER
        BackupDocumentException.Reason.FUTURE_VERSION -> VaultFailure.FUTURE_VERSION
        BackupDocumentException.Reason.MANIFEST_MISMATCH -> VaultFailure.TRUNCATED
        BackupDocumentException.Reason.SCHEMA_INVALID -> VaultFailure.SCHEMA_INVALID
        BackupDocumentException.Reason.REFERENCE_BROKEN -> VaultFailure.REFERENCE_BROKEN
    }

private fun List<CsvColumnMapping>.toViews(): List<VaultCsvMappingView> =
    map { mapping ->
        when (val target = mapping.target) {
            CsvTarget.Day ->
                VaultCsvMappingView(mapping.sourceColumn, isDay = true, kind = null, unit = null, customName = null)
            is CsvTarget.Metric ->
                VaultCsvMappingView(
                    sourceColumn = mapping.sourceColumn,
                    isDay = false,
                    kind = target.kind,
                    unit = target.unit,
                    customName = target.customName,
                )
        }
    }

private fun List<VaultCsvMappingView>.toCore(): List<CsvColumnMapping> =
    mapNotNull { view ->
        when {
            view.isDay -> CsvColumnMapping(view.sourceColumn, CsvTarget.Day)
            else ->
                view.kind?.let { kind ->
                    CsvColumnMapping(
                        view.sourceColumn,
                        CsvTarget.Metric(kind = kind, unit = view.unit ?: "", customName = view.customName),
                    )
                }
        }
    }
