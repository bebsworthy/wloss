package app.wlo.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * WLO theme — dark-first with a fixed semantic palette (R-D1): no alarm-red
 * token exists; `held` = amber is the strongest state color; urgency is
 * position + copy, never hue. Token values come from DESIGN-SYSTEM.md §1
 * (ratified); Material You dynamic color is surfaces-only when enabled later
 * and never re-sources semantic states.
 */
public object WloColors {
    // Neutral core (§1.1, dark values).
    public val Bg: Color = Color(0xFF0E1116)
    public val Surface: Color = Color(0xFF151A21)
    public val SurfaceRaised: Color = Color(0xFF1B222B)
    public val SurfaceSunken: Color = Color(0xFF0A0D11)
    public val Outline: Color = Color(0xFF2A323D)
    public val TextPrimary: Color = Color(0xFFE8ECF1)
    public val TextSecondary: Color = Color(0xFF9AA6B5)
    public val TextTertiary: Color = Color(0xFF5C6875)

    // Semantic states (§1.2) — no moral valence.
    public val Accent: Color = Color(0xFF3DD6A5)
    public val AccentDim: Color = Color(0xFF1F6B54)
    public val NeutralDelta: Color = Color(0xFF8A93A6)
    public val Held: Color = Color(0xFFE8B34B)
    public val Developing: Color = Color(0xFF7FA6C9)
    public val Info: Color = Color(0xFF6FB7FF)

    // Light theme inversions (§1.1).
    public val BgLight: Color = Color(0xFFF7F8FA)
    public val SurfaceLight: Color = Color(0xFFFFFFFF)
    public val SurfaceSunkenLight: Color = Color(0xFFEEF1F4)
    public val TextPrimaryLight: Color = Color(0xFF171C23)

    // Data-viz series (§1.3, Okabe-Ito).
    public val Series1: Color = Color(0xFF56B4E9)
    public val Series2: Color = Color(0xFFE69F00)
    public val Series3: Color = Color(0xFFCC79A7)
    public val Series4: Color = Color(0xFF009E73)
    public val Series5: Color = Color(0xFF999999)
}

/** Semantic tokens M3's scheme cannot express (wells, deltas, quality states). */
public data class WloExtendedColors(
    val accentDim: Color,
    val neutralDelta: Color,
    val held: Color,
    val developing: Color,
    val info: Color,
    val textTertiary: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val series: List<Color>,
)

public val LocalWloExtendedColors: ProvidableCompositionLocal<WloExtendedColors> =
    staticCompositionLocalOf { defaultExtendedColors }

private val defaultExtendedColors =
    WloExtendedColors(
        accentDim = WloColors.AccentDim,
        neutralDelta = WloColors.NeutralDelta,
        held = WloColors.Held,
        developing = WloColors.Developing,
        info = WloColors.Info,
        textTertiary = WloColors.TextTertiary,
        surfaceRaised = WloColors.SurfaceRaised,
        surfaceSunken = WloColors.SurfaceSunken,
        series = listOf(WloColors.Series1, WloColors.Series2, WloColors.Series3, WloColors.Series4, WloColors.Series5),
    )

private val darkScheme: ColorScheme =
    darkColorScheme(
        primary = WloColors.Accent,
        onPrimary = WloColors.SurfaceSunken,
        secondary = WloColors.Developing,
        onSecondary = WloColors.SurfaceSunken,
        tertiary = WloColors.Info,
        onTertiary = WloColors.SurfaceSunken,
        background = WloColors.Bg,
        onBackground = WloColors.TextPrimary,
        surface = WloColors.Surface,
        onSurface = WloColors.TextPrimary,
        surfaceVariant = WloColors.SurfaceRaised,
        onSurfaceVariant = WloColors.TextSecondary,
        surfaceContainerLowest = WloColors.SurfaceSunken,
        surfaceContainerLow = WloColors.SurfaceSunken,
        surfaceContainer = WloColors.Surface,
        surfaceContainerHigh = WloColors.SurfaceRaised,
        surfaceContainerHighest = WloColors.SurfaceRaised,
        outline = WloColors.Outline,
        outlineVariant = WloColors.Outline,
        error = WloColors.Held,
        onError = WloColors.SurfaceSunken,
    )

private val lightScheme: ColorScheme =
    lightColorScheme(
        primary = WloColors.AccentDim,
        onPrimary = WloColors.BgLight,
        background = WloColors.BgLight,
        onBackground = WloColors.TextPrimaryLight,
        surface = WloColors.SurfaceLight,
        onSurface = WloColors.TextPrimaryLight,
        surfaceVariant = WloColors.SurfaceSunkenLight,
        onSurfaceVariant = WloColors.TextSecondary,
        outline = WloColors.Outline,
        // Red does not exist in WLO's theme; `held` amber is the strongest state color.
        error = WloColors.Held,
        onError = WloColors.TextPrimaryLight,
    )

/** Convenience accessor. */
public val wloExtendedColors: WloExtendedColors
    @Composable get() = LocalWloExtendedColors.current

/** Convenience accessor for the WLO type ramp (R-D3). */
public val wloTypography: WloTypography
    @Composable get() = LocalWloTypography.current

/**
 * Dark-first theme (the 6 a.m. weigh-in happens in a dark bathroom, R-D1).
 * Typography defaults to the Inter ramp ([wloTypography], R-D3); pass
 * `darkTheme = true` explicitly where the app forces the canonical scheme.
 */
@Composable
public fun WloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    typography: WloTypography = wloTypography(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalWloExtendedColors provides defaultExtendedColors,
        LocalWloTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme else lightScheme,
            typography = wloMaterialTypography(typography),
            content = content,
        )
    }
}
