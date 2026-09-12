package app.wlo.app.navigation

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import app.wlo.app.ui.PlaceholderCopy
import app.wlo.app.ui.PlaceholderScreen
import app.wlo.app.ui.hub.HubScreen
import app.wlo.app.ui.hub.HubViewModel
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.rememberWloHaptics
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import org.koin.androidx.compose.koinViewModel

/**
 * The WLO shell: five-tab bottom navigation (R-D2) + NavHost, with `wlo://`
 * deep links registered per tab (IA.md §3). Dark is the base scheme (R-D1);
 * the activity forces `darkTheme = true`.
 */
@Composable
public fun WloApp(
    newIntent: Intent?,
    onDestinationChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController: NavHostController = rememberNavController()
    val haptics = rememberWloHaptics()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    DisposableEffect(navController) {
        val listener =
            object : NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(
                    controller: NavController,
                    destination: NavDestination,
                    arguments: Bundle?,
                ) {
                    onDestinationChanged(destination.route)
                }
            }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // singleTask re-delivery: the activity hands us the new intent here.
    LaunchedEffect(newIntent) {
        newIntent?.let(navController::handleDeepLink)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            WloBottomBar(
                selectedRoute = currentRoute,
                onSelect = { route ->
                    haptics.perform(WloHaptic.SegmentTick)
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = WloTabs.HUB,
            modifier = Modifier.padding(innerPadding),
        ) {
            for (tab in WLO_TABS) {
                composable(
                    route = tab.route,
                    deepLinks =
                        WloDeepLinks.patternsByRoute[tab.route].orEmpty().map { pattern ->
                            navDeepLink { uriPattern = pattern }
                        },
                ) {
                    when (tab.route) {
                        WloTabs.HUB -> HubScreen(viewModel = koinViewModel<HubViewModel>())
                        WloTabs.PLAN -> PlaceholderScreen(title = tab.label, body = PlaceholderCopy.PLAN)
                        WloTabs.INSIGHTS -> PlaceholderScreen(title = tab.label, body = PlaceholderCopy.INSIGHTS)
                        WloTabs.ARCHIVE -> PlaceholderScreen(title = tab.label, body = PlaceholderCopy.ARCHIVE)
                        else -> PlaceholderScreen(title = tab.label, body = PlaceholderCopy.DIGESTION)
                    }
                }
            }
        }
    }
}

/**
 * Bottom navigation bar, WLO-styled: hairline top divider, icon + label per
 * tab, active tab in accent (CSS `.navbar`), a detent tick per selection.
 */
@Composable
private fun WloBottomBar(
    selectedRoute: String?,
    onSelect: (String) -> Unit,
): Unit =
    Column {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = WloSpacing.TIGHT, vertical = WloSpacing.TIGHT),
        ) {
            for (tab in WLO_TABS) {
                val isSelected = tab.route == selectedRoute
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .padding(vertical = WloSpacing.TIGHT)
                            .clickable { onSelect(tab.route) }
                            .semantics {
                                role = Role.Tab
                                this.selected = isSelected
                            }.testTag("tab-${tab.route}"),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else wloExtendedColors.textTertiary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = tab.label,
                        style = wloType.label.copy(fontSize = wloType.label.fontSize * 0.92f),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else wloExtendedColors.textTertiary,
                    )
                }
            }
        }
    }
