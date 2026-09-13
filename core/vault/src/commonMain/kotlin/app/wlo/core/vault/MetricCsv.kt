package app.wlo.core.vault

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Metric CSV v1 (F13 §3 "CSV per metric", as a single RFC-4180 grid): the EAV
 * kinds become COLUMNS — day, time, then one column per built-in kind
 * (weight_kg, trend_kg, kcal_in, kcal_out, body_fat_pct) plus one column per
 * discovered custom metric (`custom/<name>_<unit>`). One row PER EVENT
 * (R-B8: "the raw points … ship in exports" — two weigh-ins in a day are two
 * rows for that day; an empty cell carries "no event of this kind at this
 * capture").
 *
 * The same column vocabulary drives CSV IMPORT ([CsvColumnMapping]) — export
 * and import speak one dialect, and R-S4's mapping-remembering wizard (PART
 * B UI) maps any foreign CSV onto it via [CsvMappingSniffer].
 */
public object MetricCsv {
    public const val COL_DAY: String = "day"
    public const val COL_TIME: String = "time"

    /** Built-in kind → column name (unit-suffixed, per F06 §3 method registry). */
    public val BUILTIN_COLUMNS: Map<String, String> =
        mapOf(
            "weight" to "weight_kg",
            "trend" to "trend_kg",
            "intake" to "kcal_in",
            "burn" to "kcal_out",
            "body-fat" to "body_fat_pct",
        )

    public fun customColumn(
        name: String,
        unit: String,
    ): String = "custom/$name" + if (unit.isBlank()) "" else "_$unit"

    /**
     * Renders the events as one RFC-4180 CSV. Events of unknown kinds with no
     * metric attr are skipped with a warning (never fatal, never guessed).
     */
    public fun write(
        events: List<CsvEvent>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): CsvOutput {
        val customColumns = LinkedHashMap<String, Unit>()
        for (event in events) {
            if (event.metricName != null) {
                customColumns.putIfAbsent(customColumn(event.metricName, event.unit), Unit)
            }
        }
        val columns =
            buildList {
                add(COL_DAY)
                add(COL_TIME)
                addAll(BUILTIN_COLUMNS.values)
                addAll(customColumns.keys)
            }

        val out = StringBuilder()
        out.append(rfcRecord(columns))

        val warnings = mutableListOf<String>()
        for (event in events) {
            val dateTime = Instant.fromEpochMilliseconds(event.capturedAtEpochMs).toLocalDateTime(timeZone)
            val column =
                when {
                    event.metricName != null -> customColumn(event.metricName, event.unit)
                    else -> BUILTIN_COLUMNS[event.kind]
                }
            if (column == null) {
                warnings.add("skipped event ${event.eventId}: unknown kind '${event.kind}' without metric attr")
                continue
            }
            val values =
                columns.associateWith { col ->
                    when (col) {
                        COL_DAY -> dateTime.date.toString()
                        COL_TIME -> dateTime.time.toString()
                        column -> formatValue(event.valueReal)
                        else -> ""
                    }
                }
            out.append(rfcRecord(columns.map(values::getValue)))
        }
        return CsvOutput(out.toString(), warnings)
    }

    private fun formatValue(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /** RFC-4180 record: quote fields containing comma/quote/newline; CRLF. */
    public fun rfcRecord(fields: List<String>): String = fields.joinToString(separator = ",") { encodeField(it) } + "\r\n"

    /** RFC-4180 field quoting (doubled quotes). */
    public fun encodeField(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }

    /** One event-level export row. */
    public data class CsvEvent(
        public val eventId: String,
        public val kind: String,
        public val valueReal: Double,
        public val unit: String,
        public val capturedAtEpochMs: Long,
        /** The `metric=<name>` EAV attr for CUSTOM events; null for built-ins. */
        public val metricName: String? = null,
    )

    public data class CsvOutput(
        public val csv: String,
        public val warnings: List<String>,
    )
}

/**
 * The parsed CSV table (RFC-4180: quoted fields, doubled quotes, CRLF or LF).
 * Pure and property-testable; PART B's wizard renders [header] for mapping.
 */
public data class CsvTable(
    public val header: List<String>,
    public val rows: List<List<String>>,
) {
    public fun column(named: String): Int = header.indexOf(named)

    public companion object {
        public fun parse(text: String): CsvTable {
            val records = parseRecords(text)
            require(records.isNotEmpty()) { "empty CSV" }
            return CsvTable(header = records.first(), rows = records.drop(1))
        }

        private fun parseRecords(text: String): List<List<String>> {
            val records = mutableListOf<List<String>>()
            var field = StringBuilder()
            var record = mutableListOf<String>()
            var inQuotes = false
            var i = 0

            fun endField() {
                record.add(field.toString())
                field = StringBuilder()
            }

            fun endRecord() {
                endField()
                records.add(record)
                record = mutableListOf()
            }

            while (i < text.length) {
                val c = text[i]
                when {
                    inQuotes ->
                        when {
                            c == '"' ->
                                if (i + 1 < text.length && text[i + 1] == '"') {
                                    field.append('"')
                                    i++
                                } else {
                                    inQuotes = false
                                }

                            else -> field.append(c)
                        }

                    c == '"' -> inQuotes = true
                    c == ',' -> endField()
                    c == '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                        endRecord()
                    }

                    c == '\n' -> endRecord()
                    else -> field.append(c)
                }
                i++
            }
            if (field.isNotEmpty() || record.isNotEmpty()) endRecord()
            return records
        }
    }
}

