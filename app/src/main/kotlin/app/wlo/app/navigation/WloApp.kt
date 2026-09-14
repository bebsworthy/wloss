package app.wlo.app.navigation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import app.wlo.app.ui.settings.SettingsScreen
import app.wlo.app.ui.shell.ShellState
import app.wlo.app.ui.shell.ShellViewModel
import app.wlo.app.ui.zoo.ZooScreen
import app.wlo.core.designsystem.WloBottomBar
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloTabItem
import app.wlo.core.designsystem.rememberWloHaptics
import app.wlo.core.media.WloCaptureMode
import app.wlo.core.media.WloShutterBridge
import app.wlo.core.media.WloViewfinder
import app.wlo.feature.f01.onboarding.ui.OnboardingScreen
import app.wlo.feature.f02.food.state.CaptureLensMode
import app.wlo.feature.f02.food.ui.CaptureScreen
import app.wlo.feature.f02.food.ui.DiaryDayScreen
import app.wlo.feature.f02.food.ui.FoodLogScreen
import app.wlo.feature.f03.planning.F03Routes
import app.wlo.feature.f03.planning.ui.PlanScreen
import app.wlo.feature.f03.planning.ui.PlanSegment
import app.wlo.feature.f03.planning.ui.RecipeEditScreen
import app.wlo.feature.f04.shopping.F04Routes
import app.wlo.feature.f04.shopping.ui.ListScreen
import app.wlo.feature.f04.shopping.ui.PantryScreen
import app.wlo.feature.f06.weight.F06Routes
import app.wlo.feature.f06.weight.ui.BodyFatScreen
import app.wlo.feature.f06.weight.ui.MathDocsScreen
import app.wlo.feature.f06.weight.ui.WeightScreen
import app.wlo.feature.f10.hub.ui.HubActions
import app.wlo.feature.f10.hub.ui.HubScreen
import app.wlo.feature.f12.consent.F12Routes
import app.wlo.feature.f12.consent.ui.AiStudioScreen
import app.wlo.feature.f12.consent.ui.ConsentSheetDemoScreen
import app.wlo.feature.f12.consent.ui.ReceiptsScreen
import app.wlo.feature.f13.vault.F13Routes
import app.wlo.feature.f13.vault.ui.BackupControlsScreen
import app.wlo.feature.f13.vault.ui.ExportScreen
import app.wlo.feature.f13.vault.ui.ImportScreen
import app.wlo.feature.f13.vault.ui.RestoreWizardScreen
import app.wlo.feature.f13.vault.ui.VaultDashboardScreen
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
                    selected = currentRoute.orEmpty(),
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
                    items =
                        WLO_TABS.map { tab ->
                            WloTabItem(route = tab.route, icon = tab.icon, label = tab.label)
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
                        arguments = entry.arguments,
                        shellState = shellState,
                        onSurfaceChanged = onSurfaceChanged,
                        navController = navController,
                    )
                }
            }
            // Internal surfaces without registry URIs (reached from their owners).
            composable(route = F06Routes.MATH) { MathDocsScreen() }
            composable(route = F06Routes.BODY_FAT) { BodyFatScreen(viewModel = koinViewModel()) }
            // M6 internal surfaces: Settings' children (the F12 consent demo)
            // and the F13 vault wizards have no wlo:// URIs of their own — they
            // are visited deliberately from their parent surfaces (IA §6). The
            // studio/receipts/settings/vault routes themselves are NOT listed
            // here: they carry registry deep links (wlo://ai/studio,
            // wlo://ai/receipts, wlo://settings, wlo://vault) via the loop
            // above, and a duplicate composable would overwrite those
            // pattern-carrying destinations.
            composable(route = F12Routes.CONSENT_SHEET_DEMO) {
                RouteSurface(
                    route = F12Routes.CONSENT_SHEET_DEMO,
                    arguments = null,
                    shellState = shellState,
                    onSurfaceChanged = onSurfaceChanged,
                    navController = navController,
                )
            }
            composable(route = F13Routes.BACKUP) {
                RouteSurface(
                    route = F13Routes.BACKUP,
                    arguments = null,
                    shellState = shellState,
                    onSurfaceChanged = onSurfaceChanged,
                    navController = navController,
                )
            }
            composable(route = F13Routes.RESTORE) {
                RouteSurface(
                    route = F13Routes.RESTORE,
                    arguments = null,
                    shellState = shellState,
                    onSurfaceChanged = onSurfaceChanged,
                    navController = navController,
                )
            }
            composable(route = F13Routes.EXPORT) {
                RouteSurface(
                    route = F13Routes.EXPORT,
                    arguments = null,
                    shellState = shellState,
                    onSurfaceChanged = onSurfaceChanged,
                    navController = navController,
                )
            }
            composable(route = F13Routes.IMPORT) {
                RouteSurface(
                    route = F13Routes.IMPORT,
                    arguments = null,
                    shellState = shellState,
                    onSurfaceChanged = onSurfaceChanged,
                    navController = navController,
                )
            }
            composable(
                route = F03Routes.RECIPE_EDIT,
                arguments = routeArguments(F03Routes.RECIPE_EDIT),
            ) { entry ->
                RecipeEditScreen(
                    viewModel = koinViewModel(parameters = { parametersOf(null, null) }),
                    recipeId =
                        entry.arguments
                            ?.getString(F03Routes.ARG_RECIPE_ID)
                            ?.takeIf { it.isNotEmpty() && it != "new" },
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

/** The optional query args carried by routes that take them (f02/diary?entry=, f03 focus args). */
private const val ENTRY_ARG: String = "entry"
private const val DAY_ARG: String = "day"
private const val SLOT_ARG: String = "slot"
private const val PROPOSAL_ARG: String = "proposal"
private const val RECIPE_ID_ARG: String = "recipeId"

private fun routeArguments(route: String): List<NamedNavArgument> =
    when {
        route.startsWith("f02/diary") ->
            listOf(
                navArgument(ENTRY_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            )

        route.startsWith("f03/plan/focus") ->
            listOf(
                navArgument(DAY_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument(SLOT_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            )

        route.startsWith("f03/studio") ->
            listOf(
                navArgument(PROPOSAL_ARG) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            )

        route.startsWith("f03/recipe/edit") ->
            listOf(
                navArgument(RECIPE_ID_ARG) {
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
    arguments: Bundle?,
    shellState: ShellState,
    onSurfaceChanged: (String) -> Unit,
    navController: NavHostController,
) {
    val entryArg: String? = arguments?.getString(ENTRY_ARG)
    val entryArgSecond: String? = arguments?.getString(SLOT_ARG)
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
                                onOpenPlan = { navController.navigate(WloTabs.PLAN) },
                                onPlanTomorrow = { navController.navigate(F03Routes.TOMORROW) },
                                // The meals row's plan focus: the registry maps
                                // wlo://log/planned?slot= onto f03/plan/focus
                                // (day defaults to today).
                                onOpenMealSlot = { slotId ->
                                    navController.navigate(Uri.parse("wlo://log/planned?slot=$slotId"))
                                },
                                onOpenSettings = { navController.navigate("app/settings") },
                            ),
                    )
                }
            }

        WloTabs.PLAN -> PlanTabRoute(focusDay = null, focusSlot = null, navController = navController)
        WloTabs.INSIGHTS -> PlaceholderScreen(title = "Insights", body = PlaceholderCopy.INSIGHTS)
        WloTabs.ARCHIVE -> PlaceholderScreen(title = "Archive", body = PlaceholderCopy.ARCHIVE)
        WloTabs.DIGESTION -> PlaceholderScreen(title = "Digestion", body = PlaceholderCopy.DIGESTION)

        // F03 focus routes: the touched slot for today (wlo://log/planned) and
        // the evening "plan tomorrow" card (wlo://plan/tomorrow).
        F03Routes.FOCUS ->
            PlanTabRoute(
                focusDay = entryArg?.toLongOrNull(),
                focusSlot = entryArgSecond,
                navController = navController,
            )

        F03Routes.TOMORROW ->
            PlanTabRoute(
                focusDay = tomorrowEpochDay(),
                focusSlot = null,
                navController = navController,
            )

        // The plan-studio landing keeps the F01 proposal arg passthrough (the
        // diff UI itself is F01's); the planner is the surface it lands on.
        F03Routes.STUDIO -> PlanTabRoute(focusDay = null, focusSlot = null, navController = navController)

        // F04's pipeline surfaces (the Plan tab's List/Pantry segments).
        F04Routes.LIST -> ListScreen(viewModel = koinViewModel())
        F04Routes.PANTRY -> PantryScreen(viewModel = koinViewModel())

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

        // --- M6 PART B: the F12 consent shell + the F13 vault surfaces ------
        "app/settings" ->
            SettingsScreen(
                onOpenAiStudio = { navController.navigate(F12Routes.STUDIO) },
                onOpenVault = { navController.navigate(Uri.parse("wlo://vault")) },
            )

        F12Routes.STUDIO ->
            AiStudioScreen(
                viewModel = koinViewModel(),
                onOpenReceipts = { navController.navigate(F12Routes.RECEIPTS) },
                onOpenModelManager = { navController.navigate(Uri.parse("wlo://ai/models")) },
                onOpenConsentDemo = { navController.navigate(F12Routes.CONSENT_SHEET_DEMO) },
            )

        F12Routes.RECEIPTS -> ReceiptsScreen(viewModel = koinViewModel())

        F12Routes.CONSENT_SHEET_DEMO -> ConsentSheetDemoScreen()

        F13Routes.VAULT ->
            VaultDashboardScreen(
                viewModel = koinViewModel(),
                onOpenBackup = { navController.navigate(F13Routes.BACKUP) },
                onOpenRestore = { navController.navigate(F13Routes.RESTORE) },
                onOpenExport = { navController.navigate(F13Routes.EXPORT) },
                onOpenImport = { navController.navigate(F13Routes.IMPORT) },
            )

        F13Routes.BACKUP -> BackupControlsScreen(viewModel = koinViewModel())

        F13Routes.RESTORE ->
            RestoreWizardScreen(
                viewModel = koinViewModel(),
                onDone = { navController.popBackStack() },
            )

        F13Routes.EXPORT ->
            ExportScreen(
                viewModel = koinViewModel(),
                onDone = { navController.popBackStack() },
            )

        F13Routes.IMPORT ->
            ImportScreen(
                viewModel = koinViewModel(),
                onDone = { navController.popBackStack() },
            )

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
 * The Plan tab host (M5): owns the IA §1 segmented pipeline state (Plan ·
 * Recipes · List · Pantry) and wires the segment exits onto the :app nav
 * graph (D2 — the features never see each other). [focusDay]/[focusSlot]
 * carry the deep-link context in (wlo://log/planned, wlo://plan/tomorrow).
 */
@Composable
private fun PlanTabRoute(
    focusDay: Long?,
    focusSlot: String?,
    navController: NavHostController,
) {
    val viewModel: app.wlo.feature.f03.planning.state.PlanViewModel =
        koinViewModel(parameters = { parametersOf(focusDay, focusSlot) })
    var segment by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(PlanSegment.PLAN)
    }
    PlanScreen(
        viewModel = viewModel,
        segment = segment,
        onSegmentSelect = { next -> segment = next },
        onOpenList = { navController.navigate(F04Routes.LIST) },
        onOpenPantry = { navController.navigate(F04Routes.PANTRY) },
        onEditRecipe = { recipeId ->
            // Navigation's route matcher rejects empty query values; "new" is
            // the sentinel for the create path.
            navController.navigate("f03/recipe/edit?recipeId=${recipeId ?: "new"}")
        },
    )
}

/** Tomorrow in the device zone — the plan-tomorrow deep link's focus day. */
@Composable
private fun tomorrowEpochDay(): Long {
    val koin =
        org.koin.core.context.GlobalContext
            .get()
    val clock: app.wlo.core.common.ClockPort = remember { koin.get() }
    return remember {
        app.wlo.core.common
            .DayBoundary
            .epochDay(clock.now(), kotlinx.datetime.TimeZone.currentSystemDefault()) + 1
    }
}
