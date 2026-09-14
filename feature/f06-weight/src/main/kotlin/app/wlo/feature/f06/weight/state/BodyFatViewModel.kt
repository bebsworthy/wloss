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
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.engines.BodyFatEngine
import app.wlo.core.engines.BodyFatEstimate
import app.wlo.core.model.BodyFatMethod
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.MeasurementAttr
import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import app.wlo.core.model.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    private val settings: SettingsStore,
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
                val headline =
                    BodyFatMethod
                        .fromWireName(settings.bodyFatHeadlineMethod.first())
                        ?: BodyFatMethod.NAVY_TAPE
                state.value =
                    state.value.copy(
                        heightCm = profile.heightCm,
                        sex = profile.sex,
                        method = headline,
                    )
            }
            prefillTape()
        }
    }

    /** MVI-lite intent entry point. */
    public fun onEvent(event: BodyFatEvent) {
        when (event) {
            is BodyFatEvent.MethodChange -> {
                state.value = state.value.copy(method = event.method)
                viewModelScope.launch { settings.setBodyFatHeadlineMethod(event.method.wireName) }
            }
            is BodyFatEvent.WaistChange -> state.value = state.value.copy(waistText = event.text)
            is BodyFatEvent.NeckChange -> state.value = state.value.copy(neckText = event.text)
            is BodyFatEvent.HipChange -> state.value = state.value.copy(hipText = event.text)
            BodyFatEvent.Compute -> compute()
            BodyFatEvent.SaveToLogbook -> saveToLogbook()
        }
    }

    /**
     * R2 (WLO-0035): the tape IS the measurement — the latest girth readings
     * prefill the fields so a monthly check is three taps, not three forms
     * (F06 §3 input minimization).
     */
    private suspend fun prefillTape() {
        val id = profileId ?: return
        val today = DayBoundary.epochDay(clock.now(), zone)
        val metricByEvent =
            measurements
                .attrsInRange(id, 0, today)
                .getOrNull()
                .orEmpty()
                .filter { it.attr == TAPE_METRIC_ATTR }
                .associate { it.eventId to it.valueText }
        val latest = mutableMapOf<String, MeasurementEvent>()
        for (event in measurements.range(id, 0, today).getOrNull().orEmpty()) {
            if (event.kind != MeasurementKind.CUSTOM) continue
            metricByEvent[event.id]?.let { metric -> latest[metric] = event }
        }
        if (latest.isEmpty()) return
        state.value =
            state.value.copy(
                waistText = latest["waist"]?.let { format1(it.valueReal) } ?: state.value.waistText,
                neckText = latest["neck"]?.let { format1(it.valueReal) } ?: state.value.neckText,
                hipText = latest["hip"]?.let { format1(it.valueReal) } ?: state.value.hipText,
            )
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
        val current = state.value
        viewModelScope.launch {
            val today = DayBoundary.epochDay(clock.now(), zone)

            // R2 (WLO-0035): tape and estimate both persist — the tape as
            // MEASURED girth events, the estimate as an ESTIMATED series event.
            var tapeSaved = 0
            for ((metric, text) in listOf("waist" to current.waistText, "neck" to current.neckText, "hip" to current.hipText)) {
                val cm = text.toDoubleOrNull() ?: continue
                val appended =
                    measurements.append(
                        NewMeasurement(
                            profileId = id,
                            dayEpochDay = today,
                            kind = MeasurementKind.CUSTOM,
                            valueReal = cm,
                            source = MeasurementSource.MANUAL,
                            capturedAt = clock.now(),
                            unitOverride = TAPE_UNIT,
                        ),
                    )
                when (appended) {
                    is WloResult.Ok -> {
                        tapeSaved++
                        measurements.attachAttrs(
                            appended.value.id,
                            listOf(MeasurementAttr(eventId = appended.value.id, attr = TAPE_METRIC_ATTR, valueText = metric)),
                        )
                    }

                    is WloResult.Err -> Unit
                }
            }

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
                    state.value =
                        state.value.copy(
                            notice =
                                if (tapeSaved > 0) {
                                    "saved — tape and estimate, each to its own series"
                                } else {
                                    "saved — each method keeps its own series"
                                },
                        )
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

        /** The EAV attr naming a CUSTOM event's metric ("waist" | "neck" | "hip"). */
        public const val TAPE_METRIC_ATTR: String = "metric"

        /** Girth unit (F06 §3 catalog: tape sites read in cm). */
        public const val TAPE_UNIT: String = "cm"
    }
}
