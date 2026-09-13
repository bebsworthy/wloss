package app.wlo.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Metric CSV v1: RFC-4180 writer/parser + R-S4 column mapping data API. */
class MetricCsvTest {
    @Test
    fun writer_emitsHeaderAndEventLevelRows() {
        val events =
            listOf(
                event("w1", "weight", 84.2, "kg", day = 20_000, time = "07:12:00"),
                // R-B8: two weigh-ins the same day are TWO rows.
                event("w2", "weight", 84.0, "kg", day = 20_000, time = "21:40:00"),
                event("t1", "trend", 84.15, "kg", day = 20_000, time = "07:12:00"),
                event("i1", "intake", 1830.0, "kcal", day = 20_000, time = "22:00:00"),
                event("c1", "custom", 12.5, "mmol", day = 20_000, time = "08:00:00", metricName = "ketones"),
            )
        val output = MetricCsv.write(events, utc)
        val lines = output.csv.trim().split("\r\n")
        assertEquals(
            "day,time,weight_kg,trend_kg,kcal_in,kcal_out,body_fat_pct,custom/ketones_mmol",
            lines.first(),
        )
        assertEquals(6, lines.size, "header + one row per event")
        val weightRows = lines.filter { it.contains(",84.2,") || it.contains(",84,") }
        assertEquals(2, weightRows.size)
        assertTrue(output.warnings.isEmpty())
    }

    @Test
    fun writer_skipsUnknownKindsWithWarning() {
        val output =
            MetricCsv.write(
                listOf(event("x1", "mystery", 1.0, "?", day = 20_000, time = "00:00:00")),
                utc,
            )
        assertEquals(1, output.warnings.size)
        assertTrue(output.warnings.single().contains("mystery"))
    }

    @Test
    fun rfc4180_quotingAndDoubling() {
        assertEquals("plain", MetricCsv.encodeField("plain"))
        assertEquals("\"a,b\"", MetricCsv.encodeField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", MetricCsv.encodeField("say \"hi\""))
        assertEquals("\"line\nbreak\"", MetricCsv.encodeField("line\nbreak"))
    }

    @Test
    fun parser_roundTripsQuotedFields() {
        val csv = "name,note\r\nlentils,\"stew \"\"best\"\", ok\"\r\nrice,plain\r\n"
        val table = CsvTable.parse(csv)
        assertEquals(listOf("name", "note"), table.header)
        assertEquals(listOf("lentils", "stew \"best\", ok"), table.rows[0])
        assertEquals(listOf("rice", "plain"), table.rows[1])
    }

    @Test
    fun sniffer_mapsKnownDialects() {
        val mapping = CsvMappingSniffer.sniff(listOf("Date", "Weight (kg)", "Body Fat (%)", "Mystery"))
        // Unknown columns stay unmapped — the wizard asks; we never guess.
        assertEquals(3, mapping.size)
        assertEquals(CsvTarget.Day, mapping[0].target)
        assertEquals(CsvTarget.Metric("weight", "kg"), mapping[1].target)
        assertEquals(CsvTarget.Metric("body-fat", "%"), mapping[2].target)
    }

    @Test
    fun importer_producesStagedRows_andHoldsWeakData() {
        val table =
            CsvTable.parse(
                "date,weight_kg,kcal_in,bad\r\n" +
                    "2026-09-10,84.2,1800,\r\n" +
                    "2026/09/11,84.0,not-a-number\r\n" +
                    "garbage,1,1\r\n",
            )
        val mapping =
            listOf(
                CsvColumnMapping("date", CsvTarget.Day),
                CsvColumnMapping("weight_kg", CsvTarget.Metric("weight", "kg")),
                CsvColumnMapping("kcal_in", CsvTarget.Metric("intake", "kcal")),
                CsvColumnMapping("bad", CsvTarget.Metric("burn", "kcal")),
            )
        val parsed = CsvMeasurementImporter.parse(table, mapping, utc)
        // 2026-09-10 weight + intake; 2026-09-11 weight only (intake held).
        assertEquals(3, parsed.rows.size)
        assertEquals(84.2, parsed.rows[0].valueReal)
        assertEquals(20706, parsed.rows[0].dayEpochDay) // 2026-09-10 epoch day
        assertTrue(parsed.warnings.any { it.contains("not-a-number") })
        assertTrue(parsed.warnings.any { it.contains("unparseable date") })
    }

    @Test
    fun importer_withoutDayColumn_neverPlacesRows() {
        val table = CsvTable.parse("weight_kg\r\n84.2\r\n")
        val parsed =
            CsvMeasurementImporter.parse(
                table,
                listOf(CsvColumnMapping("weight_kg", CsvTarget.Metric("weight", "kg"))),
                utc,
            )
        assertTrue(parsed.rows.isEmpty())
        assertTrue(parsed.warnings.single().contains("no day column mapped"))
    }

    @Test
    fun exportImport_shareColumnVocabulary() {
        // The sniffer can map OUR OWN export header back (round-trip dialect).
        val events = listOf(event("w1", "weight", 84.2, "kg", day = 20_000, time = "07:12:00"))
        val csv = MetricCsv.write(events, utc).csv
        val header = CsvTable.parse(csv).header
        val mapping = CsvMappingSniffer.sniff(header)
        assertTrue(mapping.any { it.target is CsvTarget.Day })
        assertTrue(mapping.any { (it.target as? CsvTarget.Metric)?.kind == "weight" })
    }

    private fun event(
        id: String,
        kind: String,
        value: Double,
        unit: String,
        day: Long,
        time: String,
        metricName: String? = null,
    ): MetricCsv.CsvEvent {
        val at = kotlinx.datetime.Instant.fromEpochMilliseconds(day * 86_400_000L + timeOfDayMs(time))
        return MetricCsv.CsvEvent(
            eventId = id,
            kind = kind,
            valueReal = value,
            unit = unit,
            capturedAtEpochMs = at.toEpochMilliseconds(),
            metricName = metricName,
        )
    }

    /** The export writes with TimeZone.UTC in these tests (deterministic). */
    private val utc = kotlinx.datetime.TimeZone.UTC

    private fun timeOfDayMs(hms: String): Long {
        val parts = hms.split(":")
        return (parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()) * 1000
    }
}
