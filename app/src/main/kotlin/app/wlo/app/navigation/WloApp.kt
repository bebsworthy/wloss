package app.wlo.app.navigation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import app.wlo.app.ui.PlaceholderCopy
import app.wlo.app.ui.PlaceholderScreen
import app.wlo.app.ui.STUB_TITLES
import app.wlo.app.ui.StubScreen
import app.wlo.app.ui.debug.EgressMonitorScreen
import app.wlo.app.ui.shell.ShellState
import app.wlo.app.ui.shell.ShellViewModel
import app.wlo.app.ui.zoo.ZooScreen
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloSpacing
import app.wlo.core.designsystem.rememberWloHaptics
import app.wlo.core.designsystem.wloExtendedColors
import app.wlo.core.designsystem.wloType
import app.wlo.core.media.WloCaptureMode
import app.wlo.core.media.WloShutterBridge
import app.wlo.core.media.WloViewfinder
import app.wlo.feature.f01.onboarding.ui.OnboardingScreen
import app.wlo.feature.f02.food.state.CaptureLensMode
import app.wlo.feature.f02.food.ui.CaptureScreen
import app.wlo.feature.f02.food.ui.DiaryDayScreen
import app.wlo.feature.f02.food.ui.FoodLogScreen
import app.wlo.feature.f06.weight.F06Routes
import app.wlo.feature.f06.weight.ui.BodyFatScreen
import app.wlo.feature.f06.weight.ui.MathDocsScreen
import app.wlo.feature.f06.weight.ui.WeightScreen
import app.wlo.feature.f10.hub.ui.HubActions
import app.wlo.feature.f10.hub.ui.HubScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The WLO shell: five-tab bottom navigation (R-D2) + NavHost, with every
 * `wlo://` deep link in the IA.md §3 registry registered against its route —
 * each resolving to a real screen or a documented stub (WloDeepLinks). Dark is
 * the base scheme (R-D1); the activity forces `darkTheme = true`. While the
 * shell gate is FRESH the Hub route hosts the F01 wizard (first-run is a flow,
 * not a modal gauntlet — IA §5) and the bottom bar stays hidden until a plan
 * exists.
 */
