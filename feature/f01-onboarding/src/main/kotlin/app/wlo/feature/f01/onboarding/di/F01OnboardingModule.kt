package app.wlo.feature.f01.onboarding.di

import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.feature.f01.onboarding.domain.FinishOnboarding
import app.wlo.feature.f01.onboarding.domain.FinishWeightFirstOnboarding
import app.wlo.feature.f01.onboarding.domain.WeightFirstOnboardingStore
import app.wlo.feature.f01.onboarding.state.GoalsEditorViewModel
import app.wlo.feature.f01.onboarding.state.OnboardingViewModel
import app.wlo.feature.f01.onboarding.state.WeightFirstOnboardingViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * F01 onboarding graph (ARCHITECTURE §2.2 feature shape): the wizard state
 * holder plus its interactors. Cross-module inputs arrive through spine doors
 * ([ProfileRepository]/[MeasurementRepository] via `:core:data`,
 * [JsonDocumentStore] via `:core:datastore`, the sealed writers via
 * `TargetsWriters`) — the composition root binds their implementations; the
 * shipped template library itself is bound in `:app` (it reads Android assets).
 */
public val f01OnboardingModule: Module =
    module {
        viewModel {
            app.wlo.feature.f01.onboarding.state
                .IntakeTargetViewModel(get(), get(), get(), get(), get(), get(), get())
        }
        single { WeightFirstOnboardingStore(documents = get()) }
        single {
            FinishWeightFirstOnboarding(
                profiles = get(),
                weighIns = get(),
                settings = get(),
                store = get(),
                clock = get(),
            )
        }
        viewModel { WeightFirstOnboardingViewModel(store = get(), finisher = get()) }
        single {
            FinishOnboarding(
                profiles = get(),
                measurements = get(),
                writers = get(),
                documents = get(),
                clock = get(),
            )
        }
        viewModel {
            OnboardingViewModel(
                profiles = get(),
                library = get(),
                finisher = get(),
                documents = get(),
                settings = get(),
                clock = get(),
            )
        }
        viewModel {
            GoalsEditorViewModel(
                profiles = get(),
                targets = get(),
                writers = get(),
                weighIns = get(),
                dayProjection = get(),
                settings = get(),
                documents = get(),
                clock = get(),
            )
        }
    }
