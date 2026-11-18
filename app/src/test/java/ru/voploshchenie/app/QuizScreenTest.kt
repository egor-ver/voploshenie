package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.data.local.db.TestDatabase
import ru.voploshchenie.app.data.local.db.entity.TestAnswerEntity
import ru.voploshchenie.app.data.local.db.dao.TestDao
import ru.voploshchenie.app.data.repository.RoomTestSessionRepository
import ru.voploshchenie.app.ui.quiz.Scale
import ru.voploshchenie.app.ui.quiz.TestQuestionScreen
import ru.voploshchenie.app.ui.quiz.QuizRoute
import ru.voploshchenie.app.ui.quiz.QuizViewModel
import ru.voploshchenie.app.ui.theme.AppTheme
import ru.voploshchenie.app.domain.model.validChoice
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.lifecycle.ViewModelStore
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-mdpi")
class T03QuizTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val viewModels = ViewModelStore()
    @After fun clearViewModels() { viewModels.clear() }

    private fun acceptStorageConsent() {
        // Согласие убрано: ждём, пока сессия загрузится и кнопка старта станет доступной.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("test_start_or_continue").fetchSemanticsNodes().any {
            !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        } }
    }

    @Test
    fun choicesAndScalePersistAcrossBackAndResume() {
        val database = Room.inMemoryDatabaseBuilder(compose.activity, TestDatabase::class.java).build()
        try {
            val source = ConfigLoader.load(compose.activity)
            val ids = listOf("INT01", "INT02", "SPH01", "SPH02", "TSK01", "TSK02")
            val config = source.copy(parts = source.parts.map { part -> part.copy(questions = part.questions.filter { it.id in ids }) }
                .filter { it.questions.isNotEmpty() })
            val repository = RoomTestSessionRepository(database.testDao(), config.version.toString(), config)
            var exits = 0
            var generation by mutableIntStateOf(0)
            lateinit var viewModel: QuizViewModel
            compose.runOnUiThread {
                compose.activity.setContent { key(generation) {
                    viewModel = remember { QuizViewModel(config, repository, resumeCurrentScreen = false) { 73 }
                        .also { viewModels.put("quiz", it) } }
                    AppTheme { QuizRoute(onExit = { exits++ }, onResult = {}, viewModel = viewModel) }
                } }
            }
            acceptStorageConsent()
            compose.waitUntil { compose.onAllNodesWithText("Начать").fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            } }
            val pairs = viewModel.state.value.screens.filter { it.part == "interests" }.map { it.id }
            assertEquals(setOf("INT01", "INT02"), pairs.toSet())
            compose.onNodeWithText("Начать").performClick()
            compose.waitUntil { compose.onAllNodesWithTag("question_${pairs.first()}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsNotEnabled()
            compose.onNodeWithText(compose.activity.getString(R.string.test_back)).assertIsNotEnabled()
            fun select(text: String) {
                compose.onNodeWithText(text).performClick()
                compose.waitUntil { compose.onAllNodesWithText(compose.activity.getString(R.string.test_next)).fetchSemanticsNodes().any {
                    !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
                } }
                compose.onNodeWithText(text).assertIsSelected()
            }
            fun next(id: String) {
                compose.onNodeWithText(compose.activity.getString(R.string.test_next)).performClick()
                compose.waitUntil { compose.onAllNodesWithTag("question_$id").fetchSemanticsNodes().isNotEmpty() }
            }
            val choices = mapOf("INT01" to "Починить или собрать устройство руками.",
                "INT02" to "Придумать дизайн и внешний образ для такой вещи.")
            select(choices.getValue(pairs.first()))
            next(pairs.last())
            select(choices.getValue(pairs.last()))
            next("SPH01")
            select("1")
            next("SPH02")
            select("5")
            next("TSK01")
            select("42")
            next("TSK02")
            compose.onNodeWithText(compose.activity.getString(R.string.test_back)).performClick()
            compose.waitUntil { compose.onAllNodesWithTag("question_TSK01").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("42").assertIsSelected()
            compose.activity.onBackPressedDispatcher.onBackPressed()
            compose.waitUntil { compose.onAllNodesWithText("Прогресс сохранён").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Продолжить").performClick()
            compose.onNodeWithText("42").assertIsSelected()
            val saved = runBlocking { requireNotNull(repository.load()) }
            assertEquals("TSK01", saved.currentScreenId)
            val questions = source.parts.flatMap { it.questions }.associateBy { it.id }
            assertEquals(mapOf("INT01" to questions.getValue("INT01").type_a, "INT02" to questions.getValue("INT02").type_b), saved.answers.pairs)
            assertEquals(mapOf("SPH01" to 1, "SPH02" to 5), saved.answers.interest)
            assertEquals(mapOf("TSK01" to "в"), saved.answers.tasks)
            assertEquals(0, exits)
            compose.runOnIdle { generation++ }
            compose.waitUntil(10_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.continue_test)).fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            } }
            compose.onNodeWithText(compose.activity.getString(R.string.continue_test)).performClick()
            compose.waitUntil { compose.onAllNodesWithTag("question_TSK02").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(compose.activity.getString(R.string.test_back)).performClick()
            compose.waitUntil { compose.onAllNodesWithTag("question_TSK01").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("42").assertIsSelected()
        } finally { database.close() }
    }

    @Test
    fun scaleTenHasLargeTargetsAndSingleSelection() {
        var value: Int? = null
        compose.runOnUiThread { compose.activity.setContent {
            var selected by remember { mutableStateOf<Int?>(null) }
            AppTheme { Scale(1..10, selected, "1", "10") { selected = it; value = it } }
        } }
        compose.onAllNodesWithText("1")[0].assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.onAllNodesWithText("1")[0].assertIsSelected()
        compose.onAllNodesWithText("10")[0].performClick()
        compose.onAllNodesWithText("10")[0].assertIsSelected()
        compose.onAllNodesWithText("1")[0].assertIsNotSelected()
        assertEquals(10, value)
    }

    @Test
    fun partInstructionsAndScaleHeadingsMatchSpecification() {
        val questions = ConfigLoader.load(compose.activity).parts.flatMap { it.questions }.associateBy { it.id }
        var id by mutableStateOf("INT01")
        compose.runOnUiThread { compose.activity.setContent {
            AppTheme { TestQuestionScreen(questions.getValue(id), 1, 10, null, {}, {}, {}, {}) }
        } }
        compose.onNodeWithText("В каждой паре выберите вариант, который вам ближе, даже если оба нравятся или оба не очень. Пропускать нельзя").assertExists()
        compose.runOnIdle { id = "CHK01" }
        compose.onNodeWithText("Ещё пять коротких выборов. Отвечайте, как чувствуете сейчас: не пытайтесь вспомнить, что отвечали раньше").assertExists()
        compose.runOnIdle { id = "SPH01" }
        compose.onNodeWithText("Мне интересно…").assertExists()
        compose.onNodeWithText("Придумывать, как заработать на идее, и считать, будет ли её покупать рынок.").assertExists()
        compose.runOnIdle { id = "NEED01" }
        compose.onNodeWithText("Насколько для вас важно в идеальной работе…").assertExists()
        compose.onNodeWithText("Сам выбираю, чем и как заниматься").assertExists()
    }

    @Test
    fun latestChoiceIsSavedWhenPreviousWriteIsPending() {
        val database = Room.inMemoryDatabaseBuilder(compose.activity, TestDatabase::class.java).build()
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        var delayWrites = false
        try {
            val dao = database.testDao()
            val source = ConfigLoader.load(compose.activity)
            val config = source.copy(parts = source.parts.filter { it.id == "tasks" }.map {
                it.copy(questions = it.questions.take(2))
            })
            val repository = RoomTestSessionRepository(object : TestDao by dao {
                override suspend fun replaceScreen(sessionId: String, screen: String, answers: List<TestAnswerEntity>, updatedAt: Long) {
                    if (delayWrites) {
                        entered.complete(Unit)
                        release.await()
                    }
                    dao.replaceScreen(sessionId, screen, answers, updatedAt)
                }
            }, config.version.toString(), config)
            var generation by mutableIntStateOf(0)
            var exits = 0
            lateinit var viewModel: QuizViewModel
            compose.runOnUiThread { compose.activity.setContent {
                key(generation) {
                    viewModel = remember { QuizViewModel(config, repository, resumeCurrentScreen = false) { 73 }
                        .also { viewModels.put("quiz", it) } }
                    AppTheme { QuizRoute(onExit = { exits++ }, onResult = {}, viewModel = viewModel) }
                }
            } }
            acceptStorageConsent()
            compose.waitUntil { compose.onAllNodesWithText("Начать").fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            } }
            compose.onNodeWithText("Начать").performClick()
            compose.waitUntil { compose.onAllNodesWithTag("question_TSK01").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { delayWrites = true }
            compose.onNodeWithText("38").performClick()
            compose.waitUntil { entered.isCompleted }
            compose.onNodeWithText("40").performClick()
            compose.onNodeWithText("44").performClick()
            assertNull(viewModel.state.value.session?.answers?.tasks?.get("TSK01"))
            compose.onNodeWithText("38").assertIsNotSelected()
            compose.onNodeWithText("40").assertIsNotSelected()
            compose.onNodeWithText(compose.activity.getString(R.string.test_next)).assertIsNotEnabled()
            compose.onNodeWithText(compose.activity.getString(R.string.quiz_close)).performClick()
            assertEquals(0, exits)
            release.complete(Unit)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.test_next)).fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            } && exits == 1 }
            compose.onNodeWithText("44").assertIsSelected()
            assertEquals("г", runBlocking { repository.load()!!.answers.tasks["TSK01"] })
            compose.runOnIdle { generation++ }
            compose.waitUntil(10_000) { compose.onAllNodesWithText(compose.activity.getString(R.string.continue_test)).fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            } }
            compose.onNodeWithText(compose.activity.getString(R.string.continue_test)).performClick()
            compose.waitUntil { compose.onAllNodesWithTag("question_TSK02").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(compose.activity.getString(R.string.test_back)).performClick()
            compose.waitUntil { compose.onAllNodesWithTag("question_TSK01").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("44").assertIsSelected()
        } finally {
            release.complete(Unit)
            database.close()
        }
    }

    @Test
    fun invalidAndOutOfRangeAnswersCannotAdvance() {
        val config = ConfigLoader.load(compose.activity)
        val pair = config.parts.flatMap { it.questions }.first { it.id == "INT01" }
        val task = config.parts.flatMap { it.questions }.first { it.id == "TSK01" }
        val scale = config.parts.flatMap { it.questions }.first { it.id == "SPH01" }
        assertFalse(validChoice(pair, null))
        assertFalse(validChoice(pair, "R"))
        assertTrue(validChoice(pair, "B"))
        assertFalse(validChoice(task, "42"))
        assertTrue(validChoice(task, "в"))
        listOf(null, "0", "6", "invalid").forEach { assertFalse(validChoice(scale, it)) }
        assertTrue(validChoice(scale, "1"))
        assertTrue(validChoice(scale, "5"))
    }
}
