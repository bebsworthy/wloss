@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.wlo.feature.f03.planning.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wlo.core.data.AgendaFood
import app.wlo.core.data.AgendaItem
import app.wlo.core.data.NutrientCoverage
import app.wlo.core.data.agendaCoverage
import app.wlo.core.designsystem.WloIcons
import app.wlo.core.designsystem.WloMealIcons
import app.wlo.core.designsystem.WloSwipeRevealRow
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.model.Recipe
import app.wlo.feature.f03.planning.state.AgendaViewModel
import app.wlo.feature.f03.planning.state.weekStart
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val meals = listOf("breakfast" to "Breakfast", "lunch" to "Lunch", "dinner" to "Dinner", "snack" to "Snacks")

private fun number(n: Double): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(n.roundToInt())

private fun date(day: Long): LocalDate = LocalDate.fromEpochDays(day.toInt())

private fun month(day: Long): String =
    date(day)
        .month.name
        .take(3)
        .lowercase()
        .replaceFirstChar { it.uppercase() }

private fun weekLabel(day: Long): String {
    val from = weekStart(day)
    val to =
        from + 6
    return if (date(from).month ==
        date(to).month
    ) {
        "${date(from).day}–${date(to).day} ${month(to)}"
    } else {
        "${date(from).day} ${month(from)}–${date(to).day} ${month(to)}"
    }
}

