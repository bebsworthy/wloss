package app.wlo.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import app.wlo.core.common.getOrNull
import app.wlo.core.data.CorrectionCacheStore
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.FoodRepository
import app.wlo.core.data.NewCustomFood
import app.wlo.core.model.EntryVia
import app.wlo.core.model.Provenance
import app.wlo.core.ports.CapturedFrame
import app.wlo.core.ports.FoodSuggestion
import app.wlo.core.ports.ModelHandle
import app.wlo.core.ports.ModelManager
import app.wlo.core.ports.OcrReader
import app.wlo.core.ports.PhotoAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The correction-loop UI E2E over the REAL app with the capture ports swapped
 * for deterministic fakes (Koin overrides): photo → chips → swap via search →
 * save with AI provenance; the held path; the rail trip; the OCR draft gate;
 * the real OFF product screen through a typed barcode (real network, cached).
 *
 * Camera: the emulator's virtual-scene camera provides the stills — the fakes
 * keep the ANALYSIS deterministic, not the camera.
 */
@RunWith(AndroidJUnit4::class)
public class M4CaptureFlowTest {
    /**
     * Camera is granted BEFORE the activity launches (declared before the
     * compose rule = outermost): the viewfinder's first permission check
     * passes and no system dialog ever blocks the composition.
     */
    @get:Rule(order = 1)
    public val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.CAMERA)

    @get:Rule(order = 2)
    public val rule = createAndroidComposeRule<MainActivity>()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var overrides: Module

    private val fakeAnalyzer = FakePhotoAnalyzer(top1 = 0.42)
    private val fakeOcr = FakeOcrReader()

    @Before
    public fun setUp() {
        val koin = GlobalContext.get()
        // Fresh installs have no profile; the capture saves need one.
        runBlocking {
            val profiles = koin.get<app.wlo.core.data.ProfileRepository>()
            val clock = koin.get<app.wlo.core.common.ClockPort>()
            profiles.active().getOrNull()?.id
                ?: profiles
                    .create(
                        app.wlo.core.data
                            .NewProfile(birthYear = 1990, heightCm = 180.0, startWeightKg = 80.0),
                        at = clock.now(),
                    ).getOrNull()!!
                    .id
        }
        overrides =
            module {
                single<PhotoAnalyzer> { fakeAnalyzer }
                single<OcrReader> { fakeOcr }
                single<ModelManager> { FakeModelManager() }
            }
        koin.loadModules(listOf(overrides), allowOverride = true)
    }

    @After
    public fun tearDown() {
        GlobalContext.get().unloadModules(listOf(overrides))
        scenario.onActivity { it.finish() }
        SystemClock.sleep(300)
    }

    private fun launchCapture() {
        if (!this::scenario.isInitialized) {
            scenario = ActivityScenario.launch(MainActivity::class.java)
        }
        // Poll INSIDE the compose rule's waitUntil: the rule's frame clock
        // only advances there, and recomposition (the newIntent state) needs
        // frames. Re-delivery is singleTask semantics — another widget tap.
        rule.waitUntil(20_000) {
            var route: String? = null
            scenario.onActivity { activity ->
                route = activity.currentDestinationForVerification
                if (route != "f02/capture") {
                    activity.deliverNewIntentForVerification(
                        Intent(activity, MainActivity::class.java).setData(Uri.parse("wlo://log/capture")),
                    )
                }
            }
            route == "f02/capture"
        }
    }

    private fun awaitRoute(expected: String) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var actual: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { activity -> actual = activity.currentDestinationForVerification }
            if (actual == expected) return
            SystemClock.sleep(100)
        }
        assertEquals(expected, actual)
    }

    @Test
    public fun photoFlow_chipsConfidenceCorrection_saveWithProvenance() {
        // A catalog food the user will swap to (search needs a hit).
        val koin = GlobalContext.get()
        runBlocking {
            val profiles = koin.get<app.wlo.core.data.ProfileRepository>()
            val foods = koin.get<FoodRepository>()
            val clock = koin.get<app.wlo.core.common.ClockPort>()
            val profileId =
                profiles.active().getOrNull()?.id
                    ?: profiles
                        .create(
                            app.wlo.core.data
                                .NewProfile(birthYear = 1990, heightCm = 180.0, startWeightKg = 80.0),
                            at = clock.now(),
                        ).getOrNull()!!
                        .id
            foods.createCustomFood(
                NewCustomFood(profileId = profileId, name = "grilled chicken salad", kcalPer100g = 150.0),
                at = clock.now(),
            )
        }

        launchCapture()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithTag("f02-capture-viewfinder").fetchSemanticsNodes().isNotEmpty()
        }

        // Shutter → fake analyzer → the editable result card.
        rule.onNodeWithTag("f02-capture-shutter").performClick()
        rule.waitUntil(30_000) {
            rule.onAllNodesWithTag("f02-capture-result").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("f02-capture-confidence").assertExists()
        rule.onNodeWithTag("f02-capture-item-0").assertExists()

        // Correction: swap chip 0 to the catalog food via the search sheet.
        rule.onNodeWithTag("f02-capture-item-swap-0").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("f02-capture-search-sheet").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("f02-capture-search-field").performTextInput("grilled")
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("f02-capture-search-hit").fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithTag("f02-capture-search-hit")[0].performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("f02-capture-item-0").fetchSemanticsNodes().isNotEmpty() }

        // Save → the saved card; then the data-level truth.
        rule.onNodeWithTag("f02-capture-save").performScrollTo().performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("f02-capture-saved").fetchSemanticsNodes().isNotEmpty() }

        runBlocking {
            val diary = koin.get<DiaryRepository>()
            val clock = koin.get<app.wlo.core.common.ClockPort>()
            val profiles = koin.get<app.wlo.core.data.ProfileRepository>()
            val profileId = profiles.active().getOrNull()!!.id
            val day =
                app.wlo.core.common.DayBoundary
                    .epochDay(clock.now(), kotlinx.datetime.TimeZone.currentSystemDefault())
            val entries = diary.day(profileId, day).getOrNull()!!.entries
            val photoEntry = entries.last { it.enteredVia == EntryVia.PHOTO }
            assertTrue(photoEntry.kcal > 0.0, "portion math ran on the swapped food")

            // Provenance row names the model + consent state (F02 §5).
            val db = koin.get<app.wlo.core.database.WloDatabase>()
            val row = db.provenance().range(profileId, day, day).last { it.scalar == photoEntry.provenanceScalar }
            assertEquals("ai-estimate", row.method)
            assertEquals(FakeModelManager.MODEL_ID, row.formulaVersion)

            // R-B6: the swap became prior signal (keyed on the MODEL's label).
            val cache = koin.get<CorrectionCacheStore>()
            val prior = cache.priorFor("butter")
            assertTrue(prior != null, "the correction must land in the R-B6 cache")
            assertEquals("grilled chicken salad", prior!!.correctedLabel)
        }
    }

    @Test
    public fun lowConfidence_showsHeldStrip_andSaveStillWorks() {
        fakeAnalyzer.top1 = 0.05
        launchCapture()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("f02-capture-viewfinder").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("f02-capture-shutter").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("f02-capture-result").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("f02-capture-held-strip").assertExists()

        // F02 §4: save still works — no modal nagging.
        rule.onNodeWithTag("f02-capture-save").performScrollTo().performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("f02-capture-saved").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    public fun railTrip_flagsTheChipWithKindCopy() {
        launchCapture()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("f02-capture-viewfinder").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("f02-capture-shutter").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("f02-capture-result").fetchSemanticsNodes().isNotEmpty() }

        // The fake's first chip ("butter", 1200 kcal/100 g hint) is clamped by
        // the density rail → marker + kind copy right on the chip.
        rule.onNodeWithTag("f02-capture-item-rail-0").assertExists()
    }

    @Test
    public fun ocrDraft_prefillsTheManualForm_neverSavesWithoutConfirm() {
        launchCapture()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("f02-capture-viewfinder").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("label").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("f02-capture-viewfinder").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("f02-capture-shutter").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("f02-capture-ocr-draft").fetchSemanticsNodes().isNotEmpty() }

        // The SAME form as the manual twin, PREFILLED by the parser…
        rule.onNodeWithTag("f02-custom-kcal").assertExists()

        // …confirm-gated: saving without a name is refused with kind copy.
        rule.onNodeWithTag("f02-custom-save").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("f02-capture-notice").fetchSemanticsNodes().isNotEmpty() }

        // Name it, confirm, and the catalog gains the food.
        rule.onNodeWithTag("f02-custom-name").performTextReplacement("port label food")
        rule.onNodeWithTag("f02-custom-save").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("f02-capture-saved").fetchSemanticsNodes().isNotEmpty() }

        runBlocking {
            val koin = GlobalContext.get()
            val foods = koin.get<FoodRepository>()
            val profiles = koin.get<app.wlo.core.data.ProfileRepository>()
            val profileId = profiles.active().getOrNull()!!.id
            val hits = (foods.search(profileId, "port label food").getOrNull()).orEmpty()
            assertTrue(hits.isNotEmpty(), "the confirmed OCR draft became a catalog food")
        }
    }

    @Test
    public fun barcode_manualTyping_realProduct_thenCached() {
        launchCapture()
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("f02-capture-viewfinder").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("barcode").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("f02-capture-barcode-field").fetchSemanticsNodes().isNotEmpty() }

        // R-U15 twin: TYPE the barcode (equal-status path), REAL OFF lookup.
        rule.onNodeWithTag("f02-capture-barcode-field").performTextInput("3017620422003")
        rule.onNodeWithContentDescription("Look up").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("f02-capture-product").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("f02-capture-product-attribution").assertExists()

        // Save as food → then re-scan the same barcode → the honest cached chip.
        rule.onNodeWithTag("f02-capture-product-save-food").performClick()
        SystemClock.sleep(1_500)
        rule.onNodeWithTag("f02-capture-again").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("f02-capture-barcode-field").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("f02-capture-barcode-field").performTextInput("3017620422003")
        rule.onNodeWithContentDescription("Look up").performClick()
        rule.waitUntil(30_000) { rule.onAllNodesWithTag("f02-capture-product").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("f02-capture-product-cached").assertExists()
    }

    // --- fakes ---------------------------------------------------------------
}

