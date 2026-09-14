package app.wlo.feature.f02.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloBanner
import app.wlo.core.designsystem.WloBannerTone
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloHaptics
import app.wlo.core.designsystem.WloIconAction
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSheet
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
        WloScreenTitle(title = "Capture", modifier = Modifier.testTag("f02-capture-title"))

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
                WloCard(
                    modifier = Modifier.testTag("f02-capture-analyzing"),
                    header = { WloCardHeader(title = "Reading it…") },
                ) {
                    Text(
                        "on-device, nothing leaves your phone",
                        style = wloType.label,
                        color = wloExtendedColors.textTertiary,
                    )
                }

            is CaptureStage.Result ->
                ResultStage(stage, viewModel, haptics, onOpenManualLadder)

            is CaptureStage.MissingModel ->
                WloCard(
                    modifier = Modifier.testTag("f02-capture-missing-model"),
                    header = { WloCardHeader(title = "The food recognizer isn't on this device yet") },
                ) {
                    Text(
                        stage.reason,
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                    WloButton(
                        label = "Open the model manager",
                        onClick = onOpenModelManager,
                        modifier = Modifier.fillMaxWidth().testTag("f02-capture-open-zoo"),
                    )
                    WloSecondaryButton(
                        label = "Type it instead",
                        onClick = onOpenManualLadder,
                        modifier = Modifier.fillMaxWidth().testTag("f02-capture-manual"),
                    )
                }

            is CaptureStage.Product -> ProductStage(stage, viewModel)

            is CaptureStage.ProductMiss ->
                WloCard(
                    modifier = Modifier.testTag("f02-capture-product-miss"),
                    header = { WloCardHeader(title = "No product under ${stage.barcode}") },
                ) {
                    Text(
                        stage.reason.userCopy(),
                        style = wloType.caption,
                        color = wloExtendedColors.textTertiary,
                    )
                    ManualBarcodeField(viewModel)
                    WloButton(
                        label = "Search your catalog",
                        onClick = onOpenManualLadder,
                        modifier = Modifier.fillMaxWidth().testTag("f02-capture-manual"),
                    )
                }

            is CaptureStage.OcrDraft -> Unit // rendered as the sheet below

            CaptureStage.Saving ->
                WloCard(
                    modifier = Modifier.testTag("f02-capture-saving"),
                    header = { WloCardHeader(title = "Saving…") },
                ) {
                }

            is CaptureStage.Saved ->
                WloCard(
                    modifier = Modifier.testTag("f02-capture-saved"),
                    header = { WloCardHeader(title = "Saved to the diary") },
                ) {
                    Text(
                        "${stage.entryCount} ${if (stage.entryCount == 1) "entry" else "entries"} · " +
                            "%,.0f kcal".format(stage.kcal),
                        style = wloType.statM,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        WloSecondaryButton(
                            label = "Scan next",
                            onClick = {
                                haptics.perform(WloHaptic.Tick)
                                viewModel.onEvent(CaptureEvent.Retake)
                            },
                            modifier = Modifier.weight(1f).testTag("f02-capture-again"),
                        )
                        WloButton(
                            label = "Done",
                            onClick = {
                                haptics.perform(WloHaptic.Settle)
                                onDone()
                            },
                            modifier = Modifier.weight(1f).testTag("f02-capture-done"),
                        )
                    }
                }
        }

        state.notice?.let {
            WloBanner(
                text = it.text,
                tone = if (it.state == NoticeState.RAIL) WloBannerTone.Warning else WloBannerTone.Info,
                actionLabel = "Dismiss",
                action = { viewModel.onEvent(CaptureEvent.DismissNotice) },
                modifier = Modifier.testTag("f02-capture-notice"),
            )
        }
    }

    CaptureSearchSheet(state, viewModel)

    // The OCR draft: the SAME custom-food form the manual twin uses, opened
    // as a sheet (prefilled, confirm-gated — nothing saves without the tap).
    (state.stage as? CaptureStage.OcrDraft)?.let { stage ->
        WloSheet(
            onDismissRequest = { viewModel.onEvent(CaptureEvent.Retake) },
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
        CaptureLensMode.PHOTO -> ShutterAction(Modifier.testTag("f02-capture-shutter"), shutter, haptics, viewModel)

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
                ShutterAction(
                    Modifier.testTag("f02-capture-shutter"),
                    shutter,
                    haptics,
                    viewModel,
                )
                Text(
                    "Nothing saves until you confirm.",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
            }

        CaptureLensMode.BARCODE -> ManualBarcodeField(viewModel)
    }

    WloSecondaryButton(
        label = "Type it instead",
        onClick = onOpenManualLadder,
        modifier = Modifier.fillMaxWidth().testTag("f02-capture-manual"),
    )
}

