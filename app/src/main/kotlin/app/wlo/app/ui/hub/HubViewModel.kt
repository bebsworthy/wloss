package app.wlo.app.ui.hub

import androidx.lifecycle.ViewModel
import app.wlo.core.common.ClockPort
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.HoldReason
import app.wlo.core.model.Provenance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant

/**
 * Hub state (M1 demo). Stat values are display-formatted with their unit
 * glyph baked in (R-D10 metric default, R-D12 unit glyphs + "Weight trend"
 * naming) but still travel as [DerivedValue] — the UI has no other rendering
 * path (D6).
 */
public data class HubUiState(
    val todayLabel: String,
    val stats: List<HubStatUi>,
)

/** One hub stat: label + derived value + how the number came to be. */
public data class HubStatUi(
    val id: String,
    val label: String,
    val value: DerivedValue<String>,
)

/** Hub intents (MVI-lite): one channel in, state out. */
public sealed interface HubEvent {
    public data object Refresh : HubEvent
}

/**
 * M1 hub state holder. The demo numbers are constructed here (a later
 * milestone sources them from :core:data + the energy engine); provenance
 * metadata is already realistic — measured / derived / held — so the
 * provenance-chip rendering path is exercised end-to-end.
 */
public class HubViewModel(
    private val clock: ClockPort,
) : ViewModel() {
    private val _uiState: MutableStateFlow<HubUiState> = MutableStateFlow(loadDemoState())

    /** Renderable hub state. */
    public val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    /** MVI-lite intent entry point. */
    public fun onEvent(event: HubEvent) {
        when (event) {
            HubEvent.Refresh -> _uiState.value = loadDemoState()
        }
    }

    private fun loadDemoState(): HubUiState {
        val now: Instant = clock.now()
        return HubUiState(
            todayLabel = "Tuesday",
            stats =
                listOf(
                    HubStatUi(
                        id = "weight-trend",
                        label = "Weight trend",
                        value =
                            DerivedValue(
                                value = "82.4 kg",
                                provenance = Provenance.Measured(at = now, instrument = "scale"),
                            ),
                    ),
                    HubStatUi(
                        id = "measured-burn",
                        label = "Measured burn",
                        value =
                            DerivedValue(
                                value = "2,410 kcal",
                                provenance =
                                    Provenance.Derived(
                                        formulaVersion = "energy-engine-v0",
                                        inputs = listOf("weight-trend", "check-in"),
                                    ),
                            ),
                    ),
                    HubStatUi(
                        id = "energy-proposal",
                        label = "Energy proposal",
                        value =
                            DerivedValue(
                                value = "1,900 kcal",
                                provenance = Provenance.Held(HoldReason.INSUFFICIENT_DATA),
                            ),
                    ),
                ),
        )
    }
}