/** Deterministic PhotoAnalyzer fake: Food-101-style top-5 with one dominant class. */
internal class FakePhotoAnalyzer(
    public var top1: Double = 0.42,
) : PhotoAnalyzer {
    override suspend fun analyze(frame: CapturedFrame): app.wlo.core.model.Analysis<List<FoodSuggestion>> {
        val rest = (1.0 - top1) / 4.0
        val suggestions =
            listOf(
                // Item 0's density hint exceeds the fats band → the sanity rail
                // clamps it and the chip shows the marker + kind copy.
                FoodSuggestion("butter", top1, 1200.0, 20.0),
                FoodSuggestion("hamburger", rest, 260.0, 220.0),
                FoodSuggestion("french_fries", rest, 310.0, 150.0),
                FoodSuggestion("greek_salad", rest, 130.0, 250.0),
                FoodSuggestion("ice_cream", rest, 210.0, 130.0),
            )
        return app.wlo.core.model.Analysis(
            value = suggestions,
            confidence = top1,
            provenance =
                Provenance.Estimated(
                    at = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
                    method = "fake analyzer (test)",
                    confidence = top1,
                    modelId = MODEL_ID,
                    consentGranted = false,
                ),
            held = top1 < PhotoAnalyzer.DEFAULT_HOLD_CONFIDENCE,
        )
    }

    public companion object {
        public const val MODEL_ID: String = "food-classifier/1"
    }
}

