package app.wlo.feature.f10.hub.di

import app.wlo.feature.f10.hub.state.HubViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * F10 graph (ARCHITECTURE §2.2 feature shape): the Hub state holder. The Hub
 * computes no science — its spine doors (projection, weigh-ins, diary,
 * targets) are bound by the composition root.
 */
public val f10HubModule: Module =
    module {
        viewModel {
            HubViewModel(
                clock = get(),
                profiles = get(),
                dayProjection = get(),
                targets = get(),
                weighIns = get(),
                diary = get(),
                planner = get(),
            )
        }
    }
