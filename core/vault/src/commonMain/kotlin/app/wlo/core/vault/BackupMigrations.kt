package app.wlo.core.vault

import kotlinx.serialization.json.JsonObject

/**
 * The single migration funnel for backup documents (ADR-004 rule 2, T-C6):
 * every historical schema version normalizes forward step-by-step until the
 * current version. One registry row per hop; CI pins every shipped version
 * with a fixture (BackupDocumentMigrationTest) — the ADR-004 "old-version
 * fixture decodes in CI" rule applied to the backup document.
 *
 * History:
 * - **v1** (M6, WLO-0028 PART A) — the first version; sections per
 *   [BackupSchema.SECTION_ORDER], `network_receipts`/`day_records`/`food_search`
 *   excluded by ruling (see BackupSchema KDoc).
 */
public object BackupMigrations {
    /** One migration hop: writtenVersion → writtenVersion + 1. */
    private val HOPS: Map<Int, (JsonObject) -> JsonObject> = mapOf(1 to { sections -> sections })

    public fun migrate(
        sections: JsonObject,
        fromVersion: Int,
        toVersion: Int,
    ): JsonObject {
        var current = sections
        var version = fromVersion
        while (version < toVersion) {
            val hop = HOPS[version] ?: return current // identity hops until v2 exists
            current = hop(current)
            version++
        }
        return current
    }
}
