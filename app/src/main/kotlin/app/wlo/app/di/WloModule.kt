package app.wlo.app.di

import android.content.Context
import androidx.room3.RoomDatabase
import app.wlo.app.ui.debug.EgressMonitorViewModel
import app.wlo.app.ui.shell.ShellViewModel
import app.wlo.app.ui.zoo.ZooViewModel
import app.wlo.core.ai.OkioZooManager
import app.wlo.core.ai.OnnxPhotoAnalyzer
import app.wlo.core.ai.WloBarcodeScanner
import app.wlo.core.ai.WloOcrReader
import app.wlo.core.ai.ZooManager
import app.wlo.core.ai.ZooManifest
import app.wlo.core.common.ClockPort
import app.wlo.core.consent.ConsentGate
import app.wlo.core.consent.ConsentLedger
import app.wlo.core.consent.ConsentTimeSource
import app.wlo.core.consent.InMemoryConsentLedger
import app.wlo.core.consent.ReplayConsentGate
import app.wlo.core.data.CorrectionCacheStore
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.DayProjector
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.FoodRepository
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.RoomDayProjectionRepository
import app.wlo.core.data.RoomDiaryRepository
import app.wlo.core.data.RoomFoodRepository
import app.wlo.core.data.RoomMeasurementRepository
import app.wlo.core.data.RoomProfileRepository
import app.wlo.core.data.RoomTargetsRepository
import app.wlo.core.data.RoomWeighInRepository
import app.wlo.core.data.TargetsRepository
import app.wlo.core.data.TargetsWriters
import app.wlo.core.data.WeighInRepository
import app.wlo.core.database.WloDatabase
import app.wlo.core.database.androidDatabaseBuilder
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.datastore.SettingsStoreFactory
import app.wlo.core.documents.OnboardingTemplates
import app.wlo.core.network.EgressLedger
import app.wlo.core.network.NetworkDispatcher
import app.wlo.core.network.OpenFoodFactsClient
import app.wlo.core.network.RoomEgressLedger
import app.wlo.core.ports.BarcodeScanner
import app.wlo.core.ports.EgressPort
import app.wlo.core.ports.ModelManager
import app.wlo.core.ports.OcrReader
import app.wlo.core.ports.OffLookupPolicy
import app.wlo.core.ports.OffRepository
import app.wlo.core.ports.PhotoAnalyzer
import app.wlo.feature.f01.onboarding.di.f01OnboardingModule
import app.wlo.feature.f01.onboarding.domain.TemplateLibrary
import app.wlo.feature.f02.food.di.f02FoodModule
import app.wlo.feature.f06.weight.di.f06WeightModule
import app.wlo.feature.f10.hub.di.f10HubModule
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module
import java.io.File

private const val SETTINGS_FILE: String = "wlo.settings.preferences_pb"
private const val DOCUMENTS_FILE: String = "wlo.documents.preferences_pb"
private const val ZOO_DIR: String = "zoo"
private const val ZOO_MANIFEST_ASSET: String = "zoo/manifest.json"

private fun settingsPath(context: Context): Path = File(context.filesDir, SETTINGS_FILE).absolutePath.toPath()

private fun documentsPath(context: Context): Path = File(context.filesDir, DOCUMENTS_FILE).absolutePath.toPath()

/**
 * State holders + ports: the MVI-lite shell, the F01 wizard, and the frozen
 * demo clock; the feature graphs (F01/F02/F06/F10) are included. Pure
 * definitions — `verify()` exercises this graph in unit tests.
 */
public val appModule: Module =
    module {
        single<ClockPort> { FixedClock(FixedClock.DEMO_NOW) }
        factory { ShellViewModel(profiles = get(), targets = get(), documents = get()) }
        // Debug egress monitor (F12 §3.8): reads the persisted receipt ledger.
        factory { EgressMonitorViewModel(ledger = get(), zoo = get()) }
        // The F12 model manager (R-S14): the wlo://ai/models surface's state.
        factory { ZooViewModel(zoo = get()) }
        includes(f01OnboardingModule, f02FoodModule, f06WeightModule, f10HubModule)
    }

/**
 * Platform bindings (Android-only, D1: visible only here): preferences
 * DataStore, the Room database, the data-spine repositories they back, and
 * the assets-backed shipped template library. The repositories are the ONLY
 * doors features see (ARCHITECTURE §2.2); the two Targets writers are handed
 * out through [TargetsWriters] (R-B2).
 */