/** The capture shutter: a filled 72 dp icon action — the flow's primary target. */
@Composable
private fun ShutterAction(
    modifier: Modifier = Modifier,
    shutter: CaptureShutter,
    haptics: WloHaptics,
    viewModel: CaptureViewModel,
): Unit =
    WloIconAction(
        imageVector = CaptureGlyphs.Scan,
        contentDescription = "Scan",
        onClick = {
            shutter.take { frame ->
                haptics.perform(WloHaptic.Tick)
                viewModel.onEvent(CaptureEvent.StillCaptured(frame))
            }
        },
        modifier = modifier,
        filled = true,
        size = 72.dp,
        iconSize = 32.dp,
    )

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
            WloIconAction(
                imageVector = CaptureGlyphs.Search,
                contentDescription = "Look up",
                onClick = {
                    if (text.isNotBlank()) {
                        viewModel.onEvent(CaptureEvent.ManualBarcode(text.trim()))
                        text = ""
                    }
                },
                size = 40.dp,
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
    WloCard(
        modifier = Modifier.testTag("f02-capture-result"),
        header = {
            WloCardHeader(
                title = "What we see",
                provenance = {
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
                },
            )
        },
    ) {
        if (stage.held) {
            Text(
                "rough guess — adjust what's wrong",
                style = wloType.caption,
                color = wloExtendedColors.held,
                modifier = Modifier.testTag("f02-capture-held-strip"),
            )
        }
        Text(
            "On-device estimate — tap a chip to correct it.",
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
    }

    for (item in stage.visibleItems) {
        ScanItemCard(item, viewModel)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT), modifier = Modifier.fillMaxWidth()) {
        WloSecondaryButton(
            label = "Add missed item",
            onClick = { viewModel.onEvent(CaptureEvent.OpenSwap(itemId = null)) },
            modifier = Modifier.weight(1f).testTag("f02-capture-add-item"),
        )
        WloSecondaryButton(
            label = "Type it instead",
            onClick = onOpenManualLadder,
            modifier = Modifier.weight(1f).testTag("f02-capture-manual"),
        )
    }

    WloButton(
        label = "Save ${stage.visibleItems.size} to diary",
        onClick = {
            haptics.perform(WloHaptic.Settle)
            viewModel.onEvent(CaptureEvent.Save)
        },
        modifier = Modifier.fillMaxWidth().testTag("f02-capture-save"),
    )
    // The retention disclosure lives ONCE, at the save decision point.
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
            WloSecondaryButton(
                label = "Swap",
                onClick = { viewModel.onEvent(CaptureEvent.OpenSwap(itemId = item.id)) },
                modifier = Modifier.weight(1f).testTag("f02-capture-item-swap-${item.id}"),
            )
            WloSecondaryButton(
                label = "Not this",
                onClick = { viewModel.onEvent(CaptureEvent.ItemRemove(item.id)) },
                modifier = Modifier.weight(1f).testTag("f02-capture-item-remove-${item.id}"),
            )
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
                style = wloType.caption,
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
            WloButton(
                label = "Add to diary",
                onClick = { viewModel.onEvent(CaptureEvent.ProductAddToDiary(product, grams)) },
                modifier = Modifier.weight(1f).testTag("f02-capture-product-add"),
            )
            WloSecondaryButton(
                label = "Save as food",
                onClick = { viewModel.onEvent(CaptureEvent.ProductSaveAsFood(product)) },
                modifier = Modifier.weight(1f).testTag("f02-capture-product-save-food"),
            )
        }
        WloSecondaryButton(
            label = "Scan next",
            onClick = { viewModel.onEvent(CaptureEvent.Retake) },
            modifier = Modifier.fillMaxWidth().testTag("f02-capture-again"),
        )
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
    WloCard(
        modifier = Modifier.testTag("f02-capture-ocr-draft"),
        header = { WloCardHeader(title = "Read from the label — a draft") },
    ) {
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

// --- shared bits ---------------------------------------------------------------

private fun modeLabel(mode: CaptureLensMode): String =
    when (mode) {
        CaptureLensMode.PHOTO -> "photo"
        CaptureLensMode.BARCODE -> "barcode"
        CaptureLensMode.LABEL -> "label"
    }

// --- swap / add search sheet ---------------------------------------------------

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
): Unit =
    WloListRow(
        label = hit.food.name,
        secondary = hit.food.brand,
        trailing = per100gTrailing(hit.food.kcalPer100g),
        modifier = Modifier.testTag("f02-capture-search-hit"),
        onClick = onClick,
    )

