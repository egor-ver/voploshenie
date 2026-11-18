package ru.voploshchenie.app.ui.navigation

import ru.voploshchenie.app.ui.theme.NavigationSpacing
import ru.voploshchenie.app.ui.theme.QuizSpacing
import ru.voploshchenie.app.R
import ru.voploshchenie.app.ui.opportunities.OpportunitiesRoute
import ru.voploshchenie.app.ui.opportunities.OpportunityDetailRoute
import ru.voploshchenie.app.ui.quiz.QuizRoute
import ru.voploshchenie.app.ui.result.ResultRoute
import ru.voploshchenie.app.ui.saved.SavedRoute
import ru.voploshchenie.app.ui.saved.DeadlinesRoute
import ru.voploshchenie.app.ui.games.GamesHubRoute
import ru.voploshchenie.app.ui.games.GamesRoute
import ru.voploshchenie.app.ui.games.MythsGameRoute
import ru.voploshchenie.app.ui.games.MythsRoute
import ru.voploshchenie.app.ui.games.ResumeGameRoute
import ru.voploshchenie.app.ui.games.ResumeRoute
import ru.voploshchenie.app.ui.games.WordleGameRoute
import ru.voploshchenie.app.ui.games.WordleRoute
import ru.voploshchenie.app.ui.theme.Spacing
import ru.voploshchenie.app.ui.theme.AppTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

@Composable
internal fun AppRoot() {
    AppTheme { AppNavigation() }
}

private data class Tab(val route: String, val label: Int, val icon: Int)

private val tabs = listOf(
    Tab("path", R.string.tab_path, R.drawable.nav_path),
    Tab("opportunities", R.string.tab_opportunities, R.drawable.nav_opportunities),
    Tab("saved", R.string.tab_saved, R.drawable.nav_saved),
    Tab("deadlines", R.string.tab_deadlines, R.drawable.nav_deadlines),
    Tab("profile", R.string.tab_profile, R.drawable.nav_profile),
)

@Composable
internal fun AppNavigation(
    pathContent: (@Composable (onStartTest: () -> Unit, onRecommendations: () -> Unit) -> Unit)? = null,
    profileContent: (@Composable (onStartTest: (String) -> Unit, onRecommendations: () -> Unit) -> Unit)? = null,
) {
    val navController = rememberNavController()
    val entry = navController.currentBackStackEntryAsState().value
    val route = entry?.destination?.route ?: "path"
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        AppBottomBar(route) { tab ->
            // Вкладка всегда открывает свой начальный экран: подборка и результат, открытые с «Пути» или «Профиля»,
            // при восстановлении стека показывались бы под чужой вкладкой.
            navController.navigate(tab) {
                popUpTo(navController.graph.startDestinationId)
                launchSingleTop = true
            }
        }
    }) { padding ->
        NavHost(navController, startDestination = "path", modifier = Modifier.padding(padding)) {
            tabRoutes(navController, pathContent, profileContent)
            testRoutes(navController)
            opportunityRoutes(navController)
            gameRoutes(navController)
        }
    }
}

private fun NavGraphBuilder.tabRoutes(
    navController: NavHostController,
    pathContent: (@Composable (() -> Unit, () -> Unit) -> Unit)?,
    profileContent: (@Composable ((String) -> Unit, () -> Unit) -> Unit)?,
) {
    val startTest = { navController.navigate("test") }
    val recommendations = { navController.navigate("recommendations?type=0") }
    val onItem: (String) -> Unit = { navController.navigate("opportunity/$it") }
    val opportunities = { navController.navigate("opportunities") }
    tabs.forEach { tab ->
        composable(tab.route) {
            when (tab.route) {
                "opportunities" -> {
                    OpportunitiesRoute(onStartTest = startTest,
                        onItem = onItem)
                }
                "saved" -> SavedRoute(onItem, opportunities)
                "deadlines" -> DeadlinesRoute(
                    onItem = onItem,
                    onOpportunities = opportunities,
                )
                "path" -> {
                    if (pathContent == null) ru.voploshchenie.app.ui.path.PathRoute(startTest, recommendations, onResult = { navController.navigate("result/$it") }, onGames = { navController.navigate(GamesRoute) })
                    else pathContent(startTest, recommendations)
                }
                "profile" -> {
                    val startNewTest: (String) -> Unit = { navController.navigate("test/new/$it") }
                    if (profileContent == null) ru.voploshchenie.app.ui.profile.ProfileRoute(startNewTest, recommendations, onResult = { navController.navigate("result/$it") }, onGames = { navController.navigate(GamesRoute) })
                    else profileContent(startNewTest, recommendations)
                }
            }
        }
    }
}

