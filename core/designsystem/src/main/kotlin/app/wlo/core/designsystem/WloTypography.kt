package app.wlo.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * OpenType feature settings for numeric text (R-D3): tabular figures are
 * mandatory wherever numbers change or align; proportional figures only in
 * running prose. `lnum` is always on in stats contexts (no oldstyle figures);
 * `zero` (slashed zero) where 0/O confusion matters (weight/log contexts);
 * `frac` in recipe/serving contexts (DESIGN-SYSTEM.md §2).
 */
public object WloFontFeatures {
    /** `tnum` + `lnum` — the default for every numeric style. */
    public const val TABULAR: String = "'tnum' 1, 'lnum' 1"

    /** [TABULAR] plus slashed zero — weight/log contexts. */
    public const val TABULAR_SLASHED_ZERO: String = "'tnum' 1, 'lnum' 1, 'zero' 1"

    /** [TABULAR] plus fractions — recipe/serving contexts. */
    public const val TABULAR_FRACTIONS: String = "'tnum' 1, 'lnum' 1, 'frac' 1"
}

/** Inter, the single WLO family (R-D3). Static instances, OFL-1.1 (see OFL.txt). */
public val WloFontFamily: FontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

/** Display optical size (`opsz` display) for hero numerals — DESIGN-SYSTEM.md §2. */
public val WloDisplayFontFamily: FontFamily =
    FontFamily(
        Font(R.font.inter_display_semibold, FontWeight.SemiBold),
    )

/**
 * WLO's type ramp (DESIGN-SYSTEM.md §2): the number is the hero, hierarchy
 * comes from size, weight, and tabular width — never from a second typeface.
 * All numeric styles carry `tnum` via [WloFontFeatures]. Colors are left
 * unspecified so styles inherit `LocalContentColor` / theme roles.
 */
public data class WloTypography(
    /** 56 sp, wght 600, display opsz, `tnum` — one per screen max. */
    val hero: TextStyle,
    /** 28 sp, wght 600, `tnum` — card-level stat (ring center, measured burn). */
    val statL: TextStyle,
    /** 20 sp, `tnum` — row-level stat (trend rows, deltas). */
    val statM: TextStyle,
    /** 15 sp, wght 500, `tnum` — dense stat lines, table cells. */
    val statS: TextStyle,
    /** 13 sp, wght 500, `tnum`, secondary color by convention — receipt lines. */
    val receipt: TextStyle,
    /**
     * 13 sp, wght 400, `text-tertiary` by convention — captions, helper and
     * explainer lines. The prose complement of [receipt] (which is the tabular
     * number line): never copy a `fontSize` from another slot to reach this.
     */
    val caption: TextStyle,
    /** 17 sp, wght 600 — card titles, section headings. */
    val title: TextStyle,
    /** 24 sp, wght 600 — the screen-title slot: one per screen. */
    val titleL: TextStyle,
    /** 15 sp, wght 400 — prose, explainers. */
    val body: TextStyle,
    /** 11 sp, wght 500, +2% tracking, sentence case — chips, axis labels. */
    val label: TextStyle,
)

/**
 * The WLO ramp per DESIGN-SYSTEM.md §2 (weights snapped to static Inter
 * instances). Audit vs §2 (WLO-0030): hero is Inter Display (`opsz` display)
 * at 56 sp w600 `tnum`; `stat-l` 28 sp w600; `stat-m` 20 sp — spec asks w550,
 * snapped to SemiBold (no 550 static instance, documented deviation); `label`
 * 11 sp w500 +2% tracking. WLO-0031 added the two highest-demand missing
 * slots: [WloTypography.titleL] (24 sp screen titles — replaces the
 * `title.fontSize * 1.5f` arithmetic) and [WloTypography.caption] (13 sp
 * helper lines — replaces `body.copy(receipt.fontSize)`).
 */
public fun wloTypography(): WloTypography =
    WloTypography(
        hero =
            TextStyle(
                fontFamily = WloDisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 56.sp,
                lineHeight = (1.02f).em,
                letterSpacing = (-0.03f).em,
                fontFeatureSettings = WloFontFeatures.TABULAR,
            ),
        statL =
            TextStyle(
                fontFamily = WloFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                letterSpacing = (-0.01f).em,
                fontFeatureSettings = WloFontFeatures.TABULAR,
            ),
        statM =
            TextStyle(
                fontFamily = WloFontFamily,
                // Spec asks wght 550; static Inter has no 550 instance —
                // SemiBold is the documented nearest snap (DESIGN-SYSTEM §2).
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                fontFeatureSettings = WloFontFeatures.TABULAR,
            ),
        statS =
            TextStyle(
                fontFamily = WloFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                fontFeatureSettings = WloFontFeatures.TABULAR,
            ),
        receipt =
            TextStyle(
                fontFamily = WloFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                fontFeatureSettings = WloFontFeatures.TABULAR,
            ),
        caption =
            TextStyle(
                fontFamily = WloFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            ),
        title =
            TextStyle(
                fontFamily = WloFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                letterSpacing = (-0.01f).em,
            ),
        titleL =
            TextStyle(
                fontFamily = WloFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                letterSpacing = (-0.01f).em,
            ),
        body =
            TextStyle(
                fontFamily = WloFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        label =
            TextStyle(
                fontFamily = WloFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = (0.02f).em,
            ),
    )

/**
 * Maps the WLO ramp onto Material 3's slots so framework components
 * (nav bars, buttons, sheets) speak Inter too.
 */
public fun wloMaterialTypography(wlo: WloTypography = wloTypography()): Typography =
    Typography(
        displayLarge = wlo.hero,
        displayMedium = wlo.statL,
        displaySmall = wlo.statM,
        headlineLarge = wlo.statL,
        headlineMedium = wlo.statL,
        headlineSmall = wlo.statM,
        titleLarge = wlo.titleL,
        titleMedium = wlo.title,
        titleSmall = wlo.title.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
        bodyLarge = wlo.body,
        bodyMedium = wlo.body.copy(fontSize = 14.sp),
        bodySmall = wlo.body.copy(fontSize = 12.sp),
        labelLarge = wlo.body.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = wlo.label,
        labelSmall = wlo.label.copy(fontSize = 10.5.sp),
    )

public val LocalWloTypography: ProvidableCompositionLocal<WloTypography> =
    staticCompositionLocalOf { wloTypography() }

/** Convenience accessor. */
public val wloType: WloTypography
    @Composable get() = LocalWloTypography.current
