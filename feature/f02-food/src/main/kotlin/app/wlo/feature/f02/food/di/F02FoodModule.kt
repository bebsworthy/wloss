package app.wlo.feature.f02.food.di

import app.wlo.feature.f02.food.state.DiaryViewModel
import app.wlo.feature.f02.food.state.FoodLogViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * F02 graph (ARCHITECTURE §2.2 feature shape): the ladder and diary state
 * holders. Inputs arrive through spine doors (:core:data repositories) bound
 * by the composition root; this module only constructs its own types.
 */
public val f02FoodModule: Module =
    module {
        viewModel { (initialQuickAdd: Boolean) ->
            FoodLogViewModel(
                clock = get(),
                profiles = get(),
                foods = get(),
                diary = get(),
                initialQuickAdd = initialQuickAdd,
            )
        }
        viewModel { (initialDay: Long?, initialEntryId: String?) ->
            DiaryViewModel(
                clock = get(),
                profiles = get(),
                foods = get(),
                diary = get(),
                initialDay = initialDay,
                initialEntryId = initialEntryId,
            )
        }
    }
