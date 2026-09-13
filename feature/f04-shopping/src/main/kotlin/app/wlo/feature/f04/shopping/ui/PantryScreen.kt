package app.wlo.feature.f04.shopping.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStatusDot
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f04.shopping.state.PantryEvent
import app.wlo.feature.f04.shopping.state.PantryRowUi
import app.wlo.feature.f04.shopping.state.PantryUiState
import app.wlo.feature.f04.shopping.state.PantryViewModel

/**
 * The pantry (F04 §4): the "what's in the house" ground truth — the use-soon
 * band (≤3 days, one amber dot, no alarm), the running-low band, the stock
 * rows with staple marks and expiry words, the typed check-in (barcode match
 * through the shared food-db door, typed-name fallback — the camera is never
 * required), the manual "mark used" deduction, and the R-S5 toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PantryScreen(
    viewModel: PantryViewModel,
    modifier: Modifier = Modifier,
) {
    val state: PantryUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val match: PantryViewModel.CheckInMatch? by viewModel.checkInMatch.collectAsStateWithLifecycle()
    val today: Long = remember { viewModel.todayEpochDay() }
    var showAdd by remember { mutableStateOf(false) }
    var deducting by remember { mutableStateOf<PantryRowUi?>(null) }

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
                    text = "Pantry",
                    style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
                    modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("f04-pantry-title"),
                )
                Text(
                    text = "what's in the house",
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
            DeductionToggle(
                enabled = state.deductionEnabled,
                onToggle = { viewModel.onEvent(PantryEvent.ToggleDeduction(it)) },
            )
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
                    state.notice?.let {
                        Text(
                            text = it,
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.testTag("f04-pantry-notice"),
                        )
                    }
                    PrimaryRow(
                        label = "+ add stock / check in",
                        modifier = Modifier.testTag("f04-pantry-add"),
                        onClick = { showAdd = true },
                    )
                    match?.let { m ->
                        Text(
                            text = m.source,
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.testTag("f04-checkin-match"),
                        )
                    }
                }
            }

            if (state.useSoon.isNotEmpty()) {
                item(key = "use-soon") {
                    WloCard(modifier = Modifier.testTag("f04-use-soon")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "use soon",
                                style = wloType.label,
                                color = wloExtendedColors.textTertiary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${state.useSoon.size}",
                                style = wloType.receipt,
                                color = wloExtendedColors.textTertiary,
                            )
                        }
                        for (row in state.useSoon) {
                            PantryRow(
                                row = row,
                                onDeduct = { deducting = row },
                                onStaple = { viewModel.onEvent(PantryEvent.SetStaple(row.groceryItemId, !row.staple)) },
                                onOutOfStock = {
                                    viewModel.onEvent(
                                        PantryEvent.SetOutOfStock(
                                            row.groceryItemId,
                                            !row.outOfStock,
                                        ),
                                    )
                                },
                            )
                        }
                        Text(
                            text = "one action each: mark used, or let the next list generation plan around it",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                }
            }

            if (state.low.isNotEmpty()) {
                item(key = "low") {
                    WloCard(modifier = Modifier.testTag("f04-low")) {
                        Text(
                            text = "running low",
                            style = wloType.label,
                            color = wloExtendedColors.textTertiary,
                        )
                        for (row in state.low) {
                            PantryRow(
                                row = row,
                                onDeduct = { deducting = row },
                                onStaple = { viewModel.onEvent(PantryEvent.SetStaple(row.groceryItemId, !row.staple)) },
                                onOutOfStock = {
                                    viewModel.onEvent(
                                        PantryEvent.SetOutOfStock(
                                            row.groceryItemId,
                                            !row.outOfStock,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item(key = "inventory") {
                WloCard(modifier = Modifier.testTag("f04-inventory")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "inventory",
                            style = wloType.label,
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${state.stock.size + state.useSoon.size + state.low.size} item(s)",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                        )
                    }
                    if (state.stock.isEmpty() && state.useSoon.isEmpty() && state.low.isEmpty()) {
                        Text(
                            text = "nothing on the shelves yet — add stock by hand or sweep it in from the list.",
                            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    for (row in state.stock) {
                        PantryRow(
                            row = row,
                            onDeduct = { deducting = row },
                            onStaple = { viewModel.onEvent(PantryEvent.SetStaple(row.groceryItemId, !row.staple)) },
                            onOutOfStock = {
                                viewModel.onEvent(
                                    PantryEvent.SetOutOfStock(
                                        row.groceryItemId,
                                        !row.outOfStock,
                                    ),
                                )
                            },
                        )
                    }
                    if (state.deductionEnabled) {
                        Text(
                            text =
                                "partial-stock deduction is on — staples come off generated lists " +
                                    "(\"need 5, have 3 → buy 2\")",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.testTag("f04-deduction-on-word"),
                        )
                    } else {
                        Text(
                            text =
                                "partial-stock deduction is off " +
                                    "(the default) — generated lists assume empty shelves",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.testTag("f04-deduction-off-word"),
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        CheckInSheet(
            match = match,
            today = today,
            suggestions = state.suggestions,
            onDismiss = {
                showAdd = false
                viewModel.checkInMatch.value = null
            },
            onBarcode = { viewModel.onEvent(PantryEvent.CheckInBarcode(it)) },
            onSave = { name, qty, unit, expiry, staple ->
                viewModel.onEvent(PantryEvent.Upsert(name, qty, unit, expiry, staple))
                viewModel.checkInMatch.value = null
                showAdd = false
            },
        )
    }

    deducting?.let { row ->
        DeductSheet(
            row = row,
            onDismiss = { deducting = null },
            onDeduct = { qty ->
                viewModel.onEvent(PantryEvent.Deduct(row.groceryItemId, qty))
                deducting = null
            },
        )
    }
}

@Composable
private fun DeductionToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
): Unit =
    Surface(
        onClick = { onToggle(!enabled) },
        modifier = Modifier.testTag("f04-deduction-toggle"),
        shape = WloShape.Chip,
        color =
            if (enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        contentColor = if (enabled) MaterialTheme.colorScheme.primary else wloExtendedColors.textTertiary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = if (enabled) "deduction on" else "deduction off",
            style = wloType.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }

@Composable
private fun PantryRow(
    row: PantryRowUi,
    onDeduct: () -> Unit,
    onStaple: () -> Unit,
    onOutOfStock: () -> Unit,
): Unit =
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = WloSpacing.ROW_MIN)
                .padding(vertical = WloSpacing.TIGHT)
                .testTag("f04-pantry-row-${row.name}"),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            if (row.useSoon) {
                WloStatusDot(color = wloExtendedColors.held)
            } else if (row.low) {
                WloStatusDot(color = wloExtendedColors.neutralDelta)
            }
            Text(
                text = row.name,
                style = wloType.body,
                textDecoration = if (row.outOfStock) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            if (row.staple) {
                Text(
                    text = "staple",
                    style = wloType.label,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), WloShape.Chip)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(
                text = row.qtyLabel,
                style = wloType.statS,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        ) {
            row.expiryWord?.let {
                Text(
                    text = it,
                    style = wloType.receipt,
                    color = if (row.useSoon) wloExtendedColors.held else wloExtendedColors.textTertiary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "mark used",
                style = wloType.label,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickableRow(onDeduct, "f04-deduct-${row.name}"),
            )
            Text(
                text = if (row.staple) "unmark staple" else "staple",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.clickableRow(onStaple, null),
            )
            Text(
                text = if (row.outOfStock) "back in stock" else "out of stock",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.clickableRow(onOutOfStock, null),
            )
        }
    }

private fun Modifier.clickableRow(
    onClick: () -> Unit,
    tag: String?,
): Modifier =
    this
        .clickable(onClick = onClick)
        .let { state -> if (tag != null) state.testTag(tag) else state }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckInSheet(
    match: PantryViewModel.CheckInMatch?,
    today: Long,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onBarcode: (String) -> Unit,
    onSave: (String, Double, String, Long?, Boolean) -> Unit,
) {
    var barcode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(match?.name.orEmpty()) }
    androidx.compose.runtime.LaunchedEffect(match) {
        match?.name?.takeIf { it.isNotBlank() }?.let { matched -> name = matched }
    }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("x") }
    var expiry by remember { mutableStateOf<Long?>(null) }
    var staple by remember { mutableStateOf(false) }
    val units = listOf("x", "g", "kg", "ml", "l")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = WloShape.SheetTop,
        modifier = Modifier.testTag("f04-checkin-sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        ) {
            Text(text = "Check in / stock-take", style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.12f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    modifier = Modifier.weight(1f).testTag("f04-checkin-barcode"),
                    label = { Text("barcode (typed — the camera path lives in capture)", style = wloType.label) },
                    singleLine = true,
                    textStyle = wloType.body.copy(fontFeatureSettings = "tnum"),
                )
                Surface(
                    onClick = { onBarcode(barcode) },
                    modifier = Modifier.testTag("f04-checkin-match-btn"),
                    shape = WloShape.Chip,
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Text(
                        text = "match",
                        style = wloType.label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().testTag("f04-checkin-name"),
                label = { Text("item name", style = wloType.label) },
                singleLine = true,
                textStyle = wloType.body,
            )
            if (suggestions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                    suggestions.take(3).forEach { candidate ->
                        SelectChip(label = candidate, selected = false, onClick = { name = candidate })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                OutlinedTextField(
                    value = qty,
                    onValueChange = { qty = it },
                    modifier = Modifier.weight(1f).testTag("f04-checkin-qty"),
                    label = { Text("how much", style = wloType.label) },
                    singleLine = true,
                    textStyle = wloType.body.copy(fontFeatureSettings = "tnum"),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                modifier = Modifier.testTag("f04-checkin-units"),
            ) {
                units.forEach { candidate ->
                    SelectChip(label = candidate, selected = candidate == unit, onClick = { unit = candidate })
                }
            }
            Text(text = "expiry (fresh items)", style = wloType.label, color = wloExtendedColors.textTertiary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                modifier = Modifier.testTag("f04-checkin-expiry"),
            ) {
                SelectChip(label = "no date", selected = expiry == null, onClick = { expiry = null })
                SelectChip(label = "+3 d", selected = expiry == today + 3, onClick = { expiry = today + 3 })
                SelectChip(label = "+7 d", selected = expiry == today + 7, onClick = { expiry = today + 7 })
            }
            SelectChip(
                label = "staple",
                selected = staple,
                onClick = { staple = !staple },
                modifier = Modifier.testTag("f04-checkin-staple"),
            )
            PrimaryRow(
                label = "save to the pantry",
                modifier = Modifier.testTag("f04-checkin-save"),
                onClick = { onSave(name.trim(), qty.toDoubleOrNull() ?: 0.0, unit, expiry, staple) },
            )
            match?.let {
                Text(
                    text = it.source,
                    style = wloType.receipt,
                    color = wloExtendedColors.textTertiary,
                )
            }
            Text(
                text = "the typed path is first-class — the scanner rides the shared capture stack when you want it",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
            Spacer(Modifier.height(WloSpacing.ROW_MIN))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeductSheet(
    row: PantryRowUi,
    onDismiss: () -> Unit,
    onDeduct: (Double) -> Unit,
) {
    var qty by remember { mutableStateOf("1") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = WloShape.SheetTop,
        modifier = Modifier.testTag("f04-deduct-sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
            verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
        ) {
            Text(
                text = "Mark used · ${row.name}",
                style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.12f),
            )
            Text(
                text = "takes ${row.qtyLabel} off the shelf — never below zero, never a guess across units",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
            OutlinedTextField(
                value = qty,
                onValueChange = { qty = it },
                modifier = Modifier.fillMaxWidth().testTag("f04-deduct-qty"),
                label = { Text("how much", style = wloType.label) },
                singleLine = true,
                textStyle = wloType.body.copy(fontFeatureSettings = "tnum"),
            )
            PrimaryRow(
                label = "used",
                modifier = Modifier.testTag("f04-deduct-run"),
                onClick = { onDeduct(qty.toDoubleOrNull() ?: 0.0) },
            )
            Spacer(Modifier.height(WloSpacing.ROW_MIN))
        }
    }
}
