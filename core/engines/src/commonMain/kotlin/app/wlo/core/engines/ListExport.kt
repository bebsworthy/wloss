package app.wlo.core.engines

import kotlinx.serialization.Serializable

/**
 * F04 list export/import writers (F04 §9: the only sanctioned "integration"
 * is a format — text/CSV/JSON to wherever the user wants it; no delivery
 * partnerships, ever).
 *
 * HOME JUSTIFICATION: these live in `:core:engines`, not `:core:documents`,
 * because exports are ARTIFACTS, not versioned documents — ADR-004's
 * envelope house rules (schemaVersion, parentVersion, migration funnel) have
 * nothing to attach to; a list export is a deterministic value-in/string-out
 * transform over engine-owned shapes, which is this module's exact contract
 * (D7: pure, golden-testable). CSV is WLO's own round-trip format (R-S4
 * "generic CSV/JSON at v1"); the parser is strict and total: failures come
 * back as [ListCsvResult.Failed] values (D8), never thrown.
 */
public object ListExport {
    // --- Row shape (the export contract, shared by all three formats) ----------

    @Serializable
    public data class ExportRow(
        public val item: String,
        public val qty: Double,
        public val unit: String,
        public val aisle: String,
        /** "pending" | "checked" (PlannedSlotState-style wire vocabulary). */
        public val state: String,
        /** `recipeId@dayEpochDay:qty unit` provenance lines, `;`-joined in CSV. */
        public val sources: List<SourceRef> = emptyList(),
    )

    @Serializable
    public data class SourceRef(
        public val recipeId: String,
        public val dayEpochDay: Long,
        public val qty: Double,
        public val unit: String,
    )

    // --- Text (the share-sheet format: aisle-grouped, human-first) --------------

    public fun toText(
        rows: List<ExportRow>,
        aisleOrder: List<String>,
        title: String = "Shopping list",
    ): String {
        val byAisle = rows.groupBy { it.aisle }
        val orderedAisles = aisleOrder.filter { byAisle.contains(it) } + byAisle.keys.filter { !aisleOrder.contains(it) }.sorted()
        return buildString {
            appendLine(title)
            appendLine("${rows.size} items")
            orderedAisles.forEach { aisle ->
                appendLine()
                appendLine(aisle.replaceFirstChar { it.uppercase() }.replace('-', ' '))
                byAisle[aisle].orEmpty().forEach { row ->
                    appendLine("  ${row.item} ${formatQty(row.qty)} ${displayUnit(row.unit)}")
                }
            }
        }
    }

    // --- CSV (own round-trip format; RFC-4180 quoting) ---------------------------

    public const val CSV_HEADER: String = "item,qty,unit,aisle,state,sources"

    public fun toCsv(rows: List<ExportRow>): String =
        buildString {
            appendLine(CSV_HEADER)
            rows.forEach { row ->
                appendLine(
                    listOf(
                        csvEscape(row.item),
                        formatQty(row.qty),
                        csvEscape(row.unit),
                        csvEscape(row.aisle),
                        csvEscape(row.state),
                        csvEscape(row.sources.joinToString(separator = ";") { encodeSource(it) }),
                    ).joinToString(separator = ","),
                )
            }
        }

    public sealed interface ListCsvResult {
        public data class Ok(
            public val rows: List<ExportRow>,
        ) : ListCsvResult

        public data class Failed(
            public val reason: String,
        ) : ListCsvResult
    }

