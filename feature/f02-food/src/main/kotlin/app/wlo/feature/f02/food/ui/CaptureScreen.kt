package app.wlo.feature.f02.food.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloHaptics
import app.wlo.core.designsystem.WloShape
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.rememberWloHaptics
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.engines.FoodRails
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Provenance
import app.wlo.core.ports.MissReason
import app.wlo.feature.f02.food.domain.ScanCorrectionLoop
import app.wlo.feature.f02.food.state.CaptureEvent
import app.wlo.feature.f02.food.state.CaptureLensMode
import app.wlo.feature.f02.food.state.CaptureStage
import app.wlo.feature.f02.food.state.CaptureUiState
import app.wlo.feature.f02.food.state.CaptureViewModel
import app.wlo.feature.f02.food.state.FoodHitUi
import app.wlo.feature.f02.food.state.NoticeState
import app.wlo.feature.f02.food.state.userCopy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun CaptureScreen(
    viewModel: CaptureViewModel,
    viewfinder: CaptureViewfinderSlot,
    onOpenManualLadder: () -> Unit,
    onOpenModelManager: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberWloHaptics()
    val shutter = remember { CaptureShutter() }

    // The barcode loop arms whenever the barcode viewfinder is frontmost.
    LaunchedEffect(state.mode, state.stage) {
        if (state.mode == CaptureLensMode.BARCODE && state.stage is CaptureStage.Viewfinder) {
            viewModel.onEvent(CaptureEvent.StartScanning)
        } else {
            viewModel.onEvent(CaptureEvent.StopScanning)
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(
            text = "Capture",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier =
                Modifier
                    .padding(top = WloSpacing.SCREEN)
                    .testTag("f02-capture-title"),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT), modifier = Modifier.fillMaxWidth()) {
            for (mode in CaptureLensMode.entries) {
                SelectChip(
                    label = modeLabel(mode),
                    selected = mode == state.mode,
                    onClick = { viewModel.onEvent(CaptureEvent.ModeChange(mode)) },
                )
            }
        }

        when (val stage = state.stage) {
            is CaptureStage.Viewfinder ->
                ViewfinderStage(state, viewModel, viewfinder, shutter, haptics, onOpenManualLadder)

            CaptureStage.Analyzing ->
                WloCard(Modifier.testTag("f02-capture-analyzing")) {
                    Text("reading it…", style = wloType.title)
                    Text(
                        "on-device, nothing leaves your phone",
                        style = wloType.label,
                        color = wloExtendedColors.textTertiary,
                    )
                }

            is CaptureStage.Result ->
                ResultStage(stage, viewModel, haptics, onOpenManualLadder)

            is CaptureStage.MissingModel ->
                WloCard(Modifier.testTag("f02-capture-missing-model")) {
                    Text("the food recognizer isn't on this device yet", style = wloType.title)
                    Text(
                        stage.reason,
                        style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                        color = wloExtendedColors.textTertiary,
                    )
                    ActionRow(
                        label = "open the model manager",
                        modifier = Modifier.fillMaxWidth().testTag("f02-capture-open-zoo"),
                    ) { onOpenModelManager() }
                    ActionRow(
                        label = "type it in instead",
                        modifier = Modifier.fillMaxWidth().testTag("f02-capture-manual"),
                    ) { onOpenManualLadder() }
                }

            is CaptureStage.Product -> ProductStage(stage, viewModel)

            is CaptureStage.ProductMiss ->
                WloCard(Modifier.testTag("f02-capture-product-miss")) {
                    Text("no product under ${stage.barcode}", style = wloType.title)
                    Text(
                        stage.reason.userCopy(),
                        style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                        color = wloExtendedColors.textTertiary,
                    )
                    ManualBarcodeField(viewModel)
                    ActionRow(
                        label = "search your catalog",
                        modifier = Modifier.fillMaxWidth().testTag("f02-capture-manual"),
                    ) { onOpenManualLadder() }
                }

            is CaptureStage.OcrDraft -> Unit // rendered as the sheet below

            CaptureStage.Saving ->
                WloCard(Modifier.testTag("f02-capture-saving")) { Text("saving…", style = wloType.title) }

            is CaptureStage.Saved ->
                WloCard(Modifier.testTag("f02-capture-saved")) {
                    Text("saved to the diary", style = wloType.title)
                    Text(
                        "${stage.entryCount} ${if (stage.entryCount == 1) "entry" else "entries"} · " +
                            "%,.0f kcal".format(stage.kcal),
                        style = wloType.statM,
                    )
                    Text(
                        "the photo stayed memory-only and is discarded at save (retention is opt-in, never default)",
                        style = wloType.label,
                        color = wloExtendedColors.textTertiary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ActionRow(
                            label = "scan next",
                            modifier = Modifier.weight(1f).testTag("f02-capture-again"),
                        ) {
                            haptics.perform(WloHaptic.Tick)
                            viewModel.onEvent(CaptureEvent.Retake)
                        }
                        ActionRow(
                            label = "done",
                            modifier = Modifier.weight(1f).testTag("f02-capture-done"),
                        ) {
                            haptics.perform(WloHaptic.Settle)
                            onDone()
                        }
                    }
                }
        }

        state.notice?.let {
            NoticeLine(
                it.text,
                rail = it.state == NoticeState.RAIL,
                onDismiss = { viewModel.onEvent(CaptureEvent.DismissNotice) },
                modifier = Modifier.testTag("f02-capture-notice"),
            )
        }
    }

    CaptureSearchSheet(state, viewModel)

    // The OCR draft: the SAME custom-food form the manual twin uses, opened
    // as a sheet (prefilled, confirm-gated — nothing saves without the tap).
    (state.stage as? CaptureStage.OcrDraft)?.let { stage ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(CaptureEvent.Retake) },
            shape = WloShape.SheetTop,
            modifier = Modifier.testTag("f02-capture-ocr-sheet"),
        ) {
            OcrDraftStage(stage, viewModel)
        }
    }
}

