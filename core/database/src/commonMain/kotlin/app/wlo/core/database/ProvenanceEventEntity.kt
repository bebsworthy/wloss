package app.wlo.core.database

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Seed table for M1: one row per event that carries a derived number, storing
 * the provenance JSON next to the value (invariant C6 — provenance on every
 * derived number, ARCHITECTURE §1.4). Real schema grows in M2 (R-B8
 * event-level storage, EAV sidecar, FTS5 food index).
 */
@Entity(tableName = "provenance_events")
public data class ProvenanceEventEntity(
    @PrimaryKey(autoGenerate = true)
    public val id: Long = 0,
    /** Day id (YYYY-MM-DD, user's zone) — see :core:common DayBoundary. */
    public val day: String,
    /** Event kind, e.g. "bmi", "weight". */
    public val kind: String,
    /** Rendered value text (settings-driven unit at write time). */
    public val valueText: String,
    /** Serialized :core:model Provenance payload. */
    public val provenanceJson: String,
    public val createdAtEpochMs: Long,
    /** Schema v2: optional free-text note (nullable — old rows stay valid). */
    public val note: String? = null,
)
