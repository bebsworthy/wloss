package app.wlo.feature.f02.food.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.fold
import app.wlo.core.common.getOrNull
import app.wlo.core.data.CorrectionCacheStore
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.EstimateProvenance
import app.wlo.core.data.FoodRepository
import app.wlo.core.data.NewCustomFood
import app.wlo.core.data.NewDiaryEntry
import app.wlo.core.data.ProfileRepository
import app.wlo.core.model.FoodItem
import app.wlo.core.ports.BarcodeScanner
import app.wlo.core.ports.CapturedFrame
import app.wlo.core.ports.OcrReader
import app.wlo.core.ports.OffProduct
import app.wlo.core.ports.OffRepository
import app.wlo.core.ports.PhotoAnalyzer
import app.wlo.feature.f02.food.domain.NutritionLabelParser
import app.wlo.feature.f02.food.domain.ScanCorrectionLoop
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * The capture flow's state holder (F02 §3 rungs 1–3 + §4). Orchestration
 * order per the architecture: the analyzer port returns, THEN the sanity
 * rails run (pure, in [ScanCorrectionLoop]), then the correction loop renders
 * — nothing auto-saves, every value editable, the manual twin always visible.
 *
 * The R-B6 correction cache is consulted when chips render and written at
 * save: label swaps and portion edits become prior signal for the NEXT scan.
 */