@Composable
private fun ViewfinderStage(
    state: CaptureUiState,
    viewModel: CaptureViewModel,
    viewfinder: CaptureViewfinderSlot,
    shutter: CaptureShutter,
    haptics: WloHaptics,
    onOpenManualLadder: () -> Unit,
) {
    viewfinder(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .testTag("f02-capture-viewfinder"),
        state.mode,
        shutter,
        { frame -> viewModel.onEvent(CaptureEvent.PreviewFrame(frame)) },
        state.viewfinderActive,
    )

    when (state.mode) {
        CaptureLensMode.PHOTO ->
            ShutterButton(Modifier.testTag("f02-capture-shutter")) {
                shutter.take { frame ->
                    haptics.perform(WloHaptic.Tick)
                    viewModel.onEvent(CaptureEvent.StillCaptured(frame))
                }
            }

        CaptureLensMode.LABEL ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            ) {
                Text(
                    "line the label up inside the brackets",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
                ShutterButton(Modifier.testTag("f02-capture-shutter")) {
                    shutter.take { frame ->
                        haptics.perform(WloHaptic.Tick)
                        viewModel.onEvent(CaptureEvent.StillCaptured(frame))
                    }
                }
                Text(
                    "values land in the SAME manual form — nothing saves until you confirm",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
            }

        CaptureLensMode.BARCODE -> ManualBarcodeField(viewModel)
    }

    ActionRow(
        label = "or type it in instead",
        modifier = Modifier.fillMaxWidth().testTag("f02-capture-manual"),
    ) { onOpenManualLadder() }
}

@Composable
private fun ShutterButton(
    modifier: Modifier = Modifier,
    onShutter: () -> Unit,
) {
    Surface(
        onClick = onShutter,
        modifier = modifier.size(72.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("scan", style = wloType.label)
        }
    }
}

/** R-U15 for barcodes: typing the number is an equal-status path. */
@Composable
private fun ManualBarcodeField(viewModel: CaptureViewModel) {
    var text by remember { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().testTag("f02-capture-barcode-field"),
        singleLine = true,
        label = { Text("…or type the barcode") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = {
            Text(
                "look up",
                style = wloType.label,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clickable(enabled = text.isNotBlank()) {
                            viewModel.onEvent(CaptureEvent.ManualBarcode(text.trim()))
                            text = ""
                        }.padding(WloSpacing.TIGHT),
            )
        },
    )
}

@Composable
private fun ResultStage(
    stage: CaptureStage.Result,
    viewModel: CaptureViewModel,
    haptics: WloHaptics,
    onOpenManualLadder: () -> Unit,
) {
    // Scan-level confidence ring text + the amber strip (F02 §4: < 0.5).
    val scanConfidence = stage.scanConfidence
    WloCard(Modifier.testTag("f02-capture-result")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "what we see", style = wloType.title, modifier = Modifier.weight(1f))
            if (scanConfidence != null) {
                val confidence =
                    DerivedValue(
                        value = scanConfidence,
                        provenance =
                            Provenance.Estimated(
                                at = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
                                method = "on-device classifier",
                                confidence = scanConfidence,
                                modelId = stage.modelId,
                                consentGranted = false,
                            ),
                    )
                ProvenanceChip(
                    value = confidence,
                    format = { "confidence %.0f%%".format(it * 100) },
                    modifier = Modifier.testTag("f02-capture-confidence"),
                )
            }
        }
        if (stage.held) {
            Text(
                "rough guess — adjust what's wrong",
                style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                color = wloExtendedColors.held,
                modifier = Modifier.testTag("f02-capture-held-strip"),
            )
        }
        Text(
            "estimates from food-classifier/1, on-device — tap a chip to fix it; nothing saves until you say so",
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
    }

    for (item in stage.visibleItems) {
        ScanItemCard(item, viewModel)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT), modifier = Modifier.fillMaxWidth()) {
        ActionRow(
            label = "add missed item",
            modifier = Modifier.weight(1f).testTag("f02-capture-add-item"),
        ) { viewModel.onEvent(CaptureEvent.OpenSwap(itemId = null)) }
        ActionRow(
            label = "type it instead",
            modifier = Modifier.weight(1f).testTag("f02-capture-manual"),
        ) { onOpenManualLadder() }
    }

    ActionRow(
        label = "save ${stage.visibleItems.size} to diary",
        modifier = Modifier.fillMaxWidth().testTag("f02-capture-save"),
    ) {
        haptics.perform(WloHaptic.Settle)
        viewModel.onEvent(CaptureEvent.Save)
    }
    Text(
        "the photo is discarded at save (opt-in retention is a setting you choose — never a default)",
        style = wloType.label,
        color = wloExtendedColors.textTertiary,
        modifier = Modifier.testTag("f02-capture-retention-note"),
    )
}