/** One wizard decision: this CSV column carries that metric. */
public data class CsvColumnMapping(
    public val sourceColumn: String,
    public val target: CsvTarget,
)

/** Import targets: the day column or a metric (built-in or custom). */
public sealed interface CsvTarget {
    /** The date/day column (any ISO-ish date or epoch-day integer). */
    public data object Day : CsvTarget

    /**
     * A metric column. [kind] is a MeasurementKind wire name; custom metrics
     * carry [customName] (the EAV `metric=<name>` value) and their own unit.
     */
    public data class Metric(
        public val kind: String,
        public val unit: String,
        public val customName: String? = null,
    ) : CsvTarget
}

/**
 * R-S4 header sniffing: proposes a mapping for the common dialects (openScale,
 * Libra, MFP-lite). PART B's wizard prefills from this, the user corrects,
 * and the result persists (remembered mappings). Pure + unit-tested.
 */
public object CsvMappingSniffer {
    private val ALIASES: Map<String, CsvTarget> =
        mapOf(
            "date" to CsvTarget.Day,
            "day" to CsvTarget.Day,
            "timestamp" to CsvTarget.Day,
            "weight" to CsvTarget.Metric("weight", "kg"),
            "weight_kg" to CsvTarget.Metric("weight", "kg"),
            "weight (kg)" to CsvTarget.Metric("weight", "kg"),
            "trend" to CsvTarget.Metric("trend", "kg"),
            "trend_kg" to CsvTarget.Metric("trend", "kg"),
            "kcal_in" to CsvTarget.Metric("intake", "kcal"),
            "calories" to CsvTarget.Metric("intake", "kcal"),
            "kcal" to CsvTarget.Metric("intake", "kcal"),
            "kcal_out" to CsvTarget.Metric("burn", "kcal"),
            "burn" to CsvTarget.Metric("burn", "kcal"),
            "body_fat" to CsvTarget.Metric("body-fat", "%"),
            "body_fat_pct" to CsvTarget.Metric("body-fat", "%"),
            "body fat (%)" to CsvTarget.Metric("body-fat", "%"),
        )

    public fun sniff(header: List<String>): List<CsvColumnMapping> =
        header.mapNotNull { column ->
            val key = column.trim().lowercase()
            ALIASES[key]?.let { CsvColumnMapping(column, it) }
        }
}

/**
 * Applies a mapping to a table, producing staged measurement rows + per-row
 * warnings (F13 §4: "unparseable rows are skipped with reasons, never
 * fatal"). Weak data is HELD: an unparseable date or number never becomes a
 * row — it becomes a warning naming the row.
 */
public object CsvMeasurementImporter {
    public data class Parsed(
        public val rows: List<StagedMeasurement>,
        public val warnings: List<String>,
    )

    public data class StagedMeasurement(
        public val dayEpochDay: Long,
        public val kind: String,
        public val valueReal: Double,
        public val unit: String,
        public val customName: String? = null,
        public val source: String = "import",
    )

    public fun parse(
        table: CsvTable,
        mapping: List<CsvColumnMapping>,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Parsed {
        val warnings = mutableListOf<String>()
        val dayIndex = mapping.firstOrNull { it.target == CsvTarget.Day }?.let { table.column(it.sourceColumn) }
        if (dayIndex == null || dayIndex < 0) {
            warnings.add("no day column mapped — rows cannot be placed")
            return Parsed(emptyList(), warnings)
        }
        val metricMappings =
            mapping
                .filter { it.target is CsvTarget.Metric }
                .mapNotNull { m -> table.column(m.sourceColumn).takeIf { it >= 0 }?.let { it to m } }

        val rows = mutableListOf<StagedMeasurement>()
        table.rows.forEachIndexed { rowIndex, cells ->
            val day = parseDay(cells.getOrNull(dayIndex))
            if (day == null) {
                warnings.add("row ${rowIndex + 2}: unparseable date '${cells.getOrNull(dayIndex)}' — skipped")
                return@forEachIndexed
            }
            for ((index, m) in metricMappings) {
                val raw = cells.getOrNull(index)?.trim().orEmpty()
                if (raw.isEmpty()) continue
                val value = raw.toDoubleOrNull()
                val target = m.target as CsvTarget.Metric
                if (value == null) {
                    warnings.add("row ${rowIndex + 2}: '$raw' is not a number for column '${m.sourceColumn}' — skipped")
                    continue
                }
                rows +=
                    StagedMeasurement(
                        dayEpochDay = day,
                        kind = if (target.customName == null) target.kind else "custom",
                        valueReal = value,
                        unit = target.unit,
                        customName = target.customName,
                    )
            }
        }
        return Parsed(rows, warnings)
    }

    /** Accepts ISO yyyy-MM-dd, yyyy/MM/dd and bare epoch days (openScale dialects). */
    public fun parseDay(raw: String?): Long? {
        raw ?: return null
        raw.toLongOrNull()?.let { return it }
        val normalized = raw.replace('/', '-').trim()
        return runCatching { LocalDate.parse(normalized).toEpochDays() }.getOrNull()
    }
}
