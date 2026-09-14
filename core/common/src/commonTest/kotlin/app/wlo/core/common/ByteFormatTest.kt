package app.wlo.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

/** The shared decimal byte formatter (WLO-0032) — one ladder, every surface. */
public class ByteFormatTest {
    @Test
    public fun bytesStayRaw() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("999 B", formatBytes(999))
    }

    @Test
    public fun kilobytesAreDecimalWithOneFractionDigit() {
        assertEquals("1.0 KB", formatBytes(1_000))
        assertEquals("1.5 KB", formatBytes(1_500))
        assertEquals("999.9 KB", formatBytes(999_900))
    }

    @Test
    public fun megabytesAreDecimalWithOneFractionDigit() {
        assertEquals("1.0 MB", formatBytes(1_000_000))
        assertEquals("23.5 MB", formatBytes(23_500_000))
    }

    @Test
    public fun formattingIsLocaleIndependent() {
        // Locale.ROOT's dot decimal regardless of the platform default locale.
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("1.5 KB", formatBytes(1_500))
        } finally {
            java.util.Locale.setDefault(original)
        }
    }
}