@Composable
private fun ScanItemCard(
    item: ScanCorrectionLoop.ScanItem,
    viewModel: CaptureViewModel,
) {
    WloCard(Modifier.testTag("f02-capture-item-${item.id}")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.displayLabel, style = wloType.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "%d%% · %s".format(
                        (item.confidence * 100).toInt(),
                        if (item.priorApplied) "your usual" else "model guess",
                    ),
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
            }
            val kcal =
                DerivedValue(
                    value = item.kcal,
                    provenance =
                        Provenance.Estimated(
                            at = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
                            method = "on-device classifier",
                            confidence = item.confidence,
                            modelId = CaptureViewModel.FOOD_CLASSIFIER_MODEL,
                            consentGranted = false,
                        ),
                )
            ProvenanceChip(
                value = kcal,
                format = { "%,.0f kcal".format(it) },
                modifier = Modifier.testTag("f02-capture-item-kcal-${item.id}"),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
        ) {
            OutlinedTextField(
                value = item.grams.toString(),
                onValueChange = { text -> viewModel.onEvent(CaptureEvent.ItemGramsChange(item.id, text)) },
                modifier = Modifier.weight(1f).testTag("f02-capture-item-grams-${item.id}"),
                singleLine = true,
                label = { Text("grams") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = wloType.statM,
            )
            ActionRow(
                label = "swap",
                modifier = Modifier.weight(1f).testTag("f02-capture-item-swap-${item.id}"),
            ) { viewModel.onEvent(CaptureEvent.OpenSwap(itemId = item.id)) }
            ActionRow(
                label = "not this",
                modifier = Modifier.weight(1f).testTag("f02-capture-item-remove-${item.id}"),
            ) { viewModel.onEvent(CaptureEvent.ItemRemove(item.id)) }
        }
        if (item.railTripped || item.densityClamped) {
            val copy =
                if (item.densityClamped) {
                    FoodRails.densityTripCopy(item.wireLabel, item.kcalPer100g)
                } else {
                    FoodRails.tripCopy(item.rail)
                }
            Text(
                copy,
                style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                color = wloExtendedColors.held,
                modifier = Modifier.testTag("f02-capture-item-rail-${item.id}"),
            )
        }
    }
}

