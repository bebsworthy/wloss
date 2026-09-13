package app.wlo.core.vault

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** FLAG_SECURE route set (IA §6 discreet mode; F13 §3 vault surfaces). */
class SecureSurfacesTest {
    @Test
    fun archiveAndVaultSurfaces_areSecure() {
        assertTrue(SecureSurfaces.isSecure("archive"))
        assertTrue(SecureSurfaces.isSecure("stub/archive-capture"))
        assertTrue(SecureSurfaces.isSecure("stub/archive-compare"))
        assertTrue(SecureSurfaces.isSecure("stub/vault"))
        assertTrue(SecureSurfaces.isSecure("f13/vault/restore?step=2"))
    }

    @Test
    fun everyDaySurfaces_areNot() {
        assertFalse(SecureSurfaces.isSecure("hub"))
        assertFalse(SecureSurfaces.isSecure("plan"))
        assertFalse(SecureSurfaces.isSecure("f02/diary?entry=x"))
        assertFalse(SecureSurfaces.isSecure("f06/weight"))
        assertFalse(SecureSurfaces.isSecure(null))
    }

    @Test
    fun queryStrings_doNotAffectClassification() {
        assertTrue(SecureSurfaces.isSecure("archive?from=hub"))
    }
}
