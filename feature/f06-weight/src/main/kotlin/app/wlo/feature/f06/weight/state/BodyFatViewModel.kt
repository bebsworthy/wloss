package app.wlo.feature.f06.weight.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.LengthUnit
import app.wlo.core.common.MassUnit
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.BODY_METRIC_ATTR
import app.wlo.core.data.BodyCanonicalInput
import app.wlo.core.data.BodyMeasurementCommand
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.engines.BodyFatEngine
import app.wlo.core.engines.BodyFatEstimate
import app.wlo.core.model.BodyFatMethod
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.Sex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.Provided
import kotlin.math.roundToLong
import kotlin.uuid.Uuid

public sealed interface BodyFatEvent {
    public data class MethodChange(
        val method: BodyFatMethod,
    ) : BodyFatEvent

    public data class WaistChange(
        val text: String,
    ) : BodyFatEvent

    public data class NeckChange(
        val text: String,
    ) : BodyFatEvent

    public data class HipChange(
        val text: String,
    ) : BodyFatEvent

    public data object Compute : BodyFatEvent

    public data object SaveToLogbook : BodyFatEvent
}

public data class BodyFatUi(
    public val method: BodyFatMethod,
    public val percentLabel: String,
    public val value: DerivedValue<Double>,
    public val rows: List<Pair<String, String>>,
)

public data class BodyFatUiState(
    public val method: BodyFatMethod = BodyFatMethod.NAVY_TAPE,
    public val waistText: String = "",
    public val neckText: String = "",
    public val hipText: String = "",
    public val lengthUnit: LengthUnit = LengthUnit.CENTIMETER,
    public val heightCm: Double? = null,
    public val sex: Sex? = null,
    public val revision: Long = 0,
    public val computedRevision: Long? = null,
    public val estimate: BodyFatUi? = null,
    public val isSaving: Boolean = false,
    public val notice: String? = null,
    public val committedOperationId: String? = null,
) {
    public val canSave: Boolean
        get() = estimate != null && computedRevision == revision && !isSaving
}

private data class ComputedBodySnapshot(
    val revision: Long,
    val estimate: BodyFatEstimate,
    val inputs: List<BodyCanonicalInput>,
)

