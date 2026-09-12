package app.wlo.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The six frozen AI consent categories (F12 §3.1, R-C1). No other feature may
 * define or rename a category; the seventh (`exercise-plan`) is pre-approved
 * for a future F05 cloud path and enters only by master-doc amendment.
 */
@Serializable
public enum class ConsentCapability(
    public val wireName: String,
) {
    @SerialName("food-photo")
    FOOD_PHOTO("food-photo"),

    @SerialName("voice-input")
    VOICE_INPUT("voice-input"),

    @SerialName("meal-planning")
    MEAL_PLANNING("meal-planning"),

    @SerialName("silhouette")
    SILHOUETTE("silhouette"),

    @SerialName("poop-photo")
    POOP_PHOTO("poop-photo"),

    @SerialName("insights-chat")
    INSIGHTS_CHAT("insights-chat"),
}
