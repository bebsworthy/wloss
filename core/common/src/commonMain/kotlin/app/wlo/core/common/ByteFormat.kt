package app.wlo.core.common

import java.util.Locale

/**
 * Byte counts, decimal (B → KB → MB, one decimal) — the ONE shared home, so
 * every surface reads bytes the same way (WLO-0032; f12/f13/Zoo carried
 * identical private copies before). Never the binary 1024 ladder used before
 * WLO-0031: 1000 B is "1.0 KB", 1000 KB is "1.0 MB".
 *
 * Locale-independent by construction ([Locale.ROOT] dot decimal), so exports
 * and UI never disagree across device locales.
 */
public fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