/** Revisioned body calculator and the single transactional persistence door. */
public class BodyFatViewModel(
    private val clock: ClockPort,
    private val profiles: ProfileRepository,
    private val measurements: MeasurementRepository,
    private val settings: SettingsStore,
    @Provided private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val zone: TimeZone = TimeZone.currentSystemDefault()
    private var profileId: String? = null
    private var computed: ComputedBodySnapshot? = null
    private var operationId: String = savedStateHandle[OPERATION_KEY] ?: Uuid.random().toString()
    private var restoredDraftNeedsNewOperation: Boolean = savedStateHandle.get<String>(OPERATION_KEY) != null

    private val state =
        MutableStateFlow(
            BodyFatUiState(
                method =
                    savedStateHandle.get<String>(METHOD_KEY)?.let(BodyFatMethod::fromWireName)
                        ?: BodyFatMethod.NAVY_TAPE,
                waistText = savedStateHandle[WAIST_KEY] ?: "",
                neckText = savedStateHandle[NECK_KEY] ?: "",
                hipText = savedStateHandle[HIP_KEY] ?: "",
                committedOperationId = savedStateHandle[COMMITTED_OPERATION_KEY],
            ),
        )

    public val uiState: StateFlow<BodyFatUiState> = state

    init {
        persistDraft(state.value)
        viewModelScope.launch {
            val initialLengthUnit =
                if (settings.massUnit.first() == MassUnit.POUND) LengthUnit.INCH else LengthUnit.CENTIMETER
            state.value = state.value.copy(lengthUnit = initialLengthUnit)
            profiles.active().getOrNull()?.let { profile ->
                profileId = profile.id
                val restoredMethod = savedStateHandle.get<String>(METHOD_KEY)?.let(BodyFatMethod::fromWireName)
                val headline =
                    restoredMethod ?: BodyFatMethod.fromWireName(settings.bodyFatHeadlineMethod.first())
                        ?: BodyFatMethod.NAVY_TAPE
                state.value = state.value.copy(heightCm = profile.heightCm, sex = profile.sex, method = headline)
            }
            if (state.value.waistText.isBlank()) prefillTape()
        }
        viewModelScope.launch {
            settings.massUnit.collectLatest { massUnit ->
                val next = if (massUnit == MassUnit.POUND) LengthUnit.INCH else LengthUnit.CENTIMETER
                if (next != state.value.lengthUnit) changeUnit(next)
            }
        }
    }

    public fun onEvent(event: BodyFatEvent) {
        if (state.value.isSaving) return
        when (event) {
            is BodyFatEvent.MethodChange -> {
                invalidate { it.copy(method = event.method) }
                viewModelScope.launch { settings.setBodyFatHeadlineMethod(event.method.wireName) }
            }
            is BodyFatEvent.WaistChange -> invalidate { it.copy(waistText = event.text) }
            is BodyFatEvent.NeckChange -> invalidate { it.copy(neckText = event.text) }
            is BodyFatEvent.HipChange -> invalidate { it.copy(hipText = event.text) }
            BodyFatEvent.Compute -> compute()
            BodyFatEvent.SaveToLogbook -> saveToLogbook()
        }
    }

    private fun invalidate(
        rotateRestoredOperation: Boolean = true,
        transform: (BodyFatUiState) -> BodyFatUiState,
    ) {
        val changed = transform(state.value)
        if (changed.committedOperationId != null || (rotateRestoredOperation && restoredDraftNeedsNewOperation)) {
            operationId = Uuid.random().toString()
        }
        if (rotateRestoredOperation) restoredDraftNeedsNewOperation = false
        computed = null
        state.value =
            changed.copy(
                revision = changed.revision + 1,
                computedRevision = null,
                estimate = null,
                notice = null,
                committedOperationId = null,
            )
        persistDraft(state.value)
    }

    private fun changeUnit(next: LengthUnit) {
        val previous = state.value.lengthUnit

        fun convert(text: String): String {
            val value = parseNumber(text) ?: return text
            return formatInput(next.fromCentimeters(previous.toCentimeters(value)))
        }
        invalidate(rotateRestoredOperation = false) {
            it.copy(
                lengthUnit = next,
                waistText = convert(it.waistText),
                neckText = convert(it.neckText),
                hipText = convert(it.hipText),
            )
        }
    }

    private suspend fun prefillTape() {
        val id = profileId ?: return
        val today = DayBoundary.epochDay(clock.now(), zone)
        val metricByEvent =
            measurements
                .attrsInRange(id, 0, today)
                .getOrNull()
                .orEmpty()
                .filter { it.attr == BODY_METRIC_ATTR }
                .associate { it.eventId to it.valueText }
        val latest = mutableMapOf<String, MeasurementEvent>()
        measurements.range(id, 0, today).getOrNull().orEmpty().forEach { event ->
            if (event.kind == MeasurementKind.CUSTOM) metricByEvent[event.id]?.let { latest[it] = event }
        }
        if (latest.isEmpty()) return
        val unit = state.value.lengthUnit
        state.value =
            state.value.copy(
                waistText = latest["waist"]?.let { formatInput(unit.fromCentimeters(it.valueReal)) }.orEmpty(),
                neckText = latest["neck"]?.let { formatInput(unit.fromCentimeters(it.valueReal)) }.orEmpty(),
                hipText = latest["hip"]?.let { formatInput(unit.fromCentimeters(it.valueReal)) }.orEmpty(),
            )
        persistDraft(state.value)
    }

    private fun compute() {
        val current = state.value
        val height = current.heightCm ?: return fail("Add height in Profile before calculating.")
        val waist = canonical(current.waistText, current.lengthUnit) ?: return fail("Enter a valid waist value.")
        val neck = canonical(current.neckText, current.lengthUnit)
        val hip = canonical(current.hipText, current.lengthUnit)
        val estimate =
            runCatching {
                BodyFatEngine.estimate(current.method, current.sex, height, waist, neck, hip, clock.now())
            }.getOrNull() ?: return fail("Those numbers don't fit the formula — check the tape values.")
        val inputs =
            buildList {
                add(BodyCanonicalInput("height", height))
                add(BodyCanonicalInput("waist", waist))
                neck?.let { add(BodyCanonicalInput("neck", it)) }
                hip?.let { add(BodyCanonicalInput("hip", it)) }
            }
        computed = ComputedBodySnapshot(current.revision, estimate, inputs)
        state.value =
            current.copy(
                computedRevision = current.revision,
                estimate =
                    BodyFatUi(
                        estimate.method,
                        "${format1(estimate.estimate.value)} %",
                        estimate.estimate,
                        listOf("Method" to methodLabel(estimate.method), "Formula" to estimate.formulaVersion) +
                            inputs.map {
                                it.name.replaceFirstChar(Char::uppercase) to "${format1(it.centimeters)} cm"
                            } +
                            ("Reads as" to "An estimate (±3–4 % typical) — never a measurement"),
                    ),
                notice = null,
            )
    }

    private fun saveToLogbook() {
        val id = profileId ?: return fail("No active profile. Finish setup, then try again.")
        val snapshot = computed ?: return fail("Calculate again before saving.")
        if (snapshot.revision != state.value.revision) return fail("Calculate again before saving.")
        state.value = state.value.copy(isSaving = true, notice = null)
        viewModelScope.launch {
            val now = clock.now()
            val result =
                measurements.saveBodyMeasurement(
                    BodyMeasurementCommand(
                        operationId = operationId,
                        profileId = id,
                        dayEpochDay = DayBoundary.epochDay(now, zone),
                        capturedAt = now,
                        methodId = snapshot.estimate.method.wireName,
                        methodVersion = snapshot.estimate.formulaVersion,
                        inputs = snapshot.inputs,
                        estimatePercent = snapshot.estimate.estimate.value,
                    ),
                )
            state.value =
                when (result) {
                    is WloResult.Ok ->
                        state.value.copy(
                            isSaving = false,
                            notice =
                                "Saved ${format1(snapshot.estimate.estimate.value)} % · " +
                                    methodLabel(snapshot.estimate.method),
                            committedOperationId = operationId,
                        )
                    is WloResult.Err ->
                        state.value.copy(
                            isSaving = false,
                            notice = "That didn't save. Nothing changed — try again.",
                        )
                }
            persistDraft(state.value)
        }
    }

    private fun fail(message: String) {
        state.value = state.value.copy(notice = message)
    }

    private fun persistDraft(value: BodyFatUiState) {
        savedStateHandle[METHOD_KEY] = value.method.wireName
        savedStateHandle[WAIST_KEY] = value.waistText
        savedStateHandle[NECK_KEY] = value.neckText
        savedStateHandle[HIP_KEY] = value.hipText
        savedStateHandle[OPERATION_KEY] = operationId
        savedStateHandle[COMMITTED_OPERATION_KEY] = value.committedOperationId
    }

    private fun canonical(
        text: String,
        unit: LengthUnit,
    ): Double? = parseNumber(text)?.takeIf { it > 0.0 }?.let(unit::toCentimeters)

    private fun methodLabel(method: BodyFatMethod): String =
        when (method) {
            BodyFatMethod.NAVY_TAPE -> "US Navy tape (Hodgdon & Beckett)"
            BodyFatMethod.RFM -> "RFM (Woolcott & Bergman 2012)"
        }

    public companion object {
        public fun format1(value: Double): String {
            val tenths = (value * 10).roundToLong()
            return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
        }

        internal fun parseNumber(text: String): Double? {
            val trimmed = text.trim()
            if (trimmed.count { it == '.' || it == ',' } > 1) return null
            return trimmed.replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
        }

        private fun formatInput(value: Double): String = format1(value)

        private const val METHOD_KEY: String = "body.method"
        private const val WAIST_KEY: String = "body.waist"
        private const val NECK_KEY: String = "body.neck"
        private const val HIP_KEY: String = "body.hip"
        private const val OPERATION_KEY: String = "body.operation"
        private const val COMMITTED_OPERATION_KEY: String = "body.committedOperation"
    }
}
