package app.wlo.feature.f06.weight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloButton
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloCardHeader
import app.wlo.core.designsystem.WloListRow
import app.wlo.core.designsystem.WloScreenTitle
import app.wlo.core.designsystem.WloSecondaryButton
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.BodyFatMethod
import app.wlo.feature.f06.weight.state.BodyFatEvent
import app.wlo.feature.f06.weight.state.BodyFatUiState
import app.wlo.feature.f06.weight.state.BodyFatViewModel

/**
 * The body-fat method registry (F06 §3: "a method registry, never one
 * number"): Navy tape and RFM, each computed honestly as an ESTIMATE with the
 * formula + inputs one tap away, each savable to its own series (the method
 * rides the event, the series stay separate).
 */
@Composable
public fun BodyFatScreen(
    viewModel: BodyFatViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        WloScreenTitle(
            title = "Body fat",
            modifier = Modifier.testTag("f06-bodyfat-title"),
        )
        Text(
            text =
                "Every method is an estimate — ±3–4 % is typical, and each method keeps its " +
                    "own series so they can disagree in the open.",
            style = wloType.caption,
            color = wloExtendedColors.textTertiary,
        )
        BodyFatCalculatorCard(viewModel = viewModel)
    }
}

/**
 * The calculator + tape entry + estimate card, embeddable — the weight
 * surface's body-fat segment hosts it (R2, WLO-0035: same screen, different
 * series). Saving persists the tape AND the estimate, each to its own series.
 */
@Composable
public fun BodyFatCalculatorCard(viewModel: BodyFatViewModel) {
    val state: BodyFatUiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN)) {
        WloCard(modifier = Modifier.testTag("f06-bodyfat-card")) {
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                for (method in BodyFatMethod.entries) {
                    SelectChip(
                        label = methodLabel(method),
                        selected = method == state.method,
                        onClick = { viewModel.onEvent(BodyFatEvent.MethodChange(method)) },
                        modifier = Modifier.testTag("f06-bf-method-${method.wireName}"),
                    )
                }
            }

            state.heightCm?.let { height ->
                Text(
                    text = "Height ${BodyFatViewModel.format1(height)} cm (from your profile)",
                    style = wloType.label,
                    color = wloExtendedColors.textTertiary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                NumberField(
                    label = "Waist cm",
                    value = state.waistText,
                    modifier = Modifier.weight(1f).testTag("f06-bf-waist"),
                ) { viewModel.onEvent(BodyFatEvent.WaistChange(it)) }
                if (state.method == BodyFatMethod.NAVY_TAPE) {
                    NumberField(
                        label = "Neck cm",
                        value = state.neckText,
                        modifier = Modifier.weight(1f).testTag("f06-bf-neck"),
                    ) { viewModel.onEvent(BodyFatEvent.NeckChange(it)) }
                    if (state.sex == app.wlo.core.model.Sex.FEMALE) {
                        NumberField(
                            label = "Hip cm",
                            value = state.hipText,
                            modifier = Modifier.weight(1f).testTag("f06-bf-hip"),
                        ) { viewModel.onEvent(BodyFatEvent.HipChange(it)) }
                    }
                }
            }

            WloButton(
                label = "Estimate",
                onClick = { viewModel.onEvent(BodyFatEvent.Compute) },
                modifier = Modifier.fillMaxWidth().testTag("f06-bf-compute"),
            )
        }

        state.estimate?.let { estimate ->
            WloCard(modifier = Modifier.testTag("f06-bf-result")) {
                WloCardHeader(title = "${methodLabel(state.method)} estimate")
                Text(
                    text = estimate.percentLabel,
                    style = wloType.statL,
                    modifier = Modifier.testTag("f06-bf-percent"),
                )
                ProvenanceChip(
                    value = estimate.value,
                    format = { "${BodyFatViewModel.format1(it)} %" },
                )
                estimate.rows.forEach { (label, value) ->
                    WloListRow(
                        label = label,
                        value = { Text(text = value, style = wloType.receipt) },
                    )
                }
                WloSecondaryButton(
                    label = "Save measurement",
                    onClick = { viewModel.onEvent(BodyFatEvent.SaveToLogbook) },
                    modifier = Modifier.fillMaxWidth().testTag("f06-bf-save"),
                )
            }
        }

        state.notice?.let {
            Text(
                text = it,
                style = wloType.caption,
                color = wloExtendedColors.held,
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
): Unit =
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = wloType.statS,
        placeholder = { Text(label, style = wloType.caption) },
    )

private fun methodLabel(method: BodyFatMethod): String =
    when (method) {
        BodyFatMethod.NAVY_TAPE -> "Navy method"
        BodyFatMethod.RFM -> "RFM"
    }
