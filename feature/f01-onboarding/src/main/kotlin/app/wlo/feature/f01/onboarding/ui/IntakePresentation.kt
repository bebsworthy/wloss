package app.wlo.feature.f01.onboarding.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.WloFontFamily
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Provenance
import app.wlo.feature.f01.onboarding.domain.IntakeEntryMode
import app.wlo.feature.f01.onboarding.state.IntakeOrigin
import app.wlo.feature.f01.onboarding.state.IntakeTargetState
import app.wlo.feature.f01.onboarding.state.IntakeTargetViewModel
import app.wlo.feature.f01.onboarding.state.SuggestionStatus
import kotlin.math.roundToInt

/** Type and spacing from approved flow 10; explicit line heights avoid inherited M3 ramp metrics. */
internal fun intakeText(
    size: Int,
    lineHeight: Float = size * 1.45f,
    weight: FontWeight = FontWeight.Normal,
): TextStyle =
    TextStyle(
        fontFamily = WloFontFamily,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        letterSpacing = 0.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

@Composable
internal fun IntakeOverview(state: IntakeTargetState) {
    val goal =
        state.record
            ?.document
            ?.goal
            ?.targetWeightKg
    val weight = state.weight
    if (goal != null && weight != null) {
        Row(Modifier.padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Weight goal", style = intakeText(13), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${massUnit(state).formatNumber(weight)} → ${massUnit(state).format(goal)}",
                style = intakeText(13),
                color = wloExtendedColors.chartGoal,
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 17.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IntakeEnergyStat(
            "Maintenance",
            state.maintenanceValue,
            if (state.measured) "From your logs" else "Formula estimate",
            Modifier.weight(1f),
            goal = false,
        )
        val amount =
            state.intake?.let {
                DerivedValue(it, Provenance.Derived("intake/display-v1", listOf(state.origin.name.lowercase())))
            }
        IntakeEnergyStat(
            if (state.weekly) "Your daily intake · weekly average" else "Your daily intake",
            amount,
            when (state.origin) {
                IntakeOrigin.SAVED -> "Saved target"
                IntakeOrigin.SUGGESTED ->
                    if (state.suggested == state.intake) "Suggested" else "Selected intake"
                IntakeOrigin.MANUAL -> "Set by you"
            },
            Modifier.weight(1f),
            goal = true,
        )
    }
    HorizontalDivider()
    IntakeProjection(state)
    HorizontalDivider()
}

@Composable
private fun IntakeEnergyStat(
    label: String,
    value: DerivedValue<Double>?,
    source: String,
    modifier: Modifier,
    goal: Boolean,
) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, horizontalAlignment = if (goal) Alignment.End else Alignment.Start) {
        Text(label, style = intakeText(12), color = secondary)
        Spacer(Modifier.height(6.dp))
        Text(
            value?.value?.let(::kcal) ?: "—",
            style =
                intakeText(34, 44.2f, FontWeight.Medium).copy(
                    letterSpacing = (-0.5).sp,
                    fontFeatureSettings = "tnum, lnum",
                ),
            color = if (goal) wloExtendedColors.chartGoal else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text("kcal / day", style = intakeText(12), color = secondary)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.heightIn(min = (24.5f * LocalDensity.current.fontScale).dp)) {
            value?.let {
                ProvenanceChip(
                    it,
                    ::kcal,
                    label = source,
                    compact = true,
                    labelColor = if (goal) wloExtendedColors.chartGoal else wloExtendedColors.developing,
                )
            }
        }
    }
}

