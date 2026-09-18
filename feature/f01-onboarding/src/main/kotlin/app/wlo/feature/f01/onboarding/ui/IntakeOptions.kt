package app.wlo.feature.f01.onboarding.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.feature.f01.onboarding.state.IntakeTargetState
import app.wlo.feature.f01.onboarding.state.IntakeTargetViewModel

@Composable
internal fun IntakeNutrients(
    state: IntakeTargetState,
    viewModel: IntakeTargetViewModel,
) {
    Column(Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Balanced preset. Change the split if you prefer.",
            style = intakeText(13),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IntakeOptionField("Protein · %", state.protein, state.saving, Modifier.weight(1f)) { value ->
                viewModel.edit { it.copy(protein = value, macrosEdited = true) }
            }
            IntakeOptionField("Carbs · %", state.carbs, state.saving, Modifier.weight(1f)) { value ->
                viewModel.edit { it.copy(carbs = value, macrosEdited = true) }
            }
            IntakeOptionField("Fat · %", state.fat, state.saving, Modifier.weight(1f)) { value ->
                viewModel.edit { it.copy(fat = value, macrosEdited = true) }
            }
        }
        IntakeOptionField("Fiber · g per day", state.fiber, state.saving) { value ->
            viewModel.edit { it.copy(fiber = value) }
        }
        Text(
            "Split must total 100%. Grams are rounded for display and follow each day’s calories.",
            style = intakeText(12),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IntakeOptionField(
    label: String,
    value: String,
    saving: Boolean,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = intakeText(12), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value,
            onChange,
            enabled = !saving,
            singleLine = true,
            textStyle = intakeText(14),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        )
    }
}

@Composable
internal fun IntakeSchedule(
    state: IntakeTargetState,
    viewModel: IntakeTargetViewModel,
) {
    Column(Modifier.padding(bottom = 20.dp)) {
        listOf(false to "Same every day", true to "More on weekends").forEach { (weekly, title) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .selectable(selected = state.weekly == weekly, enabled = !state.saving, role = Role.RadioButton) {
                        viewModel.edit { it.copy(weekly = weekly, scheduleEdited = true) }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The full 80dp row is the radio's touch target; shrink only its visual slot.
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    RadioButton(
                        selected = state.weekly == weekly,
                        onClick = null,
                        enabled = !state.saving,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(title, style = intakeText(14))
            }
            HorizontalDivider()
        }
        if (state.weekly) {
            Spacer(Modifier.height(16.dp))
            IntakeOptionField("Extra per weekend day · kcal", state.weekendExtra, state.saving) { value ->
                viewModel.edit { it.copy(weekendExtra = value, scheduleEdited = true) }
            }
        }
        IntakeWeekBars(state)
        Text(
            "${kcal(state.intake?.times(7))} kcal per week · total stays the same",
            style = intakeText(12),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.weekly) {
            Text(
                intakeScheduleSummary(state),
                style = intakeText(12),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun intakeScheduleSummary(state: IntakeTargetState): String {
    if (!state.weekly) return "Same target every day"
    val days = intakeDailyAmounts(state) ?: return "Review daily schedule"
    if (days.take(5).distinct().size != 1 || days.takeLast(2).distinct().size != 1) return "Custom daily targets"
    return "${kcal(days.first())} weekdays · ${kcal(days.last())} weekends"
}

/** Match the writer's preserved seven-day schedule until the user explicitly edits its allocation. */
private fun intakeDailyAmounts(state: IntakeTargetState): List<Double>? {
    val total = state.intake ?: return null
    if (!state.weekly) return List(7) { total }
    val energy = state.record?.document?.energy
    val days =
        if (!state.scheduleEdited && energy?.schedule?.size == 7) {
            val delta = total - (energy.weeklyBudgetKcal ?: return null) / 7
            energy.schedule.map { it + delta }
        } else {
            val extra = state.weekendExtra.toDoubleOrNull() ?: return null
            List(7) { if (it < 5) total - extra * 0.4 else total + extra }
        }
    return days.takeIf { it.all { value -> value.isFinite() && value >= 0 } }
}

/** M3 has no chart: Canvas draws the approved seven-bar preview from the draft's actual daily allocations. */
@Composable
private fun IntakeWeekBars(state: IntakeTargetState) {
    val days = intakeDailyAmounts(state) ?: return
    val blue = wloExtendedColors.developing
    val green = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = intakeText(10).copy(color = textColor)
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().padding(vertical = 22.dp).height(110.dp)) {
        val gap = 8.dp.toPx()
        val width = (size.width - gap * 6) / 7
        listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, day ->
            val amount = days[index]
            val height =
                (amount / 3000 * 80)
                    .coerceIn(4.0, 80.0)
                    .toFloat()
                    .dp
                    .toPx()
            val x = index * (width + gap)
            drawRoundRect(
                if (index < 5) blue else green,
                Offset(x, size.height - 20.dp.toPx() - height),
                Size(width, height),
                CornerRadius(4.dp.toPx()),
            )
            val label = measurer.measure(day, style)
            drawText(label, topLeft = Offset(x + (width - label.size.width) / 2, size.height - label.size.height))
        }
    }
}