@Composable
public fun MealAgendaScreen(viewModel: AgendaViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addMeal by rememberSaveable { mutableStateOf<String?>(null) }
    var replacement by rememberSaveable(
        stateSaver =
            Saver<AgendaItem?, String>(
                save = { it?.let { item -> Json.encodeToString(AgendaItem.serializer(), item) } ?: "" },
                restore = { it.takeIf(String::isNotEmpty)?.let { text -> Json.decodeFromString<AgendaItem>(text) } },
            ),
    ) { mutableStateOf<AgendaItem?>(null) }
    var detail by remember { mutableStateOf<Recipe?>(null) }
    var nutritionDetails by rememberSaveable { mutableStateOf(false) }
    var calendar by rememberSaveable { mutableStateOf(false) }
    var suggest by rememberSaveable { mutableStateOf(false) }
    var openRow by remember { mutableStateOf<String?>(null) }
    var addedCount by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(addMeal) { addedCount = 0 }
    val snack = remember { SnackbarHostState() }
    val sheetState = rememberSaveableStateHolder()
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val outcome = snack.showSnackbar(message, if (state.undo != null) "Undo" else null, withDismissAction = true)
        if (outcome == SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.dismissMessage()
    }
    Scaffold(snackbarHost = { SnackbarHost(snack) }, topBar = {
        TopAppBar(
            title = { Text("Plan", style = wloType.statM) },
            expandedHeight = 56.dp,
            actions = {
                IconButton(onClick = { viewModel.select(state.selected - 7) }) {
                    Text(
                        "‹",
                        fontSize = 28.sp,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "Previous week"
                            },
                    )
                }
                TextButton(
                    onClick = {
                        calendar = true
                    },
                    modifier =
                        Modifier.semantics {
                            contentDescription = "Choose date"
                        },
                    contentPadding = PaddingValues(horizontal = 2.dp),
                ) {
                    Text(weekLabel(state.selected), style = wloType.statS, color = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { viewModel.select(state.selected + 7) }) {
                    Text(
                        "›",
                        fontSize = 28.sp,
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    "Next week"
                            },
                    )
                }
                IconButton(onClick = { suggest = true }, enabled = state.draft == null && !state.busy) {
                    Icon(WloMealIcons.Suggest, "Suggest meals", tint = MaterialTheme.colorScheme.primary)
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
    }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).testTag("meal-agenda"),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                var drag by remember { mutableFloatStateOf(0f) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp).pointerInput(state.selected) {
                        detectHorizontalDragGestures(onDragStart = {
                            drag =
                                0f
                        }, onDragEnd = {
                            if (abs(drag) >
                                60
                            ) {
                                viewModel.select(state.selected + if (drag < 0) 7 else -7)
                            }
                        }) { _, amount -> drag += amount }
                    },
                ) {
                    for (day in weekStart(state.selected)..weekStart(state.selected) + 6) {
                        val items = state.items.filter { it.day == day }
                        val coverage = agendaCoverage(items)[0]
                        val target =
                            state.days
                                .firstOrNull { it.dayEpochDay == day }
                                ?.budgetKcal
                                ?.value
                        DayButton(day, state.selected == day, items.isNotEmpty(), coverage, target, {
                            openRow = null
                            viewModel.select(day)
                        }, Modifier.weight(1f))
                    }
                }
            }
            item {
                val current = state.days.firstOrNull { it.dayEpochDay == state.selected }
                NutritionSummary(
                    agendaCoverage(
                        state.items.filter {
                            it.day == state.selected
                        },
                    ),
                    listOf(
                        current?.budgetKcal?.value,
                        current?.proteinG?.value,
                        current?.carbG?.value,
                        current?.fatG?.value,
                        current?.fiberG?.value,
                    ),
                    onExplain = { nutritionDetails = true },
                )
            }
            if (state.draft != null) {
                item {
                    DraftActions(state.busy, viewModel::discard, viewModel::commit)
                }
            }
            meals.forEach { (key, label) ->
                val entries = state.items.filter { it.day == state.selected && it.meal == key }
                item(key = "header-$key") {
                    var expanded by rememberSaveable(state.selected, key) { mutableStateOf(true) }
                    // Expansion remains a standard ListItem action, no extra chevron.
                    MealSection(
                        key,
                        label,
                        entries,
                        expanded,
                        { expanded = !expanded },
                        { addMeal = key },
                        state.busy,
                        openRow,
                        { openRow = it },
                        {
                            replacement = it
                            addMeal = key
                        },
                        viewModel::remove,
                        { item -> detail = state.recipes.firstOrNull { it.id == item.food.recipeId } },
                    )
                }
            }
        }
    }
    if (nutritionDetails) {
        NutritionDetails(
            state.items.filter { it.day == state.selected },
            state.days.firstOrNull { it.dayEpochDay == state.selected },
            state.selected,
            onDismiss = { nutritionDetails = false },
        )
    }
    if (calendar) {
        CalendarDialog(state.selected, state.today, { calendar = false }, {
            viewModel.select(it)
            calendar = false
        })
    }
    if (addMeal !=
        null
    ) {
        AcquisitionSheet(
            state.foods,
            replacement,
            state.selected <= state.today,
            state.busy,
            "${meals.firstOrNull { it.first == addMeal }?.second} · ${date(state.selected)}",
            addedCount,
            state.message,
            {
                addMeal = null
                replacement = null
            },
            { food, quantity, eaten ->
                val existing = replacement
                if (existing != null) {
                    viewModel.replace(existing, food, quantity) {
                        addMeal = null
                        replacement = null
                    }
                } else {
                    viewModel.add(addMeal!!, food, quantity, eaten) { addedCount++ }
                }
            },
        )
    }
    if (suggest) {
        sheetState.SaveableStateProvider("suggest") {
            SuggestSheet(
                state.selected,
                state.busy,
                state.message,
                {
                    viewModel.cancelPreview()
                    suggest = false
                },
            ) { from, to, selected, weekdays ->
                viewModel.preview(from, to, selected, weekdays) {
                    suggest =
                        false
                }
            }
        }
    }
    detail?.let { recipe ->
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(recipe.name, style = wloType.titleL)
                recipe.ingredients.forEach { ingredient ->
                    Text("${trim1(ingredient.qty)} ${ingredient.unit} ${ingredient.name}", style = wloType.body)
                }
                recipe.steps.forEachIndexed {
                    index,
                    step,
                    ->
                    Text("${index + 1}. $step", Modifier.padding(vertical = 8.dp), style = wloType.body)
                }
                TextButton(onClick = {
                    detail =
                        null
                }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun DayButton(
    day: Long,
    selected: Boolean,
    hasItems: Boolean,
    coverage: NutrientCoverage,
    target: Double?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(
                RoundedCornerShape(12.dp),
            ).background(
                if (selected) colors.surfaceContainerHigh else colors.background,
            ).clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription =
                    "${date(day)}" +
                    if (!hasItems) {
                        ", no entries"
                    } else if (!coverage.complete) {
                        ", nutrition incomplete"
                    } else if (target != null &&
                        coverage.known > target
                    ) {
                        ", ${number(coverage.known - target)} kcal over target"
                    } else {
                        ", ${number(coverage.known)} kcal"
                    }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date(day).dayOfWeek.name.take(1),
            style = wloType.label,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
        )
        Box(
            Modifier.size((34 * LocalDensity.current.fontScale).coerceAtMost(52f).dp),
            contentAlignment = Alignment.Center,
        ) {
            if (hasItems && target != null && target > 0) {
                Canvas(Modifier.size((28 * LocalDensity.current.fontScale).coerceAtMost(48f).dp)) {
                    val stroke =
                        Stroke(
                            1.5.dp.toPx(),
                            pathEffect =
                                if (!coverage.complete) {
                                    PathEffect.dashPathEffect(
                                        floatArrayOf(3.dp.toPx(), 3.dp.toPx()),
                                    )
                                } else {
                                    null
                                },
                        )
                    drawCircle(colors.outlineVariant, style = stroke)
                    if (coverage.complete) {
                        drawArc(
                            colors.onSurfaceVariant,
                            -90f,
                            (360 * coverage.known / target).coerceIn(0.0, 360.0).toFloat(),
                            false,
                            style = stroke,
                        )
                    }
                }
            }
            Text(
                date(day).day.toString(),
                style = wloType.statS,
                color = if (selected) colors.primary else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NutritionSummary(
    values: List<NutrientCoverage>,
    targets: List<Double?>,
    onExplain: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "How these totals are calculated", onClick = onExplain)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Nutrient("Calories", values[0], targets[0], "kcal", true)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("Protein", "Carbs", "Fat").forEachIndexed {
                i,
                name,
                ->
                Box(Modifier.weight(1f)) { Nutrient(name, values[i + 1], targets[i + 1], "g", false) }
            }
        }
        val fiber = values[4]
        val target = targets[4]
        Text(
            "Fiber   ${number(
                fiber.known,
            )}${target?.let { " / ${number(it)} g" } ?: " g"}   ${remainder(fiber,target,"g")}",
            style = wloType.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun remainder(
    value: NutrientCoverage,
    target: Double?,
    unit: String,
): String =
    when {
        !value.complete -> "incomplete"
        target == null || target <= 0 -> ""
        number(value.known) == number(target) -> "Target reached"
        value.known > target -> "${number(value.known.roundToInt().toDouble() - target.roundToInt())} $unit over target"
        else -> "${number(target.roundToInt().toDouble() - value.known.roundToInt())} $unit remaining"
    }

@Composable
private fun Nutrient(
    label: String,
    value: NutrientCoverage,
    target: Double?,
    unit: String,
    hero: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = wloType.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hero && LocalDensity.current.fontScale > 1.2f) {
            Text(number(value.known), style = wloType.statL, modifier = Modifier.testTag("agenda-kcal"))
            Text(target?.let { "/ ${number(it)} $unit" } ?: unit, style = wloType.caption)
            Text(remainder(value, target, unit), style = wloType.caption)
        } else if (hero) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(number(value.known), style = wloType.statL, modifier = Modifier.testTag("agenda-kcal"))
                    Text(
                        target?.let {
                            " / ${number(it)} $unit"
                        } ?: " $unit",
                        style = wloType.caption,
                        modifier =
                            Modifier.padding(
                                bottom = 3.dp,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(remainder(value, target, unit), style = wloType.caption, textAlign = TextAlign.End)
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(number(value.known), style = wloType.title)
                Text(
                    target?.let {
                        " / ${number(it)} $unit"
                    } ?: " $unit",
                    style = wloType.label,
                    modifier = Modifier.padding(bottom = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                remainder(value, target, unit),
                style = wloType.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (value.complete &&
            target != null &&
            target > 0
        ) {
            CoverageBar(
                value.known,
                target,
                Modifier.fillMaxWidth().padding(top = 4.dp).height(if (hero) 5.dp else 4.dp),
            )
        }
    }
}

/** Standard progress cannot show target-relative and excess segments simultaneously (WLO-0156). */
@Composable
private fun CoverageBar(
    total: Double,
    target: Double,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val excess = wloExtendedColors.nutritionOverage
    Canvas(
        modifier.clip(RoundedCornerShape(5.dp)).semantics {
            contentDescription =
                "${number(
                    total,
                )} of ${number(
                    target,
                )}; ${if (total > target) {
                    "${number(
                        total - target,
                    )} over target"
                } else {
                    "${number(target - total)} remaining"
                }}"
        },
    ) {
        drawRect(colors.outlineVariant)
        val fraction = if (total > target) target / total else total / target
        val width = size.width * fraction.coerceIn(0.0, 1.0).toFloat()
        drawRect(colors.primary, size = Size(width, size.height))
        if (total >
            target
        ) {
            drawRect(excess, Offset(width, 0f), Size(size.width - width, size.height))
            drawLine(colors.background, Offset(width, 0f), Offset(width, size.height), 1.dp.toPx())
        }
    }
}