/** Standard M3 filled field keeps its label inside; the public border modifier supplies the approved outline. */
@Composable
internal fun IntakeAmountField(
    state: IntakeTargetState,
    viewModel: IntakeTargetViewModel,
) {
    val shape = RoundedCornerShape(5.dp)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    TextField(
        value = state.text,
        onValueChange = { value -> viewModel.edit { it.copy(text = value.replace(',', '.')) } },
        label = {
            Text(
                if (state.mode == IntakeEntryMode.INTAKE) "Daily calorie target" else "Daily adjustment",
                modifier = Modifier.padding(bottom = 2.dp),
                style = intakeText(12),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        suffix = {
            Text(
                "kcal / day",
                modifier = Modifier.offset(y = 10.dp),
                style = intakeText(14),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        textStyle = intakeText(34, 49.3f).copy(letterSpacing = (-2).sp, fontFeatureSettings = "tnum, lnum"),
        singleLine = true,
        enabled = !state.saving,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(onDone = {
                keyboard?.hide()
                focus.clearFocus()
            }),
        shape = shape,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(
                    min = 91.dp,
                ).border(1.dp, MaterialTheme.colorScheme.outline, shape),
    )
}

@Composable
internal fun IntakeSliderLabels(
    state: IntakeTargetState,
    maintenance: Double,
    extent: Double,
) {
    val direct = state.mode == IntakeEntryMode.INTAKE
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            if (direct) kcal(maintenance - extent) else "Deficit",
            style = intakeText(11),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (direct) "${kcal(maintenance)} maintenance" else "0",
            style = intakeText(11),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (direct) kcal(maintenance + extent) else "Surplus",
            style = intakeText(11),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun IntakeDisclosure(
    title: String,
    open: Boolean,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp),
        shape = RoundedCornerShape(0.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = intakeText(14, weight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let { Text(it, style = intakeText(12), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(if (open) "−" else "+", style = intakeText(22), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun intakeMacroSummary(state: IntakeTargetState): String {
    val intake = state.intake ?: return "Balanced split · ${state.protein} / ${state.carbs} / ${state.fat}"

    fun grams(
        text: String,
        divisor: Int,
    ): Int = (intake * (text.toDoubleOrNull() ?: 0.0) / divisor).roundToInt()
    return "${grams(
        state.protein,
        400,
    )} g protein · ${grams(state.carbs, 400)} g carbs · ${grams(state.fat, 900)} g fat"
}

/** Recommendation status stays visible beside its next action, never hidden in calculation details. */
@Composable
internal fun IntakeRecommendation(
    state: IntakeTargetState,
    onUse: () -> Unit,
    onHealth: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val applied = state.origin == IntakeOrigin.SUGGESTED && state.intake == state.suggested
        val adjustmentMode = state.mode == IntakeEntryMode.ADJUSTMENT
        val suggestionAmount =
            if (adjustmentMode) {
                state.suggested?.let { it - (state.maintenance ?: 0.0) }
            } else {
                state.suggested
            }
        val valueLabel = (if (adjustmentMode && (suggestionAmount ?: 0.0) > 0) "+" else "") + kcal(suggestionAmount)
        val kind = if (adjustmentMode) "adjustment" else "intake"
        val message =
            if (state.suggestionStatus == SuggestionStatus.READY) {
                "Suggested $kind${if (applied) " applied" else ""} · $valueLabel kcal"
            } else {
                state.suggestionNote
            }
        Text(message, modifier = Modifier.weight(1f), style = intakeText(12))
        when (state.suggestionStatus) {
            SuggestionStatus.READY ->
                if (!applied) {
                    TextButton(
                        onClick = onUse,
                        enabled = !state.saving,
                    ) { Text("Use", style = intakeText(12)) }
                }
            SuggestionStatus.HEALTH_REQUIRED ->
                TextButton(
                    onClick = onHealth,
                    enabled = !state.saving,
                ) { Text("Complete", style = intakeText(12)) }
            SuggestionStatus.PROFILE_REQUIRED ->
                TextButton(
                    onClick = onProfile,
                    enabled = !state.saving,
                ) { Text("Profile", style = intakeText(12)) }
            SuggestionStatus.UNAVAILABLE -> Unit
        }
    }
}
