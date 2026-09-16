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

/** True only for the five destinations that own persistent app navigation. */
internal fun isTopLevelRoute(route: String?): Boolean = route != null && WLO_TABS.any { it.route == route }

/** Material compact/medium boundary used to choose navigation bar or rail. */
internal fun useNavigationRail(
    width: Dp,
    showTopLevelNavigation: Boolean,
): Boolean = showTopLevelNavigation && width >= 600.dp

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
        "ai/models" -> "On-device models"
        "app/settings" -> "Settings"
        "app/profile" -> "Profile"
        F01Routes.STUDIO -> "Goals"
        F12Routes.STUDIO -> "AI studio"
        F12Routes.RECEIPTS -> "AI receipts"
        F12Routes.CONSENT_SHEET_DEMO -> "Consent preview"
        F13Routes.VAULT -> "Data vault"
        F13Routes.BACKUP -> "Backups"
        F13Routes.RESTORE -> "Restore"
        F13Routes.EXPORT -> "Export"
        F13Routes.IMPORT -> "Import"
        "debug/egress" -> "Egress monitor"
        else -> STUB_TITLES[route] ?: "WLO"
    }
