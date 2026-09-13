package app.wlo.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rotation semantics (F13 §3/R-U5: write-new-then-rotate keep 7). */
class RotationPolicyTest {
    @Test
    fun keepsNewestSeven() {
        val names = (1..10).map { RotationPolicy.fileNameFor("2026-09-%02d".format(it)) }
        val retired = RotationPolicy.filesToRetire(names)
        assertEquals(3, retired.size)
        assertEquals(
            listOf(
                "wlo_backup_2026-09-01.wlo",
                "wlo_backup_2026-09-02.wlo",
                "wlo_backup_2026-09-03.wlo",
            ),
            retired,
        )
    }

    @Test
    fun fewerThanKeep_retiresNothing() {
        val names = (1..7).map { RotationPolicy.fileNameFor("2026-09-%02d".format(it)) }
        assertTrue(RotationPolicy.filesToRetire(names).isEmpty())
    }

    @Test
    fun foreignFiles_neverTouched() {
        val retired =
            RotationPolicy.filesToRetire(
                listOf(
                    "wlo_backup_2026-08-01.wlo",
                    "wlo_backup_2026-08-02.wlo",
                    "wlo_backup_2026-08-03.wlo",
                    "wlo_backup_2026-08-04.wlo",
                    "wlo_backup_2026-08-05.wlo",
                    "wlo_backup_2026-08-06.wlo",
                    "wlo_backup_2026-08-07.wlo",
                    "wlo_backup_2026-08-08.wlo", // oldest → retired
                    "vacation.jpg",
                    "notes.txt",
                    "wlo_backup_old.json", // legacy suffix — not ours to delete
                    "wlo_export_2026-08-02.wlo",
                ),
            )
        assertEquals(listOf("wlo_backup_2026-08-01.wlo"), retired)
    }

    @Test
    fun sameDaySupersede_countsOnce() {
        val names =
            listOf(
                "wlo_backup_2026-09-01.wlo",
                "wlo_backup_2026-09-01.wlo", // SAF same-name rewrite (deduped by policy)
                "wlo_backup_2026-09-02.wlo",
                "wlo_backup_2026-09-03.wlo",
                "wlo_backup_2026-09-04.wlo",
                "wlo_backup_2026-09-05.wlo",
                "wlo_backup_2026-09-06.wlo",
                "wlo_backup_2026-09-07.wlo",
            )
        // 8 names but 7 DISTINCT backup days → exactly at keep → nothing retired.
        assertTrue(RotationPolicy.filesToRetire(names, keep = 7).isEmpty())
        // One more day pushes the oldest out even though 8 raw names remain.
        assertEquals(
            listOf("wlo_backup_2026-09-01.wlo"),
            RotationPolicy.filesToRetire(names + "wlo_backup_2026-09-08.wlo", keep = 7),
        )
    }

    @Test
    fun fileNameFor_hasCanonicalShape() {
        assertEquals("wlo_backup_2026-09-12.wlo", RotationPolicy.fileNameFor("2026-09-12"))
        assertTrue(RotationPolicy.isBackupFile("wlo_backup_2026-09-12.wlo"))
        assertTrue(!RotationPolicy.isBackupFile("wlo_backup_2026-09-12.json"))
    }

    @Test
    fun zeroKeep_retiresEverything() {
        val names = listOf("wlo_backup_2026-09-01.wlo", "wlo_backup_2026-09-02.wlo")
        assertEquals(names, RotationPolicy.filesToRetire(names, keep = 0))
    }
}
