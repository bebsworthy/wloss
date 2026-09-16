package app.wlo.feature.f13.vault.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.ports.DataVaultPort
import app.wlo.core.ports.VaultCsvMappingView
import app.wlo.core.ports.VaultCsvPreview
import app.wlo.core.ports.VaultCsvRender
import app.wlo.core.ports.VaultCsvStagedReport
import app.wlo.core.ports.VaultFailure
import app.wlo.core.ports.VaultOperationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Export wizard state (F13 §3 formats; R-U5: plaintext is an explicit choice). */
public data class ExportUiState(
    /** "bundle" | "csv" — the picker's two documented formats. */
    public val format: String = FORMAT_BUNDLE,
    public val csvPreview: VaultCsvRender? = null,
    public val lastFileName: String? = null,
    public val lastSizeBytes: Long = 0,
    public val failure: String? = null,
    public val busy: Boolean = false,
) {
    public companion object {
        public const val FORMAT_BUNDLE: String = "bundle"
        public const val FORMAT_CSV: String = "csv"
    }
}

public class ExportViewModel(
    private val vault: DataVaultPort,
) : ViewModel() {
    private val stateFlow = MutableStateFlow(ExportUiState())

    public val state: StateFlow<ExportUiState> = stateFlow.asStateFlow()

    private var bundleBytes: ByteArray? = null
    private var retryDestination: String? = null

    init {
        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(csvPreview = vault.renderMetricCsv())
        }
    }

    public fun pickFormat(format: String) {
        stateFlow.value = stateFlow.value.copy(format = format)
    }

    /** Renders the chosen artifact; returns (fileName, bytes) for the SAF write. */
    public suspend fun render(): Pair<String, ByteArray> =
        when (stateFlow.value.format) {
            ExportUiState.FORMAT_CSV -> {
                val csv = stateFlow.value.csvPreview ?: vault.renderMetricCsv()
                "wlo_metrics.csv" to csv.csv.toByteArray(Charsets.UTF_8)
            }

            else -> {
                val bytes = bundleBytes ?: vault.renderJsonBundle().also { bundleBytes = it }
                "wlo_export.json" to bytes
            }
        }

    /** The screen's SAF create-document launcher produced a destination. */
    public fun onDestinationPicked(destination: String?) {
        if (destination == null) {
            stateFlow.value = stateFlow.value.copy(busy = false)
            return
        }
        retryDestination = destination
        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(busy = true, failure = null, lastFileName = null)
            try {
                val (fileName, bytes) = render()
                val outcome = vault.writeToDestination(destination, fileName, bytes)
                stateFlow.value =
                    stateFlow.value.copy(
                        lastFileName = outcome.fileName,
                        lastSizeBytes = outcome.sizeBytes,
                        failure = null,
                        busy = false,
                    )
            } catch (failure: VaultOperationException) {
                stateFlow.value = stateFlow.value.copy(failure = failure.message, busy = false)
            }
        }
    }

    public fun retryWrite() {
        onDestinationPicked(retryDestination)
    }
}

/** One editable CSV mapping row in the import wizard. */
public data class CsvMappingRow(
    public val sourceColumn: String,
    /** Null = unmapped/ignored; "day" = the day column; otherwise a kind wire name. */
    public val targetKind: String?,
    public val unit: String?,
    public val customName: String?,
)

public data class ImportUiState(
    public val step: Step = Step.Pick,
    public val fileName: String? = null,
    /** True when the picked file is a WLO export bundle (vs a generic CSV). */
    public val isBundle: Boolean = false,
    public val csvPreview: VaultCsvPreview? = null,
    public val mapping: List<CsvMappingRow> = emptyList(),
    public val staged: VaultCsvStagedReport? = null,
    public val committedRows: Int = 0,
    public val failure: String? = null,
    /** Room may be committed and the durable restore journal still pending. */
    public val recoveryPending: Boolean = false,
    public val recoverTo: Step? = null,
    public val mappingError: String? = null,
) {
    public enum class Step {
        Pick,
        Reading,

        /** CSV only: map columns onto WLO's metric vocabulary (R-S4). */
        Mapping,

        /** The staged report (rows parsed / skipped with reasons). */
        Reviewing,
        Applying,
        Done,
        RecoverableError,
    }
}

