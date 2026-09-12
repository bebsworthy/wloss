package app.wlo.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Bristol stool scale, F09's capture vocabulary (1–7). */
@Serializable
public enum class BristolType(
    public val number: Int,
) {
    @SerialName("bristol-1")
    TYPE_1(1),

    @SerialName("bristol-2")
    TYPE_2(2),

    @SerialName("bristol-3")
    TYPE_3(3),

    @SerialName("bristol-4")
    TYPE_4(4),

    @SerialName("bristol-5")
    TYPE_5(5),

    @SerialName("bristol-6")
    TYPE_6(6),

    @SerialName("bristol-7")
    TYPE_7(7),
}
