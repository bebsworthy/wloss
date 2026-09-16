package app.wlo.core.common

/** Locale-tolerant decimal parsing for editable health-number drafts. */
public object DecimalInput {
    /** Accepts either one comma or one dot decimal separator and rejects non-finite values. */
    public fun parse(text: String): Double? {
        val value = text.trim()
        if (value.isEmpty() || value.count { it == '.' || it == ',' } > 1) return null
        return value.replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
    }
}