public class CaptureViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    private val photoAnalyzer: PhotoAnalyzer,
    private val barcodeScanner: BarcodeScanner,
    private val ocrReader: OcrReader,
    private val off: OffRepository,
    private val modelManager: app.wlo.core.ports.ModelManager,
    private val correctionCache: CorrectionCacheStore,
    private val onMissingModel: () -> Unit = {},
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private val state = MutableStateFlow(CaptureUiState())
    public val uiState: StateFlow<CaptureUiState> = state

    /** The barcode scan loop's job (active only in BARCODE viewfinder). */
    private var scanJob: Job? = null

    /** Debounced swap/add search source. */
    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch { correctionCache.ensureLoaded() }
        observeQueries()
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: CaptureEvent) {
        when (event) {
            is CaptureEvent.ModeChange -> setMode(event.mode)
            is CaptureEvent.PreviewFrame -> {
                if (state.value.mode == CaptureLensMode.BARCODE && state.value.stage is CaptureStage.Viewfinder) {
                    barcodeScanner.observe(event.frame)
                }
            }

            is CaptureEvent.StillCaptured -> {
                android.util.Log.d("WloCapture", "StillCaptured reached VM, mode=${state.value.mode}")
                onStill(event.frame)
            }
            is CaptureEvent.ManualBarcode -> onBarcode(event.barcode)
            CaptureEvent.StartScanning -> startBarcodeLoop()
            CaptureEvent.StopScanning -> scanJob?.cancel()
            is CaptureEvent.ItemGramsChange ->
                updateItem(event.itemId) { item ->
                    val grams = event.text.toDoubleOrNull()
                    if (grams == null) item else ScanCorrectionLoop.withGrams(item, grams)
                }

            is CaptureEvent.ItemRemove -> updateItem(event.itemId) { item -> item.copy(removed = true) }
            is CaptureEvent.OpenSwap ->
                state.value =
                    state.value.copy(swappingItemId = event.itemId, searchResults = emptyList())
            CaptureEvent.DismissSearch -> state.value = state.value.copy(swappingItemId = null)
            is CaptureEvent.SwapQueryChange -> {
                state.value = state.value.copy(searchQuery = event.text)
                queryFlow.value = event.text
            }
            is CaptureEvent.PickSwap -> pickSwap(event.hit)
            CaptureEvent.Save -> saveScan()
            CaptureEvent.Retake -> state.value = CaptureUiState(mode = state.value.mode, slot = state.value.slot)
            is CaptureEvent.ProductAddToDiary -> addProductToDiary(event.product, event.grams)
            is CaptureEvent.ProductSaveAsFood -> saveProductAsFood(event.product)
            is CaptureEvent.OcrDraftChange -> updateOcrDraft(event.draft)
            CaptureEvent.OcrSave -> saveOcrDraft()
            CaptureEvent.DismissNotice -> state.value = state.value.copy(notice = null)
        }
    }

    private fun setMode(mode: CaptureLensMode) {
        if (state.value.mode == mode) return
        state.value = CaptureUiState(mode = mode, slot = state.value.slot)
        if (mode == CaptureLensMode.BARCODE) startBarcodeLoop() else scanJob?.cancel()
    }

    private fun onStill(frame: CapturedFrame) {
        when (state.value.mode) {
            CaptureLensMode.PHOTO -> analyzePhoto(frame)
            CaptureLensMode.LABEL -> readLabel(frame)
            CaptureLensMode.BARCODE -> Unit // barcode uses the preview stream
        }
    }

    // --- photo rung -------------------------------------------------------

    private fun analyzePhoto(frame: CapturedFrame) {
        state.value = state.value.copy(stage = CaptureStage.Analyzing)
        viewModelScope.launch {
            val handle = modelManager.ensureAvailable(FOOD_CLASSIFIER_MODEL)
            if (handle.isFailure) {
                state.value =
                    state.value.copy(
                        stage =
                            CaptureStage.MissingModel(
                                modelId = FOOD_CLASSIFIER_MODEL,
                                reason =
                                    "the food recognizer isn't downloaded yet " +
                                        "(${handle.exceptionOrNull()?.message ?: "not available"})",
                            ),
                    )
                onMissingModel()
                return@launch
            }
            val analysis = photoAnalyzer.analyze(frame)
            var items = ScanCorrectionLoop.itemsFromSuggestions(analysis.value)
            // R-B6 priors: the user's usual corrections pre-apply (visible on the chip).
            items =
                items.map { item ->
                    correctionCache.priorFor(item.wireLabel)?.let { prior ->
                        ScanCorrectionLoop
                            .applyPrior(item, prior.correctedLabel, prior.lastGrams)
                            .let { applied ->
                                // keep the resolved catalog pointer sticky
                                applied.copy(resolvedFoodId = prior.correctedFoodId)
                            }
                    } ?: item
                }
            state.value =
                state.value.copy(
                    stage =
                        CaptureStage.Result(
                            items = items,
                            scanConfidence = analysis.confidence,
                            held = analysis.held,
                            modelId = FOOD_CLASSIFIER_MODEL,
                            railTripped = items.any(ScanCorrectionLoop.ScanItem::railTripped),
                        ),
                )
        }
    }

    // --- barcode rung -----------------------------------------------------

    private fun startBarcodeLoop() {
        scanJob?.cancel()
        scanJob =
            viewModelScope.launch {
                val inBarcodeViewfinder =
                    state.value.mode == CaptureLensMode.BARCODE && state.value.stage is CaptureStage.Viewfinder
                while (isActive && inBarcodeViewfinder) {
                    val barcode = barcodeScanner.scanNext() ?: break
                    onBarcode(barcode.value.value)
                }
            }
    }

    private fun onBarcode(barcode: String) {
        state.value = state.value.copy(stage = CaptureStage.Analyzing)
        viewModelScope.launch {
            when (val result = off.lookup(barcode)) {
                is app.wlo.core.ports.OffLookupResult.Hit ->
                    state.value = state.value.copy(stage = CaptureStage.Product(result.product, result.servedFromCache))

                is app.wlo.core.ports.OffLookupResult.Miss ->
                    state.value = state.value.copy(stage = CaptureStage.ProductMiss(result.reason, result.barcode))
            }
        }
    }

    // --- label rung -------------------------------------------------------

    private fun readLabel(frame: CapturedFrame) {
        state.value = state.value.copy(stage = CaptureStage.Analyzing)
        viewModelScope.launch {
            val analysis = ocrReader.read(frame)
            if (analysis.held || analysis.value.isBlank()) {
                state.value =
                    state.value.copy(
                        stage = CaptureStage.Viewfinder,
                        notice = NoticeUi("no readable text in that shot — try again, or type the values in the form"),
                    )
                return@launch
            }
            val parsed = NutritionLabelParser.parse(analysis.value)
            state.value =
                state.value.copy(
                    stage =
                        CaptureStage.OcrDraft(
                            parsed = parsed,
                            draft =
                                CustomFoodDraft(
                                    name = "",
                                    kcalText = parsed.kcalPer100g?.let(::formatNumber)?.removeSuffix(".0") ?: "",
                                    proteinText = parsed.proteinGPer100g?.let(::formatNumber)?.removeSuffix(".0") ?: "",
                                    carbText = parsed.carbGPer100g?.let(::formatNumber)?.removeSuffix(".0") ?: "",
                                    fatText = parsed.fatGPer100g?.let(::formatNumber)?.removeSuffix(".0") ?: "",
                                    fiberText = parsed.fiberGPer100g?.let(::formatNumber)?.removeSuffix(".0") ?: "",
                                    macrosVerified = false,
                                ),
                        ),
                )
        }
    }

    private fun updateOcrDraft(draft: CustomFoodDraft) {
        (state.value.stage as? CaptureStage.OcrDraft)?.let { stage ->
            state.value = state.value.copy(stage = stage.copy(draft = draft))
        }
    }

    /** OCR draft → catalog: EXPLICIT confirmation only (R-U15: never auto-saves). */
    private fun saveOcrDraft() {
        val stage = state.value.stage as? CaptureStage.OcrDraft ?: return
        val draft = stage.draft
        if (draft.name.isBlank()) {
            state.value = state.value.copy(notice = NoticeUi("give it a name and it becomes a food you keep"))
            return
        }
        viewModelScope.launch {
            val profileId = activeProfileId() ?: return@launch
            val outcome =
                foods.createCustomFood(
                    food =
                        NewCustomFood(
                            profileId = profileId,
                            name = draft.name.trim(),
                            kcalPer100g = draft.kcalText.toDoubleOrNull(),
                            proteinGPer100g = draft.proteinText.toDoubleOrNull(),
                            carbGPer100g = draft.carbText.toDoubleOrNull(),
                            fatGPer100g = draft.fatText.toDoubleOrNull(),
                            fiberGPer100g = draft.fiberText.toDoubleOrNull(),
                            macrosVerified = draft.macrosVerified,
                        ),
                    at = clock.now(),
                )
            when (outcome) {
                is WloResult.Ok -> {
                    val grams = 100.0
                    logOne(
                        food = outcome.value,
                        grams = grams,
                        estimate = null,
                        via = app.wlo.core.model.EntryVia.MANUAL_CUSTOM,
                    )
                    state.value =
                        state.value.copy(
                            stage = CaptureStage.Saved(entryCount = 1, kcal = outcome.value.kcalPer100g ?: 0.0),
                            notice = NoticeUi("${outcome.value.name} is in your catalog — captured once"),
                        )
                }

                is WloResult.Err ->
                    state.value = state.value.copy(notice = NoticeUi(outcome.error.userCopy(), NoticeState.RAIL))
            }
        }
    }

    // --- correction loop edits --------------------------------------------

    private fun updateItem(
        itemId: Int,
        transform: (ScanCorrectionLoop.ScanItem) -> ScanCorrectionLoop.ScanItem,
    ) {
        (state.value.stage as? CaptureStage.Result)?.let { stage ->
            state.value =
                state.value.copy(
                    stage =
                        stage.copy(
                            items =
                                stage.items.map { item ->
                                    if (item.id == itemId) transform(item) else item
                                },
                            railTripped = stage.items.map(transform).any(ScanCorrectionLoop.ScanItem::railTripped),
                        ),
                )
        }
    }

    private fun pickSwap(hit: FoodHitUi) {
        val stage = state.value.stage as? CaptureStage.Result ?: return
        val targetId = state.value.swappingItemId
        state.value = state.value.copy(swappingItemId = null, searchQuery = "")
        if (targetId == null) {
            // "add missed item": a new chip from a catalog hit (no model claim).
            val grams =
                hit.food.servingPresets
                    .firstOrNull()
                    ?.grams ?: 100.0
            val per100 = hit.food.kcalPer100g ?: 0.0
            val newItem =
                ScanCorrectionLoop.ScanItem(
                    id = (stage.items.maxOfOrNull { it.id } ?: -1) + 1,
                    wireLabel = hit.food.name,
                    displayLabel = hit.food.name,
                    confidence = 1.0,
                    grams = grams,
                    kcalPer100g = per100,
                    rail =
                        app.wlo.core.engines.FoodRails
                            .clampEnergy(hit.food.name, grams, per100 * grams / 100.0),
                    resolvedFoodId = hit.food.id,
                )
            state.value =
                state.value.copy(stage = stage.copy(items = stage.items + newItem))
        } else {
            // The MODEL's label stays the analyzed reference (the R-B6 prior is
            // keyed on what the model claimed); only the display + the resolved
            // catalog row + the density move to the user's food.
            updateItem(targetId) { item ->
                val per100 =
                    hit.food.kcalPer100g
                        ?.let {
                            app.wlo.core.engines.FoodRails
                                .clampDensity(item.wireLabel, it)
                        }
                        ?: item.kcalPer100g
                item
                    .copy(displayLabel = hit.food.name, resolvedFoodId = hit.food.id, kcalPer100g = per100)
                    .let {
                        it.copy(
                            rail =
                                app.wlo.core.engines.FoodRails.clampEnergy(
                                    it.wireLabel,
                                    it.grams,
                                    it.kcalPer100g * it.grams / 100.0,
                                ),
                        )
                    }
            }
        }
    }

    // --- saves ------------------------------------------------------------

    private fun saveScan() {
        val stage = state.value.stage as? CaptureStage.Result ?: return
        val visible = stage.visibleItems
        if (visible.isEmpty()) return
        state.value = state.value.copy(stage = CaptureStage.Saving)
        viewModelScope.launch {
            val profileId =
                activeProfileId() ?: run {
                    state.value =
                        state.value.copy(stage = stage, notice = NoticeUi("no profile yet — finish onboarding first"))
                    return@launch
                }
            var savedKcal = 0.0
            var savedCount = 0
            for (item in visible) {
                val food = resolveOrCreateFood(profileId, item) ?: continue
                val grams = ScanCorrectionLoop.roundGrams(item.grams)
                val log =
                    logOne(
                        food,
                        grams,
                        EstimateProvenance(
                            modelId = stage.modelId,
                            confidence = item.confidence,
                            consentGranted = false,
                            held = stage.held,
                        ),
                        app.wlo.core.model.EntryVia.PHOTO,
                    )
                if (log != null) {
                    savedKcal += log.kcal
                    savedCount++
                }
                // R-B6: record the correction as prior signal (swaps + portions).
                if (item.displayLabel != ScanCorrectionLoop.displayName(item.wireLabel) ||
                    item.priorApplied ||
                    item.resolvedFoodId != null
                ) {
                    correctionCache.recordCorrection(
                        analyzedLabel = item.wireLabel,
                        correctedLabel = item.displayLabel,
                        correctedFoodId = item.resolvedFoodId ?: food.id,
                        lastGrams = grams,
                        atEpochMs = clock.now().toEpochMilliseconds(),
                    )
                }
            }
            state.value =
                state.value.copy(
                    stage = CaptureStage.Saved(entryCount = savedCount, kcal = savedKcal),
                )
        }
    }

    /** Portion math runs in the repository; this writes one entry. */
    private suspend fun logOne(
        food: FoodItem,
        grams: Double,
        estimate: EstimateProvenance?,
        via: app.wlo.core.model.EntryVia,
    ): app.wlo.core.model.DiaryEntry? =
        when (
            val outcome =
                diary.logEntry(
                    NewDiaryEntry(
                        profileId = food.profileId,
                        dayEpochDay = DayBoundary.epochDay(clock.now(), zone),
                        mealSlot = state.value.slot,
                        foodItemId = food.id,
                        quantity = grams,
                        unit = "g",
                        enteredVia = via,
                        estimate = estimate,
                    ),
                    clock.now(),
                )
        ) {
            is WloResult.Ok -> outcome.value
            is WloResult.Err -> {
                state.value = state.value.copy(notice = NoticeUi(outcome.error.userCopy(), NoticeState.RAIL))
                null
            }
        }

    /** Exact-name match first; else create the class-prior food (R-U15-editable). */
    private suspend fun resolveOrCreateFood(
        profileId: String,
        item: ScanCorrectionLoop.ScanItem,
    ): FoodItem? {
        item.resolvedFoodId?.let { id ->
            foods.byId(id).getOrNull()?.let { return it }
        }
        val hits =
            when (val result = foods.search(profileId, item.displayLabel, limit = 1)) {
                is WloResult.Ok -> result.value
                is WloResult.Err -> emptyList()
            }
        hits.firstOrNull { it.item.name.equals(item.displayLabel, ignoreCase = true) }?.let { return it.item }
        val created =
            foods.createCustomFood(
                food =
                    NewCustomFood(
                        profileId = profileId,
                        name = item.displayLabel,
                        aliases = item.wireLabel,
                        kcalPer100g = item.kcalPer100g,
                        macrosVerified = false,
                    ),
                at = clock.now(),
            )
        return when (created) {
            is WloResult.Ok -> created.value
            is WloResult.Err -> {
                state.value = state.value.copy(notice = NoticeUi(created.error.userCopy(), NoticeState.RAIL))
                null
            }
        }
    }

    // --- product stage ----------------------------------------------------

    /** OFF product → catalog row (source = OFF), without logging a portion. */
    private fun saveProductAsFood(product: OffProduct) {
        viewModelScope.launch {
            val profileId = activeProfileId() ?: return@launch
            val existing = resolveOrCreateOffFood(profileId, product)
            if (existing != null) {
                state.value = state.value.copy(notice = NoticeUi("${existing.name} is in your catalog"))
            }
        }
    }

    private fun addProductToDiary(
        product: OffProduct,
        grams: Double,
    ) {
        viewModelScope.launch {
            val profileId = activeProfileId() ?: return@launch
            val food = resolveOrCreateOffFood(profileId, product) ?: return@launch
            val safeGrams = grams.coerceIn(0.0, 5_000.0)
            logOne(food, safeGrams, estimate = null, via = app.wlo.core.model.EntryVia.MANUAL_SEARCH)
            state.value =
                state.value.copy(
                    stage = CaptureStage.Saved(entryCount = 1, kcal = (product.kcalPer100g ?: 0.0) * safeGrams / 100.0),
                    notice = NoticeUi("added to the diary"),
                )
        }
    }

    private suspend fun resolveOrCreateOffFood(
        profileId: String,
        product: OffProduct,
    ): FoodItem? {
        val hits =
            when (val result = foods.search(profileId, product.name, limit = 1)) {
                is WloResult.Ok -> result.value
                is WloResult.Err -> emptyList()
            }
        hits.firstOrNull { it.item.name.equals(product.name, ignoreCase = true) }?.let { return it.item }
        val food = product.toNewCustomFood(profileId)
        return when (val created = foods.createCustomFood(food = food, at = clock.now())) {
            is WloResult.Ok -> created.value
            is WloResult.Err -> {
                state.value = state.value.copy(notice = NoticeUi(created.error.userCopy(), NoticeState.RAIL))
                null
            }
        }
    }

    // --- shared plumbing --------------------------------------------------

    @OptIn(FlowPreview::class)
    private fun observeQueries() {
        viewModelScope.launch {
            queryFlow.debounce(SEARCH_DEBOUNCE_MS).collectLatest { query -> runSearch(query) }
        }
    }

    private suspend fun runSearch(query: String) {
        val profileId = activeProfileId() ?: return
        if (query.isBlank()) {
            state.value = state.value.copy(searchResults = emptyList(), searching = false)
            return
        }
        val hits =
            when (val result = foods.search(profileId, query)) {
                is WloResult.Ok -> result.value
                is WloResult.Err -> emptyList()
            }
        state.value = state.value.copy(searchResults = hits.map(FoodHitUi::from), searching = false)
    }

    private suspend fun activeProfileId(): String? = profiles.active().fold(onOk = { it?.id }, onErr = { null })

    private fun app.wlo.core.common.AppError.userCopy(): String =
        when (this) {
            is app.wlo.core.common.AppError.InvalidInput ->
                if (detail.contains("energy-density rail")) {
                    "Nothing is denser than pure fat — 900 kcal per 100 g is the " +
                        "physical ceiling. Check the label once more."
                } else {
                    detail
                }
            else -> "that didn't save — nothing changed"
        }

    public override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }

    public companion object {
        /**
         * The zoo model this flow consumes (ADR-007). Duplicated here because
         * features cannot see :core:ai (D1) — the string is pinned by the
         * zoo manifest and the analyzer's own constant.
         */
        public const val FOOD_CLASSIFIER_MODEL: String = "food-classifier/1"

        private const val SEARCH_DEBOUNCE_MS: Long = 150L

        public fun formatNumber(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}