@Composable
private fun MealSection(
    key: String,
    label: String,
    entries: List<AgendaItem>,
    expanded: Boolean,
    toggle: () -> Unit,
    add: () -> Unit,
    busy: Boolean,
    openRow: String?,
    open: (String?) -> Unit,
    replace: (AgendaItem) -> Unit,
    remove: (AgendaItem) -> Unit,
    details: (AgendaItem) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sums = agendaCoverage(entries)
    Column {
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = colors.outlineVariant)
        ListItem(
            headlineContent = { Text(label, style = wloType.title) },
            leadingContent = { MealIcon(key) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entries.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                "${number(sums[0].known)} kcal${if (!sums[0].complete) "+" else ""}",
                                style = wloType.statS,
                            )
                            Text(
                                listOf("P", "C", "F")
                                    .mapIndexed {
                                        i,
                                        n,
                                        ->
                                        "$n ${number(sums[i + 1].known)}${if (!sums[i + 1].complete) "+" else ""} g"
                                    }.joinToString(if (LocalDensity.current.fontScale > 1.2f) "\n" else " · "),
                                style = wloType.label,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = add, enabled = !busy, modifier = Modifier.testTag("add-$key")) {
                        Icon(WloIcons.Plus, "Add food to $label", tint = colors.primary)
                    }
                }
            },
            modifier =
                Modifier.padding(horizontal = 4.dp).heightIn(min = 88.dp).clickable(onClick = toggle).semantics {
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
            colors = ListItemDefaults.colors(containerColor = colors.background),
        )
        if (expanded) {
            entries.forEach { item ->
                key(item.id) {
                    val recipe = item.food.recipeId != null
                    val count = if (recipe) 3 else 2
                    WloSwipeRevealRow(revealWidth = (count * 48).dp, onTrigger = {
                    }, triggerFraction = 40f / (count * 48), revealed = openRow == item.id, onRevealChange = {
                        open(
                            if (it) item.id else null,
                        )
                    }, reveal = {
                        _,
                        _,
                        ->
                        if (openRow == item.id) {
                            Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    open(null)
                                    replace(item)
                                }, enabled = !busy && !item.draft) {
                                    Icon(WloMealIcons.Replace, "Replace ${item.food.name}")
                                }
                                if (recipe) {
                                    IconButton(onClick = {
                                        open(null)
                                        details(item)
                                    }) {
                                        Icon(WloMealIcons.Recipe, "Recipe details for ${item.food.name}")
                                    }
                                }
                                IconButton(onClick = {
                                    open(null)
                                    remove(item)
                                }, enabled = !busy && !item.draft) {
                                    Icon(
                                        WloMealIcons.Remove,
                                        "Remove ${item.food.name}",
                                    )
                                }
                            }
                        }
                    }) {
                        ListItem(
                            headlineContent = { Text(item.food.name, style = wloType.statS) },
                            supportingContent = {
                                Text(
                                    "${trim1(item.quantity)} × ${item.food.unit} · ${item.food.kcal?.let {
                                        "${number(
                                            it * item.quantity,
                                        )} kcal"
                                    } ?: "kcal unknown"} · ${if (item.draft) {
                                        "Suggestion"
                                    } else if (item.eaten) {
                                        "Eaten"
                                    } else {
                                        "Planned"
                                    }}",
                                    style = wloType.caption,
                                    color = colors.onSurfaceVariant,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = colors.background),
                            modifier =
                                Modifier
                                    .padding(horizontal = 4.dp)
                                    .semantics {
                                        customActions =
                                            listOf(
                                                CustomAccessibilityAction("Show actions") {
                                                    open(item.id)
                                                    true
                                                },
                                                CustomAccessibilityAction("Replace") {
                                                    if (!item.draft) replace(item)
                                                    !item.draft
                                                },
                                                CustomAccessibilityAction("Remove") {
                                                    if (!item.draft) remove(item)
                                                    !item.draft
                                                },
                                            )
                                    }.clickable { open(if (openRow == item.id) null else item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MealIcon(meal: String) {
    val image =
        when (meal) {
            "breakfast" -> WloMealIcons.Breakfast
            "lunch" -> WloMealIcons.Lunch
            "dinner" -> WloMealIcons.Dinner
            else -> WloMealIcons.Snacks
        }
    Icon(image, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CalendarDialog(
    selected: Long,
    today: Long,
    dismiss: () -> Unit,
    choose: (Long) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = selected * 86_400_000L)
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(state.selectedDateMillis) {
        if (initialized) state.selectedDateMillis?.let { choose(it / 86_400_000) }
        initialized = true
    }
    DatePickerDialog(onDismissRequest = dismiss, confirmButton = {
        TextButton(onClick = {
            choose(today)
        }) { Text("Today") }
    }, dismissButton = {
        TextButton(
            onClick = dismiss,
        ) { Text("Cancel") }
    }) { DatePicker(state, showModeToggle = false) }
}

@Composable
private fun AcquisitionSheet(
    foods: List<AgendaFood>,
    replacement: AgendaItem?,
    allowEaten: Boolean,
    busy: Boolean,
    context: String,
    addedCount: Int,
    message: String?,
    dismiss: () -> Unit,
    save: (AgendaFood, Double, Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var eaten by rememberSaveable { mutableStateOf(replacement?.eaten ?: allowEaten) }
    var candidate by rememberSaveable(
        stateSaver =
            Saver<AgendaFood?, String>(
                save = { it?.let { food -> Json.encodeToString(AgendaFood.serializer(), food) } ?: "" },
                restore = { it.takeIf(String::isNotEmpty)?.let { text -> Json.decodeFromString<AgendaFood>(text) } },
            ),
    ) { mutableStateOf(replacement?.food) }
    var quantity by rememberSaveable { mutableStateOf(replacement?.quantity?.let(::trim1) ?: "1") }
    var custom by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("serving") }
    var kcal by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = dismiss) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(20.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (replacement ==
                            null
                        ) {
                            "Add food or drink"
                        } else {
                            "Replace food"
                        },
                        style = wloType.title,
                        modifier = Modifier.weight(1f),
                    )
                    ; TextButton(onClick = dismiss) { Text("Done") }
                }
            }
            item { Text(context, style = wloType.caption) }
            if (addedCount > 0) item { Text("$addedCount items added", style = wloType.caption) }
            if (message != null) {
                item {
                    Text(message, color = MaterialTheme.colorScheme.error, style = wloType.caption)
                }
            }
            if (replacement == null &&
                allowEaten
            ) {
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Eaten", "Planned").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected =
                                    eaten == (i == 0),
                                onClick = { eaten = i == 0 },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(label) }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(query, {
                    query = it
                }, label = {
                    Text(
                        "Search foods and recipes",
                    )
                }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), singleLine = true)
            }
            candidate?.let { food ->
                item {
                    Text(food.name, style = wloType.title)
                    OutlinedTextField(quantity, {
                        quantity = it
                    }, label = {
                        Text(
                            "Quantity (${food.unit})",
                        )
                    }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        onClick = {
                            save(food, quantity.toDouble(), eaten)
                            if (replacement ==
                                null
                            ) {
                                candidate = null
                            }
                        },
                        enabled =
                            !busy &&
                                quantity.toDoubleOrNull()?.let {
                                    it.isFinite() && it > 0
                                } == true,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (replacement ==
                                null
                            ) {
                                "Add food"
                            } else {
                                "Replace"
                            },
                        )
                    }
                }
            }
            items(
                foods
                    .filter {
                        it.name.contains(query, true)
                    }.take(40),
                key = { "${it.recipeId ?: it.foodId}:${it.name}" },
            ) { food ->
                ListItem(
                    headlineContent = { Text(food.name, style = wloType.body) },
                    supportingContent = {
                        Text(
                            "${if (food.unit == "g") "100 g" else "1 ${food.unit}"} · ${food.kcal?.let {
                                number(
                                    it * if (food.unit == "g") 100 else 1,
                                ) + " kcal"
                            } ?: "kcal unknown"}",
                            style = wloType.caption,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            if (replacement !=
                                null
                            ) {
                                candidate = food
                                quantity =
                                    if (food.unit ==
                                        "g"
                                    ) {
                                        "100"
                                    } else {
                                        "1"
                                    }
                            } else {
                                save(
                                    food,
                                    if (food.unit ==
                                        "g"
                                    ) {
                                        100.0
                                    } else {
                                        1.0
                                    },
                                    eaten,
                                )
                            }
                        }, enabled = !busy) { Icon(WloIcons.Plus, "Add ${food.name}") }
                    },
                    modifier =
                        Modifier.clickable {
                            candidate =
                                food
                            ; quantity = if (food.unit == "g") "100" else "1"
                        },
                )
            }
            item { TextButton(onClick = { custom = !custom }) { Text("Enter another food or drink") } }
            if (custom) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            name,
                            { name = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            quantity,
                            { quantity = it },
                            label = { Text("Quantity") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            unit,
                            { unit = it },
                            label = { Text("Unit") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            kcal,
                            { kcal = it },
                            label = { Text("Calories per unit (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                save(AgendaFood(name, unit, kcal.toDoubleOrNull()), quantity.toDouble(), eaten)
                            },
                            enabled =
                                !busy &&
                                    name.isNotBlank() &&
                                    unit.isNotBlank() &&
                                    quantity.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true &&
                                    (kcal.isBlank() || kcal.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true),
                        ) {
                            Text(
                                if (replacement ==
                                    null
                                ) {
                                    "Add food"
                                } else {
                                    "Replace"
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestSheet(
    selected: Long,
    busy: Boolean,
    message: String?,
    dismiss: () -> Unit,
    preview: (Long, Long, Set<String>, Boolean) -> Unit,
) {
    var from by rememberSaveable { mutableLongStateOf(selected) }
    var to by rememberSaveable { mutableLongStateOf(weekStart(selected) + 6) }
    var weekdays by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableStateOf(listOf("breakfast", "lunch", "dinner")) }
    var picker by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("Suggest meals", style = wloType.titleL)
            if (message != null) Text(message, color = MaterialTheme.colorScheme.error, style = wloType.caption)
            Text("Fill the empty meals you choose.", style = wloType.caption)
            Row {
                TextButton(onClick = { picker = "from" }) { Text("From ${date(from)}") }
                TextButton(onClick = { picker = "to" }) { Text("Through ${date(to)}") }
            }
            meals.forEach { (key, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(key in chosen, { checked ->
                        chosen =
                            if (checked) chosen + key else chosen - key
                    })
                    ;Text(label)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(weekdays, { weekdays = it })
                Text("Weekdays only")
            }
            Button(
                onClick = {
                    preview(from, to, chosen.toSet(), weekdays)
                },
                enabled = !busy && chosen.isNotEmpty() && to >= from && to - from < 31,
                modifier =
                    Modifier
                        .fillMaxWidth(),
            ) {
                Text(if (busy) "Preparing…" else "Preview suggestions")
            }
        }
    }
    picker?.let { which ->
        CalendarDialog(
            if (which ==
                "from"
            ) {
                from
            } else {
                to
            },
            selected,
            { picker = null },
            {
                if (which == "from") from = it else to = it
                picker = null
            },
        )
    }
}

@Composable
private fun NutritionDetails(
    items: List<AgendaItem>,
    day: app.wlo.core.data.DayView?,
    selected: Long,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("How these totals are calculated", style = MaterialTheme.typography.titleLarge)
            Text(
                "Saved portions are multiplied by their nutrition per unit. " +
                    "Diary entries and unresolved plans count once. " +
                    "Suggestions count only in this preview. Missing nutrition stays unknown.",
            )
            val current = day
            val targetSource = current?.budgetKcal?.provenance as? app.wlo.core.model.Provenance.Derived
            val version =
                targetSource
                    ?.inputs
                    ?.firstOrNull {
                        it.startsWith(
                            "targetsVersion=",
                        )
                    }?.substringAfter('=')
            Text(
                version?.let { "Published goals, version $it, effective for ${date(selected)}." }
                    ?: "No published calorie target for this date.",
                style = wloType.caption,
            )
            items.forEach { item ->
                val source =
                    when {
                        item.legacyClaim -> "Legacy confirmed plan; no diary link"
                        item.draft -> "Suggestion preview"
                        item.eaten -> "Food diary"
                        else -> "Planned food"
                    }
                Text("${item.food.name} · $source", style = wloType.body)
                Text(
                    "${item.quantity} × ${item.food.unit} · " +
                        when {
                            item.food.recipeId != null -> "Saved recipe, version ${item.food.recipeVersion}"
                            item.food.foodId != null -> "Saved food catalog nutrition"
                            else -> "Saved nutrition snapshot"
                        },
                    style = wloType.caption,
                )
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    }
}

@Composable
private fun DraftActions(
    busy: Boolean,
    discard: () -> Unit,
    commit: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Suggestions", Modifier.weight(1f), style = wloType.body)
        TextButton(onClick = discard, enabled = !busy) { Text("Discard") }
        TextButton(onClick = commit, enabled = !busy) { Text("Add suggestions") }
    }
}
