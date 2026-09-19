package app.wlo.feature.f03.planning.di

import app.wlo.feature.f03.planning.state.PlanViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * F03 graph (ARCHITECTURE §2.2 feature shape): the plan surface's state
 * holder. Inputs arrive through spine doors (:core:data repositories) bound by
 * the composition root; focus parameters carry the deep-link context in.
 */
public val f03PlanningModule: Module =
    module {
        viewModel { (focusDay: Long?, focusSlot: String?) ->
            app.wlo.feature.f03.planning.state
                .AgendaViewModel(get(), get(), get(), get(), get(), get(), focusDay, focusSlot)
        }
        viewModel { (focusDay: Long?, focusSlot: String?) ->
            PlanViewModel(
                clock = get(),
                profiles = get(),
                planner = get(),
                dayProjection = get(),
                recipes = get(),
                diary = get(),
                initialFocusDay = focusDay,
                initialFocusSlot = focusSlot,
            )
        }
    }
