package app.wlo.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Shared chrome glyphs for WLO atoms. The module carries no icon-font
 * dependency — marks are hand-built vectors (same idiom as
 * [WloProvenanceGlyphs]) painted black so `Icon(tint = ...)` recolors them.
 */
public object WloIcons {
    /** Paint source for all glyph paths; `Icon(tint = ...)` recolors at render. */
    private val Black: SolidColor = SolidColor(Color.Black)

    /** Forward affordance for navigable rows ([WloListRow] chevron). */
    public val ChevronRight: ImageVector =
        glyph("WloChevronRight") {
            path(
                stroke = Black,
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.2f, 2.6f)
                lineTo(7.6f, 6f)
                lineTo(4.2f, 9.4f)
            }
        }

    private inline fun glyph(
        name: String,
        builder: ImageVector.Builder.() -> ImageVector.Builder,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 12.dp,
                defaultHeight = 12.dp,
                viewportWidth = 12f,
                viewportHeight = 12f,
            ).builder()
            .build()
}
