package app.wlo.feature.f06.weight.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Standard Material 3 date/time picker launchers for F06 (WLO-0069).
 * M3's own dialogs and picker content are used directly; this file only
 * converts their values to the F06 state holder's stable ISO/24-hour text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeightDatePickerButton(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        enabled = enabled,
        modifier =
            modifier
                .semantics { contentDescription = "Date, $value. Choose date" }
                .testTag(testTag),
    ) {
        PickerButtonLabel(label = "Date", value = value)
    }

    if (open) {
        val initialMillis =
            runCatching { LocalDate.parse(value).toEpochDays() * MILLIS_PER_DAY }
                .getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onValueChange(LocalDate.fromEpochDays(millis / MILLIS_PER_DAY).toString())
                        }
                        open = false
                    },
                    modifier = Modifier.testTag("$testTag-confirm"),
                ) {
                    Text("Use date")
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(
                state = state,
                modifier = Modifier.testTag("$testTag-picker"),
                showModeToggle = false,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeightTimePickerButton(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        enabled = enabled,
        modifier =
            modifier
                .semantics { contentDescription = "Time, $value. Choose time" }
                .testTag(testTag),
    ) {
        PickerButtonLabel(label = "Time", value = value)
    }

    if (open) {
        val initial = runCatching { LocalTime.parse(value) }.getOrNull() ?: LocalTime(12, 0)
        val state =
            rememberTimePickerState(
                initialHour = initial.hour,
                initialMinute = initial.minute,
                is24Hour = true,
            )
        TimePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange("%02d:%02d".format(state.hour, state.minute))
                        open = false
                    },
                    modifier = Modifier.testTag("$testTag-confirm"),
                ) {
                    Text("Use time")
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            },
            title = { Text("Choose time") },
            content = {
                TimePicker(
                    state = state,
                    modifier = Modifier.testTag("$testTag-picker"),
                )
            },
            modifier = Modifier.testTag("$testTag-dialog"),
        )
    }
}

@Composable
private fun PickerButtonLabel(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = wloType.caption, color = wloExtendedColors.textTertiary)
        Text(text = value, style = wloType.body)
    }
}

private const val MILLIS_PER_DAY: Long = 86_400_000L
