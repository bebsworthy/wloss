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
 *
 * Two hairline weights, both round-capped and round-joined: the 12-viewport
 * micro-glyphs (1.4 stroke — [ChevronRight]) and the 24-viewport action marks
 * (1.8–2 stroke — the stepper/close/check/search/scan/flame set). The
 * 24-viewport geometry is ported verbatim from the in-feature glyphs these
 * replaced (f01's wizard steppers, f02's capture marks), so existing call
 * sites render pixel-identical.
 */
public object WloIcons {
    /** Paint source for all glyph paths; `Icon(tint = ...)` recolors at render. */
    private val Black: SolidColor = SolidColor(Color.Black)

    /** Chart settings: two adjustment rails, distinct from an overflow menu. */
    public val Tune: ImageVector =
        mark("WloTune") {
            path(stroke = Black, strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
                moveTo(3f, 7f)
                lineTo(6f, 7f)
                moveTo(12f, 7f)
                lineTo(21f, 7f)
                moveTo(3f, 17f)
                lineTo(12f, 17f)
                moveTo(18f, 17f)
                lineTo(21f, 17f)
                moveTo(12f, 7f)
                arcTo(3f, 3f, 0f, true, true, 6f, 7f)
                arcTo(3f, 3f, 0f, true, true, 12f, 7f)
                moveTo(18f, 17f)
                arcTo(3f, 3f, 0f, true, true, 12f, 17f)
                arcTo(3f, 3f, 0f, true, true, 18f, 17f)
            }
        }

    /** Standard vertical overflow menu affordance. */
    public val MoreVertical: ImageVector =
        mark("WloMoreVertical") {
            path(fill = Black) {
                moveTo(12f, 5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 8f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 5f)
                close()
                moveTo(12f, 10.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 13.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 10.5f)
                close()
                moveTo(12f, 16f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 19f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12f, 16f)
                close()
            }
        }

    /** Standard back/up affordance for nested app-bar navigation. */
    public val ArrowBack: ImageVector =
        mark("WloArrowBack") {
            path(
                stroke = Black,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19f, 12f)
                horizontalLineTo(5f)
                moveTo(11f, 6f)
                lineTo(5f, 12f)
                lineTo(11f, 18f)
            }
        }

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

    /** Stepper decrease (port of f01's wizard mark). */
    public val Minus: ImageVector =
        mark("WloMinus") {
            path(
                stroke = Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }

    /** Stepper increase (port of f01's wizard mark). */
    public val Plus: ImageVector =
        mark("WloPlus") {
            path(
                stroke = Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 5f)
                lineTo(12f, 19f)
                moveTo(5f, 12f)
                lineTo(19f, 12f)
            }
        }

    /** Remove / dismiss (port of f01's wizard mark). */
    public val Close: ImageVector =
        mark("WloClose") {
            path(
                stroke = Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }

    /** Affirmation / done ("I read the label" verifications, save confirmations). */
    public val Check: ImageVector =
        mark("WloCheck") {
            path(
                stroke = Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12.5f)
                lineTo(10f, 17.5f)
                lineTo(19f, 6.5f)
            }
        }

    /** Catalog lookup: the magnifier (port of f02's capture mark). */
    public val Search: ImageVector =
        mark("WloSearch") {
            path(
                stroke = Black,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 10.5f)
                arcTo(6f, 6f, 0f, true, true, 16.5f, 10.5f)
                arcTo(6f, 6f, 0f, true, true, 4.5f, 10.5f)
                close()
                moveTo(15.2f, 15.2f)
                lineTo(19.5f, 19.5f)
            }
        }

    /** The shutter: viewfinder brackets around the lens circle (port of f02's capture mark). */
    public val Scan: ImageVector =
        mark("WloScan") {
            path(
                stroke = Black,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.5f, 4f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, true, true, 4f, 6f)
                verticalLineTo(8.5f)
                moveTo(15.5f, 4f)
                horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, true, true, 20f, 6f)
                verticalLineTo(8.5f)
                moveTo(20f, 15.5f)
                verticalLineTo(18f)
                arcTo(2f, 2f, 0f, true, true, 18f, 20f)
                horizontalLineTo(15.5f)
                moveTo(8.5f, 20f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, true, true, 4f, 18f)
                verticalLineTo(15.5f)
            }
            path(
                stroke = Black,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.8f, 12f)
                arcTo(3.2f, 3.2f, 0f, true, true, 15.2f, 12f)
                arcTo(3.2f, 3.2f, 0f, true, true, 8.8f, 12f)
                close()
            }
        }

    /** Streak flame (Hub streak chip): a lick over the bowl, round-joined so
     * the silhouette stays readable at 14–16 dp. Geometry follows the Lucide
     * flame mark (ISC), the proven small-size flame. */
    public val Flame: ImageVector =
        mark("WloFlame") {
            path(
                stroke = Black,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.5f, 14.5f)
                arcTo(2.5f, 2.5f, 0f, false, false, 11f, 12f)
                curveToRelative(0f, -1.38f, -0.5f, -2f, -1f, -3f)
                curveToRelative(-1.072f, -2.143f, -0.224f, -4.054f, 2f, -6f)
                curveToRelative(0.5f, 2.5f, 2f, 4.9f, 4f, 6.5f)
                curveToRelative(2f, 1.6f, 3f, 3.5f, 3f, 5.5f)
                arcToRelative(7f, 7f, 0f, true, true, -14f, 0f)
                curveToRelative(0f, -1.153f, 0.433f, -2.294f, 1f, -3f)
                arcToRelative(2.5f, 2.5f, 0f, false, false, 2.5f, 2.5f)
                close()
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

    private inline fun mark(
        name: String,
        builder: ImageVector.Builder.() -> ImageVector.Builder,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).builder()
            .build()
}
