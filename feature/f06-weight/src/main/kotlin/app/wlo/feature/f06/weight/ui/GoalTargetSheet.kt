package app.wlo.feature.f06.weight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wlo.core.designsystem.WloSheet
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.feature.f06.weight.state.GoalTargetDraft
import kotlin.math.abs

/** Native M3 sheet and field: saving a reference weight does not request forecast math. */
@Composable
internal fun GoalTargetSheet(
    draft: GoalTargetDraft,
    currentKg: Double?,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    WloSheet(
        onDismissRequest = onDismiss,
        skipPartiallyExpanded = true,
        modifier = Modifier.semantics { paneTitle = "Weight goal" },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Weight goal",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                IconButton(onClick = onDismiss, enabled = !draft.saving) {
                    Text("×", modifier = Modifier.semantics { contentDescription = "Close without saving" })
                }
            }
            if (draft.loading) {
                Text(draft.error ?: "Loading goal…", modifier = Modifier.padding(bottom = 24.dp))
            } else {
                val invalid = draft.kilograms == null
                OutlinedTextField(
                    value = draft.text,
                    onValueChange = onEdit,
                    label = { Text("Target weight") },
                    suffix = { Text(draft.unit.symbol, style = WeightOverviewTypography.unit) },
                    textStyle =
                        WeightOverviewTypography.hero.copy(
                            fontSize = 48.sp,
                            lineHeight = 62.sp,
                            color = wloExtendedColors.chartGoal,
                        ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = !draft.saving,
                    isError = invalid && draft.text.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("goal-target-input"),
                )
                val comparison =
                    currentKg?.let { current ->
                        "Current trend ${draft.unit.format(current)}" +
                            (
                                draft.kilograms?.let { target ->
                                    val delta = current - target
                                    if (abs(delta) < 0.000001) {
                                        " · At target"
                                    } else {
                                        " · ${draft.unit.format(abs(delta))} " +
                                            "${if (delta > 0) "above" else "below"} target"
                                    }
                                } ?: ""
                            )
                    } ?: "Your current trend is still forming."
                Text(
                    comparison,
                    style = WeightOverviewTypography.date,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(top = 10.dp, bottom = 24.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                )
                val error =
                    draft.error ?: if (invalid && draft.text.isNotEmpty()) {
                        "Enter a weight greater than zero."
                    } else {
                        null
                    }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
                }
                Button(
                    onClick = if (draft.changed) onSave else onDismiss,
                    enabled = !draft.saving && !invalid,
                    modifier = Modifier.fillMaxWidth().testTag("goal-target-save"),
                ) {
                    Text(
                        if (draft.saving) {
                            "Saving…"
                        } else if (draft.changed) {
                            "Save goal"
                        } else {
                            "Done"
                        },
                        style = WeightOverviewTypography.action,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}
