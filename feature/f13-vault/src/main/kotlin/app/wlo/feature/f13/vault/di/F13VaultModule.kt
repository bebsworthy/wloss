package app.wlo.feature.f13.vault.di

import app.wlo.feature.f13.vault.state.AndroidImportSourceReader
import app.wlo.feature.f13.vault.state.BackupControlsViewModel
import app.wlo.feature.f13.vault.state.ExportViewModel
import app.wlo.feature.f13.vault.state.ImportSourceReader
import app.wlo.feature.f13.vault.state.ImportViewModel
import app.wlo.feature.f13.vault.state.RestoreWizardViewModel
import app.wlo.feature.f13.vault.state.VaultDashboardViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * F13 vault-surface graph (ARCHITECTURE §2.2 feature shape). Inputs arrive
 * through spine doors: [app.wlo.core.ports.DataVaultPort] (the D1 port the
 * composition root binds to the backup/restore pipeline), the typed settings
 * store, and [app.wlo.core.ports.BackupScheduler] for the daily WorkManager
 * schedule.
 */
public val f13VaultModule: Module =
    module {
        single<ImportSourceReader> { AndroidImportSourceReader(androidContext()) }
        viewModel {
            VaultDashboardViewModel(
                vault = get(),
                scheduler = get(),
                profiles = get(),
                healthConnectSync = get(),
            )
        }
        viewModel { BackupControlsViewModel(vault = get(), settings = get(), scheduler = get()) }
        viewModel { RestoreWizardViewModel(vault = get()) }
        viewModel { ExportViewModel(vault = get()) }
        viewModel { ImportViewModel(vault = get(), settings = get(), reader = get(), savedStateHandle = get()) }
    }
