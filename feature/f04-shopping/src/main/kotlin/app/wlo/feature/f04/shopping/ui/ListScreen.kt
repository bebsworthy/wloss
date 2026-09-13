package app.wlo.feature.f04.shopping.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCheckRow
import app.wlo.core.designsystem.WloPrimaryRow
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f04.shopping.state.ListEvent
import app.wlo.feature.f04.shopping.state.ListItemUi
import app.wlo.feature.f04.shopping.state.ListUiState
import app.wlo.feature.f04.shopping.state.ListViewModel
import kotlinx.coroutines.launch

/**
 * The shopping list (F04 §4's in-store surface): aisle groups in the shipped
 * taxonomy order, 48 dp check rows with haptics, the reconciliation banner
 * ("your checks are safe"), the R-S5 prompt, sweep-to-pantry, and the
 * text/CSV/JSON exports with the CSV round-trip. Offline, forever.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ListScreen(
    viewModel: ListViewModel,
    modifier: Modifier = Modifier,
) {
    val state: ListUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    // SAF create-document writers (the lean, permission-free export path).
    val csvWriter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                viewModel.exportCsv()?.let { content -> writeAndShare(context, uri, content, "text/csv") }
            }
        }
    val jsonWriter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                viewModel.exportJson()?.let { content -> writeAndShare(context, uri, content, "application/json") }
            }
        }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WloSpacing.SCREEN),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Shopping",
                    style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
                    modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("f04-list-title"),
                )
                Text(
                    text = state.weekLabel?.let { "for the week of $it" } ?: "no plan behind the list yet",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
            state.planClaim?.let { claim ->
                ProvenanceChip(value = claim, format = { "%,d kcal planned".format(it.toInt()) })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(WloSpacing.SCREEN),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN)) {
                    state.prompt?.let { prompt ->
                        DeductionPromptCard(
                            prompt = prompt,
                            enabled = state.deductionEnabled,
                            onAnswer = { viewModel.onEvent(ListEvent.AnswerPrompt(it)) },
                        )
                    }

                    state.banner?.let { banner ->
                        ReconciliationBanner(banner, onDismiss = { viewModel.onEvent(ListEvent.DismissBanner) })
                    }

                    state.notice?.let { notice ->
                        Text(
                            text = notice,
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.testTag("f04-list-notice"),
                        )
                    }

                    PrimaryRow(
                        label = if (state.busy) "building…" else "build list · week pre-selected",
                        modifier = Modifier.testTag("f04-generate"),
                        enabled = !state.busy,
                        onClick = { viewModel.onEvent(ListEvent.Generate) },
                    )

                    RowCard(state)

                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                        PrimaryRow(
                            label = "+ add item",
                            modifier = Modifier.weight(1f).testTag("f04-add-open"),
                            onClick = { showAdd = true },
                        )
                        PrimaryRow(
                            label = "import csv",
                            modifier = Modifier.weight(1f).testTag("f04-import-open"),
                            onClick = { showImport = true },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                        PrimaryRow(
                            label = "share text",
                            modifier = Modifier.weight(1f).testTag("f04-export-text"),
                            onClick = {
                                scope.launch {
                                    val text = viewModel.exportText() ?: return@launch
                                    shareText(context, "WLO shopping list", text)
                                }
                            },
                        )
                        PrimaryRow(
                            label = "export csv",
                            modifier = Modifier.weight(1f).testTag("f04-export-csv"),
                            onClick = { csvWriter.launch("wlo-shopping-list.csv") },
                        )
                        PrimaryRow(
                            label = "json",
                            modifier = Modifier.weight(1f).testTag("f04-export-json"),
                            onClick = { jsonWriter.launch("wlo-shopping-list.json") },
                        )
                    }
                }
            }

            if (state.groups.isEmpty() && state.checked.isEmpty() && state.removed.isEmpty()) {
                item {
                    WloCard(modifier = Modifier.testTag("f04-empty")) {
                        Text(text = "the list is empty", style = wloType.title)
                        Text(
                            text = "build it from the week's plan, or add items by hand — both work offline.",
                            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            for (group in state.groups) {
                item(key = "aisle-${group.aisleWord}") {
                    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        Row(modifier = Modifier.testTag("f04-aisle-${group.aisleWord}")) {
                            Text(
                                text = group.aisleWord,
                                style = wloType.label,
                                color = wloExtendedColors.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${group.rows.size}",
                                style = wloType.receipt,
                                color = wloExtendedColors.textTertiary,
                            )
                        }
                        for (row in group.rows) {
                            CheckableRow(row) { checked -> viewModel.onEvent(ListEvent.Check(row.id, checked)) }
                        }
                    }
                }
            }

            if (state.checked.isNotEmpty()) {
                item(key = "checked-header") {
                    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        Row(modifier = Modifier.testTag("f04-checked-header")) {
                            Text(
                                text = "in the trolley",
                                style = wloType.label,
                                color = wloExtendedColors.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${state.checkedCount}",
                                style = wloType.receipt,
                                color = wloExtendedColors.textTertiary,
                            )
                        }
                        PrimaryRow(
                            label = "sweep ${state.checkedCount} to pantry",
                            modifier = Modifier.testTag("f04-sweep"),
                            onClick = { viewModel.onEvent(ListEvent.SweepChecked) },
                        )
                        Text(
                            text = "purchased amounts land in stock — editable in the pantry",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                        for (row in state.checked) {
                            CheckableRow(row) { checked -> viewModel.onEvent(ListEvent.Check(row.id, checked)) }
                        }
                    }
                }
            }

            if (state.removed.isNotEmpty()) {
                item(key = "removed-header") {
                    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                        Text(
                            text = "struck through by the plan — recoverable",
                            style = wloType.label,
                            color = wloExtendedColors.textTertiary,
                        )
                        for (row in state.removed) {
                            Surface(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .testTag("f04-restore-${row.id}"),
                                shape = WloShape.Chip,
                                color = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                onClick = { viewModel.onEvent(ListEvent.Restore(row.id)) },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = WloSpacing.CARD, vertical = WloSpacing.TIGHT),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = row.name,
                                            style = wloType.body,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                        )
                                        Text(
                                            text = "${row.qtyLabel} · tap to put it back",
                                            style = wloType.receipt,
                                            color = wloExtendedColors.textTertiary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddItemSheet(
            onDismiss = { showAdd = false },
            onAdd = { name, qty, unit ->
                viewModel.onEvent(ListEvent.Add(name, qty, unit))
                showAdd = false
            },
        )
    }
    if (showImport) {
        ImportSheet(
            onDismiss = { showImport = false },
            onImport = { csv ->
                viewModel.onEvent(ListEvent.ImportCsv(csv))
                showImport = false
            },
        )
    }
}

@Composable
private fun CheckableRow(
    row: ListItemUi,
    onToggle: (Boolean) -> Unit,
): Unit =
    WloCheckRow(
        name = row.name,
        qtyLabel = row.qtyLabel,
        checked = row.checked,
        onToggle = { onToggle(!row.checked) },
        subLabel = row.subLabel,
        deltaLabel = row.deltaLabel,
        modifier = Modifier.testTag("f04-item-${row.name}"),
    )

@Composable
private fun RowCard(state: ListUiState): Unit =
    WloCard(modifier = Modifier.testTag("f04-counts-card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "to buy · ${state.toBuyCount}",
                style = wloType.body,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "in the trolley · ${state.checkedCount}",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
        if (state.deductionEnabled) {
            Text(
                text = "pantry deduction on — staples you already have come off the totals",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
    }

@Composable
private fun ReconciliationBanner(
    banner: app.wlo.feature.f04.shopping.state.ReconciliationUi,
    onDismiss: () -> Unit,
): Unit =
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("f04-banner"),
        shape = WloShape.Chip,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Column(Modifier.padding(WloSpacing.CARD), verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            Text(
                text = "Plan changed: ${banner.headline}. Your checks are safe.",
                style = wloType.body,
                modifier = Modifier.testTag("f04-banner-text"),
            )
            Text(
                text =
                    "${banner.checksKept} ticked item(s) stayed ticked — " +
                        "the list reconciles by delta, never a reset.",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                PrimaryRow(label = "got it", modifier = Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }

/** The R-S5 one-time prompt: the default stays OFF; the choice is remembered. */
@Composable
private fun DeductionPromptCard(
    prompt: app.wlo.feature.f04.shopping.state.DeductionPromptUi,
    enabled: Boolean,
    onAnswer: (Boolean) -> Unit,
): Unit =
    WloCard(modifier = Modifier.testTag("f04-rs5-prompt")) {
        Text(text = "Deduct what you have at home?", style = wloType.title)
        Text(
            text =
                "when on, staples in your pantry (${prompt.staplesWord}) come off every generated list — " +
                    "\"need 5 eggs, have 3 → buy 2\". Off by default so deductions never " +
                    "surprise; flip it in the pantry anytime.",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
            PrimaryRow(
                label = if (enabled) "on — keep it on" else "stay off",
                modifier = Modifier.weight(1f).testTag("f04-rs5-stay-off"),
                onClick = { onAnswer(false) },
            )
            PrimaryRow(
                label = "turn it on",
                modifier = Modifier.weight(1f).testTag("f04-rs5-turn-on"),
                onClick = { onAnswer(true) },
            )
        }
        Text(
            text = "your choice is remembered; this asks once.",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemSheet(
    onDismiss: () -> Unit,
    onAdd: (String, Double, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    val units = listOf("x", "g", "kg", "ml", "l", "cup", "tbsp", "tsp")
    var unit by remember { mutableStateOf("x") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = WloShape.SheetTop,
        modifier = Modifier.testTag("f04-add-sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        ) {
            Text(text = "Add item", style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.12f))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().testTag("f04-add-name"),
                label = { Text("name", style = wloType.label) },
                singleLine = true,
                textStyle = wloType.body,
            )
            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it },
                modifier = Modifier.fillMaxWidth().testTag("f04-add-qty"),
                label = { Text("how much", style = wloType.label) },
                singleLine = true,
                textStyle = wloType.body.copy(fontFeatureSettings = "tnum"),
            )
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                modifier = Modifier.testTag("f04-add-units"),
            ) {
                items(units) { candidate ->
                    app.wlo.core.designsystem.SelectChip(
                        label = candidate,
                        selected = candidate == unit,
                        onClick = { unit = candidate },
                    )
                }
            }
            PrimaryRow(
                label = "add to the list",
                modifier = Modifier.testTag("f04-add-save"),
                onClick = { onAdd(name.trim(), qty.toDoubleOrNull() ?: 1.0, unit) },
            )
            Text(
                text = "the aisle is guessed on add — one correction teaches it forever",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
            Spacer(Modifier.height(WloSpacing.ROW_MIN))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var csv by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = WloShape.SheetTop,
        modifier = Modifier.testTag("f04-import-sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        ) {
            Text(text = "Import CSV", style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.12f))
            Text(
                text = "the app's own export format round-trips: item,qty,unit,aisle,state",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
            OutlinedTextField(
                value = csv,
                onValueChange = { csv = it },
                modifier = Modifier.fillMaxWidth().testTag("f04-import-text"),
                label = { Text("paste the CSV", style = wloType.label) },
                textStyle = wloType.receipt,
                minLines = 4,
            )
            PrimaryRow(
                label = "import",
                modifier = Modifier.testTag("f04-import-run"),
                onClick = { onImport(csv) },
            )
            Spacer(Modifier.height(WloSpacing.ROW_MIN))
        }
    }
}

/** The share sheet (F04 §9: "a format, not a partnership"). */
internal fun shareText(
    context: Context,
    subject: String,
    body: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    context.startActivity(Intent.createChooser(intent, null))
}

private suspend fun writeAndShare(
    context: Context,
    uri: android.net.Uri,
    content: String,
    mime: String,
) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        }
    }
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, null))
}

/** Local alias — the shared atom lives in :core:designsystem (single owner). */
@Composable
internal fun PrimaryRow(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Unit = WloPrimaryRow(label = label, modifier = modifier, enabled = enabled, onClick = onClick)
