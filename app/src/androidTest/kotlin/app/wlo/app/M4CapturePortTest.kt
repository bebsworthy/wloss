package app.wlo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.wlo.core.ai.WloBarcodeScanner
import app.wlo.core.ai.WloOcrReader
import app.wlo.core.ai.ZooManager
import app.wlo.core.common.getOrNull
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.EstimateProvenance
import app.wlo.core.data.FoodRepository
import app.wlo.core.data.NewCustomFood
import app.wlo.core.data.NewDiaryEntry
import app.wlo.core.database.WloDatabase
import app.wlo.core.model.EntryVia
import app.wlo.core.model.Provenance
import app.wlo.core.ports.CapturedFrame
import app.wlo.core.ports.MissReason
import app.wlo.core.ports.OffLookupResult
import app.wlo.core.ports.OffRepository
import app.wlo.core.ports.PhotoAnalyzer
import app.wlo.feature.f02.food.domain.NutritionLabelParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.EAN13Writer
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * M4 PART B, port level — the REAL engines on the REAL emulator:
 *  - the ONNX analyzer over the real zoo artifact (downloaded from the pinned
 *    HF URL if cold) against a bundled Public-Domain meal photo;
 *  - the pinned preprocessing math (torchvision eval transforms);
 *  - the barcode port (ZXing engines exercised directly — decode of a
 *    generated EAN-13) → the REAL OFF lookup → cache honesty via receipts;
 *  - the OCR port over a Canvas-rendered nutrition label;
 *  - airplane mode: OFF degrades, the analyzer keeps working offline.
 */
@RunWith(AndroidJUnit4::class)
public class M4CapturePortTest {
    private val koin get() = GlobalContext.get()

    // --- analyzer ------------------------------------------------------------

    @Test
    public fun realAnalyzerProducesSuggestionsProvenanceAndRailsFromBundledMealPhoto() {
        val zoo = koin.get<ZooManager>()
        val analyzer =
            app.wlo.core.ai.OnnxPhotoAnalyzer(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                modelManager = zoo,
            )
        val frame = bundledMealFrame()

        // Ensures the model (download-once; the artifact may already be cold-installed).
        val analysis =
            runBlocking {
                zoo.ensureAvailable("food-classifier/1").getOrThrow()
                analyzer.analyze(frame)
            }

        assertTrue(analysis.value.isNotEmpty(), "the real photo must produce suggestions")
        assertTrue(analysis.value.size <= PhotoAnalyzer.TOP_K)
        val confidences = analysis.value.map { it.confidence }
        assertEquals(confidences, confidences.sortedDescending(), "top-k is confidence-ordered")
        assertTrue(confidences.sum() <= 1.0 + 1e-6, "softmax probabilities")
        val provenance = analysis.provenance
        assertTrue(provenance is Provenance.Estimated)
        provenance as Provenance.Estimated
        assertEquals("food-classifier/1", provenance.modelId)
        assertEquals(false, provenance.consentGranted, "on-device inference: no consent, no cloud")
        assertEquals(analysis.held, (analysis.confidence ?: 0.0) < PhotoAnalyzer.DEFAULT_HOLD_CONFIDENCE)

        // Rails: every suggestion's portion-scaled estimate stays within the
        // class band (the anti-27M-kcal guarantee on real model output).
        for (item in app.wlo.feature.f02.food.domain.ScanCorrectionLoop
            .itemsFromSuggestions(analysis.value)) {
            assertTrue(
                item.kcal <= item.grams * item.rail.band.kcalPerGram + 1e-6,
                "${item.wireLabel}: ${item.kcal} kcal exceeds the ${item.rail.band} ceiling",
            )
        }
    }

    @Test
    public fun preprocessingMatchesPinnedTransformMath() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val neverManager =
            object : app.wlo.core.ports.ModelManager {
                override suspend fun ensureAvailable(modelId: String): Result<app.wlo.core.ports.ModelHandle> =
                    Result.failure(IllegalStateException("unused in this test"))