private fun NavGraphBuilder.gameRoutes(navController: NavHostController) {
    val back = { navController.popBackStack() }
    composable(GamesRoute) { GamesHubRoute(onOpen = { navController.navigate(it) }) }
    composable(WordleGameRoute) { WordleRoute(onBack = { back() }, onNextGame = { navController.navigate(MythsGameRoute) { popUpTo(GamesRoute) } }) }
    composable(MythsGameRoute) {
        MythsRoute(onBack = { back() }, onWordle = { navController.navigate(WordleGameRoute) { popUpTo(GamesRoute) } },
            onOpportunities = { navController.navigate("opportunities") { popUpTo(navController.graph.startDestinationId) } })
    }
    composable(ResumeGameRoute) { ResumeRoute(onBack = { back() }, onOpportunities = { navController.navigate("opportunities") { popUpTo(navController.graph.startDestinationId) } }) }
}

private fun NavGraphBuilder.testRoutes(navController: NavHostController) {
    listOf("test", "test/new/{sessionId}").forEach { route ->
        val arguments = if (route == "test") emptyList()
            else listOf(navArgument("sessionId") { type = NavType.StringType })
        composable(route, arguments = arguments) {
            QuizRoute(onExit = { navController.popBackStack() }, onResult = { sessionId ->
                navController.navigate("result/$sessionId") { popUpTo(route) { inclusive = true } }
            })
        }
    }
    composable("result/{sessionId}", arguments = listOf(navArgument("sessionId") { type = NavType.StringType })) {
        ResultRoute(onOpportunities = { internships ->
            navController.navigate("recommendations?type=${if (internships) 2 else 0}") { popUpTo("result/{sessionId}") { inclusive = true } }
        })
    }
}

private fun NavGraphBuilder.opportunityRoutes(navController: NavHostController) {
    composable("recommendations?type={type}", arguments = listOf(
        navArgument("type") { type = NavType.IntType; defaultValue = 0 },
        navArgument("recommendationsOnly") { type = NavType.BoolType; defaultValue = true })) {
        OpportunitiesRoute(onStartTest = { navController.navigate("test") },
            onItem = { navController.navigate("opportunity/$it") })
    }
    composable("opportunity/{itemId}", arguments = listOf(navArgument("itemId") { type = NavType.StringType })) { detailEntry ->
        val itemId = requireNotNull(detailEntry.arguments?.getString("itemId"))
        OpportunityDetailRoute(onBack = { navController.popBackStack() }, onSimilar = { navController.navigate("similar/$itemId") })
    }
    composable("similar/{similarTo}", arguments = listOf(navArgument("similarTo") { type = NavType.StringType })) {
        OpportunitiesRoute(onStartTest = {}, onItem = { navController.navigate("opportunity/$it") }, onBack = { navController.popBackStack() })
    }
}

@Composable
internal fun AppBottomBar(route: String, onSelect: (String) -> Unit) {
    val opportunitiesRoute = route.startsWith("recommendations") || route.startsWith("opportunity/") || route.startsWith("similar/")
    if (route in tabs.map(Tab::route) || opportunitiesRoute || route.startsWith("result") || route == GamesRoute) Surface(color = MaterialTheme.colorScheme.surface) {
        val navOutline = MaterialTheme.colorScheme.outlineVariant
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = NavigationSpacing.height).navigationBarsPadding().testTag("bottom_bar").drawBehind {
            drawLine(navOutline, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), QuizSpacing.stroke.toPx())
        }) {
            tabs.forEach { tab ->
                val selected = route == tab.route || opportunitiesRoute && tab.route == "opportunities" || route.startsWith("result") && tab.route == "path" || route == GamesRoute && tab.route == "path"
                Column(
                    modifier = Modifier.weight(1f).padding(bottom = Spacing.step * 2).testTag("tab_${tab.route}").selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(tab.route) },
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Spacer(Modifier.size(width = NavigationSpacing.indicatorWidth, height = NavigationSpacing.indicatorHeight).clip(RoundedCornerShape(NavigationSpacing.indicatorRadius)).background(
                        if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                    ))
                    Spacer(Modifier.height(NavigationSpacing.iconGap))
                    Icon(painterResource(tab.icon), contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.step))
                    Text(stringResource(tab.label), Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = NavigationSpacing.labelSize,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