/**
 * The import surface (F13 §3): JSON bundles re-import through the same staged
 * funnel as restore; generic CSVs go through the R-S4 column-mapping wizard —
 * prefilled by the sniffer, corrected by the user, REMEMBERED for next time.
 */
public class ImportViewModel(
    private val vault: DataVaultPort,
    private val settings: SettingsStore,
    private val reader: ImportSourceReader,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val stateFlow = MutableStateFlow(ImportUiState())

    public val state: StateFlow<ImportUiState> = stateFlow.asStateFlow()

    private var pendingBytes: ByteArray? = null
    private var pendingName: String? = null
    private var pendingUri: String? = null
    private var stagingPath: String? = savedStateHandle[KEY_STAGING_PATH]
    private var sourceRevision: Long = 0L
    private var stageAttempt: Long = 0L

    init {
        val recoveredPath = stagingPath
        val recoveredName = savedStateHandle.get<String>(KEY_FILE_NAME)
        if (recoveredPath != null && recoveredName != null) {
            stateFlow.value = ImportUiState(step = ImportUiState.Step.Reading, fileName = recoveredName)
            viewModelScope.launch { loadStagedSource(recoveredPath, recoveredName) }
        }
    }

    public fun onSourcePicked(uri: String) {
        sourceRevision++
        stageAttempt++
        pendingUri = uri
        stagePickedUri(uri)
    }

    private fun stagePickedUri(uri: String) {
        stateFlow.value = ImportUiState(step = ImportUiState.Step.Reading)
        viewModelScope.launch {
            try {
                val source = reader.stage(uri)
                stagingPath = source.path
                savedStateHandle[KEY_STAGING_PATH] = source.path
                savedStateHandle[KEY_FILE_NAME] = source.fileName
                loadStagedSource(source.path, source.fileName)
            } catch (failure: ImportSourceException) {
                recoverable(ImportUiState.Step.Pick, failure.message ?: "The selected file could not be read.")
            }
        }
    }

    private suspend fun loadStagedSource(
        path: String,
        fileName: String,
    ) {
        try {
            beginSource(fileName, reader.load(path))
        } catch (failure: ImportSourceException) {
            recoverable(ImportUiState.Step.Pick, failure.message ?: "The staged file could not be read.")
        }
    }

    /** Test/support entry point; the release picker uses bounded private staging above. */
    public fun onFilePicked(
        fileName: String,
        bytes: ByteArray,
    ) {
        if (bytes.size > MAX_IMPORT_BYTES) {
            recoverable(
                ImportUiState.Step.Pick,
                "This file is larger than 20 MiB. Split it into smaller files and import them separately.",
            )
            return
        }
        beginSource(fileName, bytes)
    }

    private fun beginSource(
        fileName: String,
        bytes: ByteArray,
    ) {
        sourceRevision++
        stageAttempt++
        pendingBytes = bytes
        pendingName = fileName
        val looksLikeBundle = fileName.endsWith(".json", ignoreCase = true)
        stateFlow.value =
            ImportUiState(
                step = ImportUiState.Step.Reading,
                fileName = fileName,
                isBundle = looksLikeBundle,
            )
        if (looksLikeBundle) {
            // Bundles carry their own schema — straight to staging (the same
            // funnel as restore; the report is the mapping step's stand-in).
            stage { stateFlow.value = it.copy(step = ImportUiState.Step.Reviewing) }
        } else {
            buildCsvPreview()
        }
    }

    private fun buildCsvPreview() {
        val bytes = pendingBytes ?: return
        viewModelScope.launch {
            try {
                val preview = vault.previewCsv(bytes)
                val remembered = rememberedMappingFor(preview)
                val rows =
                    preview.columns.map { column ->
                        val hit = remembered.firstOrNull { candidate -> candidate?.sourceColumn == column }
                        CsvMappingRow(
                            sourceColumn = column,
                            targetKind = hit?.takeIf { it.isDay }?.let { "day" } ?: hit?.kind,
                            unit = hit?.unit,
                            customName = hit?.customName,
                        )
                    }
                stateFlow.value =
                    stateFlow.value.copy(
                        step = ImportUiState.Step.Mapping,
                        csvPreview = preview,
                        mapping = rows,
                        failure = null,
                        recoverTo = null,
                    )
            } catch (failure: VaultOperationException) {
                recoverable(ImportUiState.Step.Reading, failure.message ?: "The CSV could not be parsed.")
            }
        }
    }

    /** Sniffer first, then the R-S4 memory for anything it didn't name. */
    private suspend fun rememberedMappingFor(preview: VaultCsvPreview): List<VaultCsvMappingView?> {
        val proposed = preview.proposed
        val memoryJson = settings.rememberedCsvMapping.first() ?: return proposed
        val memory =
            runCatching { Json.decodeFromString<List<RememberedMapping>>(memoryJson) }.getOrDefault(emptyList())
        return preview.columns.map { column ->
            proposed.firstOrNull { it.sourceColumn == column }
                ?: memory
                    .firstOrNull { it.column == column }
                    ?.let { hit ->
                        VaultCsvMappingView(
                            sourceColumn = column,
                            isDay = hit.kind == "day",
                            kind = if (hit.kind == "day") null else hit.kind,
                            unit = hit.unit,
                            customName = hit.customName,
                        )
                    }
        }
    }

    /** The user set/cleared one column's target. */
    public fun setMapping(
        column: String,
        targetKind: String?,
        unit: String? = null,
        customName: String? = null,
    ) {
        sourceRevision++
        stageAttempt++
        val rows =
            stateFlow.value.mapping.map { row ->
                if (row.sourceColumn == column) {
                    row.copy(targetKind = targetKind, unit = unit, customName = customName)
                } else {
                    row
                }
            }
        stateFlow.value = stateFlow.value.copy(mapping = rows, staged = null, mappingError = null)
    }

    public fun stage() {
        val validation = validateCsvMapping(stateFlow.value.mapping)
        if (validation != null) {
            stateFlow.value = stateFlow.value.copy(mappingError = validation)
            return
        }
        stage { stateFlow.value = it }
    }

    private fun stage(onStaged: (ImportUiState) -> Unit) {
        val bytes = pendingBytes ?: return
        val isBundle = stateFlow.value.isBundle
        val revision = sourceRevision
        val attempt = ++stageAttempt
        val mappingSnapshot = stateFlow.value.mapping
        viewModelScope.launch {
            try {
                if (isBundle) {
                    // Bundle import = staged restore funnel; the vault applies
                    // it through commitStaged (the mapping UI never shows).
                    val report = stageBundle(bytes)
                    if (revision != sourceRevision || attempt != stageAttempt) return@launch
                    stateFlow.value =
                        stateFlow.value.copy(
                            staged = VaultCsvStagedReport(stagedRows = report.totalRows, warnings = report.warnings),
                            failure = null,
                            recoveryPending = false,
                        )
                } else {
                    val mapping =
                        mappingSnapshot.mapNotNull { row ->
                            when (row.targetKind) {
                                null -> null
                                "day" ->
                                    VaultCsvMappingView(
                                        row.sourceColumn,
                                        isDay = true,
                                        kind = null,
                                        unit = null,
                                        customName = null,
                                    )
                                else ->
                                    VaultCsvMappingView(
                                        row.sourceColumn,
                                        isDay = false,
                                        kind = row.targetKind,
                                        unit = row.unit,
                                        customName = row.customName,
                                    )
                            }
                        }
                    val staged = vault.stageCsvImport(bytes, mapping)
                    if (revision != sourceRevision || attempt != stageAttempt) return@launch
                    remember(mapping)
                    stateFlow.value =
                        stateFlow.value.copy(
                            step = ImportUiState.Step.Reviewing,
                            staged = staged,
                            failure = null,
                            recoveryPending = false,
                            recoverTo = null,
                            mappingError = null,
                        )
                }
                onStaged(stateFlow.value)
            } catch (failure: VaultOperationException) {
                if (revision != sourceRevision || attempt != stageAttempt) return@launch
                recoverable(
                    if (isBundle) ImportUiState.Step.Reading else ImportUiState.Step.Mapping,
                    failure.message ?: "The source could not be reviewed.",
                )
            }
        }
    }

    private suspend fun stageBundle(bytes: ByteArray) = vault.stageBundleImport(bytes)

    public fun commit() {
        if (stateFlow.value.step == ImportUiState.Step.Applying) return
        val reviewToken = stateFlow.value.staged?.reviewToken
        stateFlow.value = stateFlow.value.copy(step = ImportUiState.Step.Applying)
        viewModelScope.launch {
            try {
                val result =
                    if (stateFlow.value.isBundle) {
                        val commit = vault.commitStaged()
                        VaultCsvStagedReport(stagedRows = commit.inserted, warnings = commit.warnings)
                    } else {
                        vault.commitCsvImport(
                            reviewToken
                                ?: throw VaultOperationException(
                                    VaultFailure.BAD_HEADER,
                                    "Review the current mapping before importing.",
                                ),
                        )
                    }
                stateFlow.value =
                    stateFlow.value.copy(
                        step = ImportUiState.Step.Done,
                        committedRows = result.stagedRows,
                        recoveryPending = false,
                        recoverTo = null,
                    )
                discardStaging()
            } catch (failure: VaultOperationException) {
                stateFlow.value =
                    stateFlow.value.copy(
                        step = ImportUiState.Step.RecoverableError,
                        failure = failure.message,
                        recoveryPending = failure.failure == VaultFailure.RECOVERY_PENDING,
                        recoverTo = ImportUiState.Step.Reviewing,
                    )
            }
        }
    }

    public fun backToMapping() {
        if (!stateFlow.value.isBundle) {
            stateFlow.value = stateFlow.value.copy(step = ImportUiState.Step.Mapping, failure = null, recoverTo = null)
        }
    }

    public fun retry() {
        when (stateFlow.value.recoverTo) {
            ImportUiState.Step.Pick -> pendingUri?.let(::stagePickedUri) ?: chooseAnotherFile()
            ImportUiState.Step.Reading ->
                if (stateFlow.value.isBundle) {
                    stage { stateFlow.value = it.copy(step = ImportUiState.Step.Reviewing) }
                } else {
                    buildCsvPreview()
                }
            ImportUiState.Step.Mapping -> stage()
            ImportUiState.Step.Reviewing -> commit()
            else -> Unit
        }
    }

    /** R-S4: the corrected mapping persists (one global memory in v1). */
    private suspend fun remember(mapping: List<VaultCsvMappingView>) {
        val json =
            Json.encodeToString(
                mapping.map {
                    RememberedMapping(
                        column = it.sourceColumn,
                        kind = if (it.isDay) "day" else it.kind,
                        unit = it.unit,
                        customName = it.customName,
                    )
                },
            )
        settings.setRememberedCsvMapping(json)
    }

    public fun chooseAnotherFile() {
        discardStaging()
        pendingBytes = null
        pendingName = null
        pendingUri = null
        stateFlow.value = ImportUiState()
    }

    public fun reset() {
        chooseAnotherFile()
    }

    private fun discardStaging() {
        val path = stagingPath ?: return
        stagingPath = null
        savedStateHandle.remove<String>(KEY_STAGING_PATH)
        savedStateHandle.remove<String>(KEY_FILE_NAME)
        viewModelScope.launch { reader.discard(path) }
    }

    private fun recoverable(
        recoverTo: ImportUiState.Step,
        message: String,
    ) {
        stateFlow.value =
            stateFlow.value.copy(
                step = ImportUiState.Step.RecoverableError,
                failure = message,
                recoverTo = recoverTo,
            )
    }

    @Serializable
    private data class RememberedMapping(
        val column: String,
        val kind: String? = null,
        val unit: String? = null,
        val customName: String? = null,
    )

    private companion object {
        const val KEY_STAGING_PATH: String = "f13.import.stagingPath"
        const val KEY_FILE_NAME: String = "f13.import.fileName"
        const val MAX_IMPORT_BYTES: Int = 20 * 1024 * 1024
    }
}

public fun validateCsvMapping(mapping: List<CsvMappingRow>): String? {
    val dates = mapping.count { it.targetKind == "day" }
    if (dates != 1) return "Choose exactly one Date column. Dates must use YYYY-MM-DD."
    val measurements = mapping.filter { it.targetKind != null && it.targetKind != "day" }
    if (measurements.isEmpty()) return "Choose at least one measurement column."
    if (measurements.any { it.targetKind == "trend" }) return "Trend is derived by WLO and cannot be imported."
    val duplicates =
        measurements
            .groupBy { row -> row.customName?.let { "custom:$it" } ?: row.targetKind }
            .filterValues { rows -> rows.size > 1 }
    if (duplicates.isNotEmpty()) return "Each destination can be mapped only once."
    if (measurements.any { it.targetKind == "weight" && it.unit !in setOf("kg", "lb") }) {
        return "Choose kg or lb for every Weight column."
    }
    return null
}
