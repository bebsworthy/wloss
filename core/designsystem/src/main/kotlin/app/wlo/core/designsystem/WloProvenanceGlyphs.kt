package app.wlo.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Provenance glyphs (DESIGN-SYSTEM.md §7.1 chip anatomy): minimal geometric
 * marks, numbers-first (R-D7 spirit). Rendered through `Icon(tint = ...)` —
 * paints are declared black so the tint carries the state color.
 */
public object WloProvenanceGlyphs {
    /** Paint source for all glyph paths; `Icon(tint = ...)` recolors at render. */
    private val Black: SolidColor = SolidColor(Color.Black)

    /** Direct measurement — a solid dot (the reading is the fact). */
    public val Measured: ImageVector =
        glyph("WloMeasured") {
            path(fill = Black) {
                moveTo(2.4f, 6f)
                arcTo(3.6f, 3.6f, 0f, true, false, 9.6f, 6f)
                arcTo(3.6f, 3.6f, 0f, true, false, 2.4f, 6f)
                close()
            }
        }

    /** Engine output — an outlined circle around a dot (formula wraps inputs). */
    public val Derived: ImageVector =
        glyph("WloDerived") {
            path(
                stroke = Black,
                strokeLineWidth = 1.3f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(2.7f, 6f)
                arcTo(3.3f, 3.3f, 0f, true, false, 9.3f, 6f)
                arcTo(3.3f, 3.3f, 0f, true, false, 2.7f, 6f)
                close()
            }
            path(fill = Black) {
                moveTo(4.8f, 6f)
                arcTo(1.2f, 1.2f, 0f, true, false, 7.2f, 6f)
                arcTo(1.2f, 1.2f, 0f, true, false, 4.8f, 6f)
                close()
            }
        }

    /** Estimate — a wave (the number moves with its inputs). */
    public val Estimated: ImageVector =
        glyph("WloEstimated") {
            path(
                stroke = Black,
                strokeLineWidth = 1.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(1.8f, 7.4f)
                curveTo(3.2f, 4.4f, 4.8f, 4.4f, 6f, 6.1f)
                curveTo(7.2f, 7.8f, 8.8f, 7.8f, 10.2f, 4.9f)
            }
        }

    /** Held — pause bars (weak data waits, it is never guessed). */
    public val Held: ImageVector =
        glyph("WloHeld") {
            path(fill = Black) {
                moveTo(3.3f, 2.8f)
                horizontalLineToRelative(1.7f)
                verticalLineToRelative(6.4f)
                horizontalLineTo(3.3f)
                close()
            }
            path(fill = Black) {
                moveTo(7f, 2.8f)
                horizontalLineToRelative(1.7f)
                verticalLineToRelative(6.4f)
                horizontalLineTo(7f)
                close()
            }
        }

    /** The info mark — the tap-through to "how we got here". */
    public val Info: ImageVector =
        glyph("WloInfo") {
            path(
                stroke = Black,
                strokeLineWidth = 1.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(1.5f, 6f)
                arcTo(4.5f, 4.5f, 0f, true, false, 10.5f, 6f)
                arcTo(4.5f, 4.5f, 0f, true, false, 1.5f, 6f)
                close()
            }
            path(fill = Black) {
                moveTo(5.3f, 3.4f)
                arcTo(0.7f, 0.7f, 0f, true, false, 6.7f, 3.4f)
                arcTo(0.7f, 0.7f, 0f, true, false, 5.3f, 3.4f)
                close()
            }
            path(
                stroke = Black,
                strokeLineWidth = 1.3f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(6f, 5.4f)
                verticalLineTo(8.6f)
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
