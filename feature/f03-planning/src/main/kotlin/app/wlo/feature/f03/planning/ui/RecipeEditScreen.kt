package app.wlo.feature.f03.planning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import app.wlo.core.designsystem.SelectChip
import app.wlo.core.designsystem.WloCard
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.feature.f03.planning.state.PlanEvent
import app.wlo.feature.f03.planning.state.PlanViewModel
import app.wlo.feature.f03.planning.state.RecipeForm

/**
 * The R-U15 recipe editor: create, or edit → version N+1. Per-serving macros +
 * meal-slot chips + tags — lean but real; the library the engine deals from is
 * always user-editable (R-S3's inspectable-content rule, continued).
 */
@Composable
public fun RecipeEditScreen(
    viewModel: PlanViewModel,
    recipeId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var form by remember { mutableStateOf(RecipeForm(existingId = recipeId)) }

    LaunchedEffect(recipeId) {
        recipeId?.let { id ->
            viewModel.loadRecipeForm(id)?.let { loaded -> form = loaded }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WloSpacing.SCREEN)
                .padding(bottom = WloSpacing.SCREEN),
        verticalArrangement = Arrangement.spacedBy(WloSpacing.SCREEN),
    ) {
        Text(
            text = if (recipeId == null) "New recipe" else "Edit recipe",
            style = wloType.title.copy(fontSize = wloType.title.fontSize * 1.5f),
            modifier = Modifier.padding(top = WloSpacing.SCREEN).testTag("f03-recipe-edit-title"),
        )

        WloCard {
            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it) },
                modifier = Modifier.fillMaxWidth().testTag("f03-recipe-name"),
                label = { Text("name", style = wloType.label) },
                singleLine = true,
                textStyle = wloType.body,
            )
            OutlinedTextField(
                value = form.servingsBase,
                onValueChange = { form = form.copy(servingsBase = it) },
                modifier = Modifier.fillMaxWidth().testTag("f03-recipe-servings"),
                label = { Text("servings the recipe makes", style = wloType.label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = wloType.body.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                text = "suits",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.TIGHT)) {
                for (slot in listOf("breakfast", "lunch", "dinner", "snack")) {
                    SelectChip(
                        label = slot,
                        selected = slot in form.mealSlots,
                        onClick = {
                            form =
                                if (slot in form.mealSlots) {
                                    form.copy(mealSlots = form.mealSlots - slot)
                                } else {
                                    form.copy(mealSlots = form.mealSlots + slot)
                                }
                        },
                        modifier = Modifier.testTag("f03-recipe-slot-$slot"),
                    )
                }
            }
        }

        WloCard {
            Text(
                text = "per-serving nutrition",
                style = wloType.label,
                color = wloExtendedColors.textTertiary,
            )
            MacroField("kcal", form.kcal, "f03-recipe-kcal") { form = form.copy(kcal = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                MacroField("P g", form.proteinG, "f03-recipe-protein", Modifier.weight(1f)) { value ->
                    form = form.copy(proteinG = value)
                }
                MacroField("C g", form.carbG, "f03-recipe-carb", Modifier.weight(1f)) { form = form.copy(carbG = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(WloSpacing.CARD)) {
                MacroField("F g", form.fatG, "f03-recipe-fat", Modifier.weight(1f)) { form = form.copy(fatG = it) }
                MacroField("fiber g", form.fiberG, "f03-recipe-fiber", Modifier.weight(1f)) { value ->
                    form = form.copy(fiberG = value)
                }
            }
            OutlinedTextField(
                value = form.tags,
                onValueChange = { form = form.copy(tags = it) },
                modifier = Modifier.fillMaxWidth().testTag("f03-recipe-tags"),
                label = { Text("tags (comma-separated)", style = wloType.label) },
                singleLine = true,
                textStyle = wloType.body,
            )
            Text(
                text = "provenance: manual entry · nutrition estimated",
                style = wloType.receipt,
                color = wloExtendedColors.textTertiary,
            )
        }

        PrimaryRow(
            label = if (recipeId == null) "save to library" else "save as new version",
            modifier = Modifier.testTag("f03-recipe-save"),
            onClick = {
                viewModel.onEvent(PlanEvent.SaveRecipe(form))
                onDone()
            },
        )
        PrimaryRow(
            label = "discard",
            modifier = Modifier.testTag("f03-recipe-discard"),
            onClick = onDone,
        )
    }
}

@Composable
private fun MacroField(
    label: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
): Unit =
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().testTag(tag),
        label = { Text(label, style = wloType.label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = wloType.body.copy(fontFeatureSettings = "tnum"),
    )
