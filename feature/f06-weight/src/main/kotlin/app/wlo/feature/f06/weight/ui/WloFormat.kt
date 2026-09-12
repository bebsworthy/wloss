package app.wlo.feature.f06.weight.ui

/** Display formatting for the F06 numerals (tnum-friendly, one/two decimals). */
internal fun format1(value: Double): String = decimals(value, 1)

internal fun format2(value: Double): String = decimals(value, 2)

private fun decimals(
    value: Double,
    places: Int,
): String {
    val scale = pow10(places)
    val rounded = (value * scale).toLong()
    val whole = rounded / scale
    val fraction = (rounded % scale).toString().padStart(places, '0')
    return if (fraction.all { it == '0' }) "$whole" else "$whole.$fraction"
}

private fun pow10(places: Int): Long {
    var result = 1L
    repeat(places) { result *= 10L }
    return result
}
