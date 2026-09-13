package app.wlo.app

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Shell-level helpers for the onboarding acceptance tests. The `pm clear` /
 * `am force-stop` commands run as shell via UiAutomation — the test itself
 * lives in the `app.wlo.test` process and survives the app process dying.
 */
public object OnboardingRobot {
    /** The target package (the app under test), not the test package. */
    public fun targetPackage(): String = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    /**
     * Runs a shell command as shell and WAITS for it to finish (drains the
     * output stream before closing). Closing the ParcelFileDescriptor
     * without draining can cancel the in-flight command — fast ones (`am
     * start`) still land, but redirect writes (`… > /sdcard/Download/x`)
     * silently never complete.
     */
    public fun shell(command: String) {
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
            .let { fd ->
                java.io.FileInputStream(fd.fileDescriptor).use { input -> input.readBytes() }
                fd.close()
            }
    }

    /**
     * Runs a shell command and returns its stdout (bounded — for short
     * outputs like `ls`). The returned ParcelFileDescriptor must be fully
     * drained before close, hence the blocking read here.
     */
    public fun shellWithOutput(command: String): String =
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
            .let { fd ->
                val bytes =
                    fd.fileDescriptor.let {
                        java.io.FileInputStream(it).use { input -> input.readBytes() }
                    }
                fd.close()
                bytes.toString(Charsets.UTF_8)
            }

    /** Kills all app state AND the app process — the next launch is a cold start. */
    public fun clearAppState() {
        shell("pm clear ${targetPackage()}")
        Thread.sleep(SETTLE_MS)
    }

    /** Force-stops the app process (the draft must already be on disk). */
    public fun forceStopApp() {
        shell("am force-stop ${targetPackage()}")
        Thread.sleep(SETTLE_MS)
    }

    /** The device handle for cross-process (UiAutomator) interaction. */
    public fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * Drives the wizard with UiAutomator (works from the test process without
     * a compose rule): "Set up my plan", then every Continue, then Start.
     * Accepts every default — zero fields are mandatory (F01 §4).
     */
    public fun driveThroughWizardWithUiAutomator() {
        val ui = device()
        ui.waitForIdle()
        clickText(ui, "Set up my plan", required = true)
        var continues = 0
        while (continues < CONTINUE_STEPS && clickText(ui, "Continue", required = false)) {
            continues += 1
        }
        clickText(ui, "Start", required = true)
        // Wait for the plan write + gate flip to put the Hub on screen.
        ui.wait(Until.hasObject(By.textContains("Weight trend")), TIMEOUT_MS)
    }

    private fun clickText(
        ui: UiDevice,
        text: String,
        required: Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val nodes = ui.findObjects(By.textContains(text))
            if (nodes.isNotEmpty()) {
                nodes.last().click()
                return true
            }
            Thread.sleep(POLL_MS)
        }
        if (required) error("button containing \"$text\" never appeared")
        return false
    }

    private const val CONTINUE_STEPS: Int = 7
    private const val TIMEOUT_MS: Long = 10_000
    private const val POLL_MS: Long = 200
    private const val SETTLE_MS: Long = 800
}

/**
 * Drives the wizard from wherever it stands to the Hub via the compose tree,
 * accepting every default. Taps whichever commit control the current step
 * shows — "next" until the review step, then "start".
 */
public fun ComposeTestRule.driveToHub() {
    repeat(MAX_STEPS) {
        waitForIdle()
        val start = onAllNodesWithTag("onboarding-start", useUnmergedTree = true)
        if (start.fetchSemanticsNodes().isNotEmpty()) {
            start[0].performClick()
            awaitHub()
            return
        }
        val next = onAllNodesWithTag("onboarding-next", useUnmergedTree = true)
        if (next.fetchSemanticsNodes().isNotEmpty()) {
            next[0].performClick()
        }
    }
    awaitHub()
}

/** Polls until the hub trend card exists (the plan write flips the gate). */
public fun ComposeTestRule.awaitHub() {
    repeat(HUB_POLLS) {
        waitForIdle()
        if (onAllNodesWithTag("hub-trend-card", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) {
            return
        }
        Thread.sleep(POLL_MS)
    }
    error("the Hub never took over after Start")
}

private const val MAX_STEPS: Int = 10
private const val HUB_POLLS: Int = 60
private const val POLL_MS: Long = 100
