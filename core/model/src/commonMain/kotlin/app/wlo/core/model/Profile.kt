package app.wlo.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Profile domain type (R-B9: single-profile v1, partition-ready — every domain
 * row carries `profileId`; the default profile is created at onboarding).
 * Storage shape lives in `:core:database`; this is the spine's public type.
 */
@Serializable
public data class Profile(
    public val id: String,
    public val sex: Sex?,
    public val birthYear: Int,
    public val heightCm: Double,
    public val startWeightKg: Double,
    public val activityLevel: ActivityLevel,
    /** Metric default, imperial a user setting (R-D10). */
    public val unitPreference: UnitSystem = UnitSystem.METRIC,
    public val createdAt: Instant,
    /** Archive-don't-delete: set when the profile is retired, never hard-deleted. */
    public val archivedAt: Instant? = null,
) {
    /** Age in whole years at [Instant] (deterministic; engines take the year in). */
    public fun ageAtYear(year: Int): Int = (year - birthYear).coerceAtLeast(0)
}

/** Recorded sex; `null` = undisclosed (floors/BMR then use registry defaults). */
@Serializable
public enum class Sex(
    public val wireName: String,
) {
    @SerialName("female")
    FEMALE("female"),

    @SerialName("male")
    MALE("male"),

    @SerialName("other")
    OTHER("other"),
    ;

    public companion object {
        public fun fromWireName(name: String?): Sex? = name?.let { n -> entries.firstOrNull { it.wireName == n } }
    }
}

/**
 * Activity level for the formula-estimate multiplier (F01 §3). The published
 * standard factors live in [ConstantsRegistry.activityMultiplier].
 */
@Serializable
public enum class ActivityLevel(
    public val wireName: String,
) {
    @SerialName("sedentary")
    SEDENTARY("sedentary"),

    @SerialName("light")
    LIGHT("light"),

    @SerialName("moderate")
    MODERATE("moderate"),

    @SerialName("active")
    ACTIVE("active"),

    @SerialName("very-active")
    VERY_ACTIVE("very-active"),
    ;

    public companion object {
        public fun fromWireName(name: String): ActivityLevel? = entries.firstOrNull { it.wireName == name }
    }
}

/** Units: metric default and fallback (R-D10); imperial is a user setting. */
@Serializable
public enum class UnitSystem(
    public val wireName: String,
) {
    @SerialName("metric")
    METRIC("metric"),

    @SerialName("imperial")
    IMPERIAL("imperial"),
    ;

    public companion object {
        public fun fromWireName(name: String): UnitSystem? = entries.firstOrNull { it.wireName == name }
    }
}
