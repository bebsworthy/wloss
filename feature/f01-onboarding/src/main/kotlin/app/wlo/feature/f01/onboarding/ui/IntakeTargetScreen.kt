package app.wlo.feature.f01.onboarding.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.common.MassUnit
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.model.UnitSystem
import app.wlo.feature.f01.onboarding.domain.IntakeEntryMode
import app.wlo.feature.f01.onboarding.state.IntakeTargetState
import app.wlo.feature.f01.onboarding.state.IntakeTargetViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

/** Standard M3 inputs; projection uses the shared, accessible forecast chart. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun IntakeTargetScreen(
    viewModel: IntakeTargetViewModel,
    onDone: () -> Unit,
    onProfile: () -> Unit,
    onHealth: () -> Unit,
    registerUp: ((() -> Unit)?) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.saved) {
        if (state.saved) {
            android.widget.Toast
                .makeText(context, "Intake saved", android.widget.Toast.LENGTH_SHORT)
                .show()
            onDone()
        }
    }
    BackHandler(enabled = state.saving) { }
    DisposableEffect(state.saving) {
        registerUp(if (state.saving) ({}) else null)
        onDispose { registerUp(null) }
    }
    var details by rememberSaveable { mutableStateOf(false) }
    var nutrients by rememberSaveable { mutableStateOf(false) }
    var schedule by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().imePadding().padding(start = 23.dp, end = 23.dp, top = 14.dp, bottom = 25.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.loading && !state.saving && state.record != null,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(if (state.saving) "Saving…" else "Save", style = intakeText(14, 20.3f, FontWeight.SemiBold)) }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                .padding(horizontal = 23.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            if (state.loading) {
                CircularProgressIndicator()
                return@Column
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                if (state.record == null) TextButton(onClick = viewModel::reload) { Text("Retry") }
            }
            IntakeOverview(state)
            Spacer(Modifier.height(16.dp))
            IntakeModeSelector(state, viewModel)
            Spacer(Modifier.height(10.dp))
            IntakeAmountField(state, viewModel)
            val maintenance = state.maintenance
            if (maintenance != null) {
                val delta = (state.intake ?: maintenance) - maintenance
                val extent = maxOf(1000.0, kotlin.math.ceil(abs(delta) / 250) * 250).coerceAtMost(1_000_000.0)
                IntakeSlider(
                    value = delta.coerceIn(-extent, extent).toFloat(),
                    onValueChange = viewModel::slide,
                    onValueChangeFinished = viewModel::finishSliding,
                    enabled = !state.saving,
                    valueRange = -extent.toFloat()..extent.toFloat(),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Intake adjustment" },
                )
                IntakeSliderLabels(state, maintenance, extent)
            } else {
                // Direct intake remains editable without a maintenance estimate.
                IntakeSlider(
                    value = (state.intake ?: 0.0).coerceIn(0.0, 1_000_000.0).toFloat(),
                    valueRange = 0f..maxOf(4000.0, state.intake ?: 0.0).coerceAtMost(1_000_000.0).toFloat(),
                    onValueChange = { value ->
                        viewModel.edit { it.copy(text = ((value / 25).roundToInt() * 25).toString()) }
                    },
                    enabled = !state.saving,
                )
            }
            IntakeRecommendation(state, viewModel::useSuggestion, onHealth, onProfile)
            Spacer(Modifier.height(10.dp))
            IntakeDisclosure("How is this calculated?", details, onClick = { details = !details })
            if (details || maintenance == null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IntakeDetails(state, onProfile)
                }
            }
            HorizontalDivider()
            IntakeDisclosure(
                "Nutrient targets",
                nutrients,
                subtitle = intakeMacroSummary(state),
                onClick = { nutrients = !nutrients },
            )
            if (nutrients) IntakeNutrients(state, viewModel)
            HorizontalDivider()
            IntakeDisclosure(
                "Daily schedule",
                schedule,
                subtitle = intakeScheduleSummary(state),
                onClick = { schedule = !schedule },
            )
            if (schedule) IntakeSchedule(state, viewModel)
        }
    }
}

internal fun kcal(value: Double?): String =
    value?.takeIf { it.isFinite() }?.let {
        java.text.NumberFormat
            .getIntegerInstance()
            .format(it)
    } ?: "—"

internal fun massUnit(state: IntakeTargetState): MassUnit =
    if (state.profile?.unitPreference == UnitSystem.IMPERIAL) MassUnit.POUND else MassUnit.KILOGRAM

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntakeDetails(
    state: IntakeTargetState,
    onProfile: () -> Unit,
) {
    Text(
        if (state.measured) {
            "Maintenance comes from your logged intake and weight trend."
        } else {
            "Maintenance uses Mifflin–St Jeor and your activity level. " +
                "It is an estimate, not a measured requirement."
        },
    )
    Text(
        "The forecast uses a decelerating model and uncertainty bands, not a promised date. " +
            "Manual intake stays fixed when maintenance changes.",
        style = MaterialTheme.typography.bodySmall,
    )
    state.profile?.let { profile ->
        Text(
            "Height: ${profile.heightCm ?: "not entered"} cm · " +
                "birth year: ${profile.birthYear ?: "not entered"} · activity: ${profile.activityLevel.wireName}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (profile.sex == null || profile.sex == app.wlo.core.model.Sex.OTHER) {
            Text(
                "Formula uses the midpoint sex correction when no male/female parameter is selected.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (state.intake != null && state.maintenance != null) {
        Text(
            "${kcal(
                state.maintenance,
            )} + (${kcal(state.intake!! - state.maintenance)}) = ${kcal(state.intake)} kcal/day",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    TextButton(onClick = onProfile, enabled = !state.saving) { Text("Edit profile details") }
    Text(
        "For eligible adults, loss suggestions start at −500 kcal when BMI is at least 25; " +
            "gain suggestions start at +300 kcal. These are starting estimates, " +
            "not personalized medical prescriptions.",
        style = MaterialTheme.typography.bodySmall,
    )
}

/**
 * Standard M3 Slider interactions with documented thumb/track slots. The stock track reserves
 * half a thumb at each edge; the track-slot width compensates that inset to match flow 10.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntakeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
    val marker = MaterialTheme.colorScheme.outline
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        valueRange = valueRange,
        modifier =
            modifier
                .drawBehind {
                    drawLine(
                        marker,
                        Offset(size.width / 2, 33.dp.toPx()),
                        Offset(size.width / 2, 38.dp.toPx()),
                        1.dp.toPx(),
                    )
                },
        interactionSource = interaction,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interaction,
                colors = colors,
                enabled = enabled,
                thumbSize = DpSize(16.dp, 16.dp),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier =
                    Modifier
                        .height(8.dp)
                        .layout { measurable, constraints ->
                            // M3 reserves half a thumb at each end. Bleed only that inset so its track
                            // aligns with the field; keep the standard Slider touch/drag semantics.
                            val inset = 8.dp.roundToPx()
                            val placeable =
                                measurable.measure(
                                    constraints.copy(
                                        minWidth = constraints.minWidth + inset * 2,
                                        maxWidth = constraints.maxWidth + inset * 2,
                                    ),
                                )
                            layout(constraints.maxWidth, placeable.height) { placeable.place(-inset, 0) }
                        }.border(1.dp, marker, RoundedCornerShape(4.dp)),
                colors = colors,
                enabled = enabled,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 0.dp,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntakeModeSelector(
    state: IntakeTargetState,
    viewModel: IntakeTargetViewModel,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(50.dp)) {
        IntakeEntryMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = state.mode == mode,
                modifier = Modifier.height(50.dp),
                onClick = { viewModel.switchMode(mode) },
                enabled = !state.saving && (mode == IntakeEntryMode.INTAKE || state.maintenance != null),
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = wloExtendedColors.accentDim,
                        activeContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Text(
                    if (mode ==
                        IntakeEntryMode.INTAKE
                    ) {
                        "Intake"
                    } else {
                        "Adjustment"
                    },
                    style = intakeText(13, 18.85f, FontWeight.Medium),
                )
            }
        }
    }
}