@Composable
private fun ProductStage(
    stage: CaptureStage.Product,
    viewModel: CaptureViewModel,
) {
    val product = stage.product
    var gramsText by remember(product.barcode) { mutableStateOf("100") }
    WloCard(Modifier.testTag("f02-capture-product")) {
        Text(product.name, style = wloType.title)
        product.brand?.let { Text(it, style = wloType.label, color = wloExtendedColors.textTertiary) }
        if (stage.servedFromCache) {
            Text(
                "cached — no network just now",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.testTag("f02-capture-product-cached"),
            )
        }
        val grams = gramsText.toDoubleOrNull() ?: 0.0
        val per100 = product.kcalPer100g
        if (per100 != null) {
            val kcal =
                DerivedValue(
                    value = per100 * grams / 100.0,
                    provenance =
                        Provenance.Measured(
                            at = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
                            instrument = "Open Food Facts (ODbL)",
                        ),
                )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { gramsText = it },
                    modifier = Modifier.weight(1f).testTag("f02-capture-product-grams"),
                    singleLine = true,
                    label = { Text("grams") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = wloType.statM,
                )
                ProvenanceChip(
                    value = kcal,
                    format = { "%,.0f kcal".format(it) },
                    modifier = Modifier.testTag("f02-capture-product-kcal"),
                )
            }
        }
        MacroRow("protein", product.proteinGPer100g)
        MacroRow("carbs", product.carbGPer100g)
        MacroRow("fat", product.fatGPer100g)
        MacroRow("fiber", product.fiberGPer100g)
        product.servingSizeText?.let {
            Text("serving: $it", style = wloType.label, color = wloExtendedColors.textTertiary)
        }
        Text(
            product.attribution,
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
            modifier = Modifier.testTag("f02-capture-product-attribution"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT), modifier = Modifier.fillMaxWidth()) {
            ActionRow(
                label = "add to diary",
                modifier = Modifier.weight(1f).testTag("f02-capture-product-add"),
            ) { viewModel.onEvent(CaptureEvent.ProductAddToDiary(product, grams)) }
            ActionRow(
                label = "save as food",
                modifier = Modifier.weight(1f).testTag("f02-capture-product-save-food"),
            ) { viewModel.onEvent(CaptureEvent.ProductSaveAsFood(product)) }
        }
        ActionRow(
            label = "scan next",
            modifier = Modifier.fillMaxWidth().testTag("f02-capture-again"),
        ) { viewModel.onEvent(CaptureEvent.Retake) }
    }
}

@Composable
private fun MacroRow(
    label: String,
    value: Double?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = WloSpacing.ROW_MIN),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = wloType.body, modifier = Modifier.weight(1f))
        Text(
            text = value?.let { "${format(it)} g / 100 g" } ?: "—",
            style = wloType.receipt,
            color = wloExtendedColors.textTertiary,
        )
    }
}

