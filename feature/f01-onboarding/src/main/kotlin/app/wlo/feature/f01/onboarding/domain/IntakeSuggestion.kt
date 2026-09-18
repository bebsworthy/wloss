package app.wlo.feature.f01.onboarding.domain

/**
 * Candidates from the reviewed AHA/ACC/TOS loss and NHS gain guidance (WLO-0125).
 * These must still pass the shared eligibility contract before presentation.
 * The weight goal sets direction; its distance never increases the deficit.
 */
public object IntakeSuggestion {
    public fun candidate(
        maintenanceKcal: Double,
        currentKg: Double,
        goalKg: Double,
        heightCm: Double?,
    ): Double? {
        if (!maintenanceKcal.isFinite() || maintenanceKcal <= 0) return null
        if (listOf(currentKg, goalKg).any { !it.isFinite() || it <= 0 }) return null
        val adjustment =
            when {
                goalKg < currentKg -> {
                    val height = heightCm?.takeIf { it.isFinite() && it > 0 } ?: return null
                    val bmi = currentKg / (height / 100 * height / 100)
                    if (bmi < 25) return null
                    -500.0
                }
                goalKg > currentKg -> 300.0
                else -> 0.0
            }
        return (maintenanceKcal + adjustment).takeIf { it > 0 }
    }
}