/**
 * Zoo-less ModelManager fake: the flow's ensure step passes WITHOUT any
 * network (the fake analyzer never reads the artifact — a real-path probe
 * would trigger a fresh zoo download inside the UI wait, the opposite of
 * deterministic).
 */
internal class FakeModelManager : ModelManager {
    override suspend fun ensureAvailable(modelId: String): Result<ModelHandle> =
        Result.success(ModelHandle(modelId = modelId, sizeBytes = 0L, sha256Hex = "test", filePath = null))

    override suspend fun reclaim(modelId: String): Result<Unit> = Result.success(Unit)

    public companion object {
        public const val MODEL_ID: String = "food-classifier/1"
    }
}

/** Deterministic OcrReader fake: a crisp label block (the parser's happy path). */
internal class FakeOcrReader : OcrReader {
    override suspend fun read(frame: CapturedFrame): app.wlo.core.model.Analysis<String> =
        app.wlo.core.model.Analysis(
            value =
                "Nutrition Facts\nPer 100 g\nEnergy 250 kcal\nTotal Fat 12 g\n" +
                    "Carbohydrate 30 g\nFibre 3.5 g\nProtein 8 g",
            confidence = null,
            provenance = Provenance.Measured(at = kotlinx.datetime.Instant.fromEpochMilliseconds(0), instrument = "fake OCR (test)"),
            held = false,
        )
}
