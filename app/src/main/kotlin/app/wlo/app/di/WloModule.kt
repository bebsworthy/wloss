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
import app.wlo.core.consent.ReplayConsentGate
import app.wlo.core.data.CorrectionCacheStore
import app.wlo.core.data.DayProjectionRepository
import app.wlo.core.data.DayProjector
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.FoodRepository
import app.wlo.core.data.GroceryRepository
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.PantryRepository
import app.wlo.core.data.PlannerRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.RecipeRepository
import app.wlo.core.data.RoomDayProjectionRepository
import app.wlo.core.data.RoomDiaryRepository
import app.wlo.core.data.RoomFoodRepository
import app.wlo.core.data.RoomGroceryRepository
import app.wlo.core.data.RoomMeasurementRepository
import app.wlo.core.data.RoomPantryRepository
import app.wlo.core.data.RoomPlannerRepository
import app.wlo.core.data.RoomProfileRepository
import app.wlo.core.data.RoomRecipeRepository
import app.wlo.core.data.RoomShoppingListRepository
import app.wlo.core.data.RoomTargetsRepository
import app.wlo.core.data.RoomWeighInRepository
import app.wlo.core.data.ShoppingListRepository
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
import app.wlo.feature.f03.planning.di.f03PlanningModule
import app.wlo.feature.f04.shopping.di.f04ShoppingModule
import app.wlo.feature.f06.weight.di.f06WeightModule
import app.wlo.feature.f10.hub.di.f10HubModule
import app.wlo.feature.f12.consent.di.f12ConsentModule
import app.wlo.feature.f13.vault.di.f13VaultModule
import kotlinx.coroutines.flow.first
import okio.Path
import okio.Path.Companion.toPath
import org.koin.androidx.viewmodel.dsl.viewModel
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
        // The Settings surface's app-lock state (M6 PART B).
        viewModel {
            app.wlo.app.ui.settings
                .SettingsViewModel(settings = get(), appLock = get())
        }
        // The profile-facts editor (WLO-0035 W4): onboarding answers, correctable.
        viewModel {
            app.wlo.app.ui.settings
                .ProfileFactsViewModel(profiles = get())
        }
        // Debug egress monitor (F12 §3.8): reads the persisted receipt ledger.
        factory { EgressMonitorViewModel(ledger = get(), zoo = get()) }
        // The F12 model manager (R-S14): the wlo://ai/models surface's state.
        factory { ZooViewModel(zoo = get()) }
        includes(
            f01OnboardingModule,
            f02FoodModule,
            f03PlanningModule,
            f04ShoppingModule,
            f06WeightModule,
            f10HubModule,
            f12ConsentModule,
            f13VaultModule,
        )
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

        // F03/F04 planning spine (M5): the planner + list + pantry + recipe
        // doors, and the shipped seed bundle's reader (R-S3 content rules).
        single<RecipeRepository> { RoomRecipeRepository(db = get<WloDatabase>()) }
        single<PlannerRepository> {
            RoomPlannerRepository(
                db = get<WloDatabase>(),
                targets = get<TargetsRepository>(),
                recipes = get<RecipeRepository>(),
                diary = get<DiaryRepository>(),
                projector = get<DayProjector>(),
                clock = get<ClockPort>(),
            )
        }
        single<PantryRepository> { RoomPantryRepository(db = get<WloDatabase>(), clock = get<ClockPort>()) }
        single<ShoppingListRepository> {
            RoomShoppingListRepository(
                db = get<WloDatabase>(),
                planner = get<PlannerRepository>(),
                recipes = get<RecipeRepository>(),
                pantry = get<PantryRepository>(),
                clock = get<ClockPort>(),
            )
        }
        single<GroceryRepository> { RoomGroceryRepository(db = get<WloDatabase>()) }

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
        // M6 PART B: the PERSISTENT consent ledger (Room `consent_ledger`,
        // hash-chained) — grants survive restarts and ride every backup. The
        // kill-switch wrapper refuses new GRANTS while Cloud: OFF (F12 §3.4).
        single<ConsentLedger> {
            app.wlo.app.di.KillSwitchConsentLedger(
                delegate =
                    app.wlo.core.data
                        .RoomConsentLedger(db = get(), timeSource = get()),
                settings = get(),
            )
        }
        // The dispatcher's gate: replay of the ledger, overridden by the kill
        // switch — a stale grant can never send a byte while it is on.
        single<ConsentGate> {
            app.wlo.app.di.KillSwitchConsentGate(
                delegate = ReplayConsentGate(ledger = get()),
                settings = get(),
            )
        }
        // R-C4, verbatim: "F13 integration toggle (not an F12 AI category),
        // default on, cached, per-lookup audit trail" — the REAL settings
        // toggle now that M6 ships the Data Vault settings keys.
        single<OffLookupPolicy> {
            val settings = get<app.wlo.core.datastore.SettingsStore>()
            OffLookupPolicy { settings.foodDbLookupsEnabled.first() }
        }
        // T-K4 diagnostics: the opt-in crash-report toggle + endpoint,
        // default OFF / empty (the dispatcher denies itself until both exist).
        single<app.wlo.core.ports.DiagnosticsPolicy> {
            val settings = get<app.wlo.core.datastore.SettingsStore>()
            app.wlo.core.ports
                .DiagnosticsPolicy { settings.diagnosticsCrashReports.first() }
        }
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

        // --- F13 Data Vault (M6 PART A, WLO-0028) --------------------------
        // Storage accounting + attachment partitions (the M4 photo pipeline's
        // persistence tenant; R-U14/R-U18 posture enforced in VaultFileStore).
        single {
            app.wlo.core.vault
                .VaultFileStore(context = get<Context>())
        }
        // Backup assembly + restore machinery over the real spine.
        single {
            app.wlo.core.vault.SnapshotAssembler(
                db = get<WloDatabase>(),
                settings = get<app.wlo.core.datastore.SettingsStore>(),
                documents = get<JsonDocumentStore>(),
            )
        }
        single<app.wlo.core.vault.AutoBackupKeyProvider> {
            get<app.wlo.core.vault.AutoBackupKeyVault>()
        }
        single<app.wlo.core.vault.BackupStoreFactory> {
            val context = get<Context>()
            app.wlo.core.vault.BackupStoreFactory { destination ->
                app.wlo.core.vault
                    .SafBackupStore(context, destination)
            }
        }
        single<app.wlo.core.vault.EpochClock> {
            app.wlo.core.vault
                .EpochClock { System.currentTimeMillis() }
        }
        single<app.wlo.core.vault.BackupManager> {
            app.wlo.core.vault.BackupManager(
                assembler = get(),
                storeFactory = get(),
                autoKeys = get(),
                clock = get(),
            )
        }
        single<app.wlo.core.vault.StagedRestorer> {
            app.wlo.core.vault
                .StagedRestorer(db = get<WloDatabase>())
        }
        single<app.wlo.core.vault.RestoreCommitter> {
            app.wlo.core.vault.RestoreCommitter(
                db = get<WloDatabase>(),
                settings = get<app.wlo.core.datastore.SettingsStore>(),
                documents = get<JsonDocumentStore>(),
                projector = get<app.wlo.core.data.DayProjector>(),
            )
        }
        // BackupScheduler port impl: WorkManager daily once a folder is chosen.
        single<app.wlo.core.ports.BackupScheduler> {
            app.wlo.core.vault
                .WorkManagerBackupScheduler(context = get<Context>())
        }
        // App-lock state holder (PART B renders the lock surface + gate flow).
        single {
            app.wlo.core.vault
                .AppLockController()
        }

        // --- M6 PART B: the F12 receipt viewer + F13 vault-surface ports -----
        // The receipt ledger's READ side (D1 port over the Room ledger).
        single<app.wlo.core.ports.ReceiptAuditLog> {
            app.wlo.app.di
                .RoomReceiptAudit(ledger = get())
        }
        // The concrete auto-key vault (the AutoBackupKeyProvider binding below
        // wraps it; the VaultPortAdapter needs the storing half too).
        single<app.wlo.core.vault.AutoBackupKeyVault> {
            app.wlo.core.vault
                .AutoBackupKeyVault(context = get<Context>())
        }
        single<app.wlo.core.ports.DataVaultPort> {
            app.wlo.app.di.VaultPortAdapter(
                manager = get(),
                assembler = get(),
                restorer = get(),
                committer = get(),
                vaultFiles = get(),
                autoKeys = get(),
                storeFactory = get(),
                settings = get(),
                documents = get(),
                db = get(),
                measurements = get(),
                diaries = get(),
                profiles = get(),
                clock = get(),
            )
        }
    }
