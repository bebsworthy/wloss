package app.wlo.core.vault

import androidx.room3.withWriteTransaction
import app.wlo.core.common.ClockPort
import app.wlo.core.data.DayProjector
import app.wlo.core.database.MeasurementEventAttrEntity
import app.wlo.core.database.MeasurementEventEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.model.MeasurementKind

/** Atomic, retry-safe Room commit for one staged CSV import. */
public class CsvImportCommitter(
    private val db: WloDatabase,
    private val projector: DayProjector,
    private val clock: ClockPort,
    private val transactionHook: suspend () -> Unit = {},
) {
    public data class Result(
        public val inserted: Int,
        public val skipped: Int,
    )

    public suspend fun commit(
        profileId: String,
        rows: List<CsvMeasurementImporter.StagedMeasurement>,
    ): Result {
        val validKinds = MeasurementKind.entries.map { it.wireName }.toSet()
        val valid = rows.filter { it.kind in validKinds }
        val ids = valid.map { row -> eventId(profileId, row) }
        val before = ids.count { id -> db.measurementEvents().byId(id) != null }
        val capturedAt = clock.now().toEpochMilliseconds()

        db.withWriteTransaction {
            db.measurementEvents().insertAllIgnoring(
                valid.map { row ->
                    MeasurementEventEntity(
                        id = eventId(profileId, row),
                        profileId = profileId,
                        dayEpochDay = row.dayEpochDay,
                        kind = row.kind,
                        valueReal = row.valueReal,
                        unit = row.unit,
                        source = row.source,
                        capturedAtEpochMs = capturedAt,
                    )
                },
            )
            transactionHook()
            db.measurementEventAttrs().insertAllIgnoring(
                valid.mapNotNull { row ->
                    row.customName?.let { name ->
                        MeasurementEventAttrEntity(
                            eventId = eventId(profileId, row),
                            attr = METRIC_ATTR,
                            valueText = name,
                        )
                    }
                },
            )
        }

        for (day in valid.map { it.dayEpochDay }.distinct()) {
            projector.refresh(profileId, day, day)
        }
        val after = ids.count { id -> db.measurementEvents().byId(id) != null }
        val inserted = after - before
        return Result(inserted = inserted, skipped = rows.size - inserted)
    }

    private fun eventId(
        profileId: String,
        row: CsvMeasurementImporter.StagedMeasurement,
    ): String = "csv-${BackupCodec.sha256("$profileId|${row.importKey}".toByteArray())}"

    private companion object {
        const val METRIC_ATTR: String = "metric"
    }
}