                override suspend fun reclaim(modelId: String): Result<Unit> = Result.success(Unit)
            }
        val analyzer =
            app.wlo.core.ai
                .OnnxPhotoAnalyzer(context = context, modelManager = neverManager)
        // Pure red pixel: R channel normalizes to (1 - 0.485) / 0.229.
        val pixels = IntArray(224 * 224) { Color.RED }
        val data = analyzer.toNormalizedNchw(pixels)
        assertEquals(3 * 224 * 224, data.size)
        assertEquals((1.0 - 0.485) / 0.229, data[0].toDouble(), 1e-4)
        assertEquals((0.0 - 0.456) / 0.224, data[224 * 224].toDouble(), 1e-4)
        assertEquals((0.0 - 0.406) / 0.225, data[2 * 224 * 224].toDouble(), 1e-4)
    }

    // --- barcode + OFF -------------------------------------------------------

    @Test
    public fun barcodePortDecodesGeneratedEan13_offLookupHitsAndCaches() {
        val scanner = WloBarcodeScanner()
        val frame = ean13Frame("3017620422003")

        val barcode =
            runBlocking {
                withTimeoutOrNull(20_000) {
                    val awaiting = async(kotlinx.coroutines.Dispatchers.Default) { scanner.scanNext() }
                    // Feed frames until the decode lands (stride-tolerant port).
                    while (!awaiting.isCompleted) {
                        scanner.observe(frame)
                        delay(200)
                    }
                    awaiting.await()
                }
            } ?: error("the port never decoded the generated EAN-13")

        assertEquals("3017620422003", barcode.value.value)

        val off = koin.get<OffRepository>()
        val ledger = koin.get<app.wlo.core.network.EgressLedger>()
        val before = runBlocking { ledger.countByPurpose()[app.wlo.core.ports.EgressPurpose.OFF_LOOKUP] ?: 0L }

        val first = runBlocking { off.lookup("3017620422003") }
        assertTrue(first is OffLookupResult.Hit, "OFF must know 3017620422003 (real network)")
        val product = (first as OffLookupResult.Hit).product
        assertTrue(product.name.isNotBlank())
        assertTrue(product.attribution.contains("ODbL"), "the ODbL attribution rides the product")

        val second = runBlocking { off.lookup("3017620422003") }
        assertTrue(second is OffLookupResult.Hit && second.servedFromCache, "second lookup is served from cache (honest UI)")

        val receiptsAfter = runBlocking { ledger.countByPurpose()[app.wlo.core.ports.EgressPurpose.OFF_LOOKUP] ?: 0L }
        assertEquals(before + 2, receiptsAfter, "per-lookup receipts (miss hits are allowed to re-query)")
        val cacheHits =
            runBlocking { ledger.recent(50) }.count {
                it.purpose == app.wlo.core.ports.EgressPurpose.OFF_LOOKUP &&
                    it.outcome == app.wlo.core.network.EgressOutcome.CACHE_HIT
            }
        assertTrue(cacheHits >= 1, "the cache hit writes a CACHE_HIT receipt")
    }

    // --- OCR -----------------------------------------------------------------

    @Test
    public fun ocrPortReadsRenderedLabel_intoDraftPrefill() {
        val reader = WloOcrReader()
        val frame = labelFrame()

        val analysis = runBlocking { reader.read(frame) }
        assertTrue(!analysis.held, "the rendered label must read cleanly, got: ${analysis.value}")
        val parsed = NutritionLabelParser.parse(analysis.value)
        assertTrue(parsed.foundAny, "parser found nothing in: ${analysis.value.take(200)}")
        assertNotNull(parsed.kcalPer100g)
    }

    // --- diary save path (photo entries with AI provenance) -------------------

    @Test
    public fun photoSaveWritesDiaryEntryWithAiProvenanceAndCorrectionCache() {
        val clock = koin.get<app.wlo.core.common.ClockPort>()
        val profiles = koin.get<app.wlo.core.data.ProfileRepository>()
        val foods = koin.get<FoodRepository>()
        val diary = koin.get<DiaryRepository>()
        val cache = koin.get<app.wlo.core.data.CorrectionCacheStore>()

        runBlocking {
            val profileId =
                profiles.active().getOrNull()?.id
                    ?: profiles
                        .create(
                            app.wlo.core.data.NewProfile(
                                birthYear = 1990,
                                heightCm = 180.0,
                                startWeightKg = 80.0,
                            ),
                            at = clock.now(),
                        ).getOrNull()!!
                        .id
            val day =
                app.wlo.core.common.DayBoundary
                    .epochDay(clock.now(), kotlinx.datetime.TimeZone.currentSystemDefault())
            val before = (diary.day(profileId, day).getOrNull())?.entries?.size ?: 0

            val food =
                foods
                    .createCustomFood(
                        NewCustomFood(profileId = profileId, name = "port-test pizza", kcalPer100g = 266.0),
                        at = clock.now(),
                    ).getOrNull()!!
            val outcome =
                diary
                    .logEntry(
                        NewDiaryEntry(
                            profileId = profileId,
                            dayEpochDay = day,
                            mealSlot = app.wlo.core.model.MealSlot.LUNCH,
                            foodItemId = food.id,
                            quantity = 200.0,
                            unit = "g",
                            enteredVia = EntryVia.PHOTO,
                            estimate =
                                EstimateProvenance(
                                    modelId = "food-classifier/1",
                                    confidence = 0.42,
                                    consentGranted = false,
                                    held = false,
                                ),
                        ),
                        at = clock.now(),
                    ).getOrNull()!!

            assertEquals(EntryVia.PHOTO, outcome.enteredVia)
            val dayDiary = diary.day(profileId, day).getOrNull()!!
            assertEquals(before + 1, dayDiary.entries.size)
            assertEquals(EntryVia.PHOTO, dayDiary.entries.last().enteredVia)

            // The provenance row names the model (the "how we got here" sheet's source).
            val db = koin.get<WloDatabase>()
            val rows = db.provenance().range(profileId, day, day)
            val own = rows.last { it.scalar == outcome.provenanceScalar }
            assertEquals("ai-estimate", own.method)
            assertEquals("food-classifier/1", own.formulaVersion)

            // The correction cache records the swap as prior signal (R-B6).
            cache.recordCorrection(
                "pizza",
                "port-test pizza",
                correctedFoodId = food.id,
                lastGrams = 200.0,
                atEpochMs = clock.now().toEpochMilliseconds(),
            )
            assertEquals("port-test pizza", cache.priorFor("pizza")!!.correctedLabel)
        }
    }

    // --- airplane mode ---------------------------------------------------------

    @Test
    public fun airplaneMode_offDegradesAnalyzerKeepsWorking() {
        val zoo = koin.get<ZooManager>()
        val off = koin.get<OffRepository>(OffRepository::class)
        val analyzer =
            app.wlo.core.ai.OnnxPhotoAnalyzer(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                modelManager = zoo,
            )
        // Warm the zoo BEFORE cutting the radio (download-once, R-S14).
        runBlocking { zoo.ensureAvailable("food-classifier/1").getOrThrow() }

        assertTrue(M4TestSupport.setAirplaneMode(true), "airplane mode must engage")
        try {
            val miss = runBlocking { off.lookup("3017620422003") }
            assertTrue(miss is OffLookupResult.Miss, "offline lookups miss")
            assertEquals(MissReason.NETWORK_UNAVAILABLE, (miss as OffLookupResult.Miss).reason)

            val analysis = runBlocking { analyzer.analyze(bundledMealFrame()) }
            assertTrue(analysis.provenance is Provenance.Estimated, "on-device inference never needs the radio")
        } finally {
            assertTrue(M4TestSupport.setAirplaneMode(false), "airplane mode must release")
        }
    }

    // --- fixtures ----------------------------------------------------------------

    /** The bundled Public-Domain meal photo (Wikimedia Commons "Pizza (1).jpg"). */
    private fun bundledMealFrame(): CapturedFrame {
        // androidTest assets live in the INSTRUMENTATION apk, not the target.
        val bytes =
            InstrumentationRegistry
                .getInstrumentation()
                .context.assets
                .open("meal_pizza.jpg")
                .use { it.readBytes() }
        val options =
            android.graphics.BitmapFactory
                .Options()
                .apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return CapturedFrame(bytes = bytes, widthPx = options.outWidth, heightPx = options.outHeight)
    }

    /** Renders an EAN-13 barcode bitmap → memory-only JPEG frame. */
    private fun ean13Frame(content: String): CapturedFrame {
        val matrix: BitMatrix = EAN13Writer().encode(content, BarcodeFormat.EAN_13, 720, 280)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmapFrame(bitmap)
    }

    /** Renders a crisp nutrition label → memory-only JPEG frame. */
    private fun labelFrame(): CapturedFrame {
        val bitmap = Bitmap.createBitmap(900, 700, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 42f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        val lines =
            listOf(
                "Nutrition Facts",
                "Per 100 g",
                "Energy 250 kcal",
                "Total Fat 12 g",
                "Carbohydrate 30 g",
                "Fibre 3.5 g",
                "Protein 8 g",
            )
        lines.forEachIndexed { index, line -> canvas.drawText(line, 40f, 80f + index * 70f, paint) }
        return bitmapFrame(bitmap)
    }

    private fun bitmapFrame(bitmap: Bitmap): CapturedFrame {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        val frame = CapturedFrame(bytes = out.toByteArray(), widthPx = bitmap.width, heightPx = bitmap.height)
        bitmap.recycle()
        return frame
    }
}
