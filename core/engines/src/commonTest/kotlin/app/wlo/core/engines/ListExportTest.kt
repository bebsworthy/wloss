package app.wlo.core.engines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F04 exports (F04 §9: a format, not a partnership): the CSV is WLO's own
 * round-trip format and the parser is strict; the text format is the
 * share-sheet human view; JSON carries full fidelity.
 */
class ListExportTest {
    private val rows =
        listOf(
            ListExport.ExportRow(
                item = "red onions",
                qty = 3.0,
                unit = "x",
                aisle = "produce",
                state = "pending",
                sources =
                    listOf(
                        ListExport.SourceRef("red-lentil-curry", 20_700, 1.5, "x"),
                        ListExport.SourceRef("beef-chili", 20_702, 1.5, "x"),
                    ),
            ),
            ListExport.ExportRow("greek yogurt, whole", 500.0, "g", "dairy", "checked"),
            ListExport.ExportRow("olive oil", 3.0, "tbsp", "pantry", "pending"),
        )

    @Test
    fun csvRoundTrips() {
        val csv = ListExport.toCsv(rows)
        val parsed = ListExport.parseCsv(csv)
        assertTrue(parsed is ListExport.ListCsvResult.Ok, "own format always parses: $csv")
        assertEquals(rows, parsed.rows, "CSV round-trip is exact, quoting included")
    }

    @Test
    fun csvQuotesAndEscapes() {
        val csv = ListExport.toCsv(rows)
        assertTrue(csv.contains("\"greek yogurt, whole\""), "comma-bearing names are quoted")
        assertTrue(csv.contains("red-lentil-curry@20700:1.5 x;beef-chili@20702:1.5 x"), "sources encode provenance")
    }

    @Test
    fun parserRejectsForeignFormatsAsValues() {
        val wrongHeader = "name,amount\nonions,3"
        val rejected = ListExport.parseCsv(wrongHeader)
        assertTrue(rejected is ListExport.ListCsvResult.Failed)
        assertTrue(rejected.reason.contains("header"))

        val badQty = ListExport.toCsv(rows) + "onions,many,x,produce,pending,"
        val rejectedQty = ListExport.parseCsv(badQty)
        assertTrue(rejectedQty is ListExport.ListCsvResult.Failed)
        assertTrue(rejectedQty.reason.contains("not a number"))
    }

    @Test
    fun jsonRoundTrips() {
        val json = ListExport.toJson(rows)
        val parsed = ListExport.fromJson(json)
        assertTrue(parsed is ListExport.ListCsvResult.Ok)
        assertEquals(rows, parsed.rows)
    }

    @Test
    fun textIsAisleGroupedHumanFirst() {
        val text = ListExport.toText(rows, aisleOrder = taxonomyOrder, title = "WLO shopping list")
        assertTrue(text.contains("3 items"))
        assertTrue(text.indexOf("Produce") < text.indexOf("Dairy"), "taxonomy order rules the share sheet")
        assertTrue(text.contains("red onions 3 x"))
        assertTrue(text.contains("greek yogurt, whole 500 g"))
    }

    private val taxonomyOrder = listOf("produce", "bakery", "dairy", "meat-fish", "frozen", "pantry", "household")
}
