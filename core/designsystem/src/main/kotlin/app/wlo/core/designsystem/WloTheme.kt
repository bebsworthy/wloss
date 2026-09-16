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
    public val Outline: Color = Color(0xFF84909D)
    public val OutlineVariant: Color = Color(0xFF46515E)
    public val TextPrimary: Color = Color(0xFFE8ECF1)
    public val TextSecondary: Color = Color(0xFF9AA6B5)

    // 4.68:1 against SurfaceRaised, the lowest dark-scheme contrast this token sees.
    public val TextTertiary: Color = Color(0xFF808C9A)

    // Semantic states (§1.2) — no moral valence.
    public val Accent: Color = Color(0xFF3DD6A5)
    public val AccentDim: Color = Color(0xFF1F6B54)
    public val NeutralDelta: Color = Color(0xFF8A93A6)
    public val Held: Color = Color(0xFFE8B34B)
    public val Developing: Color = Color(0xFF7FA6C9)
    public val Info: Color = Color(0xFF6FB7FF)
    public val ErrorContainer: Color = Color(0xFF5B4500)
    public val OnErrorContainer: Color = Color(0xFFFFE08A)

    // Light theme inversions (§1.1).
    public val BgLight: Color = Color(0xFFF7F8FA)
    public val SurfaceLight: Color = Color(0xFFFFFFFF)
    public val SurfaceSunkenLight: Color = Color(0xFFEEF1F4)
    public val TextPrimaryLight: Color = Color(0xFF171C23)
    public val TextSecondaryLight: Color = Color(0xFF46525F)
    public val TextTertiaryLight: Color = Color(0xFF4D5966)
    public val OutlineLight: Color = Color(0xFF66717D)
    public val OutlineVariantLight: Color = Color(0xFFB8C1CB)
    public val PrimaryLight: Color = Color(0xFF006B50)
    public val PrimaryContainerLight: Color = Color(0xFFB8F1DB)
    public val OnPrimaryContainerLight: Color = Color(0xFF002117)
    public val SecondaryLight: Color = Color(0xFF405E76)
    public val SecondaryContainerLight: Color = Color(0xFFD2E5F5)
    public val OnSecondaryContainerLight: Color = Color(0xFF0B1E2C)
    public val TertiaryLight: Color = Color(0xFF205D7A)
    public val TertiaryContainerLight: Color = Color(0xFFC9E9FF)
    public val OnTertiaryContainerLight: Color = Color(0xFF001E2C)
    public val HeldLight: Color = Color(0xFF725500)
    public val HeldContainerLight: Color = Color(0xFFFFE08A)
    public val OnHeldContainerLight: Color = Color(0xFF251A00)
    public val DevelopingLight: Color = Color(0xFF365F7D)
    public val InfoLight: Color = Color(0xFF1E5F8A)

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
    staticCompositionLocalOf { darkExtendedColors }

private val darkExtendedColors =
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

private val lightExtendedColors =
    darkExtendedColors.copy(
        held = WloColors.HeldLight,
        developing = WloColors.DevelopingLight,
        info = WloColors.InfoLight,
        textTertiary = WloColors.TextTertiaryLight,
        surfaceRaised = WloColors.SurfaceLight,
        surfaceSunken = WloColors.SurfaceSunkenLight,
    )

internal val WloDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = WloColors.Accent,
        onPrimary = WloColors.SurfaceSunken,
        primaryContainer = WloColors.AccentDim,
        onPrimaryContainer = WloColors.TextPrimary,
        secondary = WloColors.Developing,
        onSecondary = WloColors.SurfaceSunken,
        secondaryContainer = Color(0xFF253C50),
        onSecondaryContainer = Color(0xFFD2E5F5),
        tertiary = WloColors.Info,
        onTertiary = WloColors.SurfaceSunken,
        tertiaryContainer = Color(0xFF194566),
        onTertiaryContainer = Color(0xFFC9E9FF),
        background = WloColors.Bg,
        onBackground = WloColors.TextPrimary,
        surface = WloColors.Surface,
        onSurface = WloColors.TextPrimary,
        surfaceVariant = WloColors.SurfaceRaised,
        onSurfaceVariant = WloColors.TextSecondary,
        surfaceDim = WloColors.SurfaceSunken,
        surfaceBright = WloColors.SurfaceRaised,
        surfaceContainerLowest = WloColors.SurfaceSunken,
        surfaceContainerLow = WloColors.SurfaceSunken,
        surfaceContainer = WloColors.Surface,
        surfaceContainerHigh = WloColors.SurfaceRaised,
        surfaceContainerHighest = WloColors.SurfaceRaised,
        outline = WloColors.Outline,
        outlineVariant = WloColors.OutlineVariant,
        error = WloColors.Held,
        onError = WloColors.SurfaceSunken,
        errorContainer = WloColors.ErrorContainer,
        onErrorContainer = WloColors.OnErrorContainer,
        inverseSurface = WloColors.TextPrimary,
        inverseOnSurface = WloColors.SurfaceSunken,
        inversePrimary = WloColors.AccentDim,
        surfaceTint = WloColors.Accent,
        scrim = Color.Black,
    )

internal val WloLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = WloColors.PrimaryLight,
        onPrimary = Color.White,
        primaryContainer = WloColors.PrimaryContainerLight,
        onPrimaryContainer = WloColors.OnPrimaryContainerLight,
        secondary = WloColors.SecondaryLight,
        onSecondary = Color.White,
        secondaryContainer = WloColors.SecondaryContainerLight,
        onSecondaryContainer = WloColors.OnSecondaryContainerLight,
        tertiary = WloColors.TertiaryLight,
        onTertiary = Color.White,
        tertiaryContainer = WloColors.TertiaryContainerLight,
        onTertiaryContainer = WloColors.OnTertiaryContainerLight,
        background = WloColors.BgLight,
        onBackground = WloColors.TextPrimaryLight,
        surface = WloColors.SurfaceLight,
        onSurface = WloColors.TextPrimaryLight,
        surfaceVariant = WloColors.SurfaceSunkenLight,
        onSurfaceVariant = WloColors.TextSecondaryLight,
        surfaceDim = WloColors.SurfaceSunkenLight,
        surfaceBright = WloColors.SurfaceLight,
        surfaceContainerLowest = WloColors.SurfaceLight,
        surfaceContainerLow = Color(0xFFF2F4F7),
        surfaceContainer = WloColors.BgLight,
        surfaceContainerHigh = WloColors.SurfaceSunkenLight,
        surfaceContainerHighest = Color(0xFFE5E9ED),
        outline = WloColors.OutlineLight,
        outlineVariant = WloColors.OutlineVariantLight,
        // Red does not exist in WLO's theme; `held` amber is the strongest state color.
        error = WloColors.HeldLight,
        onError = Color.White,
        errorContainer = WloColors.HeldContainerLight,
        onErrorContainer = WloColors.OnHeldContainerLight,
        inverseSurface = WloColors.TextPrimaryLight,
        inverseOnSurface = WloColors.BgLight,
        inversePrimary = WloColors.Accent,
        surfaceTint = WloColors.PrimaryLight,
        scrim = Color.Black,
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
        LocalWloExtendedColors provides if (darkTheme) darkExtendedColors else lightExtendedColors,
        LocalWloTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) WloDarkColorScheme else WloLightColorScheme,
            typography = wloMaterialTypography(typography),
            content = content,
        )
    }
}
