package app.wlo.feature.f04.shopping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloBadge
import app.wlo.core.designsystem.WloBadgeTone
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloIconAction
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSheet
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.WloStatusDot
import app.wlo.core.designsystem.WloSwitchRow
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
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WloSpacing.SCREEN),
        ) {
            WloScreenTitle(title = "Pantry", modifier = Modifier.testTag("f04-pantry-title"))
            Text(
                text = "what's in the house",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
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
                    // R-S5: the switch IS the setting — the row reports the
                    // persisted boolean directly (set semantics, not flip).
                    WloCard {
                        WloSwitchRow(
                            label = "Auto-deduct from the list",
                            checked = state.deductionEnabled,
                            onCheckedChange = { viewModel.onEvent(PantryEvent.ToggleDeduction(it)) },
                            modifier = Modifier.testTag("f04-deduction-toggle"),
                        )
                    }
                    PrimaryRow(
                        label = "Add stock",
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
                    WloCard(
                        modifier = Modifier.testTag("f04-use-soon"),
                        header = {
                            WloCardHeader(
                                title = "Use soon",
                                provenance = {
                                    Text(
                                        text = "${state.useSoon.size}",
                                        style = wloType.receipt,
                                        color = wloExtendedColors.textTertiary,
                                    )
                                },
                            )
                        },
                    ) {
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
                    WloCard(
                        modifier = Modifier.testTag("f04-low"),
                        header = { WloCardHeader(title = "Running low") },
                    ) {
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
                WloCard(
                    modifier = Modifier.testTag("f04-inventory"),
                    header = {
                        WloCardHeader(
                            title = "Inventory",
                            provenance = {
                                val count = state.stock.size + state.useSoon.size + state.low.size
                                Text(
                                    text = if (count == 1) "1 item" else "$count items",
                                    style = wloType.receipt,
                                    color = wloExtendedColors.textTertiary,
                                )
                            },
                        )
                    },
                ) {
                    if (state.stock.isEmpty() && state.useSoon.isEmpty() && state.low.isEmpty()) {
                        Text(
                            text = "Nothing on the shelves yet — add stock by hand or sweep it in from the list.",
                            style = wloType.caption,
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
                                "Partial-stock deduction is on — staples come off generated lists " +
                                    "(\"need 5, have 3 → buy 2\").",
                            style = wloType.receipt,
                            color = wloExtendedColors.textTertiary,
                            modifier = Modifier.testTag("f04-deduction-on-word"),
                        )
                    } else {
                        Text(
                            text =
                                "Partial-stock deduction is off " +
                                    "(the default) — generated lists assume empty shelves.",
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
                WloBadge(text = "Staple", tone = WloBadgeTone.Accent)
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
            WloIconAction(
                imageVector = PantryRowGlyphs.Used,
                contentDescription = "Mark used",
                onClick = onDeduct,
                modifier = Modifier.testTag("f04-deduct-${row.name}"),
            )
            WloIconAction(
                imageVector = if (row.staple) PantryRowGlyphs.Stapled else PantryRowGlyphs.Staple,
                contentDescription = if (row.staple) "Unmark staple" else "Mark as staple",
                onClick = onStaple,
            )
            WloIconAction(
                imageVector = if (row.outOfStock) PantryRowGlyphs.StockIn else PantryRowGlyphs.StockOut,
                contentDescription = if (row.outOfStock) "Back in stock" else "Out of stock",
                onClick = onOutOfStock,
            )
        }
    }

/** Row-action glyphs — hand-built vectors, the [app.wlo.core.designsystem.WloIcons] idiom (no icon font). */
private object PantryRowGlyphs {
    /** Paint source for all glyph paths; `Icon(tint = ...)` recolors at render. */
    private val Ink: SolidColor = SolidColor(Color.Black)

    private inline fun glyph(
        name: String,
        builder: ImageVector.Builder.() -> ImageVector.Builder,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 12f,
                viewportHeight = 12f,
            ).builder()
            .build()

    private fun PathBuilder.star(): PathBuilder =
        apply {
            moveTo(6f, 1.6f)
            lineTo(7.5f, 4.7f)
            lineTo(10.9f, 5.1f)
            lineTo(8.4f, 7.4f)
            lineTo(9f, 10.8f)
            lineTo(6f, 9.2f)
            lineTo(3f, 10.8f)
            lineTo(3.6f, 7.4f)
            lineTo(1.1f, 5.1f)
            lineTo(4.5f, 4.7f)
            close()
        }

    /** A stroked circle, four arcs (r 4.4 about the 12×12 center). */
    private fun PathBuilder.circle(): PathBuilder =
        apply {
            moveTo(6f, 1.6f)
            curveTo(8.43f, 1.6f, 10.4f, 3.57f, 10.4f, 6f)
            curveTo(10.4f, 8.43f, 8.43f, 10.4f, 6f, 10.4f)
            curveTo(3.57f, 10.4f, 1.6f, 8.43f, 1.6f, 6f)
            curveTo(1.6f, 3.57f, 3.57f, 1.6f, 6f, 1.6f)
            close()
        }

    /** Mark used — a check. */
    public val Used: ImageVector =
        glyph("PantryUsed") {
            path(
                stroke = Ink,
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(2.6f, 6.5f)
                lineTo(5.1f, 9f)
                lineTo(9.4f, 3.4f)
            }
        }

    /** Not a staple — the outline star. */
    public val Staple: ImageVector =
        glyph("PantryStaple") {
            path(
                stroke = Ink,
                strokeLineWidth = 1.2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                star()
            }
        }

    /** A staple — the filled star. */
    public val Stapled: ImageVector =
        glyph("PantryStapled") {
            path(fill = Ink) {
                star()
            }
        }

    /** Out of stock — the slashed circle. */
    public val StockOut: ImageVector =
        glyph("PantryStockOut") {
            path(
                stroke = Ink,
                strokeLineWidth = 1.2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                circle()
            }
            path(
                stroke = Ink,
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(3.2f, 8.8f)
                lineTo(8.8f, 3.2f)
            }
        }

    /** Back in stock — the checked circle. */
    public val StockIn: ImageVector =
        glyph("PantryStockIn") {
            path(
                stroke = Ink,
                strokeLineWidth = 1.2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                circle()
            }
            path(
                stroke = Ink,
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.1f, 6.2f)
                lineTo(5.5f, 7.6f)
                lineTo(8f, 4.6f)
            }
        }
}

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
    LaunchedEffect(match) {
        match?.name?.takeIf { it.isNotBlank() }?.let { matched -> name = matched }
    }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("x") }
    var expiry by remember { mutableStateOf<Long?>(null) }
    var staple by remember { mutableStateOf(false) }
    val units = listOf("x", "g", "kg", "ml", "l")

    WloSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("f04-checkin-sheet")) {
        WloCardHeader(title = "Stock-take")
        Row(
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                modifier = Modifier.weight(1f).testTag("f04-checkin-barcode"),
                label = { Text("Barcode", style = wloType.label) },
                singleLine = true,
                textStyle = wloType.statS,
            )
            WloSecondaryButton(
                label = "Match",
                onClick = { onBarcode(barcode) },
                modifier = Modifier.testTag("f04-checkin-match-btn"),
            )
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
        OutlinedTextField(
            value = qty,
            onValueChange = { qty = it },
            modifier = Modifier.fillMaxWidth().testTag("f04-checkin-qty"),
            label = { Text("how much", style = wloType.label) },
            singleLine = true,
            textStyle = wloType.statS,
        )
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
            label = "Save to the pantry",
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
        Spacer(Modifier.heightIn(WloSpacing.ROW_MIN))
    }
}

@Composable
private fun DeductSheet(
    row: PantryRowUi,
    onDismiss: () -> Unit,
    onDeduct: (Double) -> Unit,
) {
    var qty by remember { mutableStateOf("1") }
    WloSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("f04-deduct-sheet")) {
        WloCardHeader(title = "Mark used · ${row.name}")
        Text(
            text = "Takes ${row.qtyLabel} off the shelf.",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
        OutlinedTextField(
            value = qty,
            onValueChange = { qty = it },
            modifier = Modifier.fillMaxWidth().testTag("f04-deduct-qty"),
            label = { Text("how much", style = wloType.label) },
            singleLine = true,
            textStyle = wloType.statS,
        )
        PrimaryRow(
            label = "Used",
            modifier = Modifier.testTag("f04-deduct-run"),
            onClick = { onDeduct(qty.toDoubleOrNull() ?: 0.0) },
        )
        Spacer(Modifier.heightIn(WloSpacing.ROW_MIN))
    }
}
