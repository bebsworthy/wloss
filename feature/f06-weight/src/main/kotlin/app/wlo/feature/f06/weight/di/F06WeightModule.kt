package app.wlo.feature.f06.weight.di

import app.wlo.core.datastore.SettingsStore
import app.wlo.feature.f06.weight.state.BodyFatViewModel
import app.wlo.feature.f06.weight.state.BodySectionUi
import app.wlo.feature.f06.weight.state.LogbookViewModel
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
        viewModel { (initialSheetOpen: Boolean, initialSection: BodySectionUi) ->
            WeighInViewModel(
                clock = get(),
                profiles = get(),
                weighIns = get(),
                measurements = get(),
                massUnits = get<SettingsStore>().massUnit,
                initialSheetOpen = initialSheetOpen,
                initialSection = initialSection,
            )
        }
        viewModel { BodyFatViewModel(clock = get(), profiles = get(), measurements = get(), settings = get()) }
        viewModel {
            LogbookViewModel(
                clock = get(),
                profiles = get(),
                weighIns = get(),
                measurements = get(),
                settings = get(),
            )
        }
    }
