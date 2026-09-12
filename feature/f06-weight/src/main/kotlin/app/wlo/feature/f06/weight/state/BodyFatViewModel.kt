package app.wlo.feature.f06.weight.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.NewMeasurement
import app.wlo.core.data.ProfileRepository
import app.wlo.core.engines.BodyFatEngine
import app.wlo.core.engines.BodyFatEstimate
import app.wlo.core.model.BodyFatMethod
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.MeasurementAttr
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import app.wlo.core.model.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/** Body-fat intents (MVI-lite). */
public sealed interface BodyFatEvent {
    public data class MethodChange(
        public val method: BodyFatMethod,
    ) : BodyFatEvent

    public data class WaistChange(
        public val text: String,
    ) : BodyFatEvent

    public data class NeckChange(
        public val text: String,
    ) : BodyFatEvent

    public data class HipChange(
        public val text: String,
    ) : BodyFatEvent

    public data object Compute : BodyFatEvent

    public data object SaveToLogbook : BodyFatEvent
}

/** One computed estimate + everything the provenance chip/sheet needs. */
public data class BodyFatUi(
    public val method: BodyFatMethod,
    public val percentLabel: String,
    /** D6-typed estimate (estimated provenance: formula + inputs on tap). */
    public val value: DerivedValue<Double>,
    public val rows: List<Pair<String, String>>,
)

/** The body-fat surface's state. */
public data class BodyFatUiState(
    public val method: BodyFatMethod = BodyFatMethod.NAVY_TAPE,
    public val waistText: String = "",
    public val neckText: String = "",
    public val hipText: String = "",
    public val heightCm: Double? = null,
    public val sex: Sex? = null,
    public val estimate: BodyFatUi? = null,
    public val notice: String? = null,
)

/**
 * The body-fat method registry's state holder (F06 §3: "a method registry,
 * never one number"). Every result is `estimated` provenance — girth formulas
 * are estimates with formula + inputs on tap, never measurements. The navy
 * series and the RFM series are kept apart by the method attr at save time.
 */
public class BodyFatViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val measurements: MeasurementRepository,
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private var profileId: String? = null

    private val state = MutableStateFlow(BodyFatUiState())
    private var lastEstimate: BodyFatEstimate? = null

    /** Renderable state. */
    public val uiState: StateFlow<BodyFatUiState> = state

    init {
        viewModelScope.launch {
            profiles.active().getOrNull()?.let { profile ->
                profileId = profile.id
                state.value =
                    state.value.copy(
                        heightCm = profile.heightCm,
                        sex = profile.sex,
                    )
            }
        }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: BodyFatEvent) {
        when (event) {
            is BodyFatEvent.MethodChange -> state.value = state.value.copy(method = event.method)
            is BodyFatEvent.WaistChange -> state.value = state.value.copy(waistText = event.text)
            is BodyFatEvent.NeckChange -> state.value = state.value.copy(neckText = event.text)
            is BodyFatEvent.HipChange -> state.value = state.value.copy(hipText = event.text)
            BodyFatEvent.Compute -> compute()
            BodyFatEvent.SaveToLogbook -> saveToLogbook()
        }
    }

    private fun compute() {
        val current = state.value
        val height = current.heightCm ?: return
        val waist = current.waistText.toDoubleOrNull() ?: return
        val neck = current.neckText.toDoubleOrNull()
        val hip = current.hipText.toDoubleOrNull()
        val estimate =
            runCatching {
                BodyFatEngine.estimate(
                    method = current.method,
                    sex = current.sex,
                    heightCm = height,
                    waistCm = waist,
                    neckCm = neck,
                    hipCm = hip,
                    at = clock.now(),
                )
            }.getOrNull()
        if (estimate == null) {
            state.value = state.value.copy(notice = "those numbers don't fit the formula — check the tape values")
            return
        }
        lastEstimate = estimate
        state.value =
            current.copy(
                estimate =
                    BodyFatUi(
                        method = estimate.method,
                        percentLabel = "${format1(estimate.estimate.value)} %",
                        value = estimate.estimate,
                        rows =
                            listOf(
                                "method" to methodLabel(estimate.method),
                                "formula" to estimate.formulaVersion,
                            ) + estimate.inputs.map { it.substringBefore('=') to it.substringAfter('=') } +
                                listOf(
                                    "reads as" to "an estimate (±3–4 % typical) — never a measurement",
                                ),
                    ),
                notice = null,
            )
    }

    private fun saveToLogbook() {
        val id = profileId ?: return
        val estimate = lastEstimate ?: return
        viewModelScope.launch {
            val today = DayBoundary.epochDay(clock.now(), zone)
            val appended =
                measurements.append(
                    NewMeasurement(
                        profileId = id,
                        dayEpochDay = today,
                        kind = MeasurementKind.BODY_FAT,
                        valueReal = estimate.estimate.value,
                        source = MeasurementSource.MANUAL,
                        capturedAt = clock.now(),
                    ),
                )
            when (appended) {
                is WloResult.Ok -> {
                    val attr =
                        MeasurementAttr(
                            eventId = appended.value.id,
                            attr = "method",
                            valueText = estimate.method.wireName,
                        )
                    measurements.attachAttrs(appended.value.id, listOf(attr))
                    state.value = state.value.copy(notice = "saved — each method keeps its own series")
                }

                is WloResult.Err -> state.value = state.value.copy(notice = "that didn't save — nothing changed")
            }
        }
    }

    private fun methodLabel(method: BodyFatMethod): String =
        when (method) {
            BodyFatMethod.NAVY_TAPE -> "US Navy tape (Hodgdon & Beckett)"
            BodyFatMethod.RFM -> "RFM (Woolcott & Bergman 2012)"
        }

    public companion object {
        /** One-decimal label for percent estimates. */
        public fun format1(value: Double): String {
            val tenths = (value * 10).toLong()
            val whole = tenths / 10
            val tenth = tenths % 10
            return "$whole.$tenth"
        }
    }
}
