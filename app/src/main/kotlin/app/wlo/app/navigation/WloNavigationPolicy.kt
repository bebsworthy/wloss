package app.wlo.app.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.wlo.app.ui.STUB_TITLES
import app.wlo.feature.f01.onboarding.F01Routes
import app.wlo.feature.f03.planning.F03Routes
import app.wlo.feature.f04.shopping.F04Routes
import app.wlo.feature.f06.weight.F06Routes
import app.wlo.feature.f12.consent.F12Routes
import app.wlo.feature.f13.vault.F13Routes

public data class RouteMetadata(
    public val destinationId: String,
    public val label: String,
    public val topLevelOwner: String,
    public val appBarTitle: String,
    public val showsUp: Boolean,
    public val paneTitle: String = appBarTitle,
)

private val TOP_LEVEL_METADATA: Map<String, RouteMetadata> =
    mapOf(
        WloTabs.WEIGHT to RouteMetadata(WloTabs.WEIGHT, "Weight", WloTabs.WEIGHT, "Weight", false),
        WloTabs.HUB to RouteMetadata(WloTabs.HUB, "Hub", WloTabs.HUB, "Hub", false),
        WloTabs.PLAN to RouteMetadata(WloTabs.PLAN, "Plan", WloTabs.PLAN, "Plan", false),
        WloTabs.MORE to RouteMetadata(WloTabs.MORE, "More", WloTabs.MORE, "More", false),
    )

/** True only for destinations that own persistent app navigation. */
internal fun isTopLevelRoute(route: String?): Boolean = route != null && WLO_TABS.any { it.route == route }

/** Material compact/medium boundary used to choose navigation bar or rail. */
internal fun useNavigationRail(
    width: Dp,
    showTopLevelNavigation: Boolean,
): Boolean = showTopLevelNavigation && width >= 600.dp

internal fun useSupportingPane(width: Dp): Boolean = width >= 840.dp

internal fun routeMetadata(route: String?): RouteMetadata? {
    if (route == null) return null
    TOP_LEVEL_METADATA[route]?.let { return it }
    if (route == WloDeepLinks.INSIGHTS_UNAVAILABLE) {
        return RouteMetadata(route, "More", WloTabs.MORE, "More", false)
    }
    val title = routeTitle(route) ?: return null
    val owner =
        when {
            route == F01Routes.PLAN_STUDIO || route.startsWith("f03/") || route.startsWith("f04/") -> WloTabs.PLAN
            route.startsWith("f06/") -> WloTabs.WEIGHT
            else -> WloTabs.MORE
        }
    return RouteMetadata(route, title, owner, title, true)
}

/** User-facing app-bar title for nested routes. */
internal fun routeTitle(route: String?): String? =
    when (route) {
        null -> null
        "f02/diary?entry={entry}" -> "Diary"
        "f02/capture" -> "Capture food"
        "f02/log" -> "Log food"
        "f02/quick-kcal" -> "Quick calories"
        WloTabs.ARCHIVE -> "Archive"
        WloTabs.DIGESTION -> "Digestion"
        F03Routes.FOCUS, F03Routes.TOMORROW, F03Routes.STUDIO -> "Meal plan"
        F03Routes.RECIPE_EDIT -> "Recipe"
        F04Routes.LIST -> "Shopping list"
        F04Routes.PANTRY -> "Pantry"
        "f06/weight", "f06/log" -> "Weight"
        F06Routes.MATH -> "Weight calculation"
        F06Routes.BODY_FAT -> "Body fat"
        F06Routes.LOGBOOK -> "Weight logbook"
        F06Routes.LOGBOOK_RANGE -> "Weight logbook"
        WloDeepLinks.INSIGHTS_UNAVAILABLE -> "More"
        "ai/models" -> "On-device models"
        "app/settings" -> "Settings"
        "app/settings/units" -> "Weight unit"
        "app/settings/reminder" -> "Weigh-in reminder"
        "app/settings/lock" -> "App lock"
        "app/settings/diagnostics" -> "Diagnostics"
        "app/health-connect" -> "Health Connect"
        "f13/storage" -> "Storage"
        F01Routes.PLAN_STUDIO -> "Diet plan"
        "app/profile" -> "Profile"
        F01Routes.STUDIO -> "Goals"
        F12Routes.STUDIO -> "AI"
        F12Routes.RECEIPTS -> "Activity history"
        F12Routes.CONSENT_SHEET_DEMO -> "Consent preview"
        F13Routes.VAULT -> "Data & backup"
        F13Routes.BACKUP -> "Backup"
        F13Routes.RESTORE -> "Restore backup"
        F13Routes.EXPORT -> "Export data"
        F13Routes.IMPORT -> "Import data"
        "debug/egress" -> "Egress monitor"
        else -> STUB_TITLES[route] ?: "WLO"
    }
