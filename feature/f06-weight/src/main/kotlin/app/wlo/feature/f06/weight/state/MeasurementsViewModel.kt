package app.wlo.feature.f06.weight.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.ClockPort
import app.wlo.core.common.DayBoundary
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.MeasurementSession
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.SessionReading
import app.wlo.core.model.MeasurementEvent
import app.wlo.core.model.MeasurementKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.util.UUID

public enum class BodyMetric(
    public val key: String,
    public val label: String,
) {
    FAT("body-fat", "Body fat"),
    WAIST("waist", "Waist"),
    HIPS("hip", "Hips"),
    CHEST("chest", "Chest"),
    LEFT_THIGH("left-thigh", "Left thigh"),
    RIGHT_THIGH("right-thigh", "Right thigh"),
    LEFT_ARM("left-arm", "Left arm"),
    RIGHT_ARM("right-arm", "Right arm"),
    ;

    public val unit: String get() = if (this == FAT) "%" else "cm"
}

public data class BodyReading(
    public val metric: BodyMetric,
    public val event: MeasurementEvent,
    public val series: String,
)

public data class MeasurementsState(
    public val readings: List<BodyReading> = emptyList(),
    public val loading: Boolean = true,
    public val saving: Boolean = false,
    public val error: String? = null,
    public val saved: Boolean = false,
    public val preferred: List<String> = listOf("waist", "hip", "chest"),
)

public class MeasurementsViewModel(
    private val repository: MeasurementRepository,
    private val profiles: ProfileRepository,
    private val clock: ClockPort,
    private val handle: SavedStateHandle,
    private val documents: app.wlo.core.datastore.JsonDocumentStore,
) : ViewModel() {
    private val mutable = MutableStateFlow(MeasurementsState())
    public val state: kotlinx.coroutines.flow.StateFlow<MeasurementsState> = mutable.asStateFlow()
    public val today: Long get() = DayBoundary.epochDay(clock.now(), TimeZone.currentSystemDefault())

    init {
        refresh()
    }

    public fun refresh() {
        viewModelScope.launch {
            val profile = profiles.active().getOrNull()
            if (profile == null) {
                mutable.value = mutable.value.copy(loading = false, error = "No active profile.")
                return@launch
            }
            val preferred =
                documents
                    .readText("measurements/fields/${profile.id}")
                    ?.split(",")
                    ?.filter { key -> BodyMetric.entries.any { it.key == key } }
                    ?: mutable.value.preferred
            mutable.value = mutable.value.copy(preferred = preferred)
            val events = repository.range(profile.id, -100000, today)
            val attrs = repository.attrsInRange(profile.id, -100000, today)
            if (events is WloResult.Err || attrs is WloResult.Err) {
                mutable.value = mutable.value.copy(loading = false, error = "Could not load measurements. Try again.")
                return@launch
            }
            val attributes = (attrs as WloResult.Ok).value.groupBy { it.eventId }
            val readings =
                (events as WloResult.Ok)
                    .value
                    .mapNotNull { event ->
                        val a = attributes[event.id].orEmpty()
                        val key =
                            if (event.kind == MeasurementKind.BODY_FAT) {
                                "body-fat"
                            } else if (event.kind == MeasurementKind.CUSTOM) {
                                a.firstOrNull { it.attr == "metric" }?.valueText
                            } else {
                                null
                            }
                        val metric = BodyMetric.entries.firstOrNull { it.key == key } ?: return@mapNotNull null
                        if (event.unit != metric.unit) return@mapNotNull null
                        val method = a.firstOrNull { it.attr == "method" }?.valueText
                        val source =
                            when (event.source) {
                                "scale" -> "Scale"
                                "health-connect" -> "Health Connect"
                                "manual" -> if (metric == BodyMetric.FAT) "Manual reading" else "Tape"
                                "engine" -> "Tape estimate"
                                else -> event.source
                            }
                        val detail = method?.takeUnless { it == "tape" || it.startsWith("reported-") }
                        BodyReading(metric, event, listOfNotNull(source, detail).joinToString(" · "))
                    }.sortedWith(compareBy<BodyReading> { it.event.dayEpochDay }.thenBy { it.event.capturedAt })
            mutable.value = mutable.value.copy(readings = readings, loading = false, error = null)
        }
    }

    public fun save(
        fields: Map<String, String>,
        date: String,
        scale: Boolean,
    ) {
        if (mutable.value.saving) return
        val day = runCatching { LocalDate.parse(date).toEpochDays() }.getOrNull()
        val readings =
            fields.filterValues { it.isNotBlank() }.map { (key, raw) ->
                SessionReading(
                    key,
                    raw.trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN,
                    if (key == "body-fat" && scale) "scale" else "manual",
                )
            }
        val invalidValues =
            readings.any {
                !it.value.isFinite() || it.value <= 0 || (it.metric == "body-fat" && it.value >= 100)
            }
        val invalidDate = day == null || day > today
        if (invalidDate || readings.isEmpty() || invalidValues) {
            mutable.value =
                mutable.value.copy(
                    error = "Enter a valid date and positive measurements. Body fat must be below 100%.",
                )
            return
        }
        mutable.value = mutable.value.copy(saving = true, error = null)
        viewModelScope.launch {
            val profile = profiles.active().getOrNull()
            if (profile == null) {
                mutable.value = mutable.value.copy(saving = false, error = "No active profile.")
                return@launch
            }
            val operation =
                handle.get<String>("operation")
                    ?: UUID.randomUUID().toString().also { handle["operation"] = it }
            val result =
                repository.saveSession(
                    MeasurementSession(
                        operation,
                        profile.id,
                        requireNotNull(day),
                        if (day == today) {
                            clock.now()
                        } else {
                            LocalDate.fromEpochDays(day).atStartOfDayIn(TimeZone.currentSystemDefault())
                        },
                        readings,
                    ),
                )
            if (result is WloResult.Err) {
                mutable.value =
                    mutable.value.copy(
                        saving = false,
                        error = "Measurements were not saved. Your entries are kept; try again.",
                    )
            } else {
                handle.remove<String>("operation")
                mutable.value = mutable.value.copy(saving = false, saved = true)
                refresh()
            }
        }
    }

    public fun rememberFields(fields: List<String>) {
        viewModelScope.launch {
            val profile = profiles.active().getOrNull() ?: return@launch
            documents.writeText("measurements/fields/${profile.id}", fields.joinToString(","))
        }
    }

    public fun acknowledgeSave() {
        mutable.value = mutable.value.copy(saved = false, error = null)
    }
}
