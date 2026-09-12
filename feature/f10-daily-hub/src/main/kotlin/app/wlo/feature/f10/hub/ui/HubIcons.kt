package app.wlo.feature.f10.hub.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Quick-action rail glyphs (F10 §5: photo · weigh · gut · workout), hairline
 * strokes matching the WLO 1 dp outline language. The gut glyph is the neutral
 * leaf (F09); the workout glyph is a barbell, numbers-first.
 */
public object HubIcons {
    private const val VIEWPORT: Float = 24f
    private val Stroke: SolidColor = SolidColor(Color.Black)

    private fun builder(name: String): ImageVector.Builder =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        )

    private fun stroke(build: PathBuilder.() -> Unit): ImageVector =
        builder("hub-${build.hashCode()}")
            .path(
                stroke = Stroke,
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                build()
            }.build()

    /** Photo — the shutter body with a lens ring. */
    public val Photo: ImageVector =
        stroke {
            moveTo(4.5f, 8.5f)
            horizontalLineTo(8f)
            lineTo(9.8f, 6.2f)
            horizontalLineTo(14.2f)
            lineTo(16f, 8.5f)
            horizontalLineTo(19.5f)
            arcTo(1.4f, 1.4f, 0f, true, true, 20.9f, 9.9f)
            verticalLineTo(17f)
            arcTo(1.4f, 1.4f, 0f, true, true, 19.5f, 18.4f)
            horizontalLineTo(4.5f)
            arcTo(1.4f, 1.4f, 0f, true, true, 3.1f, 17f)
            verticalLineTo(9.9f)
            arcTo(1.4f, 1.4f, 0f, true, true, 4.5f, 8.5f)
            close()
            moveTo(12f, 15.4f)
            arcTo(2.6f, 2.6f, 0f, true, false, 12f, 10.2f)
            arcTo(2.6f, 2.6f, 0f, true, false, 12f, 15.4f)
            close()
        }

    /** Weigh — a gauge: arc + needle (trend-first, not a scale plate). */
    public val Weigh: ImageVector =
        stroke {
            moveTo(4f, 15.5f)
            arcTo(8f, 8f, 0f, true, true, 20f, 15.5f)
            moveTo(12f, 15.5f)
            lineTo(15.2f, 9.8f)
            moveTo(7.4f, 19f)
            horizontalLineTo(16.6f)
        }

    /** Gut — the neutral leaf (F09: leaf/wave, no emoji). */
    public val Gut: ImageVector =
        stroke {
            moveTo(19.5f, 4.5f)
            curveTo(11f, 4.5f, 5.5f, 10f, 4.8f, 19.2f)
            curveTo(13.5f, 18.8f, 19.2f, 13.2f, 19.5f, 4.5f)
            close()
            moveTo(6.2f, 17.8f)
            curveTo(9.5f, 13.5f, 13.5f, 9.5f, 18f, 6f)
        }

    /** Workout — a barbell, seen head-on. */
    public val Workout: ImageVector =
        stroke {
            moveTo(3.5f, 9f)
            verticalLineTo(15f)
            moveTo(6.5f, 7.5f)
            verticalLineTo(16.5f)
            moveTo(17.5f, 7.5f)
            verticalLineTo(16.5f)
            moveTo(20.5f, 9f)
            verticalLineTo(15f)
            moveTo(6.5f, 12f)
            horizontalLineTo(17.5f)
        }
}
