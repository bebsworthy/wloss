package app.wlo.app

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.wlo.core.vault.SecureSurfaces
import app.wlo.core.vault.applyFlagSecure
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The FLAG_SECURE route contract (IA §6 discreet mode; F13 §3): the route set
 * classifies, and the activity window actually flips the flag on a secure
 * route and clears it again on an everyday one.
 */
@RunWith(AndroidJUnit4::class)
public class M6FlagSecureWindowTest {
    @Test
    public fun secureRoutesSetTheFlag_everydayRoutesClearIt() {
        ActivityScenario.launch(MainActivity::class.java).onActivity { activity ->
            val secure = { activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0 }
            val clear = { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }

            clear()
            assertFalse(secure(), "starts clear")

            applyFlagSecure(activity, "archive")
            assertTrue(secure(), "the Archive tab is FLAG_SECURE (IA §6)")

            applyFlagSecure(activity, "f13/vault/restore?step=2")
            assertTrue(secure(), "vault surfaces are FLAG_SECURE")

            applyFlagSecure(activity, "hub")
            assertFalse(secure(), "the Hub is not")
            Unit
        }
        assertTrue(SecureSurfaces.isSecure("stub/archive-capture"))
        assertFalse(SecureSurfaces.isSecure("f06/weight"))
    }
}
