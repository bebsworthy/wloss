package app.wlo.feature.f04.shopping.state

import app.wlo.core.model.DerivedValue

/*
 * The list + pantry render shapes (F04 §4). Quantities are recipe/plan
 * derivations rendered as receipt words next to the row name; the ONE chipped
 * number is the list's plan-total claim, so every number the user shops
 * against still travels with provenance (D6).
 */

/** One shopping-list row (F04 §3 `ListItem` + its render words). */
public data class ListItemUi(
    public val id: String,
    public val groceryItemId: String,
    public val name: String,
    public val qtyLabel: String,
    public val deltaLabel: String?,
    /** Per-recipe provenance ("for: 3 planned meals · tap for the diff"). */
    public val subLabel: String?,
    public val aisleWord: String,
    public val checked: Boolean,
)

/** One aisle group, in the shipped taxonomy order (learned order is v1.x). */
public data class AisleGroupUi(
    public val aisleWord: String,
    public val rows: List<ListItemUi>,
)

/** The reconciliation banner ("your checks are safe" — the anti-Mealime contract). */
public data class ReconciliationUi(
    public val added: Int,
    public val quantityChanged: Int,
    public val removed: Int,
    public val restored: Int,
    public val checksKept: Int,
) {
    public val headline: String
        get() =
            buildList {
                if (added > 0) add("$added added")
                if (quantityChanged > 0) {
                    add(
                        "$quantityChanged quantity ${if
                            (quantityChanged == 1) {
                            "up"
                        } else {
                            "changed"
                        }}",
                    )
                }
                if (removed > 0) add("$removed struck through")
                if (restored > 0) add("$restored back")
            }.joinToString(", ")
                .ifEmpty { "nothing changed" }
}

/** The R-S5 one-time prompt (partial-stock deduction, off by default). */
public data class DeductionPromptUi(
    public val staplesWord: String,
)

/** The list surface's state. */
public data class ListUiState(
    public val profileId: String?,
    public val groups: List<AisleGroupUi>,
    public val checked: List<ListItemUi>,
    public val removed: List<ListItemUi>,
    /** The plan-derived claim the current list serves, chipped (null = no plan). */
    public val planClaim: DerivedValue<Double>?,
    public val weekLabel: String?,
    public val banner: ReconciliationUi?,
    public val prompt: DeductionPromptUi?,
    public val deductionEnabled: Boolean,
    public val busy: Boolean,
    public val notice: String?,
) {
    public val toBuyCount: Int
        get() = groups.sumOf { group -> group.rows.size }

    public val checkedCount: Int
        get() = checked.size

    public companion object {
        public val LOADING: ListUiState =
            ListUiState(
                profileId = null,
                groups = emptyList(),
                checked = emptyList(),
                removed = emptyList(),
                planClaim = null,
                weekLabel = null,
                banner = null,
                prompt = null,
                deductionEnabled = false,
                busy = false,
                notice = null,
            )
    }
}

/** One pantry row (F04 §3 `PantryItem` + its render words). */
public data class PantryRowUi(
    public val groceryItemId: String,
    public val name: String,
    public val qtyLabel: String,
    public val staple: Boolean,
    public val outOfStock: Boolean,
    /** "expires in 2 d" / "exp Sep 17" / null. */
    public val expiryWord: String?,
    /** The "use soon" band (≤ 3 days) — the only breathing-tint band. */
    public val useSoon: Boolean,
    public val low: Boolean,
)

/** The pantry surface's state. */
public data class PantryUiState(
    public val profileId: String?,
    public val useSoon: List<PantryRowUi>,
    public val low: List<PantryRowUi>,
    public val stock: List<PantryRowUi>,
    public val deductionEnabled: Boolean,
    public val suggestions: List<String>,
    public val notice: String?,
) {
    public companion object {
        public val LOADING: PantryUiState =
            PantryUiState(
                profileId = null,
                useSoon = emptyList(),
                low = emptyList(),
                stock = emptyList(),
                deductionEnabled = false,
                suggestions = emptyList(),
                notice = null,
            )
    }
}
