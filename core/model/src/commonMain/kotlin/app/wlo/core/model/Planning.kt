package app.wlo.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The canonical item space shared by F03 (meal planning) and F04 (shopping
 * list & pantry) — the DRY anchor of ARCHITECTURE §2.5 ("canonical food/item
 * space for F03↔F04 → :core:model"). These types are the SINGLE OWNER of the
 * shapes both features speak: grocery items, recipes, planned slots and their
 * state machine, consolidation units and the aisle taxonomy. No duplicates of
 * these types may appear in `:core:engines` / `:core:data` / features — the
 * check is review-level (checkArchitecture sees types, not duplicates), kept
 * honest per the M5 review convention.
 *
 * Recipes and grocery items are CONTENT (R-S3): they ship as inspectable data
 * files, never paywalled, user-editable, excludable. License rides the recipe
 * row ("CC0" for the shipped seeds).
 */

/** Recipe identity — stable across versions (versions are immutable rows). */
public typealias RecipeId = String

/** One calendar slot in a plan (R-B1: F03 owns these; they project into the day record). */
public typealias SlotId = String

/**
 * The shipped aisle taxonomy (F04 §3 "a shipped canonical taxonomy… editable
 * wholesale"). Wire names are stable storage/exports vocabulary; display names
 * localize in the UI layer. [defaultOrder] is the shipped aisle sort order
 * (the user's own drag-reorder lives in settings, not here).
 */
@Serializable
public enum class Aisle(
    public val wireName: String,
    public val defaultOrder: Int,
) {
    @SerialName("produce")
    PRODUCE("produce", 0),

    @SerialName("bakery")
    BAKERY("bakery", 1),

    @SerialName("dairy")
    DAIRY("dairy", 2),

    @SerialName("meat-fish")
    MEAT_FISH("meat-fish", 3),

    @SerialName("frozen")
    FROZEN("frozen", 4),

    @SerialName("pantry")
    PANTRY("pantry", 5),

    @SerialName("household")
    HOUSEHOLD("household", 6),

    /** Never assigned by the shipped engine to a known item — the honest bucket. */
    @SerialName("other")
    OTHER("other", 7),
    ;

    public companion object {
        public fun fromWireName(name: String): Aisle? = entries.firstOrNull { it.wireName == name }
    }
}

/** Consolidation dimension of a kitchen measure (F04 §3: never fake a cross-dimension conversion). */
@Serializable
public enum class MeasureKind {
    /** grams (canonical = g). */
    MASS,

    /** milliliters (canonical = ml). */
    VOLUME,

    /** pieces/items (canonical = count). */
    COUNT,
}

/**
 * One recipe ingredient unit. Canonical factors: MASS → grams, VOLUME → ml,
 * COUNT → pieces. Same-kind units are convertible ("250 ml + 1 cup ≈ 487 ml",
 * cup = 240 ml per the F04 §3 canonical case); cross-kind units are NOT — they
 * group under one item with per-source amounts shown ("flour: 300 g + 2 cups").
 * Conversion math lives in :core:common Units (R-D10 converter extension).
 */
@Serializable
public enum class MeasureUnit(
    public val wireName: String,
    public val kind: MeasureKind,
    /** Canonical amount of one unit: g (MASS), ml (VOLUME), pieces (COUNT). */
    public val canonicalFactor: Double,
) {
    @SerialName("g")
    GRAM("g", MeasureKind.MASS, 1.0),

    @SerialName("kg")
    KILOGRAM("kg", MeasureKind.MASS, 1_000.0),

    @SerialName("oz")
    OUNCE("oz", MeasureKind.MASS, 28.349523125),

    @SerialName("lb")
    POUND("lb", MeasureKind.MASS, 453.59237),

    @SerialName("ml")
    MILLILITER("ml", MeasureKind.VOLUME, 1.0),

    @SerialName("l")
    LITER("l", MeasureKind.VOLUME, 1_000.0),

    /** US legal cup = 240 ml — the F04 §3 consolidation example's factor. */
    @SerialName("cup")
    CUP("cup", MeasureKind.VOLUME, 240.0),

    @SerialName("tbsp")
    TABLESPOON("tbsp", MeasureKind.VOLUME, 15.0),

    @SerialName("tsp")
    TEASPOON("tsp", MeasureKind.VOLUME, 5.0),

    /** Pieces/items ("2 eggs", "1 onion", "1 bunch coriander"). */
    @SerialName("x")
    COUNT("x", MeasureKind.COUNT, 1.0),
    ;

    public companion object {
        public fun fromWireName(name: String): MeasureUnit? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * One canonical grocery item — the list/pantry/plan ingredient vocabulary
 * (F04 §3 data model: `ListItem.canonicalItem` / `PantryItem.canonicalItem`).
 * Distinct from the F02 food catalog ([FoodItem]): groceries are what recipes
 * are made of and lists are checked off with; the catalog is what gets logged.
 */
@Serializable
public data class GroceryItem(
    public val id: String,
    public val name: String,
    /** Shipped aisle tag ([Aisle.wireName]); user corrections override per item. */
    public val aisle: String,
    /** The unit new pantry/list rows default to (e.g. eggs count in "x", rice in "g"). */
    public val defaultUnit: String = MeasureUnit.COUNT.wireName,
    /**
     * g per ml for liquids/pourables — an OPTIONAL hint for volume→mass display
     * and pantry sweeps; NEVER used by list consolidation (cross-dimension
     * sums stay honest per F04 §3).
     */
    public val densityGPerMl: Double? = null,
    /**
     * g per piece for count items (1 egg ≈ 50 g) — the nutrition/estimation
     * hint; list consolidation stays in counts and never uses it.
     */
    public val gramsPerPiece: Double? = null,
    /** Search aliases ("scallion" for "spring onion"). */
    public val aliases: List<String> = emptyList(),
)

/**
 * The R-S8 simplified FODMAP vocabulary — the six shipped wire tags ("open
 * set": user/AI recipes may carry other tags; the shipped validator and F09's
 * correlation consume these six). The five fermentable carbohydrate classes +
 * `unknown` for the honest not-yet-categorized case (never a silent guess).
 */
public object FodmapTags {
    public const val FRUCTOSE: String = "fructose"
    public const val LACTOSE: String = "lactose"
    public const val FRUCTANS: String = "fructans"
    public const val GOS: String = "gos"
    public const val POLYOLS: String = "polyols"
    public const val UNKNOWN: String = "unknown"

    /** The shipped vocabulary, in canonical order. */
    public val ALL: List<String> = listOf(FRUCTOSE, LACTOSE, FRUCTANS, GOS, POLYOLS, UNKNOWN)
}

/**
 * One recipe ingredient line, normalized against the canonical item space so
 * F04's consolidation and pantry deduction work with zero mapping (F03 §3
 * "the recipe object"). [qty] is in [unit]; [optional] lines don't block
 * generation when the pantry lacks them.
 */
@Serializable
public data class RecipeIngredient(
    /** [GroceryItem.id] — the canonical reference. */
    public val groceryItemId: String,
    /** Denormalized display name (the canonical name at authoring time). */
    public val name: String,
    public val qty: Double,
    /** [MeasureUnit.wireName]. */
    public val unit: String,
    public val optional: Boolean = false,
)

/** Per-serving nutrition (R-A4 v1 scope: kcal + macros + fiber). */
@Serializable
public data class NutritionPerServing(
    public val kcal: Double,
    public val proteinG: Double,
    public val carbG: Double,
    public val fatG: Double,
    public val fiberG: Double,
)

/** Where a recipe row came from (F03 §3 provenance). */
@Serializable
public enum class RecipeSource(
    public val wireName: String,
) {
    /** Shipped seed library (R-S3, license CC0). */
    @SerialName("seed")
    SEED("seed"),

    /** Manual entry. */
    @SerialName("manual")
    MANUAL("manual"),

    /** URL/text import (structured parsing, F03 §3). */
    @SerialName("import")
    IMPORT("import"),

    /** AI draft — visually distinct until reviewed and saved (F03 §9). */
    @SerialName("ai-draft")
    AI_DRAFT("ai-draft"),
    ;

    public companion object {
        public fun fromWireName(name: String): RecipeSource? = entries.firstOrNull { it.wireName == name }
    }
}

/** How the per-serving nutrition numbers were obtained (F03 §3 `nutrition.basis`). */
@Serializable
public enum class NutritionBasis(
    public val wireName: String,
) {
    @SerialName("label")
    LABEL("label"),

    @SerialName("db")
    DB("db"),

    @SerialName("estimated")
    ESTIMATED("estimated"),
    ;

    public companion object {
        public fun fromWireName(name: String): NutritionBasis? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * The recipe object (F03 §3): versioned, editable, exportable. Versions are
 * immutable rows keyed (id, version) — an edit writes vN+1. Ingredients
 * reference the canonical grocery space; macros are size-normalized
 * per-serving so plan rollups never scale by assumption.
 */
@Serializable
public data class Recipe(
    public val id: RecipeId,
    public val version: Int = 1,
    public val name: String,
    /** Servings the nutrition + ingredient lines are normalized to (never a 2/4/6 straitjacket). */
    public val servingsBase: Double,
    public val cuisine: String? = null,
    /** Meal slots this recipe suits ([MealSlot.wireName]); the planner filters on it. */
    public val slots: List<String> = emptyList(),
    /** Diet/facet tags (vegetarian, vegan, high-fiber, quick, batch, …) — open vocabulary. */
    public val tags: List<String> = emptyList(),
    /** R-S8 FODMAP tags — subset of [FodmapTags.ALL] for shipped content. */
    public val fodmapTags: List<String> = emptyList(),
    public val ingredients: List<RecipeIngredient> = emptyList(),
    /** Plain text in v1 (F03 §3); cook-mode formatting is a [future] concern. */
    public val steps: List<String> = emptyList(),
    public val nutrition: NutritionPerServing,
    public val nutritionBasis: NutritionBasis = NutritionBasis.ESTIMATED,
    public val source: RecipeSource = RecipeSource.MANUAL,
    /** R-S3: "CC0" for every shipped seed; null for user-authored rows. */
    public val license: String? = null,
    public val rating: Int? = null,
    public val lastPlannedAtEpochMs: Long? = null,
    /** Archive-don't-delete (F13 §3): retirement, never a hard delete. */
    public val archivedAtEpochMs: Long? = null,
)

/**
 * One planned meal slot — the R-B1 day-record shape F03 owns and projects:
 * the planned/replaced distinction lives here, and `replaced` slots link the
 * F02 diary entry that owns the nutrition (F03 keeps slot labeling).
 *
 * Rows are append-only records: a swap retires the old row as [PlannedSlotState.SWAPPED]
 * and inserts a fresh PLANNED successor chained by [replacesSlotId] /
 * [successorSlotId] — history never rewrites. Per-serving macros are
 * denormalized onto the slot at deal time so the day projection never depends
 * on a later recipe version.
 */
@Serializable
public data class PlannedSlot(
    public val id: SlotId,
    public val planId: String,
    public val profileId: String,
    public val dayEpochDay: Long,
    public val mealSlot: String,
    /** Null = unfillable slot — the honest "add anything" card (F03 §4 fallback (a)). */
    public val recipeId: RecipeId? = null,
    public val recipeVersion: Int? = null,
    public val recipeName: String? = null,
    public val servings: Double = 1.0,
    public val state: PlannedSlotState = PlannedSlotState.PLANNED,
    /** R-B1 `replaced`: the F02 diary entry that now owns this meal's nutrition. */
    public val replacedByEntryId: String? = null,
    /** Swap chain: this retired record's successor / this record's predecessor. */
    public val successorSlotId: SlotId? = null,
    public val replacesSlotId: SlotId? = null,
    /**
     * Leftovers (F03 §3): non-null when this instance eats from a cook
     * event's batch — nutrition follows the eating slot, ingredient weight
     * stays charged to the cook slot ([PlannedSlot.isCookEvent]).
     */
    public val parentSlotId: SlotId? = null,
    public val isCookEvent: Boolean = false,
    /** Batch size of the cook event (servings emitted). */
    public val batchServings: Double? = null,
    /** Denormalized per-serving macros (the recipe version dealt at generation). */
    public val kcalPerServing: Double? = null,
    public val proteinGPerServing: Double? = null,
    public val carbGPerServing: Double? = null,
    public val fatGPerServing: Double? = null,
    public val fiberGPerServing: Double? = null,
    public val createdAtEpochMs: Long = 0,
    public val updatedAtEpochMs: Long? = null,
)

/**
 * Planned-slot states (R-B1: the F03-owned state machine that projects into
 * the day record). Wire names are the storage/exports vocabulary:
 * planned / confirmed / swapped / skipped / replaced.
 */
@Serializable
public enum class PlannedSlotState(
    public val wireName: String,
) {
    /** The plan says this meal; open until the day resolves it. */
    @SerialName("planned")
    PLANNED("planned"),

    /** "Ate this" — adopts the recipe nutrition; terminal (the F02 entry owns logging). */
    @SerialName("confirmed")
    CONFIRMED("confirmed"),

    /** Retired by a swap; the successor slot carries [PlannedSlot.replacesSlotId]. */
    @SerialName("swapped")
    SWAPPED("swapped"),

    /** Not having it — no guilt, feeds adherence honestly (F03 §6). Terminal. */
    @SerialName("skipped")
    SKIPPED("skipped"),

    /** Ate something else — links the actual F02 entry, which owns the nutrition (R-B1). Terminal. */
    @SerialName("replaced")
    REPLACED("replaced"),
    ;

    public companion object {
        public fun fromWireName(name: String): PlannedSlotState? = entries.firstOrNull { it.wireName == name }
    }
}
