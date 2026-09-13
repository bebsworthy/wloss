package app.wlo.feature.f02.food.domain

/**
 * Nutrition-label OCR text → prefilled custom-food DRAFT (F02 §3 rung 3).
 * Pure text heuristics over the OCR string — the DRAFT is exactly that: the
 * manual confirm screen (R-U15 twin, same form, empty) is mandatory before
 * anything saves. Fields are the per-100 g column; values are never invented:
 * a number lands in the draft only when its macro keyword names it on the line.
 */
public object NutritionLabelParser {
    public data class ParsedLabel(
        public val kcalPer100g: Double? = null,
        public val fatGPer100g: Double? = null,
        public val carbGPer100g: Double? = null,
        public val fiberGPer100g: Double? = null,
        public val proteinGPer100g: Double? = null,
        /** Free-text serving size line when present ("one glass (250 ml)"). */
        public val servingSizeText: String? = null,
        /** True when ANY macro was found (an empty parse means "type it manually"). */
        public val foundAny: Boolean,
    )

    /** Number (with optional trailing unit token) — "12 g", "250kcal", "3.5". */
    private val NUMBER: Regex = Regex("([0-9]+(?:[.][0-9]+)?)\\s*(kcal|kj|cal|g|mg|ml)?")

    /**
     * Parses the OCR text. Line-oriented, case-insensitive, tolerates
     * `per 100g` markers, UK/US spellings (fibre/fiber), and `kJ` next to
     * kcal (kJ converts; kcal wins when both name numbers).
     */
    public fun parse(text: String): ParsedLabel {
        var kcal: Double? = null
        var fat: Double? = null
        var carb: Double? = null
        var fiber: Double? = null
        var protein: Double? = null
        var serving: String? = null

        for (rawLine in text.lines()) {
            val line = rawLine.trim().lowercase().replace(',', '.')
            if (line.isBlank()) continue

            if (kcal == null && matchesEnergy(line)) kcal = kcalOn(line)
            if (fat == null && matchesKeyword(line, "fat", exclude = "saturated")) fat = gramsOn(line)
            if (carb == null && matchesCarb(line)) carb = carb ?: gramsOn(line)
            if (fiber == null && (matchesKeyword(line, "fiber") || matchesKeyword(line, "fibre"))) fiber = gramsOn(line)
            if (protein == null && matchesKeyword(line, "protein")) protein = gramsOn(line)
            if (serving == null && (line.contains("serving size") || line.contains("per serving"))) {
                serving = rawLine.trim()
            }
        }
        return ParsedLabel(
            kcalPer100g = kcal,
            fatGPer100g = fat,
            carbGPer100g = carb,
            fiberGPer100g = fiber,
            proteinGPer100g = protein,
            servingSizeText = serving,
            foundAny = kcal != null || fat != null || carb != null || fiber != null || protein != null,
        )
    }

    private fun matchesEnergy(line: String): Boolean = listOf("energy", "calorie", "kcal", "kj").any(line::contains)

    private fun matchesCarb(line: String): Boolean =
        matchesKeyword(line, "carbohydrate") ||
            matchesKeyword(line, "carb", exclude = "carbon")

    /** `keyword` must appear with word-ish boundaries; [exclude] vetoes the line. */
    private fun matchesKeyword(
        line: String,
        keyword: String,
        exclude: String? = null,
    ): Boolean {
        if (exclude != null && line.contains(exclude)) return false
        return Regex("(^|[^a-z])$keyword").containsMatchIn(line)
    }

    /** kcal first; kJ converts; a bare number on an energy line counts as kcal. */
    private fun kcalOn(line: String): Double? {
        val numbers = NUMBER.findAll(line).toList()
        numbers
            .firstOrNull { it.groupValues[2] == "kcal" || it.groupValues[2] == "cal" }
            ?.let { return it.groupValues[1].toDoubleOrNull() }
        numbers
            .firstOrNull { it.groupValues[2] == "kj" }
            ?.let { return (it.groupValues[1].toDoubleOrNull() ?: return null) / 4.184 }
        if (numbers.isNotEmpty() && numbers[0].groupValues[2].isBlank()) {
            return numbers[0].groupValues[1].toDoubleOrNull()
        }
        return null
    }

    /** The first `N g`-style number on the line (no unit token → still accepted). */
    private fun gramsOn(line: String): Double? {
        for (match in NUMBER.findAll(line)) {
            val unit = match.groupValues[2]
            if (unit == "g" || unit.isBlank() || unit == "mg") {
                val value = match.groupValues[1].toDoubleOrNull() ?: continue
                return if (unit == "mg") value / 1000.0 else value
            }
        }
        return null
    }
}
