package app.wlo.core.engines

import app.wlo.core.model.Aisle

/**
 * F04 aisle engine v1 (F04 §3): a shipped canonical taxonomy + learned
 * per-item overrides. One drag teaches an item forever (the correction map is
 * persisted per item by :core:data); the shipped keyword table guesses for
 * items with no tag and no correction; the item's own shipped aisle tag is
 * the first resort. TOTAL by construction — every input resolves to an
 * [Aisle] (OTHER is the honest fallback bucket, never a guess dressed up).
 *
 * v1 is deliberately keyword-flat: the "on-device keyword/embedding model"
 * of F04 §3 lands when there is real correction data to beat; the override
 * map is already the output shape, so upgrading the guesser changes nothing
 * downstream.
 */
public object AisleEngine {
    public const val VERSION: String = "aisle/keyword-v1"

    /** Assignment input — the minimal item view the engine needs. */
    public data class Item(
        public val id: String,
        public val name: String,
        /** Shipped aisle tag ([Aisle.wireName]); null for user-added items. */
        public val aisleTag: String?,
        public val aliases: List<String> = emptyList(),
    )

    /**
     * Total assignment: user correction (learned forever) → shipped tag →
     * keyword guess → OTHER. Pure and deterministic; ties resolve to the
     * first keyword hit in table order.
     */
    public fun assign(
        item: Item,
        corrections: Map<String, String> = emptyMap(),
    ): Aisle {
        corrections[item.id]?.let { learned -> Aisle.fromWireName(learned)?.let { return it } }
        item.aisleTag?.let { tag -> Aisle.fromWireName(tag)?.let { return it } }
        val haystack =
            (listOf(item.name) + item.aliases).joinToString(separator = " ").lowercase()
        for ((keyword, aisle) in KEYWORD_AISLES) {
            if (haystack.contains(keyword)) return aisle
        }
        return Aisle.OTHER
    }

    /** The shipped sort order (user reorder lives in settings, respected above this). */
    public fun taxonomyOrder(): List<Aisle> = Aisle.entries.sortedBy { it.defaultOrder }

    /**
     * The v1 keyword table for untagged items — common staples first, longest
     * keywords first within a scan is unnecessary: first match wins and the
     * table is ordered most-specific-last intentionally (e.g. "greek yogurt"
     * before "yogurt" never matters — both are dairy).
     */
    private val KEYWORD_AISLES: List<Pair<String, Aisle>> =
        listOf(
            "yogurt" to Aisle.DAIRY,
            "yoghurt" to Aisle.DAIRY,
            "milk" to Aisle.DAIRY,
            "cheese" to Aisle.DAIRY,
            "feta" to Aisle.DAIRY,
            "mozzarella" to Aisle.DAIRY,
            "parmesan" to Aisle.DAIRY,
            "butter" to Aisle.DAIRY,
            "cream" to Aisle.DAIRY,
            "egg" to Aisle.DAIRY,
            "tofu" to Aisle.PRODUCE,
            "spinach" to Aisle.PRODUCE,
            "kale" to Aisle.PRODUCE,
            "lettuce" to Aisle.PRODUCE,
            "tomato" to Aisle.PRODUCE,
            "onion" to Aisle.PRODUCE,
            "garlic" to Aisle.PRODUCE,
            "pepper" to Aisle.PRODUCE,
            "cucumber" to Aisle.PRODUCE,
            "carrot" to Aisle.PRODUCE,
            "potato" to Aisle.PRODUCE,
            "apple" to Aisle.PRODUCE,
            "banana" to Aisle.PRODUCE,
            "lemon" to Aisle.PRODUCE,
            "lime" to Aisle.PRODUCE,
            "berry" to Aisle.PRODUCE,
            "avocado" to Aisle.PRODUCE,
            "broccoli" to Aisle.PRODUCE,
            "mushroom" to Aisle.PRODUCE,
            "herb" to Aisle.PRODUCE,
            "basil" to Aisle.PRODUCE,
            "cilantro" to Aisle.PRODUCE,
            "coriander" to Aisle.PRODUCE,
            "parsley" to Aisle.PRODUCE,
            "salmon" to Aisle.MEAT_FISH,
            "tuna" to Aisle.MEAT_FISH,
            "cod" to Aisle.MEAT_FISH,
            "chicken" to Aisle.MEAT_FISH,
            "beef" to Aisle.MEAT_FISH,
            "pork" to Aisle.MEAT_FISH,
            "turkey" to Aisle.MEAT_FISH,
            "lamb" to Aisle.MEAT_FISH,
            "shrimp" to Aisle.MEAT_FISH,
            "prawn" to Aisle.MEAT_FISH,
            "bread" to Aisle.BAKERY,
            "tortilla" to Aisle.BAKERY,
            "bun" to Aisle.BAKERY,
            "bagel" to Aisle.BAKERY,
            "pita" to Aisle.BAKERY,
            "frozen" to Aisle.FROZEN,
            "ice cream" to Aisle.FROZEN,
            "peas" to Aisle.FROZEN,
            "rice" to Aisle.PANTRY,
            "pasta" to Aisle.PANTRY,
            "flour" to Aisle.PANTRY,
            "sugar" to Aisle.PANTRY,
            "salt" to Aisle.PANTRY,
            "oil" to Aisle.PANTRY,
            "vinegar" to Aisle.PANTRY,
            "bean" to Aisle.PANTRY,
            "lentil" to Aisle.PANTRY,
            "oat" to Aisle.PANTRY,
            "cereal" to Aisle.PANTRY,
            "canned" to Aisle.PANTRY,
            "honey" to Aisle.PANTRY,
            "sauce" to Aisle.PANTRY,
            "spice" to Aisle.PANTRY,
            "pepper flake" to Aisle.PANTRY,
            "detergent" to Aisle.HOUSEHOLD,
            "soap" to Aisle.HOUSEHOLD,
            "paper" to Aisle.HOUSEHOLD,
            "foil" to Aisle.HOUSEHOLD,
            "sponge" to Aisle.HOUSEHOLD,
            "trash" to Aisle.HOUSEHOLD,
            "pet food" to Aisle.HOUSEHOLD,
            "cat food" to Aisle.HOUSEHOLD,
            "dog food" to Aisle.HOUSEHOLD,
        )
}