@Composable
public fun WloApp(
    newIntent: Intent?,
    onDestinationChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
    onSurfaceChanged: (String) -> Unit = {},
) {
    val navController: NavHostController = rememberNavController()
    val haptics = rememberWloHaptics()
    val shell: ShellViewModel = koinViewModel<ShellViewModel>()
    val shellState: ShellState by shell.state.collectAsStateWithLifecycle()
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
            if (shellState == ShellState.Onboarded) {
                WloBottomBar(
                    selectedRoute = currentRoute,
                    onSelect = { route ->
                        haptics.perform(WloHaptic.SegmentTick)
                        // Tab switches rebuild the tab root fresh: pop the whole
                        // stack — deep-link-landed destinations included — so a
                        // tab tap ALWAYS lands on the tab, whatever surface the
                        // user was on.
                        navController.navigate(route) {
                            popUpTo(0) { saveState = false }
                            launchSingleTop = true
                            restoreState = false
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = WloTabs.HUB,
            modifier = Modifier.padding(innerPadding),
        ) {
            for ((route, patterns) in WloDeepLinks.patternsByRoute) {
                composable(
                    route = route,
                    deepLinks = patterns.map { pattern -> navDeepLink { uriPattern = pattern } },
                    arguments = routeArguments(route),
                ) { entry ->
                    RouteSurface(
                        route = route,
                        entryArg = entry.arguments?.getString(ENTRY_ARG),
                        shellState = shellState,
                        onSurfaceChanged = onSurfaceChanged,
                        navController = navController,
                    )
                }
            }
            // Internal surfaces without registry URIs (reached from their owners).
            composable(route = F06Routes.MATH) { MathDocsScreen() }
            composable(route = F06Routes.BODY_FAT) { BodyFatScreen(viewModel = koinViewModel()) }
        }
    }
}

/** The optional query arg carried by routes that take one (f02/diary?entry=). */
private const val ENTRY_ARG: String = "entry"

private fun routeArguments(route: String): List<NamedNavArgument> =
    when {
        route.startsWith("f02/diary") ->
            listOf(
                navArgument(ENTRY_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            )
        else -> emptyList()
    }

/** Route → screen. The only place cross-feature routes meet their owners (D2). */
@Composable
private fun RouteSurface(
    route: String,
    entryArg: String?,
    shellState: ShellState,
    onSurfaceChanged: (String) -> Unit,
    navController: NavHostController,
) {
    when (route) {
        WloTabs.HUB ->
            when (shellState) {
                ShellState.Loading -> Box(Modifier.fillMaxSize())
                // Fresh install: the wizard owns the surface — wlo://hub
                // resolves to onboarding until a plan exists.
                ShellState.Fresh -> {
                    onSurfaceChanged("onboarding")
                    OnboardingScreen(viewModel = koinViewModel())
                }

                ShellState.Onboarded -> {
                    onSurfaceChanged("hub")
                    HubScreen(
                        viewModel = koinViewModel(),
                        actions =
                            HubActions(
                                // The diary route carries the optional ?entry=
                                // scaffold; navigate as a URI so the pattern
                                // matcher applies its default.
                                onOpenDiary = { navController.navigate(Uri.parse("wlo://diary")) },
                                onOpenCapture = { navController.navigate("f02/capture") },
                                onQuickAddKcal = { navController.navigate("f02/quick-kcal") },
                                onLogWeight = { navController.navigate("f06/log") },
                                onOpenWeight = { navController.navigate("f06/weight") },
                                onGutLog = { navController.navigate("stub/gut") },
                                onWorkout = { navController.navigate("stub/exercise") },
                            ),
                    )
                }
            }

        WloTabs.PLAN -> PlaceholderScreen(title = "Plan", body = PlaceholderCopy.PLAN)
        WloTabs.INSIGHTS -> PlaceholderScreen(title = "Insights", body = PlaceholderCopy.INSIGHTS)
        WloTabs.ARCHIVE -> PlaceholderScreen(title = "Archive", body = PlaceholderCopy.ARCHIVE)
        WloTabs.DIGESTION -> PlaceholderScreen(title = "Digestion", body = PlaceholderCopy.DIGESTION)

        // The diary destination's route carries the optional ?entry= scaffold.
        "f02/diary?entry={entry}" ->
            DiaryDayScreen(
                viewModel = koinViewModel(parameters = { parametersOf(null, entryArg?.takeIf { it.isNotEmpty() }) }),
            )

        // The F02 capture flow (M4 PART B): the camera stack (:core:media)
        // composes into the flow's viewfinder slot here — the ONLY place the
        // restricted module meets the feature (D1).
        "f02/capture" -> {
            onSurfaceChanged("f02/capture")
            CaptureScreen(
                viewModel = koinViewModel(),
                viewfinder = { viewfinderModifier, lensMode, shutter, onPreview, isActive ->
                    val bridge = remember { WloShutterBridge() }
                    val mediaMode =
                        when (lensMode) {
                            CaptureLensMode.PHOTO -> WloCaptureMode.PHOTO
                            CaptureLensMode.BARCODE -> WloCaptureMode.BARCODE
                            CaptureLensMode.LABEL -> WloCaptureMode.LABEL
                        }
                    WloViewfinder(
                        modifier = viewfinderModifier,
                        mode = mediaMode,
                        shutter = bridge,
                        onPreviewFrame = onPreview,
                        isActive = isActive,
                    )
                    LaunchedEffect(bridge) { shutter.bind { onFrame -> bridge.takeStill(onFrame) } }
                },
                onOpenManualLadder = { navController.navigate("f02/log") },
                onOpenModelManager = { navController.navigate(Uri.parse("wlo://ai/models")) },
                onDone = { navController.popBackStack() },
            )
        }

        // The F12 model manager (R-S14): zoo catalog, download/reclaim.
        "ai/models" -> ZooScreen()

        "f02/log" -> FoodLogScreen(viewModel = koinViewModel(parameters = { parametersOf(false) }))

        "f02/quick-kcal" -> FoodLogScreen(viewModel = koinViewModel(parameters = { parametersOf(true) }))

        "f06/weight" ->
            WeightScreen(
                viewModel = koinViewModel(parameters = { parametersOf(false) }),
                onOpenMath = { navController.navigate(F06Routes.MATH) },
                onOpenBodyFat = { navController.navigate(F06Routes.BODY_FAT) },
            )

        "f06/log" ->
            WeightScreen(
                viewModel = koinViewModel(parameters = { parametersOf(true) }),
                onOpenMath = { navController.navigate(F06Routes.MATH) },
                onOpenBodyFat = { navController.navigate(F06Routes.BODY_FAT) },
            )

        F06Routes.MATH -> MathDocsScreen()
        F06Routes.BODY_FAT -> BodyFatScreen(viewModel = koinViewModel())

        // Debug diagnostics (M4): the egress monitor — debug builds render the
        // persisted receipt ledger; release builds get an honest note instead.
        "debug/egress" -> EgressMonitorScreen()

        else -> StubScreen(title = STUB_TITLES[route] ?: "Coming later")
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
