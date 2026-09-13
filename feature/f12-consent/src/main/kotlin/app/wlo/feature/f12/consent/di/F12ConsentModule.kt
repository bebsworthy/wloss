package app.wlo.feature.f12.consent.di

import app.wlo.feature.f12.consent.state.AiStudioViewModel
import app.wlo.feature.f12.consent.state.ReceiptsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * F12 consent-shell graph (ARCHITECTURE §2.2 feature shape): the AI Studio
 * and receipt-log state holders. Inputs arrive through spine doors —
 * [app.wlo.core.consent.ConsentLedger]/[app.wlo.core.consent.ConsentGate]
 * (bound in the composition root over the Room store) and
 * [app.wlo.core.ports.ReceiptAuditLog] (the D1 port over `:core:network`'s
 * ledger) — plus the typed settings store for the kill switch and the
 * ADR-008 diagnostics toggles.
 */
public val f12ConsentModule: Module =
    module {
        viewModel { AiStudioViewModel(ledger = get(), gate = get(), settings = get()) }
        viewModel { ReceiptsViewModel(audit = get()) }
    }
