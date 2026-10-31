package com.example.redirect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.redirect.data.AppData
import com.example.redirect.data.Destination
import com.example.redirect.data.DestinationType
import com.example.redirect.data.currentBook
import com.example.redirect.ui.home.HomeScreen
import com.example.redirect.ui.library.BookDetailScreen
import com.example.redirect.ui.library.DiscoverScreen
import com.example.redirect.ui.library.LibraryScreen
import com.example.redirect.ui.onboarding.OnboardingScreen
import com.example.redirect.ui.profile.ProfileScreen
import com.example.redirect.ui.reader.ReaderScreen
import com.example.redirect.ui.rules.InterventionScreen
import com.example.redirect.ui.rules.OfflineActivityScreen
import com.example.redirect.ui.rules.RuleEditorScreen
import com.example.redirect.ui.rules.RulesScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val RULES = "rules"
    const val PROFILE = "profile"
    const val DISCOVER = "discover"
    fun book(id: String) = "book/$id"
    fun reader(id: String) = "reader/$id"
    fun ruleEditor(id: String? = null, trigger: String? = null) =
        "rule/edit?id=${id.orEmpty()}&trigger=${trigger.orEmpty()}"
    fun intervention(triggerId: String, ruleId: String? = null, live: Boolean = false) =
        "intervention/${android.net.Uri.encode(triggerId)}?rule=${ruleId.orEmpty()}&live=$live"
    fun activity(destinationId: String) = "activity/${android.net.Uri.encode(destinationId)}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(Routes.LIBRARY, "Library", Icons.Outlined.LocalLibrary, Icons.Rounded.LocalLibrary),
    Tab(Routes.RULES, "Rules", Icons.Outlined.AltRoute, Icons.Rounded.AltRoute),
    Tab(Routes.PROFILE, "Profile", Icons.Outlined.AccountCircle, Icons.Rounded.AccountCircle),
)

/** Navigation actions shared by screens. */
class AppActions(private val nav: NavHostController, private val vm: AppViewModel) {
    fun back() = nav.popBackStack()
    fun tab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun openBook(id: String) = nav.navigate(Routes.book(id))
    fun read(id: String) = nav.navigate(Routes.reader(id))
    fun discover() = nav.navigate(Routes.DISCOVER)
    fun editRule(id: String? = null, trigger: String? = null) = nav.navigate(Routes.ruleEditor(id, trigger))
    fun previewTrigger(triggerId: String) = nav.navigate(Routes.intervention(triggerId))
    fun previewRule(ruleId: String, triggerId: String) = nav.navigate(Routes.intervention(triggerId, ruleId))

    /** Opens a destination for real: built-ins inside the app, external apps via Android. */
    fun openDestination(d: Destination, data: AppData): Boolean {
        when (d.type) {
            DestinationType.CURRENT_BOOK -> {
                val book = data.currentBook ?: run { vm.toast("Pick a book to read first"); tab(Routes.LIBRARY); return false }
                read(book.id)
            }
            DestinationType.SPECIFIC_BOOK -> d.targetId?.let(::read)
            DestinationType.LIBRARY -> tab(Routes.LIBRARY)
            DestinationType.OFFLINE_ACTIVITY -> nav.navigate(Routes.activity(d.id))
            DestinationType.CHOICE_SCREEN -> vm.toast("Choose an alternative from the list")
            DestinationType.EXTERNAL_APP -> {
                val ok = d.targetId?.let { vm.repo.installedApps.launch(it, d.launchConfig["deepLink"]) } == true
                if (!ok) vm.toast("${d.displayName} isn't installed on this device")
                return ok
            }
        }
        return true
    }
}

@Composable
fun AppNavigation(vm: AppViewModel, data: AppData) {
    val nav = rememberNavController()
    val actions = remember(nav) { AppActions(nav, vm) }
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in tabs.map { it.route }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // A distracting app was opened: show the intervention on top of whatever is open here.
    val pending by vm.pendingInterruption.collectAsStateWithLifecycle()
    LaunchedEffect(pending, data.preferences.onboardingComplete) {
        val trigger = pending ?: return@LaunchedEffect
        // Already showing an intervention (e.g. the app re-asserted after a launch race): keep it.
        val showing = nav.currentBackStackEntry?.destination?.route?.startsWith("intervention/") == true
        if (data.preferences.onboardingComplete && !showing) {
            nav.navigate(Routes.intervention(trigger, live = true)) { launchSingleTop = true }
        }
        vm.interruptionShown()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        bottomBar = {
            AnimatedVisibility(showBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            val selected = route == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { actions.tab(tab.route) },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = nav,
                startDestination = if (data.preferences.onboardingComplete) Routes.HOME else Routes.ONBOARDING,
                enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(vm, data, onDone = {
                        nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    })
                }
                composable(Routes.HOME, enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) { HomeScreen(vm, data, actions) }
                composable(Routes.LIBRARY, enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) { LibraryScreen(vm, data, actions) }
                composable(Routes.RULES, enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) { RulesScreen(vm, data, actions) }
                composable(Routes.PROFILE, enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
                    ProfileScreen(vm, data, actions, onReset = {
                        nav.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
                    })
                }
                composable(Routes.DISCOVER) { DiscoverScreen(vm, data, actions) }
                composable("book/{id}") { entry ->
                    BookDetailScreen(vm, data, actions, entry.arguments?.getString("id").orEmpty())
                }
                composable(
                    "reader/{id}",
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popExitTransition = { fadeOut() },
                ) { entry ->
                    ReaderScreen(vm, data, entry.arguments?.getString("id").orEmpty(), onExit = { actions.back() })
                }
                composable(
                    "rule/edit?id={id}&trigger={trigger}",
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType; defaultValue = "" },
                        navArgument("trigger") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { entry ->
                    RuleEditorScreen(
                        vm, data, actions,
                        ruleId = entry.arguments?.getString("id")?.ifEmpty { null },
                        initialTrigger = entry.arguments?.getString("trigger")?.ifEmpty { null },
                    )
                }
                composable(
                    "intervention/{trigger}?rule={rule}&live={live}",
                    arguments = listOf(
                        navArgument("rule") { type = NavType.StringType; defaultValue = "" },
                        navArgument("live") { type = NavType.BoolType; defaultValue = false },
                    ),
                    enterTransition = { fadeIn() + slideInVertically { it / 8 } },
                    popExitTransition = { fadeOut() + slideOutVertically { it / 8 } },
                ) { entry ->
                    InterventionScreen(
                        vm, data, actions,
                        triggerId = entry.arguments?.getString("trigger").orEmpty(),
                        ruleId = entry.arguments?.getString("rule")?.ifEmpty { null },
                        live = entry.arguments?.getBoolean("live") == true,
                    )
                }
                composable("activity/{id}") { entry ->
                    OfflineActivityScreen(data, entry.arguments?.getString("id").orEmpty(), onDone = { actions.back() })
                }
            }
        }
    }
}
