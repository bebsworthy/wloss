package app.wlo.app.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The five-tab bottom navigation set (R-D2 / IA.md §1): Hub · Plan · Insights
 * · Archive · Digestion. Capture is a flow-over-context, never a tab; weight
 * and body numbers are reached from the Hub.
 */
public object WloTabs {
    public const val HUB: String = "hub"
    public const val PLAN: String = "plan"
    public const val INSIGHTS: String = "insights"
    public const val ARCHIVE: String = "archive"
    public const val DIGESTION: String = "digestion"
}

/** One bottom-bar destination: route, user label, glyph. */
public data class WloTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** The tab list in navigation order (R-D2). */
public val WLO_TABS: List<WloTab> =
    listOf(
        WloTab(WloTabs.HUB, "Hub", WloTabIcons.Hub),
        WloTab(WloTabs.PLAN, "Plan", WloTabIcons.Plan),
        WloTab(WloTabs.INSIGHTS, "Insights", WloTabIcons.Insights),
        WloTab(WloTabs.ARCHIVE, "Archive", WloTabIcons.Archive),
        WloTab(WloTabs.DIGESTION, "Digestion", WloTabIcons.Digestion),
    )

/**
 * Tab glyphs: hairline strokes matching WLO's 1 dp outline language. Archive
 * is the R-D9 abstract stacked-records mark — deliberately not a camera or
 * body glyph. Digestion is the neutral leaf (F09: leaf/wave, no emoji).
 */
public object WloTabIcons {
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

    private fun ImageVector.Builder.stroke(build: PathBuilder.() -> Unit): ImageVector.Builder =
        path(
            stroke = Stroke,
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            build()
        }

    /** Hub — the home marker, open gable so density stays light. */
    public val Hub: ImageVector =
        builder("WloTabHub")
            .stroke {
                moveTo(4f, 11f)
                lineTo(12f, 4.5f)
                lineTo(20f, 11f)
                moveTo(6.2f, 9.4f)
                verticalLineTo(19f)
                horizontalLineTo(17.8f)
                verticalLineTo(9.4f)
            }.build()

    /** Plan — the week grid: a calendar sheet with pinned header. */
    public val Plan: ImageVector =
        builder("WloTabPlan")
            .stroke {
                moveTo(4.5f, 7.5f)
                arcTo(2f, 2f, 0f, true, true, 6.5f, 5.5f)
                horizontalLineTo(17.5f)
                arcTo(2f, 2f, 0f, true, true, 19.5f, 7.5f)
                verticalLineTo(17.5f)
                arcTo(2f, 2f, 0f, true, true, 17.5f, 19.5f)
                horizontalLineTo(6.5f)
                arcTo(2f, 2f, 0f, true, true, 4.5f, 17.5f)
                close()
                moveTo(4.5f, 10f)
                horizontalLineTo(19.5f)
                moveTo(8.5f, 3.5f)
                verticalLineTo(7f)
                moveTo(15.5f, 3.5f)
                verticalLineTo(7f)
            }.build()

    /** Insights — three ascending marks, numbers-first (R-D7). */
    public val Insights: ImageVector =
        builder("WloTabInsights")
            .stroke {
                moveTo(6.5f, 19f)
                verticalLineTo(13.5f)
                moveTo(12f, 19f)
                verticalLineTo(8f)
                moveTo(17.5f, 19f)
                verticalLineTo(11f)
            }.build()

    /** Archive — R-D9 stacked records: two offset outlines, never a camera. */
    public val Archive: ImageVector =
        builder("WloTabArchive")
            .apply {
                path(
                    stroke = Stroke,
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    strokeAlpha = 0.45f,
                ) {
                    moveTo(6.5f, 5f)
                    horizontalLineTo(17.5f)
                    arcTo(1.8f, 1.8f, 0f, true, true, 19.3f, 6.8f)
                    verticalLineTo(10.2f)
                    horizontalLineTo(4.7f)
                    verticalLineTo(6.8f)
                    arcTo(1.8f, 1.8f, 0f, true, true, 6.5f, 5f)
                    close()
                }
            }.stroke {
                moveTo(6.5f, 10.2f)
                horizontalLineTo(17.5f)
                arcTo(1.8f, 1.8f, 0f, true, true, 19.3f, 12f)
                verticalLineTo(17.2f)
                arcTo(1.8f, 1.8f, 0f, true, true, 17.5f, 19f)
                horizontalLineTo(6.5f)
                arcTo(1.8f, 1.8f, 0f, true, true, 4.7f, 17.2f)
                verticalLineTo(12f)
                arcTo(1.8f, 1.8f, 0f, true, true, 6.5f, 10.2f)
                close()
            }.build()

    /** Digestion — the neutral leaf (F09), a single stroke and vein. */
    public val Digestion: ImageVector =
        builder("WloTabDigestion")
            .stroke {
                moveTo(19.5f, 4.5f)
                curveTo(11f, 4.5f, 5.5f, 10f, 4.8f, 19.2f)
                curveTo(13.5f, 18.8f, 19.2f, 13.2f, 19.5f, 4.5f)
                close()
                moveTo(6.2f, 17.8f)
                curveTo(9.5f, 13.5f, 13.5f, 9.5f, 18f, 6f)
            }.build()
}