    /**
     * Strict own-format parser: header must match, state must be
     * pending/checked, qty must parse, unit must be a known [app.wlo.core.model.MeasureUnit]
     * wire name. Round-trip guarantee: `parse(toCsv(rows)) == rows` for any
     * writer output (the property test pins it).
     */
    public fun parseCsv(text: String): ListCsvResult {
        val lines = text.lines().filterIndexed { index, line -> line.isNotBlank() || index == 0 }
        if (lines.isEmpty() || lines.first().trim() != CSV_HEADER) {
            return ListCsvResult.Failed("not a WLO list CSV: header must be \"$CSV_HEADER\"")
        }
        val rows = mutableListOf<ExportRow>()
        for ((index, line) in lines.drop(1).withIndex()) {
            val fields = splitCsvLine(line)
            if (fields.size != 6) {
                return ListCsvResult.Failed("row ${index + 1}: expected 6 fields, found ${fields.size}")
            }
            val item = fields[0]
            val qtyRaw = fields[1]
            val unit = fields[2]
            val aisle = fields[3]
            val state = fields[4]
            val sourcesRaw = fields[5]
            if (item.isBlank()) return ListCsvResult.Failed("row ${index + 1}: empty item name")
            val qty = qtyRaw.toDoubleOrNull() ?: return ListCsvResult.Failed("row ${index + 1}: qty \"$qtyRaw\" is not a number")
            if (app.wlo.core.model.MeasureUnit
                    .fromWireName(unit) == null
            ) {
                return ListCsvResult.Failed("row ${index + 1}: unknown unit \"$unit\"")
            }
            if (state != STATE_PENDING && state != STATE_CHECKED) {
                return ListCsvResult.Failed("row ${index + 1}: state must be pending|checked, was \"$state\"")
            }
            val sources =
                if (sourcesRaw.isBlank()) {
                    emptyList()
                } else {
                    val parsed =
                        sourcesRaw.split(';').mapNotNull { token ->
                            if (token.isBlank()) {
                                null
                            } else {
                                decodeSource(token)
                                    ?: return ListCsvResult.Failed("row ${index + 1}: bad source \"$token\"")
                            }
                        }
                    parsed
                }
            rows += ExportRow(item = item, qty = qty, unit = unit, aisle = aisle, state = state, sources = sources)
        }
        return ListCsvResult.Ok(rows)
    }

    public const val STATE_PENDING: String = "pending"
    public const val STATE_CHECKED: String = "checked"

    // --- JSON (full fidelity, F13 can bundle it) --------------------------------

    public fun toJson(rows: List<ExportRow>): String =
        DecisionLedger.json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ExportRow.serializer()),
            rows,
        )

    public fun fromJson(text: String): ListExport.ListCsvResult =
        try {
            ListCsvResult.Ok(
                DecisionLedger.json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(ExportRow.serializer()),
                    text,
                ),
            )
        } catch (t: kotlinx.serialization.SerializationException) {
            ListCsvResult.Failed("not a WLO list JSON: ${t.message ?: "unparsable"}")
        } catch (t: IllegalArgumentException) {
            ListCsvResult.Failed("not a WLO list JSON: ${t.message ?: "unparsable"}")
        }

    // --- Internals ---------------------------------------------------------------

    private fun encodeSource(source: SourceRef): String = "${source.recipeId}@${source.dayEpochDay}:${formatQty(source.qty)} ${source.unit}"

    private fun decodeSource(token: String): SourceRef? {
        val atSplit = token.indexOf('@')
        if (atSplit <= 0) return null
        val colonSplit = token.indexOf(':', atSplit)
        if (colonSplit < 0) return null
        val recipeId = token.take(atSplit)
        val day = token.substring(atSplit + 1, colonSplit).toLongOrNull() ?: return null
        val qtyUnit = token.substring(colonSplit + 1).trim().split(' ')
        if (qtyUnit.size != 2) return null
        val qty = qtyUnit[0].toDoubleOrNull() ?: return null
        return SourceRef(recipeId, day, qty, qtyUnit[1])
    }

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    /** Minimal RFC-4180 field splitter: quotes, escaped quotes, commas. */
    private fun splitCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val c = line[index]
            when {
                inQuotes && c == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }

                c == '"' -> inQuotes = !inQuotes

                c == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.setLength(0)
                }

                else -> current.append(c)
            }
            index++
        }
        fields.add(current.toString())
        return fields
    }

    /** Display unit: counts read "×N" in text; CSV/JSON keep the wire unit. */
    private fun displayUnit(unit: String): String = if (unit == app.wlo.core.model.MeasureUnit.COUNT.wireName) "x" else unit

    private fun formatQty(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}
