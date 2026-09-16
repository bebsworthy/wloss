package app.wlo.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

class ThemeContrastTest {
    @Test
    fun activeRolePairingsMeetWcagContrastInBothThemes() {
        verifyScheme("dark", WloDarkColorScheme, darkExtendedColorsForTest())
        verifyScheme("light", WloLightColorScheme, lightExtendedColorsForTest())
    }

    private fun verifyScheme(
        name: String,
        scheme: ColorScheme,
        extended: ExtendedForTest,
    ) {
        val textPairs =
            listOf(
                "onPrimary" to (scheme.onPrimary to scheme.primary),
                "onPrimaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
                "onSecondary" to (scheme.onSecondary to scheme.secondary),
                "onSecondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
                "onTertiary" to (scheme.onTertiary to scheme.tertiary),
                "onTertiaryContainer" to (scheme.onTertiaryContainer to scheme.tertiaryContainer),
                "onSurface" to (scheme.onSurface to scheme.surface),
                "onSurfaceVariant" to (scheme.onSurfaceVariant to scheme.surface),
                "onError" to (scheme.onError to scheme.error),
                "onErrorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
                "inverseOnSurface" to (scheme.inverseOnSurface to scheme.inverseSurface),
                "textTertiary" to (extended.textTertiary to scheme.surface),
                "held" to (extended.held to scheme.surface),
                "developing" to (extended.developing to scheme.surface),
                "info" to (extended.info to scheme.surface),
            )
        textPairs.forEach { (role, colors) ->
            assertContrast("$name $role", colors.first, colors.second, MIN_TEXT_CONTRAST)
        }
        assertContrast("$name outline", scheme.outline, scheme.surface, MIN_COMPONENT_CONTRAST)
        assertContrast("$name chart boundary", extended.chartBoundary, scheme.surface, MIN_COMPONENT_CONTRAST)

        val selectedContainer = scheme.primary.compositeOver(scheme.surface, SELECTED_CONTAINER_ALPHA)
        assertContrast("$name selected chip", scheme.primary, selectedContainer, MIN_TEXT_CONTRAST)
    }

    private fun assertContrast(
        name: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        println("$name: %.2f:1 (minimum %.1f:1)".format(ratio, minimum))
        assertTrue(ratio >= minimum, "$name contrast was %.2f:1; expected at least %.1f:1".format(ratio, minimum))
    }
}

private data class ExtendedForTest(
    val held: Color,
    val developing: Color,
    val info: Color,
    val textTertiary: Color,
    val chartBoundary: Color,
)

private fun darkExtendedColorsForTest(): ExtendedForTest =
    ExtendedForTest(WloColors.Held, WloColors.Developing, WloColors.Info, WloColors.TextTertiary, WloColors.Accent)

private fun lightExtendedColorsForTest(): ExtendedForTest =
    ExtendedForTest(
        WloColors.HeldLight,
        WloColors.DevelopingLight,
        WloColors.InfoLight,
        WloColors.TextTertiaryLight,
        WloColors.PrimaryLight,
    )

private fun contrastRatio(
    foreground: Color,
    background: Color,
): Double {
    val foregroundLuminance = foreground.relativeLuminance()
    val backgroundLuminance = background.relativeLuminance()
    return (max(foregroundLuminance, backgroundLuminance) + 0.05) /
        (min(foregroundLuminance, backgroundLuminance) + 0.05)
}

private fun Color.relativeLuminance(): Double =
    0.2126 * red.toLinear() +
        0.7152 * green.toLinear() +
        0.0722 * blue.toLinear()

private fun Float.toLinear(): Double =
    if (this <= 0.04045f) {
        this / 12.92
    } else {
        Math.pow((this + 0.055) / 1.055, 2.4)
    }

private fun Color.compositeOver(
    background: Color,
    alpha: Float,
): Color =
    Color(
        red = red * alpha + background.red * (1f - alpha),
        green = green * alpha + background.green * (1f - alpha),
        blue = blue * alpha + background.blue * (1f - alpha),
    )

private const val MIN_TEXT_CONTRAST: Double = 4.5
private const val MIN_COMPONENT_CONTRAST: Double = 3.0
private const val SELECTED_CONTAINER_ALPHA: Float = 0.16f
