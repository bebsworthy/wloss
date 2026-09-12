package app.wlo.feature.f02.food.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import app.wlo.core.data.RoomDiaryRepository
import app.wlo.core.designsystem.ProvenanceChip
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.engines.FoodMath
import app.wlo.core.model.DerivedValue
import app.wlo.core.model.Provenance
import app.wlo.feature.f02.food.state.CustomFoodDraft
import app.wlo.feature.f02.food.state.PortionSelection

/*
 * The F02 sheets: portion sizing (presets + the g/ml/serving converter,
 * R-D10), the kcal-only quick-add (F02 §4), and the custom-food form whose
 * energy-density rail is surfaced as copy before save (F02 §3/§8) — a typo
 * check, never a scolding.
 */

/** Live kcal preview provenance (the diary math runs again at save). */
private fun portionPreview(
    grams: Double,
    kcalPer100g: Double?,
): DerivedValue<Double> =
    DerivedValue(
        value = FoodMath.scale(kcalPer100g, grams) ?: 0.0,
        provenance =
            Provenance.Derived(
                formulaVersion = RoomDiaryRepository.PORTION_FORMULA_VERSION,
                inputs = listOf("grams=$grams"),
            ),
    )

@Composable
public fun PortionSheetContent(
    selection: PortionSelection,
    onSave: () -> Unit,
    onQuantity: (String) -> Unit,
    onUnit: (String) -> Unit,
    onPreset: (Int) -> Unit,
): Unit =
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(text = selection.food.name, style = wloType.title)
        selection.food.brand?.let { brand ->
            Text(text = brand, style = wloType.label, color = wloExtendedColors.textTertiary)
        }

        if (selection.presets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT), modifier = Modifier.fillMaxWidth()) {
                selection.presets.forEachIndexed { index, preset ->
                    SelectChip(
                        label = preset.label,
                        selected = index == selection.chosenPresetIndex,
                        onClick = { onPreset(index) },
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = selection.quantityText,
                onValueChange = onQuantity,
                modifier = Modifier.weight(1f).testTag("f02-quantity-field"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = wloType.statM,
            )
            for (unit in UNITS) {
                SelectChip(
                    label = unit,
                    selected = unit == selection.unit,
                    onClick = { onUnit(unit) },
                )
            }
        }

        // Live portion preview — the number the diary math will run (D6 chip).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "on the plate",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            ProvenanceChip(
                value = portionPreview(selection.grams, selection.food.kcalPer100g),
                format = { "%,.0f kcal".format(it) },
                modifier = Modifier.testTag("f02-portion-preview"),
            )
        }

        ActionRow(label = "save to diary", modifier = Modifier.fillMaxWidth().testTag("f02-portion-save")) { onSave() }
        Spacer(Modifier.height(WloSpacing.TIGHT))
    }

@Composable
public fun QuickAddSheetContent(
    kcalText: String,
    name: String,
    onKcal: (String) -> Unit,
    onName: (String) -> Unit,
    onSave: () -> Unit,
): Unit =
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(text = "Quick-add calories", style = wloType.title)
        Text(
            text = "for stubborn cases — the number you know, nothing else implied",
            style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            color = wloExtendedColors.textTertiary,
        )
        OutlinedTextField(
            value = kcalText,
            onValueChange = onKcal,
            modifier = Modifier.fillMaxWidth().testTag("f02-quick-add-kcal"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = wloType.statL,
            placeholder = { Text("kcal", style = wloType.body, color = wloExtendedColors.textTertiary) },
        )
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("what it was (optional)", style = wloType.body) },
        )
        ActionRow(
            label = "save to diary",
            modifier = Modifier.fillMaxWidth().testTag("f02-quick-add-save"),
        ) { onSave() }
    }

@Composable
public fun CustomFoodSheetContent(
    draft: CustomFoodDraft,
    onChange: (CustomFoodDraft) -> Unit,
    onSave: () -> Unit,
): Unit =
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.CARD),
    ) {
        Text(text = if (draft.editId == null) "Create a food" else "Edit food", style = wloType.title)

        OutlinedTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it)) },
            modifier = Modifier.fillMaxWidth().testTag("f02-custom-name"),
            singleLine = true,
            placeholder = { Text("name", style = wloType.body) },
        )
        OutlinedTextField(
            value = draft.brand,
            onValueChange = { onChange(draft.copy(brand = it)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("brand (optional)", style = wloType.body) },
        )

        Text(
            text = "per 100 g (or 100 ml)",
            style = wloType.label,
            color = wloExtendedColors.textTertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            NumberField("kcal", draft.kcalText, Modifier.weight(1f).testTag("f02-custom-kcal")) {
                onChange(draft.copy(kcalText = it))
            }
            NumberField("protein", draft.proteinText, Modifier.weight(1f)) { onChange(draft.copy(proteinText = it)) }
            NumberField("carbs", draft.carbText, Modifier.weight(1f)) { onChange(draft.copy(carbText = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
            NumberField("fat", draft.fatText, Modifier.weight(1f)) { onChange(draft.copy(fatText = it)) }
            NumberField("fiber", draft.fiberText, Modifier.weight(1f)) { onChange(draft.copy(fiberText = it)) }
            Spacer(Modifier.weight(1f))
        }

        // The sanity rail, surfaced BEFORE save (F02 §3/§8: the 27M-kcal candy
        // bar cannot exist, even manually) — copy checks the label, never the person.
        if (draft.overRail) {
            Text(
                text =
                    "above the physical ceiling — pure fat is 900 kcal per 100 g, " +
                        "so nothing real is denser. Worth re-reading the label.",
                style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
                color = wloExtendedColors.held,
                modifier = Modifier.testTag("f02-custom-rail"),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = draft.macrosVerified,
                onCheckedChange = { onChange(draft.copy(macrosVerified = it)) },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
            Text(
                text = "I read the values off the label",
                style = wloType.body.copy(fontSize = wloType.receipt.fontSize),
            )
        }

        ActionRow(
            label = if (draft.editId == null) "add to my catalog" else "save changes",
            modifier = Modifier.fillMaxWidth().testTag("f02-custom-save"),
        ) { onSave() }
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
        placeholder = { Text(label, style = wloType.body.copy(fontSize = wloType.receipt.fontSize)) },
    )

private val UNITS: List<String> = listOf("g", "ml", "serving")
