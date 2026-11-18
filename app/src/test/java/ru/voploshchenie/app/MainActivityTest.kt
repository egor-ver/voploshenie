package ru.voploshchenie.app


import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.flow.first
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltTestApplication
import org.robolectric.annotation.Config
import org.junit.Rule
import androidx.activity.compose.setContent
import ru.voploshchenie.app.ui.navigation.AppNavigation

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class MainActivityTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @javax.inject.Inject lateinit var database: ru.voploshchenie.app.data.local.db.TestDatabase
    @javax.inject.Inject lateinit var testConfig: ru.voploshchenie.app.domain.model.TestConfig
    @javax.inject.Inject lateinit var testRepository: ru.voploshchenie.app.domain.repository.TestSessionRepository
    @javax.inject.Inject lateinit var savedRepository: ru.voploshchenie.app.domain.repository.SavedRepository
    @javax.inject.Inject lateinit var catalogRepository: ru.voploshchenie.app.domain.repository.CatalogRepository
    @org.junit.Before fun inject() {
        hilt.inject()
    }
    @org.junit.After fun closeDatabase() {
        database.close()
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesWithPathSelected() {
        compose.onNodeWithTag("tab_path").assertIsSelected()
        assertEquals(1, compose.onAllNodesWithText("Путь").fetchSemanticsNodes().size)
    }

    @Test
    fun activityRecreationKeepsRepositoryAndSavedSession() {
        val repository = testRepository
        val config = testConfig
        val saved = kotlinx.coroutines.runBlocking {
            repository.save(ru.voploshchenie.app.domain.model.TestSessionState(73, currentScreenId = "profile"))
        }
        compose.activityRule.scenario.recreate()
        val restored = dagger.hilt.android.EntryPointAccessors.fromApplication(
            compose.activity.applicationContext, SessionEntryPoint::class.java)

        org.junit.Assert.assertSame(repository, restored.testRepository())
        org.junit.Assert.assertSame(config, restored.testConfig())
        assertEquals(saved, kotlinx.coroutines.runBlocking { restored.testRepository().load() })
    }

    @Test
    fun navigationHiddenOnIntroductionAndQuestions() {
        compose.onNodeWithText("Пройти профтест").performClick()
        compose.onNodeWithTag("tab_path").assertDoesNotExist()
        compose.onNodeWithTag("tab_opportunities").assertDoesNotExist()
        compose.waitUntil { compose.onAllNodesWithText("Начать").fetchSemanticsNodes().any {
            !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        } }
        compose.onNodeWithText("Начать").performClick()
        compose.waitUntil { compose.onAllNodesWithText(compose.activity.getString(R.string.quiz_close)).fetchSemanticsNodes().isNotEmpty() }
        listOf("path", "opportunities", "saved", "deadlines", "profile").forEach {
            compose.onNodeWithTag("tab_$it").assertDoesNotExist()
        }
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitUntil { compose.onAllNodesWithText("Прогресс сохранён").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Выйти").performClick()
        compose.onNodeWithTag("tab_path").assertIsSelected()
    }

    @Test
    fun everyTabOpensAndRepeatedSelectionStaysOnTab() {
        listOf("opportunities" to "Возможности", "saved" to "Избранное", "deadlines" to "Дедлайны", "profile" to "Профиль", "path" to "Путь").forEach { (route, label) ->
            compose.onNodeWithTag("tab_$route").performClick()
            val matchingLabels = if (route in listOf("saved", "deadlines", "path", "profile")) 1 else 2
            assertEquals(matchingLabels, compose.onAllNodesWithText(label).fetchSemanticsNodes().size)
            compose.onNodeWithTag("tab_$route").performClick()
            assertEquals(matchingLabels, compose.onAllNodesWithText(label).fetchSemanticsNodes().size)
        }
        compose.onNodeWithTag("tab_profile").performClick()
        compose.onNodeWithTag("tab_profile").performClick()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithTag("tab_path").assertIsSelected()
        compose.onNodeWithText("Пройти профтест").performClick()
        compose.onNodeWithText("Профориентационный тест", substring = true).assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("test_start_or_continue").fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
        assertEquals(0, compose.onAllNodesWithTag("tab_path").fetchSemanticsNodes().size)
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithTag("tab_path").assertIsSelected()
    }

    @Test
    fun pathTabReturnsToPathScreenFromEveryTab() {
        val pathTitle = compose.activity.getString(ru.voploshchenie.app.R.string.path_title)
        listOf("opportunities", "saved", "deadlines", "profile").forEach { route ->
            compose.onNodeWithTag("tab_$route").performClick()
            compose.onNodeWithTag("tab_path").performClick()
            compose.onNodeWithTag("tab_path").assertIsSelected()
            compose.onNodeWithText(pathTitle).assertIsDisplayed()
        }
        val item = kotlinx.coroutines.runBlocking { catalogRepository.all() }.first()
        compose.onNodeWithTag("tab_opportunities").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("catalog_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("catalog_list").performScrollToNode(androidx.compose.ui.test.hasTestTag("opportunity_${item.id}"))
        compose.onNodeWithTag("opportunity_${item.id}").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("opportunity_detail").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_path").performClick()
        compose.onNodeWithText(pathTitle).assertIsDisplayed()
    }

    @Test
    fun tabsOpenTheirOwnScreenAfterCollectionOpenedFromPathOrProfile() {
        compose.activity.setContent {
            ru.voploshchenie.app.ui.theme.AppTheme {
                AppNavigation(
                    pathContent = { _, onRecommendations ->
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.Text("Экран пути")
                            androidx.compose.material3.Button(onRecommendations) { androidx.compose.material3.Text("Подборка") }
                        }
                    },
                    profileContent = { _, onRecommendations ->
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.Text("Экран профиля")
                            androidx.compose.material3.Button(onRecommendations) { androidx.compose.material3.Text("Подборка") }
                        }
                    },
                )
            }
        }
        fun collection() = compose.onNodeWithText("Подборка").performClick()
        fun tab(route: String, marker: String? = null) {
            compose.onNodeWithTag("tab_$route").performClick()
            compose.onNodeWithTag("tab_$route").assertIsSelected()
            listOf("Экран пути", "Экран профиля").forEach {
                assertEquals(if (it == marker) 1 else 0, compose.onAllNodesWithText(it).fetchSemanticsNodes().size)
            }
        }
        collection(); tab("path", "Экран пути")
        tab("opportunities"); tab("profile", "Экран профиля"); tab("path", "Экран пути")
        collection(); tab("profile", "Экран профиля"); tab("path", "Экран пути")
        collection(); tab("path", "Экран пути"); tab("opportunities"); tab("profile", "Экран профиля")
        tab("saved"); tab("deadlines"); tab("opportunities"); tab("path", "Экран пути")
        tab("profile", "Экран профиля"); collection(); tab("path", "Экран пути"); tab("profile", "Экран профиля")
        collection(); tab("saved"); tab("profile", "Экран профиля")
    }

    @Test
    fun savedOpportunityAppearsInDeadlinesTab() {
        val items = kotlinx.coroutines.runBlocking { catalogRepository.all() }
        val item = items.first { it.dates.isNotEmpty() }
        val unsaved = items.first { it.id != item.id && it.dates.isNotEmpty() }
        compose.onNodeWithTag("tab_opportunities").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("catalog_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("catalog_list").performScrollToNode(androidx.compose.ui.test.hasTestTag("opportunity_${item.id}"))
        compose.onNodeWithTag("opportunity_${item.id}").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("opportunity_detail").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(ru.voploshchenie.app.R.string.save)).performClick()
        compose.waitUntil { kotlinx.coroutines.runBlocking { savedRepository.observeAll().first().any { it.item_id == item.id } } }
        compose.onNodeWithText(compose.activity.getString(ru.voploshchenie.app.R.string.back_to_label, compose.activity.getString(ru.voploshchenie.app.R.string.tab_opportunities))).performClick()
        compose.onNodeWithTag("tab_deadlines").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("deadlines_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("deadlines_list").performScrollToNode(androidx.compose.ui.test.hasTestTag("deadline_${item.id}"))
        compose.onNodeWithTag("deadline_${item.id}").assertIsDisplayed()
        compose.onNodeWithText(item.title).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("deadline_${unsaved.id}").fetchSemanticsNodes().size)
        compose.onNodeWithTag("deadline_${item.id}").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("opportunity_detail").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(ru.voploshchenie.app.R.string.saved_status)).performClick()
        compose.waitUntil { kotlinx.coroutines.runBlocking { savedRepository.observeAll().first().none { it.item_id == item.id } } }
        compose.onNodeWithText(compose.activity.getString(ru.voploshchenie.app.R.string.back_to_label, compose.activity.getString(ru.voploshchenie.app.R.string.tab_opportunities))).performClick()
        compose.onNodeWithText(compose.activity.getString(ru.voploshchenie.app.R.string.deadlines_empty)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(ru.voploshchenie.app.R.string.deadlines_find)).performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag("tab_opportunities").assertIsSelected()
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface SessionEntryPoint {
    fun testRepository(): ru.voploshchenie.app.domain.repository.TestSessionRepository
    fun testConfig(): ru.voploshchenie.app.domain.model.TestConfig
}
