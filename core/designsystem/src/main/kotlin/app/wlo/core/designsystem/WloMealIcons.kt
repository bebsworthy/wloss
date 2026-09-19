package app.wlo.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Outline glyphs from the accepted Flow 11, rendered through standard M3 Icon slots. */
public object WloMealIcons {
    public val Breakfast: ImageVector =
        glyph(
            "Breakfast",
            "M3 17h18M5 21h14M7 17a5 5 0 0 1 10 0M12 3v3" +
                "M4.2 8.2l2.1 2.1M19.8 8.2l-2.1 2.1",
        )
    public val Lunch: ImageVector =
        glyph(
            "Lunch",
            "M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0M12 2v2M12 20v2M2 12h2M20 12h2" +
                "M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5",
        )
    public val Dinner: ImageVector = glyph("Dinner", "M20.8 13A9 9 0 0 1 11 3.2 9 9 0 1 0 20.8 13Z")
    public val Snacks: ImageVector =
        glyph("Snacks", "M12 7c-7-4-11 3-7 10 2 4 4 4 7 2 3 2 5 2 7-2 4-7 0-14-7-10ZM12 7c0-3 2-5 5-5-1 3-2 4-5 5Z")
    public val Replace: ImageVector = glyph("Replace", "M4 7h15l-4-4M20 17H5l4 4M19 7l-4 4M5 17l4-4")
    public val Remove: ImageVector = glyph("Remove", "M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7")
    public val Recipe: ImageVector = glyph("Recipe", "M3 4h7l2 2 2-2h7v15h-7l-2 2-2-2H3V4ZM12 6v15")
    public val Suggest: ImageVector =
        glyph(
            "Suggest",
            "M10 3l2.5 6.5L19 12l-6.5 2.5L10 21l-2.5-6.5L1 12l6.5-2.5L10 3" +
                "M19 2l1 3 3 1-3 1-1 3-1-3-3-1 3-1 1-3Z",
        )

    private fun glyph(
        name: String,
        path: String,
    ): ImageVector =
        ImageVector
            .Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
}
