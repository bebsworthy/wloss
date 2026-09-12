package app.wlo.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.wlo.app.navigation.WloApp
import app.wlo.core.designsystem.WloTheme

/**
 * The single activity (R-D2 shell). Dark is forced as the base scheme (R-D1 —
 * the 6 a.m. weigh-in happens in a dark bathroom); `wlo://` links arrive via
 * the manifest filter, first launch is handled by NavHost itself, re-delivery
 * (singleTask) by [onNewIntent].
 */
public class MainActivity : ComponentActivity() {
    /**
     * Test hook: the route reported by the last
     * [NavController.OnDestinationChangedListener] firing. Instrumented
     * deep-link tests poll this instead of scraping the view tree.
     */
    public var currentDestinationForVerification: String? = null
        private set

    private val deepLinkIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            // R-D1: dark is canonical; the light scheme is future work.
            WloTheme(darkTheme = true) {
                WloApp(
                    newIntent = deepLinkIntent.value,
                    onDestinationChanged = { route -> currentDestinationForVerification = route },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkIntent.value = intent
    }

    /**
     * Test hook: [onNewIntent] is protected, so the instrumented singleTask
     * re-delivery test goes through this public forwarder.
     */
    public fun deliverNewIntentForVerification(intent: Intent) {
        onNewIntent(intent)
    }
}
