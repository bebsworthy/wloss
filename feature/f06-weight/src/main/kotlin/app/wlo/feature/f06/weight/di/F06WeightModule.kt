package app.wlo.feature.f06.weight.di

import app.wlo.feature.f06.weight.state.BodyFatViewModel
import app.wlo.feature.f06.weight.state.WeighInViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * F06 graph (ARCHITECTURE §2.2 feature shape): the weigh-in and body-fat
 * state holders. Inputs arrive through spine doors (:core:data repositories)
 * bound by the composition root.
 */
public val f06WeightModule: Module =
    module {
        viewModel { (initialSheetOpen: Boolean) ->
            WeighInViewModel(
                clock = get(),
                profiles = get(),
                weighIns = get(),
                measurements = get(),
                initialSheetOpen = initialSheetOpen,
            )
        }
        viewModel { BodyFatViewModel(clock = get(), profiles = get(), measurements = get()) }
    }
