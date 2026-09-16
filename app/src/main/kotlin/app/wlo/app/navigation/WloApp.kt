package app.wlo.app.navigation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import app.wlo.app.ui.more.MoreScreen
import app.wlo.app.ui.settings.ProfileFactsScreen
import app.wlo.app.ui.settings.SettingsScreen
import app.wlo.app.ui.shell.ShellState
import app.wlo.app.ui.shell.ShellViewModel
import app.wlo.app.ui.zoo.ZooScreen
import app.wlo.core.designsystem.WloBottomBar
import app.wlo.core.designsystem.WloHaptic
import app.wlo.core.designsystem.WloIcons
import app.wlo.core.designsystem.WloNavigationRail
import app.wlo.core.designsystem.WloTabItem
import app.wlo.core.designsystem.rememberWloHaptics
import app.wlo.core.media.WloCaptureMode
import app.wlo.core.media.WloShutterBridge
import app.wlo.core.media.WloViewfinder
import app.wlo.feature.f01.onboarding.F01Routes
import app.wlo.feature.f01.onboarding.domain.FirstWeightSource
import app.wlo.feature.f01.onboarding.ui.GoalsEditorScreen
import app.wlo.feature.f01.onboarding.ui.OnboardingScreen
import app.wlo.feature.f01.onboarding.ui.WeightFirstOnboardingScreen
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
import app.wlo.feature.f06.weight.state.BodySectionUi
import app.wlo.feature.f06.weight.ui.BodyFatScreen
import app.wlo.feature.f06.weight.ui.LogbookScreen
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
 * The WLO shell: adaptive five-destination navigation (R-D2) + NavHost, with every
 * `wlo://` deep link in the IA.md §3 registry registered against its route —
 * each resolving to a real screen or a documented stub (WloDeepLinks). Dark is
 * the base scheme (R-D1); the activity forces `darkTheme = true`. While the
 * shell gate is FRESH the Weight route hosts the weight-first setup and
 * top-level navigation stays hidden until onboarding is complete. Nested
 * destinations replace the top-level bar or rail with a standard Material 3
 * app bar and Up action.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val metadata = routeMetadata(currentRoute)
    val isTopLevel = metadata?.showsUp == false
    val selectedTopLevel = metadata?.topLevelOwner.orEmpty()
    val navigationItems =
        remember {
            WLO_TABS.map { tab ->
                WloTabItem(route = tab.route, icon = tab.icon, label = tab.label)
            }
        }
    val selectTopLevel: (String) -> Unit = { route ->
        haptics.perform(WloHaptic.SegmentTick)
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

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

    BoxWithConstraints(modifier = modifier) {
        val showTopLevelNavigation = shellState == ShellState.Onboarded && isTopLevel
        val useNavigationRail = useNavigationRail(maxWidth, showTopLevelNavigation)
        val appBarMetadata = metadata?.takeIf { shellState == ShellState.Onboarded }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                if (appBarMetadata != null) {
                    TopAppBar(
                        title = { Text(text = appBarMetadata.appBarTitle) },
                        navigationIcon = {
                            if (appBarMetadata.showsUp) {
                                IconButton(
                                    onClick = {
                                        if (!navController.navigateUp()) {
                                            navController.navigate(appBarMetadata.topLevelOwner) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    inclusive = true
                                                }
                                            }
                                        }
                                    },
                                ) {
                                    Icon(imageVector = WloIcons.ArrowBack, contentDescription = "Navigate up")
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (showTopLevelNavigation && !useNavigationRail) {
                    WloBottomBar(
                        selected = selectedTopLevel,
                        onSelect = selectTopLevel,
                        items = navigationItems,
                        modifier = Modifier.testTag("top-level-navigation-bar"),
                    )
                }
            },
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (useNavigationRail) {
                    WloNavigationRail(
                        selected = selectedTopLevel,
                        onSelect = selectTopLevel,
                        items = navigationItems,
                        modifier = Modifier.testTag("top-level-navigation-rail"),
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = WloTabs.WEIGHT,
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { appBarMetadata?.paneTitle?.let { paneTitle = it } },
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
                    composable(route = F06Routes.LOGBOOK) { LogbookScreen(viewModel = koinViewModel()) }
                    composable(
                        route = F06Routes.LOGBOOK_RANGE,
                        arguments =
                            listOf(
                                navArgument(F06Routes.ARG_RANGE_START) { type = NavType.LongType },
                                navArgument(F06Routes.ARG_RANGE_END) { type = NavType.LongType },
                            ),
                    ) { LogbookScreen(viewModel = koinViewModel()) }
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
                    composable(route = F01Routes.PLAN_STUDIO) {
                        RouteSurface(
                            route = F01Routes.PLAN_STUDIO,
                            arguments = null,
                            shellState = shellState,
                            onSurfaceChanged = onSurfaceChanged,
                            navController = navController,
                        )
                    }
                    composable(route = F01Routes.STUDIO) {
                        RouteSurface(
                            route = F01Routes.STUDIO,
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
    if (route in WloDeepLinks.TAB_ROUTES) {
        TopLevelRouteSurface(
            route = route,
            shellState = shellState,
            onSurfaceChanged = onSurfaceChanged,
            navController = navController,
        )
        return
    }

    val entryArg: String? = arguments?.getString(ENTRY_ARG)
    val entryArgSecond: String? = arguments?.getString(SLOT_ARG)
    when (route) {
        WloDeepLinks.INSIGHTS_UNAVAILABLE ->
            MoreScreen(
                onOpenArchive = { navController.navigate(WloTabs.ARCHIVE) },
                onOpenDigestion = { navController.navigate(WloTabs.DIGESTION) },
                onOpenExercise = { navController.navigate("stub/exercise") },
                onOpenVault = { navController.navigate(F13Routes.VAULT) },
                onOpenAi = { navController.navigate(F12Routes.STUDIO) },
                onOpenSettings = { navController.navigate("app/settings") },
                notice = "Insights is not available yet. Your available data and settings are below.",
            )

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
                onOpenGoals = { navController.navigate(F01Routes.STUDIO) },
                onOpenPlanStudio = { navController.navigate(F01Routes.PLAN_STUDIO) },
                onOpenProfile = { navController.navigate("app/profile") },
            )

        // The goals editor (WLO-0035 W4): the wizard's first run is v1 of this
        // same editor — R-B2's STUDIO_F01 door, now surfaced.
        F01Routes.STUDIO ->
            GoalsEditorScreen(
                viewModel = koinViewModel(),
                onBack = { navController.popBackStack() },
            )

        F01Routes.PLAN_STUDIO -> OnboardingScreen(viewModel = koinViewModel())

        // The profile-facts editor (WLO-0035 W4): onboarding answers, correctable.
        "app/profile" -> ProfileFactsScreen()

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

        "f06/log" ->
            WeightScreen(
                viewModel = koinViewModel(parameters = { parametersOf(true, BodySectionUi.WEIGHT) }),
                bodyFatViewModel = koinViewModel(),
                onOpenMath = { navController.navigate(F06Routes.MATH) },
                onOpenLogbook = { range -> navController.navigate(F06Routes.logbook(range)) },
                onEditGoal = { navController.navigate(F01Routes.STUDIO) },
            )

        F06Routes.MATH -> MathDocsScreen()
        F06Routes.BODY_FAT -> BodyFatScreen(viewModel = koinViewModel())
        F06Routes.LOGBOOK -> LogbookScreen(viewModel = koinViewModel())

        // Debug diagnostics (M4): the egress monitor — debug builds render the
        // persisted receipt ledger; release builds get an honest note instead.
        "debug/egress" -> EgressMonitorScreen()

        else -> StubScreen(title = STUB_TITLES[route] ?: "Coming later")
    }
}

/** The five adaptive-navigation roots, kept separate from nested feature dispatch. */
@Composable
private fun TopLevelRouteSurface(
    route: String,
    shellState: ShellState,
    onSurfaceChanged: (String) -> Unit,
    navController: NavHostController,
) {
    when (route) {
        WloTabs.WEIGHT ->
            OnboardingGatedSurface(
                shellState = shellState,
                onSurfaceChanged = onSurfaceChanged,
                navController = navController,
            ) {
                onSurfaceChanged("weight")
                WeightScreen(
                    viewModel = koinViewModel(parameters = { parametersOf(false, BodySectionUi.WEIGHT) }),
                    bodyFatViewModel = koinViewModel(),
                    onOpenMath = { navController.navigate(F06Routes.MATH) },
                    onOpenLogbook = { range -> navController.navigate(F06Routes.logbook(range)) },
                    onEditGoal = { navController.navigate(F01Routes.STUDIO) },
                )
            }

        WloTabs.HUB ->
            OnboardingGatedSurface(
                shellState = shellState,
                onSurfaceChanged = onSurfaceChanged,
                navController = navController,
            ) {
                onSurfaceChanged("hub")
                HubScreen(
                    viewModel = koinViewModel(),
                    actions = hubActions(navController),
                )
            }

        WloTabs.PLAN -> PlanTabRoute(focusDay = null, focusSlot = null, navController = navController)
        WloTabs.MORE ->
            MoreScreen(
                onOpenArchive = { navController.navigate(WloTabs.ARCHIVE) },
                onOpenDigestion = { navController.navigate(WloTabs.DIGESTION) },
                onOpenExercise = { navController.navigate("stub/exercise") },
                onOpenVault = { navController.navigate(F13Routes.VAULT) },
                onOpenAi = { navController.navigate(F12Routes.STUDIO) },
                onOpenSettings = { navController.navigate("app/settings") },
            )
    }
}

@Composable
private fun OnboardingGatedSurface(
    shellState: ShellState,
    onSurfaceChanged: (String) -> Unit,
    navController: NavHostController,
    onboarded: @Composable () -> Unit,
) {
    when (shellState) {
        ShellState.Loading -> Box(Modifier.fillMaxSize())
        ShellState.Fresh -> {
            onSurfaceChanged("onboarding")
            WeightFirstOnboardingScreen(
                viewModel = koinViewModel(),
                onComplete = { source ->
                    if (source == FirstWeightSource.FILE_IMPORT) navController.navigate(F13Routes.IMPORT)
                },
            )
        }
        ShellState.Onboarded -> onboarded()
    }
}

private fun hubActions(navController: NavHostController): HubActions =
    HubActions(
        onOpenDiary = { navController.navigate(Uri.parse("wlo://diary")) },
        onOpenCapture = { navController.navigate("f02/capture") },
        onQuickAddKcal = { navController.navigate("f02/quick-kcal") },
        onLogWeight = { navController.navigate("f06/log") },
        onOpenWeight = { navController.navigate(WloTabs.WEIGHT) },
        onGutLog = { navController.navigate("stub/gut") },
        onWorkout = { navController.navigate("stub/exercise") },
        onOpenPlan = { navController.navigate(WloTabs.PLAN) },
        onPlanTomorrow = { navController.navigate(F03Routes.TOMORROW) },
        onOpenMealSlot = { slotId -> navController.navigate(Uri.parse("wlo://log/planned?slot=$slotId")) },
        onOpenSettings = { navController.navigate("app/settings") },
    )

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