public val platformModule: Module =
    module {
        single { SettingsStoreFactory.create(file = settingsPath(get<Context>())) }
        single { JsonDocumentStore.create(file = documentsPath(get<Context>())) }
        single(qualifier = qualifier("wlo-db-builder")) {
            androidDatabaseBuilder(context = get<Context>(), path = WloDatabase.NAME)
        }
        single<WloDatabase> {
            get<RoomDatabase.Builder<WloDatabase>>(qualifier = qualifier("wlo-db-builder")).build()
        }

        // Data spine (M2/M3): repositories + projections + the two writers.
        single<ProfileRepository> { RoomProfileRepository(db = get<WloDatabase>(), settings = get()) }
        single<DayProjector> { DayProjector(db = get<WloDatabase>(), clock = get<ClockPort>()) }
        single<MeasurementRepository> { RoomMeasurementRepository(db = get<WloDatabase>(), projector = get()) }
        single<FoodRepository> { RoomFoodRepository(db = get<WloDatabase>()) }
        single<DiaryRepository> {
            RoomDiaryRepository(
                db = get<WloDatabase>(),
                projector = get(),
                foodRepository = get<FoodRepository>(),
            )
        }
        single<WeighInRepository> { RoomWeighInRepository(measurements = get<MeasurementRepository>()) }
        single<TargetsRepository> { RoomTargetsRepository(db = get<WloDatabase>()) }
        single<DayProjectionRepository> {
            RoomDayProjectionRepository(
                db = get<WloDatabase>(),
                projector = get(),
                targets = get<TargetsRepository>(),
            )
        }
        single { TargetsWriters(db = get<WloDatabase>(), clock = get<ClockPort>()) }

        // F01 shipped template library: plain JSON assets, read through the
        // injectable [OnboardingTemplates.Reader] (F01 §3 — no template is
        // privileged; all inspectable, editable, excludable).
        single<OnboardingTemplates.Reader> {
            val assets = get<Context>().assets
            OnboardingTemplates.Reader { path ->
                assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        }
        single<TemplateLibrary> { TemplateLibrary { OnboardingTemplates.load(reader = get()) } }

        // --- F12 egress constitution (M4 PART A) -----------------------------
        // Real wall-clock for receipts (the demo [FixedClock] governs the data
        // spine only; egress paperwork must show true time).
        single<ConsentTimeSource> { ConsentTimeSource { System.currentTimeMillis() } }
        // Consent: the M2 in-memory ledger + replay gate (M6 swaps in the Room
        // ledger + UI). Empty grants = every FUTURE_* purpose fails closed —
        // the v1 default posture.
        single<ConsentLedger> { InMemoryConsentLedger(timeSource = get()) }
        single<ConsentGate> { ReplayConsentGate(ledger = get()) }
        // R-C4, verbatim: "F13 integration toggle (not an F12 AI category),
        // default on, cached, per-lookup audit trail" — default-ON until F13
        // ships the settings surface (M6 binds the real toggle here).
        single<OffLookupPolicy> { OffLookupPolicy { true } }
        // The receipt ledger over schema-v5 `network_receipts` (append-only).
        single<EgressLedger> { RoomEgressLedger(db = get()) }
        // THE single socket door (D1: only :app sees the implementation).
        single<EgressPort> {
            NetworkDispatcher(
                consentGate = get(),
                offLookupPolicy = get(),
                ledger = get(),
                timeSource = get(),
            )
        }
        // The R-S14 model zoo: hash-pinned download-once, size-disclosed,
        // reclaimable; manifest ships as a versioned app asset (ADR-007).
        single<ZooManifest> {
            val assets = get<Context>().assets
            ZooManifest.fromJson(
                assets.open(ZOO_MANIFEST_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() },
            )
        }
        single<ZooManager> {
            OkioZooManager(
                manifest = get(),
                egress = get(),
                fileSystem = okio.FileSystem.SYSTEM,
                baseDir = File(get<Context>().filesDir, ZOO_DIR).absolutePath.toPath(),
            )
        }
        single<ModelManager> { get<ZooManager>() }

        // --- F02 capture assists (M4 PART B) ------------------------------
        // On-device analyzer (ADR-006/ADR-007): loads the zoo model through
        // the handle; held-with-reason on every failure — never a crash.
        single<PhotoAnalyzer> { OnnxPhotoAnalyzer(context = get<Context>(), modelManager = get()) }
        // ML Kit bundled barcode engine with the ZXing fallback behind the
        // same port (audit cards: docs/tech/audit/mlkit-barcode.md).
        single<BarcodeScanner> { WloBarcodeScanner() }
        // ML Kit latin text recognition (audit card: mlkit-text-recognition.md).
        single<OcrReader> { WloOcrReader() }
        // R-C4 food-DB door: OFF v2, cache-first via the dispatcher.
        single<OffRepository> { OpenFoodFactsClient(egress = get()) }
        // R-B6 correction cache (document-backed; schema v6 may formalize).
        single { CorrectionCacheStore(documents = get<JsonDocumentStore>()) }
    }
