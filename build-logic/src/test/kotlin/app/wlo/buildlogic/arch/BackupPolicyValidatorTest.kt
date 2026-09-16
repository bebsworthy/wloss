package app.wlo.buildlogic.arch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupPolicyValidatorTest {
    private val excludes: String =
        BackupPolicyValidator.STORAGE_DOMAINS.joinToString("\n") { domain ->
            "<exclude domain=\"$domain\" path=\".\" />"
        }

    @Test
    fun mergedManifest_requiresAllThreeBackupBackstops() {
        val good = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
              <uses-permission android:name="android.permission.INTERNET" />
              <application
                android:allowBackup="false"
                android:fullBackupContent="@xml/backup_rules"
                android:dataExtractionRules="@xml/data_extraction_rules" />
            </manifest>
        """.trimIndent()
        assertEquals(emptyList(), BackupPolicyValidator.mergedManifestViolations(good))

        val weak = good.replace("android:allowBackup=\"false\"", "android:allowBackup=\"true\"")
        assertTrue(BackupPolicyValidator.mergedManifestViolations(weak).any { "allowBackup" in it })
    }

    @Test
    fun legacyRules_requireEveryStorageDomain() {
        val good = "<full-backup-content>$excludes</full-backup-content>"
        assertEquals(emptyList(), BackupPolicyValidator.legacyRulesViolations(good))

        val weak = good.replace("<exclude domain=\"database\" path=\".\" />", "")
        assertTrue(BackupPolicyValidator.legacyRulesViolations(weak).any { "database" in it })
    }

    @Test
    fun extractionRules_requireIndependentCloudAndDeviceTransferExclusions() {
        val good = """
            <data-extraction-rules>
              <cloud-backup>$excludes</cloud-backup>
              <device-transfer>$excludes</device-transfer>
            </data-extraction-rules>
        """.trimIndent()
        assertEquals(emptyList(), BackupPolicyValidator.extractionRulesViolations(good))

        val noTransfer = good.replace("<device-transfer>$excludes</device-transfer>", "")
        assertTrue(BackupPolicyValidator.extractionRulesViolations(noTransfer).any { "device-transfer" in it })
    }

    @Test
    fun includeRulesFailClosed() {
        val weak = """
            <full-backup-content>
              $excludes
              <include domain="file" path="public" />
            </full-backup-content>
        """.trimIndent()
        assertTrue(BackupPolicyValidator.legacyRulesViolations(weak).any { "must not contain" in it })
    }
}
