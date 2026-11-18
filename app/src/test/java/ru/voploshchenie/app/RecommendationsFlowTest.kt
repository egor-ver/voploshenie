package ru.voploshchenie.app

import ru.voploshchenie.app.ui.navigation.AppBottomBar

import ru.voploshchenie.app.data.local.asset.CatalogLoader
import ru.voploshchenie.app.domain.model.CatalogSphereResult
import ru.voploshchenie.app.domain.model.CatalogResult
import ru.voploshchenie.app.domain.usecase.CatalogSelection
import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.scoring.ScoringResult
import ru.voploshchenie.app.domain.scoring.ScoringEngine
import ru.voploshchenie.app.ui.opportunities.OpportunitiesRoute
import ru.voploshchenie.app.ui.opportunities.OpportunityDetailRoute
import ru.voploshchenie.app.domain.usecase.opportunityClosed
import ru.voploshchenie.app.ui.result.ResultRoute
import ru.voploshchenie.app.ui.saved.SavedRoute
import ru.voploshchenie.app.ui.theme.AppTheme
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.compose.ui.test.*
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w390dp-h844dp-mdpi")
class AN3FlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @javax.inject.Inject lateinit var database: ru.voploshchenie.app.data.local.db.TestDatabase
    @javax.inject.Inject lateinit var testConfig: ru.voploshchenie.app.domain.model.TestConfig
    @javax.inject.Inject lateinit var testRepository: ru.voploshchenie.app.domain.repository.TestSessionRepository
    @org.junit.Before fun inject() { hilt.inject() }
    @org.junit.After fun closeDatabase() { database.close() }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun biologyCtaShowsAllFallbackMarksAndOpensAndSavesDatedOpportunity() =
        verifyProfile("biology", listOf("biology", "finance", "it"))

    @Test fun telecomCtaShowsWholeCatalogAndOpensAndSavesDatedOpportunity() =
        verifyProfile("telecom", listOf("telecom"))

    private fun verifyProfile(primary: String, top: List<String>) {
        val config = testConfig
        val profile = calculatedProfile(primary, top, config)
        val previousResult = runBlocking { testRepository.latestResult() }
        val computedAt = maxOf(System.currentTimeMillis(), previousResult?.computedAt?.plus(1) ?: Long.MIN_VALUE)
        val writer = ru.voploshchenie.app.data.repository.RoomTestSessionRepository(database.testDao(), now = { computedAt })
        val session = runBlocking {
            writer.start(config.version.toString(), 73).also {
                writer.saveScreen(it.id, "profile", mapOf("__format" to "2"))
                writer.finish(it.id, Json.encodeToString(profile), 0)
            }
        }
        // Приложение показывает основной каталог + дополнительные позиции без завершившихся возможностей.
        val items = (CatalogLoader.load(compose.activity) + CatalogLoader.loadExtra(compose.activity))
            .filterNot { ru.voploshchenie.app.domain.usecase.opportunityClosed(it, java.time.LocalDate.now()) }
        val result = CatalogResult(profile.spheres.map { CatalogSphereResult(it.id, it.percent) }, profile.topSpheres)
        val picks = CatalogSelection(items).recommendations(result)
        val expectedCount = items.size
        assertEquals(expectedCount, picks.size)
        assertEquals(items.map { it.id }.toSet(), picks.map { it.item.id }.toSet())

        lateinit var navController: NavHostController
        compose.activity.setContent {
            AppTheme {
                navController = rememberNavController()
                val route = navController.currentBackStackEntryAsState().value?.destination?.route ?: "result"
                Scaffold(bottomBar = { AppBottomBar(route) { tab -> navController.navigate(tab) { launchSingleTop = true } } }) { padding ->
                NavHost(navController, startDestination = "result", modifier = androidx.compose.ui.Modifier.padding(padding)) {
                    composable("result", arguments = listOf(navArgument("sessionId") { defaultValue = session.id })) {
                        ResultRoute(onOpportunities = { internships -> navController.navigate("recommendations?type=${if (internships) 2 else 0}") })
                    }
                    composable("recommendations?type={type}", arguments = listOf(navArgument("type") { type = NavType.IntType; defaultValue = 0 },
                        navArgument("recommendationsOnly") { type = NavType.BoolType; defaultValue = true })) {
                        OpportunitiesRoute({}, { navController.navigate("item/$it") })
                    }
                    composable("item/{itemId}", arguments = listOf(navArgument("itemId") { type = NavType.StringType })) {
                        OpportunityDetailRoute(onBack = { navController.popBackStack() }, onSimilar = {})
                    }
                    composable("saved") { SavedRoute({}, {}) }
                }
                }
            }
        }

        compose.waitUntil(10_000) { compose.onAllNodesWithTag("result").fetchSemanticsNodes().isNotEmpty() }
        val resultList = compose.onNodeWithTag("result")
        repeat(12) {
            if (!runCatching { compose.onNodeWithTag("result_open_opportunities", useUnmergedTree = true).isDisplayed() }.getOrDefault(false)) {
                resultList.performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, 600f) }
            }
        }
        compose.waitUntil(10_000) {
            compose.onNodeWithTag("result_open_opportunities", useUnmergedTree = true).isDisplayed()
        }
        compose.onNodeWithTag("result_open_opportunities", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("catalog_list").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(compose.activity.getString(R.string.catalog_error)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.catalog_found, expectedCount)).assertExists()

        picks.filter { it.fromSphere != null }.forEach { recommendation ->
            compose.onNodeWithTag("catalog_list").performScrollToNode(hasTestTag("opportunity_${recommendation.item.id}"))
            compose.onNodeWithTag("recommendation_from_${recommendation.item.id}").assertExists()
            compose.onNodeWithTag("opportunity_${recommendation.item.id}").assertExists()
        }

        val dated = picks.first { it.item.dates.isNotEmpty() && !opportunityClosed(it.item, java.time.LocalDate.now()) }.item
        compose.onNodeWithTag("catalog_list").performScrollToNode(hasTestTag("opportunity_${dated.id}"))
        compose.onNodeWithTag("opportunity_${dated.id}").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("opportunity_detail").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(ru.voploshchenie.app.ui.opportunities.humanDate(dated.dates.first()), substring = true)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.save)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.saved_status)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("←  ${compose.activity.getString(R.string.tab_opportunities)}").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("tab_saved").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_saved").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("saved_${dated.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("saved_${dated.id}").assertExists()
    }

    private fun calculatedProfile(primary: String, top: List<String>, config: TestConfig): ScoringResult {
        val interests = config.questions.getValue("interests")
        val consistency = config.questions.getValue("consistency")
        val tasks = config.questions.getValue("tasks")
        return (0..5000).asSequence().map { seed ->
            val random = Random(seed)
            val pairs = interests.associate { q -> q.id to listOfNotNull(q.type_a, q.type_b).random(random) }
            val answers = TestAnswers(
                pairs = pairs,
                checks = consistency.associate { q ->
                    val linked = interests.first { it.id == q.linked }
                    q.id to if (pairs.getValue(linked.id) == q.type_a) requireNotNull(q.type_a) else requireNotNull(q.type_b)
                },
                tasks = tasks.associate { q -> q.id to q.options.random(random).id },
                interest = config.spheres.flatMap { sphere -> sphere.questions.map { it to if (sphere.id == primary) 5 else 0 } }.toMap(),
                valuesTop3 = (1..10).map { "VAL%02d".format(it) }.shuffled(random).take(3),
            )
            ScoringEngine.compute(answers, config)
        }.first { it.topSpheres.take(top.size) == top }
    }
}
