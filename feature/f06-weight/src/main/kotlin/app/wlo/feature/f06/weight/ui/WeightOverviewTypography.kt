package app.wlo.feature.f06.weight.ui

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import app.wlo.core.designsystem.WloFontFamily
import app.wlo.core.designsystem.WloFontFeatures

/**
 * Page-summary roles measured against the Inter mockup using WLO-0114 glyph overlays.
 * Explicit line heights keep small metadata from inheriting the global 22sp prose leading.
 * Use regular Inter static weights consistently for summary, controls and metadata.
 * These styles retain system font scaling and are passed to standard Material Text slots.
 */
internal object WeightOverviewTypography {
    private val base =
        TextStyle(
            fontFamily = WloFontFamily,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
    val eyebrow = base.copy(fontSize = 14.sp, lineHeight = 20.3.sp)
    val hero =
        base.copy(
            fontSize = 56.sp,
            lineHeight = 66.08.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-2).sp,
            fontFeatureSettings = "'tnum' 0, 'lnum' 1",
        )
    val unit = base.copy(fontSize = 23.sp, lineHeight = 28.sp)
    val date = base.copy(fontSize = 13.sp, lineHeight = 18.85.sp)
    val change =
        base.copy(
            fontSize = 24.sp,
            lineHeight = 34.8.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.5).sp,
            fontFeatureSettings = WloFontFeatures.TABULAR,
        )
    val goal = base.copy(fontSize = 17.sp, lineHeight = 24.65.sp, fontWeight = FontWeight.Medium)
    val supporting = base.copy(fontSize = 12.sp, lineHeight = 17.4.sp)
    val history = base.copy(fontSize = 15.sp, lineHeight = 21.75.sp)
    val control = base.copy(fontSize = 14.sp, lineHeight = 20.3.sp, fontWeight = FontWeight.SemiBold)
    val action = base.copy(fontSize = 15.sp, lineHeight = 21.75.sp, fontWeight = FontWeight.SemiBold)
}