/** The per-100 g receipt line for a hit's trailing slot (null when unknown). */
@Composable
private fun per100gTrailing(per100: Double?): (@Composable () -> Unit)? =
    if (per100 == null) {
        null
    } else {
        {
            Text(
                "${format(per100)} / 100 g",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }
    }

/**
 * The sheet that carries the swap/add search: one composable keeps the stage
 * wiring in one place (the screen body calls this when a search is open).
 */
@Composable
public fun CaptureSearchSheet(
    state: CaptureUiState,
    viewModel: CaptureViewModel,
) {
    if (state.stage is CaptureStage.Result && state.swappingItemId != null) {
        WloSheet(
            onDismissRequest = { viewModel.onEvent(CaptureEvent.DismissSearch) },
            modifier = Modifier.testTag("f02-capture-search-sheet"),
        ) {
            Text(text = "Swap the chip for a food you keep", style = wloType.title)
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onEvent(CaptureEvent.SwapQueryChange(it)) },
                modifier = Modifier.fillMaxWidth().testTag("f02-capture-search-field"),
                singleLine = true,
                placeholder = { Text("oats, grilled chicken, brand names…", style = wloType.body) },
            )
            SearchResultsList(state, viewModel)
            Spacer(Modifier.height(WloSpacing.CARD))
        }
    }
}

/** A miss reason that keeps its human copy next to the state (used by sheets). */
public fun missCopy(reason: MissReason): String = reason.userCopy()

/**
 * The capture flow's two marks. The design system's WloIcons carries chrome
 * glyphs only, so the lens's shutter and lookup marks live here — hairline
 * strokes in WLO's outline idiom, painted black so `Icon(tint)` recolors.
 */
private object CaptureGlyphs {
    private const val VIEWPORT: Float = 24f
    private val Stroke: SolidColor = SolidColor(Color.Black)

    private fun builder(name: String): ImageVector.Builder =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        )

    private fun ImageVector.Builder.stroke(build: PathBuilder.() -> Unit): ImageVector.Builder =
        path(
            stroke = Stroke,
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            build()
        }

    /** The shutter: viewfinder brackets around the lens circle. */
    public val Scan: ImageVector =
        builder("CaptureGlyphScan")
            .stroke {
                moveTo(8.5f, 4f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, true, true, 4f, 6f)
                verticalLineTo(8.5f)
                moveTo(15.5f, 4f)
                horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, true, true, 20f, 6f)
                verticalLineTo(8.5f)
                moveTo(20f, 15.5f)
                verticalLineTo(18f)
                arcTo(2f, 2f, 0f, true, true, 18f, 20f)
                horizontalLineTo(15.5f)
                moveTo(8.5f, 20f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, true, true, 4f, 18f)
                verticalLineTo(15.5f)
            }.stroke {
                moveTo(8.8f, 12f)
                arcTo(3.2f, 3.2f, 0f, true, true, 15.2f, 12f)
                arcTo(3.2f, 3.2f, 0f, true, true, 8.8f, 12f)
                close()
            }.build()

    /** Catalog lookup: the magnifier. */
    public val Search: ImageVector =
        builder("CaptureGlyphSearch")
            .stroke {
                moveTo(4.5f, 10.5f)
                arcTo(6f, 6f, 0f, true, true, 16.5f, 10.5f)
                arcTo(6f, 6f, 0f, true, true, 4.5f, 10.5f)
                close()
                moveTo(15.2f, 15.2f)
                lineTo(19.5f, 19.5f)
            }.build()
}