private fun format(value: Double): String {
    val whole = value.toLong().toDouble()
    return if (value == whole) value.toLong().toString() else value.toString()
}

@Composable
private fun OcrDraftStage(
    stage: CaptureStage.OcrDraft,
    viewModel: CaptureViewModel,
) {
    // The SAME custom-food form as the manual twin (R-U15) — prefilled,
    // confirm-gated: nothing saved until this button.
    WloCard(Modifier.testTag("f02-capture-ocr-draft")) {
        Text("read from the label — a draft, never a save", style = wloType.title)
        Text(
            "fix anything; it lands in your catalog only when you confirm",
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
    }
    CustomFoodSheetContent(
        draft = stage.draft,
        onChange = { viewModel.onEvent(CaptureEvent.OcrDraftChange(it)) },
        onSave = { viewModel.onEvent(CaptureEvent.OcrSave) },
    )
}

// --- shared bits -------------------------------------------------------------

private fun modeLabel(mode: CaptureLensMode): String =
    when (mode) {
        CaptureLensMode.PHOTO -> "photo"
        CaptureLensMode.BARCODE -> "barcode"
        CaptureLensMode.LABEL -> "label"
    }

// --- swap / add search sheet ---------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureSearchSheetRoot(
    viewModel: CaptureViewModel,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { viewModel.onEvent(CaptureEvent.DismissSearch) },
        shape = WloShape.SheetTop,
        modifier = Modifier.testTag("f02-capture-search-sheet"),
    ) {
        content()
    }
}

@Composable
private fun SearchResultsList(
    state: CaptureUiState,
    viewModel: CaptureViewModel,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("f02-capture-search-results")) {
        items(state.searchResults, key = { it.food.id }) { hit ->
            SearchHitRowSimple(hit) { viewModel.onEvent(CaptureEvent.PickSwap(hit)) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        }
    }
}

@Composable
private fun SearchHitRowSimple(
    hit: FoodHitUi,
    onClick: () -> Unit,
) = Row(
    modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = WloSpacing.ROW_INTERACTIVE)
            .testTag("f02-capture-search-hit")
            .clickable(onClick = onClick)
            .padding(vertical = WloSpacing.TIGHT),
    horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f)) {
        Text(hit.food.name, style = wloType.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        hit.food.brand?.let { Text(it, style = wloType.label, color = wloExtendedColors.textTertiary) }
    }
    hit.food.kcalPer100g?.let { per100 ->
        Text("${format(per100)} / 100 g", style = wloType.receipt, color = wloExtendedColors.textTertiary)
    }
}

/**
 * The sheet that carries the swap/add search: one root composable keeps the
 * stage wiring in one place (the screen body calls this when a search is open).
 */
@Composable
public fun CaptureSearchSheet(
    state: CaptureUiState,
    viewModel: CaptureViewModel,
) {
    if (state.stage is CaptureStage.Result && state.swappingItemId != null) {
        CaptureSearchSheetRoot(viewModel) {
            Text(
                text =
                    if (state.swappingItemId == null) {
                        "add an item from your catalog"
                    } else {
                        "swap the chip for a food you keep"
                    },
                style = wloType.title,
                modifier = Modifier.padding(horizontal = WloSpacing.SCREEN),
            )
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onEvent(CaptureEvent.SwapQueryChange(it)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = WloSpacing.SCREEN)
                        .testTag("f02-capture-search-field"),
                singleLine = true,
                placeholder = { Text("oats, grilled chicken, brand names…", style = wloType.body) },
            )
            SearchResultsList(state, viewModel)
            androidx.compose.foundation.layout
                .Spacer(Modifier.padding(WloSpacing.CARD))
        }
    }
}

/** A miss reason that keeps its human copy next to the state (used by sheets). */
public fun missCopy(reason: MissReason): String = reason.userCopy()
