package app.wlo.app

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
     * Drives the weight-first flow with UiAutomator. Unit is the sole required
     * choice; goal and first reading are skipped for deep-link setup tests.
     */
    public fun driveThroughWizardWithUiAutomator() {
        val ui = device()
        ui.waitForIdle()
        clickText(ui, "Continue", required = true)
        clickText(ui, "Kilograms", required = true)
        clickText(ui, "Continue", required = true)
        clickText(ui, "Continue or skip", required = true)
        clickText(ui, "Open Weight", required = true)
        ui.wait(Until.hasObject(By.text("Weight")), TIMEOUT_MS)
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

    private const val TIMEOUT_MS: Long = 10_000
    private const val POLL_MS: Long = 200
    private const val SETTLE_MS: Long = 800
}

/**
 * Completes the weight-first flow with the minimum profile-backed reading,
 * then opens Hub for legacy feature tests. No Diet Plan is created.
 */
public fun ComposeTestRule.driveToHub() {
    waitForIdle()
    onAllNodesWithTag("weight-first-next", useUnmergedTree = true)[0].performClick()
    onAllNodesWithTag("weight-first-unit-kg", useUnmergedTree = true)[0].performClick()
    onAllNodesWithTag("weight-first-next", useUnmergedTree = true)[0].performClick()
    onAllNodesWithTag("weight-first-next", useUnmergedTree = true)[0].performClick()
    onAllNodesWithTag("weight-first-source-manual", useUnmergedTree = true)[0].performClick()
    onAllNodesWithTag("weight-first-weight", useUnmergedTree = true)[0].performTextInput("82")
    onAllNodesWithTag("weight-first-finish", useUnmergedTree = true)[0].performClick()
    awaitWeight()
    onAllNodesWithContentDescription("Hub", useUnmergedTree = true)[0].performClick()
    awaitHub()
}

/** Polls until the primary Weight destination has replaced onboarding. */
private fun ComposeTestRule.awaitWeight() {
    repeat(HUB_POLLS) {
        waitForIdle()
        if (onAllNodesWithTag("f06-title", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) return
        Thread.sleep(POLL_MS)
    }
    error("Weight never took over after Start")
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

private const val HUB_POLLS: Int = 60
private const val POLL_MS: Long = 100
