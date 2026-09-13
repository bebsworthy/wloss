package app.wlo.feature.f04.shopping.di

import app.wlo.feature.f04.shopping.state.ListViewModel
import app.wlo.feature.f04.shopping.state.PantryViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * F04 graph (ARCHITECTURE §2.2 feature shape): the list + pantry state
 * holders. Inputs arrive through spine doors (:core:data repositories, the
 * settings store) and the shared capture ports (the OFF lookup door for the
 * barcode check-in); the composition root binds the implementations.
 */
public val f04ShoppingModule: Module =
    module {
        viewModel {
            ListViewModel(
                clock = get(),
                profiles = get(),
                planner = get(),
                list = get(),
                pantry = get(),
                recipes = get(),
                settings = get(),
            )
        }
        viewModel {
            PantryViewModel(
                clock = get(),
                profiles = get(),
                pantry = get(),
                groceries = get(),
                settings = get(),
                off = get(),
            )
        }
    }
