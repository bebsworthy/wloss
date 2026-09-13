package app.wlo.feature.f12.consent.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.consent.ConsentDecision
import app.wlo.core.consent.ConsentGate
import app.wlo.core.consent.ConsentLedger
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.model.ConsentCapability
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One AI Studio consent row (F12 §3.4): the frozen category (R-C1), its
 * one-line plain-words explainer, the current status (off / on-device /
 * on with cloud once a provider exists), and the toggle binding. Status is
 * derived, never stored: the ledger replay is the truth.
 */
public data class ConsentRowState(
    public val capability: ConsentCapability,
    public val title: String,
    public val oneLiner: String,
    public val fallbackNote: String,
    /** "off" | "on-device" | "cloud" — cloud shows once a provider exists (v1.x). */
    public val status: String,
    public val granted: Boolean,
    /** The zoo surface serves this category's model card / download (food-photo v1). */
    public val zooLink: Boolean,
    /** Enabled = toggle interactive (the kill switch disables every row). */
    public val enabled: Boolean,
)

/** The AI Studio's whole state (F12 §3.4/§5, ADR-008 diagnostics section). */
public data class AiStudioUiState(
    public val rows: List<ConsentRowState> = emptyList(),
    public val killSwitch: Boolean = false,
    /** R-C7: the honest subtitle under the kill switch — zero-guilt, zero-drama. */
    public val grantedCount: Int = 0,
    public val foodDbLookupsEnabled: Boolean = true,
    public val diagnosticsEnabled: Boolean = false,
    public val diagnosticsEndpoint: String = "",
    public val consentHistoryCount: Int = 0,
)

/**
 * The AI Studio state holder. Toggles WRITE the ledger (grant/revoke —
 * revocation is a new entry, history is evidence, F12 §3.6); the kill switch
 * is a settings posture that (a) renders every row off + disabled and (b) is
 * enforced by the bound ConsentGate — the dispatcher re-derives every denial
 * from it, so a stale grant can never send a byte while it is on.
 */
public class AiStudioViewModel(
    private val ledger: ConsentLedger,
    private val gate: ConsentGate,
    private val settings: SettingsStore,
) : ViewModel() {
    public val state: StateFlow<AiStudioUiState> =
        combine(
            ledger.observe(),
            settings.aiCloudKillSwitch,
            settings.foodDbLookupsEnabled,
            settings.diagnosticsCrashReports,
            settings.diagnosticsEndpoint,
        ) { entries, kill, foodDb, diag, endpoint ->
            // Latest entry per capability wins (the ledger replay).
            val latest =
                buildMap {
                    for (entry in entries) put(entry.capability, entry.decision)
                }
            val standingGrants =
                latest.count { it.value == ConsentDecision.GRANT }
            AiStudioUiState(
                rows =
                    ConsentCapability.entries.map { capability ->
                        // While Cloud: OFF, every glyph reads "off" (the
                        // enforced denial is rendered, not implied).
                        val isGranted = !kill && latest[capability] == ConsentDecision.GRANT
                        ConsentRowState(
                            capability = capability,
                            title = CATEGORY_TITLES.getValue(capability),
                            oneLiner = CATEGORY_ONE_LINERS.getValue(capability),
                            fallbackNote = CATEGORY_FALLBACKS.getValue(capability),
                            status = if (isGranted) STATUS_ON_DEVICE else STATUS_OFF,
                            granted = isGranted,
                            zooLink = capability == ConsentCapability.FOOD_PHOTO,
                            enabled = !kill,
                        )
                    },
                killSwitch = kill,
                grantedCount = if (kill) 0 else standingGrants,
                foodDbLookupsEnabled = foodDb,
                diagnosticsEnabled = diag,
                diagnosticsEndpoint = endpoint,
                consentHistoryCount = entries.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiStudioUiState())

    /** Toggle one category. While the kill switch is on the row is disabled —
     *  this is belt-and-braces for the UI contract, not a second gate (the
     *  bound ledger also refuses grants while Cloud: OFF). */
    public fun toggle(capability: ConsentCapability) {
        viewModelScope.launch {
            if (settings.aiCloudKillSwitch.first()) return@launch
            val grant = !gate.isGranted(capability)
            runCatching {
                ledger.record(capability, if (grant) ConsentDecision.GRANT else ConsentDecision.REVOKE)
            } // a refused write leaves the ledger (and the row) exactly as it was
        }
    }

    public fun setKillSwitch(on: Boolean) {
        viewModelScope.launch {
            settings.setAiCloudKillSwitch(on)
        }
    }

    public fun setFoodDbLookups(enabled: Boolean) {
        viewModelScope.launch { settings.setFoodDbLookupsEnabled(enabled) }
    }

    public fun setDiagnostics(enabled: Boolean) {
        viewModelScope.launch { settings.setDiagnosticsCrashReports(enabled) }
    }

    public fun setDiagnosticsEndpoint(url: String) {
        viewModelScope.launch { settings.setDiagnosticsEndpoint(url) }
    }

    private companion object {
        const val STATUS_OFF: String = "off"
        const val STATUS_ON_DEVICE: String = "on-device"

        /** R-C1 frozen order and names; copy is plain words, zero guilt (R-C7). */
        val CATEGORY_TITLES: Map<ConsentCapability, String> =
            mapOf(
                ConsentCapability.FOOD_PHOTO to "Food photo",
                ConsentCapability.VOICE_INPUT to "Voice input",
                ConsentCapability.MEAL_PLANNING to "Meal planning",
                ConsentCapability.SILHOUETTE to "Silhouette",
                ConsentCapability.POOP_PHOTO to "Poop photo",
                ConsentCapability.INSIGHTS_CHAT to "Insights chat",
            )

        val CATEGORY_ONE_LINERS: Map<ConsentCapability, String> =
            mapOf(
                ConsentCapability.FOOD_PHOTO to
                    "Let a cloud model read your food photos and labels for estimates.",
                ConsentCapability.VOICE_INPUT to
                    "Send a spoken log's audio or transcript to a cloud speech service.",
                ConsentCapability.MEAL_PLANNING to
                    "Let a cloud model write or refine meal plans and recipes.",
                ConsentCapability.SILHOUETTE to
                    "Cloud analysis of body composition shots (pose, framing).",
                ConsentCapability.POOP_PHOTO to
                    "Cloud classification of stool photos against the Bristol scale.",
                ConsentCapability.INSIGHTS_CHAT to
                    "Chat about your data with a cloud model for deeper narratives.",
            )

        val CATEGORY_FALLBACKS: Map<ConsentCapability, String> =
            mapOf(
                ConsentCapability.FOOD_PHOTO to "Off stays on-device: recognizer + database estimates.",
                ConsentCapability.VOICE_INPUT to "Off stays on-device: local speech + parser.",
                ConsentCapability.MEAL_PLANNING to "Off stays on-device: templates + rules engine.",
                ConsentCapability.SILHOUETTE to
                    "Off stays on-device: pose + measurements. Body pixels are never stored.",
                ConsentCapability.POOP_PHOTO to "Off stays on-device: local Bristol classifier + manual tap.",
                ConsentCapability.INSIGHTS_CHAT to "Off stays on-device: rule-based insights.",
            )
    }
}
