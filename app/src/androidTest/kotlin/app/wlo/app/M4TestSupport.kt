package app.wlo.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/** Shared M4-B instrumentation helpers (shell + airplane mode; the M4-A incantations). */
public object M4TestSupport {
    public fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    public fun shell(command: String): String {
        // UiAutomation.executeShellCommand yields a ParcelFileDescriptor on
        // newer APIs, an InputStream on older — normalize to a stream.
        val raw: Any =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        val stream: java.io.InputStream =
            if (raw is android.os.ParcelFileDescriptor) {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(raw)
            } else {
                raw as java.io.InputStream
            }
        return stream.use { input -> input.bufferedReader().readText().trim() }
    }

    public fun grantCameraPermission() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            target.packageName,
            android.Manifest.permission.CAMERA,
        )
    }

    public fun setAirplaneMode(enabled: Boolean): Boolean {
        shell("cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}")
        val expected = if (enabled) "enabled" else "disabled"
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (shell("cmd connectivity airplane-mode") == expected) return true
            Thread.sleep(250)
        }
        return false
    }
}
