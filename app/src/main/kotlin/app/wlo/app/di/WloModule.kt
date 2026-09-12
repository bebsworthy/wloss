package app.wlo.app.di

import android.content.Context
import androidx.room3.RoomDatabase
import app.wlo.app.ui.shell.ShellViewModel
import app.wlo.core.common.ClockPort
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
    }
