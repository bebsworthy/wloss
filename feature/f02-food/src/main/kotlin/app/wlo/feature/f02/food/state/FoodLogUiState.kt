package app.wlo.feature.f02.food.state

import app.wlo.core.data.FoodHit
import app.wlo.core.model.FoodItem
import app.wlo.core.model.MealSlot
import app.wlo.core.model.ServingPreset

/*
 * The F02 manual input ladder's render state (F02 §3 rungs 4–5 — the manual
 * parity rungs, R-U15; photo/voice/barcode arrive with the capture flow).
 * Every number that reaches the UI travels as domain state; kcal previews are
 * rendered by the designsystem provenance components (D6).
 */

/** One search hit with the visible exact-match promotion flag (F02 §3 rung 5). */
public data class FoodHitUi(
    public val food: FoodItem,
    public val exactMatch: Boolean,
) {
    public companion object {
        public fun from(hit: FoodHit): FoodHitUi = FoodHitUi(hit.item, hit.exactNameMatch)
    }
}

/** The open portion sheet: one food + the quantity/unit/preset being sized. */
public data class PortionSelection(
    public val food: FoodItem,
    public val quantityText: String,
    /** "g" | "ml" | "serving" (the v1 portion vocabulary). */
    public val unit: String,
    public val presets: List<ServingPreset>,
    public val chosenPresetIndex: Int?,
) {
    /** The portion in grams the diary math will run on (0 until parseable). */
    public val grams: Double
        get() =
            when {
                unit == UNIT_SERVING ->
                    (quantityText.toDoubleOrNull() ?: 0.0) *
                        (presets.getOrNull(chosenPresetIndex ?: 0)?.grams ?: presets.firstOrNull()?.grams ?: 0.0)
                else -> quantityText.toDoubleOrNull() ?: 0.0
            }

    public companion object {
        public const val UNIT_SERVING: String = "serving"

        public fun forFood(food: FoodItem): PortionSelection =
            PortionSelection(
                food = food,
                quantityText =
                    food.servingPresets
                        .firstOrNull()
                        ?.grams
                        ?.let { FoodLogViewModel.formatQuantity(it) } ?: "100",
                unit = "g",
                presets = food.servingPresets,
                chosenPresetIndex = food.servingPresets.firstOrNull()?.let { 0 },
            )
    }
}

/** The kcal-only quick-add draft (F02 §4: the stubborn-case path). */
public data class QuickAddDraft(
    public val kcalText: String,
    public val name: String,
)

/**
 * The custom-food draft (F02 §3 label path, R-U15). Values are per-100 g/ml;
 * the energy-density rail (>900 kcal/100 g is physically impossible) is
 * surfaced as copy BEFORE save and as a rejection notice after — never shame.
 */
public data class CustomFoodDraft(
    public val editId: String? = null,
    public val name: String = "",
    public val brand: String = "",
    public val kcalText: String = "",
    public val proteinText: String = "",
    public val carbText: String = "",
    public val fatText: String = "",
    public val fiberText: String = "",
    public val macrosVerified: Boolean = false,
) {
    /** True while the typed density exceeds the physical rail (live, pre-save). */
    public val overRail: Boolean
        get() = (kcalText.toDoubleOrNull() ?: 0.0) > RAIL_KCAL_PER_100G

    public companion object {
        /** Pure fat's energy density — nothing real is denser (F02 §3/§8). */
        public const val RAIL_KCAL_PER_100G: Double = 900.0
    }
}

/** A one-line notice (save confirmations, rail rejections); session-local. */
public data class NoticeUi(
    public val text: String,
    public val state: NoticeState = NoticeState.INFO,
)

public enum class NoticeState {
    /** Neutral confirmation. */
    INFO,

    /** A rejection that names the fix (the energy-density rail). */
    RAIL,
}

/**
 * The ladder screen's full state. The default slot follows the clock
 * (breakfast before 10:30, lunch until 15, dinner until 20, else snack).
 */
public data class FoodLogUiState(
    public val query: String = "",
    public val results: List<FoodHitUi> = emptyList(),
    public val searching: Boolean = false,
    /** The free-text note draft (R-U15 manual path; enters the diary as `held`). */
    public val hintText: String = "",
    public val slot: MealSlot = MealSlot.SNACK,
    public val selection: PortionSelection? = null,
    public val quickAdd: QuickAddDraft? = null,
    public val customDraft: CustomFoodDraft? = null,
    public val notice: NoticeUi? = null,
) {
    public companion object {
        public val LOADING: FoodLogUiState = FoodLogUiState()
    }
}
