package app.wlo.feature.f13.vault.state

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
    public fun onDestinationPicked(
        destination: String,
        fileName: String,
        bytes: ByteArray,
    ) {
        viewModelScope.launch {
            try {
                val outcome = vault.writeToDestination(destination, fileName, bytes)
                stateFlow.value =
                    stateFlow.value.copy(
                        lastFileName = outcome.fileName,
                        lastSizeBytes = outcome.sizeBytes,
                        failure = null,
                    )
            } catch (failure: VaultOperationException) {
                stateFlow.value = stateFlow.value.copy(failure = failure.message)
            }
        }
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
) {
    public enum class Step {
        Pick,

        /** CSV only: map columns onto WLO's metric vocabulary (R-S4). */
        Mapping,

        /** The staged report (rows parsed / skipped with reasons). */
        Report,
        Applying,
        Done,
        Failed,
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
) : ViewModel() {
    private val stateFlow = MutableStateFlow(ImportUiState())

    public val state: StateFlow<ImportUiState> = stateFlow.asStateFlow()

    private var pendingBytes: ByteArray? = null
    private var pendingName: String? = null

    public fun onFilePicked(
        fileName: String,
        bytes: ByteArray,
    ) {
        pendingBytes = bytes
        pendingName = fileName
        val looksLikeBundle = fileName.endsWith(".json", ignoreCase = true)
        stateFlow.value =
            ImportUiState(step = ImportUiState.Step.Mapping, fileName = fileName, isBundle = looksLikeBundle)
        if (looksLikeBundle) {
            // Bundles carry their own schema — straight to staging (the same
            // funnel as restore; the report is the mapping step's stand-in).
            stage { stateFlow.value = it.copy(step = ImportUiState.Step.Report) }
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
                stateFlow.value = stateFlow.value.copy(csvPreview = preview, mapping = rows)
            } catch (failure: VaultOperationException) {
                stateFlow.value = stateFlow.value.copy(step = ImportUiState.Step.Failed, failure = failure.message)
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
        val rows =
            stateFlow.value.mapping.map { row ->
                if (row.sourceColumn == column) {
                    row.copy(targetKind = targetKind, unit = unit, customName = customName)
                } else {
                    row
                }
            }
        stateFlow.value = stateFlow.value.copy(mapping = rows)
    }

    public fun stage() {
        stage { stateFlow.value = it }
    }

    private fun stage(onStaged: (ImportUiState) -> Unit) {
        val bytes = pendingBytes ?: return
        val isBundle = stateFlow.value.isBundle
        viewModelScope.launch {
            try {
                if (isBundle) {
                    // Bundle import = staged restore funnel; the vault applies
                    // it through commitStaged (the mapping UI never shows).
                    val report = stageBundle(bytes)
                    stateFlow.value =
                        stateFlow.value.copy(
                            staged = VaultCsvStagedReport(stagedRows = report.totalRows, warnings = report.warnings),
                            failure = null,
                            recoveryPending = false,
                        )
                } else {
                    val mapping =
                        stateFlow.value.mapping.mapNotNull { row ->
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
                    remember(mapping)
                    stateFlow.value = stateFlow.value.copy(staged = staged, failure = null, recoveryPending = false)
                }
                onStaged(stateFlow.value)
            } catch (failure: VaultOperationException) {
                stateFlow.value = stateFlow.value.copy(step = ImportUiState.Step.Failed, failure = failure.message)
            }
        }
    }

    private suspend fun stageBundle(bytes: ByteArray) = vault.stageBundleImport(bytes)

    public fun commit() {
        viewModelScope.launch {
            stateFlow.value = stateFlow.value.copy(step = ImportUiState.Step.Applying)
            try {
                val result =
                    if (stateFlow.value.isBundle) {
                        val commit = vault.commitStaged()
                        VaultCsvStagedReport(stagedRows = commit.inserted, warnings = commit.warnings)
                    } else {
                        vault.commitCsvImport()
                    }
                stateFlow.value =
                    stateFlow.value.copy(
                        step = ImportUiState.Step.Done,
                        committedRows = result.stagedRows,
                        recoveryPending = false,
                    )
            } catch (failure: VaultOperationException) {
                stateFlow.value =
                    stateFlow.value.copy(
                        step = ImportUiState.Step.Failed,
                        failure = failure.message,
                        recoveryPending = failure.failure == VaultFailure.RECOVERY_PENDING,
                    )
            }
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

    public fun reset() {
        pendingBytes = null
        pendingName = null
        stateFlow.value = ImportUiState()
    }

    @Serializable
    private data class RememberedMapping(
        val column: String,
        val kind: String? = null,
        val unit: String? = null,
        val customName: String? = null,
    )
}
