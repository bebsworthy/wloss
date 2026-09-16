package app.wlo.feature.f06.weight.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.ChartPoint
import app.wlo.core.designsystem.WloIcons
import app.wlo.core.designsystem.WloWeightChart
import app.wlo.feature.f06.weight.state.BodyMetric
import app.wlo.feature.f06.weight.state.BodyReading
import app.wlo.feature.f06.weight.state.MeasurementsViewModel
import kotlinx.datetime.LocalDate
import java.util.Locale

private fun number(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

private fun date(day: Long): String = LocalDate.fromEpochDays(day).toString()

/** Page-based browsing and a subset-entry form built from standard Material components. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun MeasurementsScreen(
    viewModel: MeasurementsViewModel,
    onEstimate: () -> Unit,
    onBack: () -> Unit,
    registerUp: ((() -> Unit)?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    var page by rememberSaveable { mutableStateOf("overview") }
    var metricKey by rememberSaveable { mutableStateOf("body-fat") }
    var chosen by rememberSaveable { mutableStateOf(listOf("waist", "hip", "chest")) }
    var draft by rememberSaveable { mutableStateOf(HashMap<String, String>()) }
    var day by rememberSaveable { mutableStateOf(date(viewModel.today)) }
    var scale by rememberSaveable { mutableStateOf(true) }
    var showDate by remember { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val metric = BodyMetric.entries.first { it.key == metricKey }
    LaunchedEffect(state.preferred) { if (page == "overview") chosen = state.preferred }
    val back = {
        if (!state.saving) {
            if (page == "record" && draft.values.any { it.isNotBlank() }) discard = true else page = "overview"
        }
    }
    DisposableEffect(page, draft, state.saving) {
        registerUp(if (page != "overview") back else null)
        onDispose { registerUp(null) }
    }
    BackHandler(page != "overview") { back() }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            draft = HashMap()
            page = "overview"
            viewModel.acknowledgeSave()
        }
    }
    if (showDate) {
        val dateState =
            rememberDatePickerState(
                initialSelectedDateMillis = LocalDate.parse(day).toEpochDays() * 86400000L,
            )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { day = date(it / 86400000L) }
                    showDate = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    }
    if (discard) {
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text("Discard measurements?") },
            text = { Text("Your entries have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    draft = HashMap()
                    discard = false
                    viewModel.acknowledgeSave()
                    page = "overview"
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } },
        )
    }
    if (picker) {
        AlertDialog(
            onDismissRequest = { picker = false },
            title = { Text("Choose measurements") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BodyMetric.entries.forEach { item ->
                        ListItem(
                            headlineContent = { Text(item.label) },
                            trailingContent = { Checkbox(item.key in chosen, onCheckedChange = null) },
                            modifier =
                                Modifier.toggleable(
                                    value = item.key in chosen,
                                    role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        chosen = if (checked) chosen + item.key else chosen - item.key
                                    },
                                ),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = false }) { Text("Done") } },
        )
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0),
            title = {
                Text(
                    if (page == "record") {
                        "Record measurements"
                    } else if (page == "detail") {
                        metric.label
                    } else {
                        "Body measurements"
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = { if (page == "overview") onBack() else back() }) {
                    Icon(WloIcons.ArrowBack, contentDescription = "Navigate up")
                }
            },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (page) {
                "overview" -> {
                    if (!state.loading && state.readings.isEmpty()) {
                        Text("Your measurements, your choice", style = MaterialTheme.typography.titleLarge)
                        Text("Record body fat, waist, hips or any measurements you want to follow. Start with one.")
                    }
                    BodyMetric.entries.forEach { item ->
                        val last = state.readings.lastOrNull { it.metric == item }
                        if (last != null) {
                            ListItem(
                                headlineContent = { Text(item.label) },
                                supportingContent = { Text("${date(last.event.dayEpochDay)} · ${last.series}") },
                                trailingContent = { Text("${number(last.event.valueReal)} ${item.unit}  ›") },
                                modifier =
                                    Modifier.clickable {
                                        metricKey = item.key
                                        page = "detail"
                                    },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                            )
                            HorizontalDivider()
                        }
                    }
                    TextButton(onClick = { viewModel.refresh() }) { Text("Refresh measurements") }
                }
                "detail" -> MeasurementDetail(metric, state.readings.filter { it.metric == metric }, viewModel.today)
                "record" -> {
                    TextButton(onClick = { showDate = true }, enabled = !state.saving) {
                        Text("Measurement date · $day")
                    }
                    Text("Fill in whichever measurements you took.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    chosen.forEach { key ->
                        val item = BodyMetric.entries.first { it.key == key }
                        val last = state.readings.lastOrNull { it.metric == item }
                        OutlinedTextField(
                            draft[key].orEmpty(),
                            onValueChange = {
                                draft = HashMap(draft).apply { put(key, it) }
                            },
                            label = { Text("${item.label} (${item.unit})") },
                            suffix = { Text(item.unit) },
                            supportingText = {
                                Text(
                                    last?.let {
                                        "Last: ${number(it.event.valueReal)} ${item.unit} · " +
                                            date(it.event.dayEpochDay)
                                    }
                                        ?: "Optional",
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if ("body-fat" in chosen) {
                        Text("Body fat source", style = MaterialTheme.typography.titleSmall)
                        Row {
                            FilterChip(
                                selected = scale,
                                onClick = { scale = true },
                                label = { Text("Scale") },
                                enabled = !state.saving,
                            )
                            Spacer(Modifier.width(8.dp))
                            FilterChip(
                                selected = !scale,
                                onClick = { scale = false },
                                label = { Text("Other reading") },
                                enabled = !state.saving,
                            )
                        }
                    }
                    TextButton(onClick = { picker = true }, enabled = !state.saving) {
                        Text("+ Add another measurement")
                    }
                }
            }
            if (page == "overview") {
                TextButton(onClick = onEstimate) { Text("Estimate body fat from tape") }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                if (page == "record") {
                    viewModel.rememberFields(chosen)
                    viewModel.save(draft.filterKeys { it in chosen }, day, scale)
                } else {
                    if (page == "detail" && metric.key !in chosen) chosen = listOf(metric.key) + chosen
                    viewModel.acknowledgeSave()
                    page = "record"
                }
            },
            enabled =
                !state.loading &&
                    !state.saving &&
                    (page != "record" || chosen.any { !draft[it].isNullOrBlank() }),
            modifier = Modifier.fillMaxWidth().padding(24.dp).heightIn(min = 52.dp),
        ) {
            Text(
                if (state.saving) {
                    "Saving…"
                } else if (page == "record") {
                    "Save measurements"
                } else {
                    "+ Record measurements"
                },
            )
        }
    }
}

@Composable
private fun MeasurementDetail(
    metric: BodyMetric,
    readings: List<BodyReading>,
    today: Long,
) {
    var selectedSource by rememberSaveable(metric.key) { mutableStateOf(readings.lastOrNull()?.series.orEmpty()) }
    var days by rememberSaveable(metric.key) { mutableIntStateOf(90) }
    val sources = readings.map { it.series }.distinct()
    val sourceReadings = readings.filter { it.series == selectedSource }
    val start = if (days == 0) sourceReadings.firstOrNull()?.event?.dayEpochDay ?: today else today - days + 1
    val visible = sourceReadings.filter { it.event.dayEpochDay >= start }
    val latest = sourceReadings.lastOrNull()
    latest?.let {
        Text("${number(it.event.valueReal)} ${metric.unit}", style = MaterialTheme.typography.displayLarge)
        Text("${date(it.event.dayEpochDay)} · ${it.series}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (sources.size > 1) {
        Text("Measurement source", style = MaterialTheme.typography.titleSmall)
        sources.forEach { source ->
            FilterChip(selectedSource == source, onClick = { selectedSource = source }, label = { Text(source) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(30 to "30 d", 90 to "90 d", 365 to "1 y", 0 to "All").forEach { (value, label) ->
            FilterChip(days == value, onClick = { days = value }, label = { Text(label) })
        }
    }
    if (visible.size > 1) {
        val change = visible.last().event.valueReal - visible.first().event.valueReal
        val changeUnit = if (metric == BodyMetric.FAT) "percentage points" else "cm"
        Text(
            "${if (change > 0) "+" else ""}${number(change)} $changeUnit",
            style = MaterialTheme.typography.titleLarge,
        )
        Text("Change · ${date(visible.first().event.dayEpochDay)} – ${date(visible.last().event.dayEpochDay)}")
    }
    if (visible.isEmpty()) {
        Text("No measurements in this period.")
    } else {
        WloWeightChart(
            samples =
                visible.map {
                    ChartPoint(
                        it.event.dayEpochDay,
                        it.event.valueReal,
                        stableKey = it.event.id,
                        captureTimeEpochMs = it.event.capturedAt.toEpochMilliseconds(),
                    )
                },
            trend = emptyList(),
            goalKg = null,
            startDay = start,
            endDay = today,
            formatWeight = { "${number(it)} ${metric.unit}" },
            description = "${metric.label} chart. ${visible.size} measurements. $selectedSource",
        )
    }
    Text("Measurement history", style = MaterialTheme.typography.titleMedium)
    visible.asReversed().forEach { reading ->
        ListItem(
            headlineContent = { Text("${number(reading.event.valueReal)} ${metric.unit}") },
            supportingContent = { Text("${date(reading.event.dayEpochDay)} · ${reading.series}") },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        )
    }
}
