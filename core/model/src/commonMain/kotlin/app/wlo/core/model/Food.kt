package app.wlo.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Canonical food/item space (DRY anchor §2.5: F02↔F03↔F04 share it) — the
 * public shape of one catalog row. Storage lives in `:core:database`
 * (archive-don't-delete, F13 §3); `:core:data` maps rows 1:1 onto this type.
 */
@Serializable
public data class FoodItem(
    public val id: String,
    public val profileId: String,
    public val name: String,
    public val brand: String? = null,
    /** Free-text alternate names the user's searches should hit (F02 §3 ladder rung 5). */
    public val aliases: String? = null,
    /** Per-100 g (solids) / per-100 ml (liquids) macros — R-A4 v1 nutrient scope. */
    public val kcalPer100g: Double? = null,
    public val proteinGPer100g: Double? = null,
    public val carbGPer100g: Double? = null,
    public val fatGPer100g: Double? = null,
    public val fiberGPer100g: Double? = null,
    /** Named portion presets ("1 cup" = 240 g), consumed by the diary portion picker. */
    public val servingPresets: List<ServingPreset> = emptyList(),
    public val source: FoodSource = FoodSource.CUSTOM,
    /**
     * User (or importer) confirmed the macros against the physical label
     * (F02 §5 "measured (label)" provenance upgrade).
     */
    public val macrosVerified: Boolean = false,
    public val verifiedAt: kotlinx.datetime.Instant? = null,
    public val createdAt: kotlinx.datetime.Instant,
    public val updatedAt: kotlinx.datetime.Instant? = null,
    /** Archive-don't-delete: set when the item is retired, never hard-deleted. */
    public val archivedAt: kotlinx.datetime.Instant? = null,
)

/** Where a catalog row came from (F02 §3 input ladder + F13 §3 catalog policy). */
@Serializable
public enum class FoodSource(
    public val wireName: String,
) {
    /** Bundled/authored starter items. */
    @SerialName("seed")
    SEED("seed"),

    /** User-created via the manual custom-food form (R-U15: equal-status manual path). */
    @SerialName("custom")
    CUSTOM("custom"),

    /** Open Food Facts lookup (R-C4 integration toggle, cached). */
    @SerialName("off")
    OFF("off"),

    /** USDA FDC lookup (R-C4). */
    @SerialName("usda-fdc")
    USDA_FDC("usda-fdc"),

    /** On-device label OCR capture (F02 §3 rung 3). */
    @SerialName("label-ocr")
    LABEL_OCR("label-ocr"),
    ;

    public companion object {
        public fun fromWireName(name: String): FoodSource? = entries.firstOrNull { it.wireName == name }
    }
}

/** One named portion preset: `"1 cup" → 240` (grams, or ml for liquids). */
@Serializable
public data class ServingPreset(
    public val label: String,
    public val grams: Double,
)

/** Diary meal grouping (R-B1: F02 owns the diary; slots project from F03 plans). */
@Serializable
public enum class MealSlot(
    public val wireName: String,
) {
    @SerialName("breakfast")
    BREAKFAST("breakfast"),

    @SerialName("lunch")
    LUNCH("lunch"),

    @SerialName("dinner")
    DINNER("dinner"),

    @SerialName("snack")
    SNACK("snack"),

    /**
     * Drink/water entries (F02 §3: "drink entries and a one-tap water
     * quick-add land in the diary like any other entry"). DEFAULTED: the M3
     * brief names four slots; DRINK keeps water out of the snack bucket
     * without a new table. Amend here, not in the schema, if F02 wants it gone.
     */
    @SerialName("drink")
    DRINK("drink"),
    ;

    public companion object {
        public fun fromWireName(name: String): MealSlot? = entries.firstOrNull { it.wireName == name }
    }
}

/** How an entry entered the diary (F02 §3 input ladder; open vocabulary, v1 rungs). */
@Serializable
public enum class EntryVia(
    public val wireName: String,
) {
    /** Searched the local catalog, picked a hit. */
    @SerialName("manual_search")
    MANUAL_SEARCH("manual_search"),

    /** Created/used a custom food via the manual form (R-U15). */
    @SerialName("manual_custom")
    MANUAL_CUSTOM("manual_custom"),

    /** Free-text hint re-estimate (F02 §3 rung 4; estimate lands via F12 later). */
    @SerialName("text_hint")
    TEXT_HINT("text_hint"),

    /** Photo ladder save (F02 §3 rung 1; arrives with the M6+ capture flow). */
    @SerialName("photo")
    PHOTO("photo"),

    /** Voice parse (F02 §3 rung 2). */
    @SerialName("voice")
    VOICE("voice"),

    /** Food Memory one-tap re-log (F02 §2). */
    @SerialName("relog")
    RELOG("relog"),

    /** kcal-only quick-add for stubborn cases (F02 §4). */
    @SerialName("quick_add")
    QUICK_ADD("quick_add"),

    /** "Log as planned" from an F03 slot (R-B1: slot links to the entry). */
    @SerialName("plan")
    PLAN("plan"),
    ;

    public companion object {
        public fun fromWireName(name: String): EntryVia? = entries.firstOrNull { it.wireName == name }
    }
}
