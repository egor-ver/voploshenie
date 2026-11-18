package ru.voploshchenie.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.data.local.db.TestDatabase
import ru.voploshchenie.app.data.repository.RoomSavedRepository
import ru.voploshchenie.app.data.repository.RoomTestSessionRepository
import ru.voploshchenie.app.domain.model.*
import ru.voploshchenie.app.domain.scoring.*
import ru.voploshchenie.app.domain.usecase.scoringAnswers
import ru.voploshchenie.app.ui.navigation.AppBottomBar
import ru.voploshchenie.app.ui.path.*
import ru.voploshchenie.app.ui.profile.*
import ru.voploshchenie.app.ui.quiz.*
import ru.voploshchenie.app.ui.result.*
import ru.voploshchenie.app.ui.theme.AppTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h844dp-mdpi")
class AN11RegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val config get() = ConfigLoader.load(compose.activity)

    @Test fun printComponentsShowSavedDirectionAndKeepActions() {
        val demo = compose.activity.assets.open("demo_answers.json").bufferedReader().use { Json.decodeFromString<Map<String, String>>(it.readText()) }
        val score = ScoringEngine.compute(scoringAnswers(demo, config), config)
        val data = pathProfileData(score, "Аня Ковалёва", config)
        var profile by mutableStateOf(false)
        var collections = 0
        var opened: String? = null
        val state = PathUiState.Content(data, "completed")
        compose.setContent { AppTheme {
            if (profile) ProfileScreen(state, {}, { collections++ }, onResult = { opened = it })
            else PathScreen(state, {}, { collections++ })
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.path_now).uppercase()).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.path_passed)).assertIsDisplayed()
        val cta = compose.onNodeWithText(compose.activity.getString(R.string.path_cta_collection))
        assertEquals(60f, cta.fetchSemanticsNode().boundsInRoot.height, .01f)
        cta.performClick()
        assertEquals(1, collections)
        compose.runOnIdle { profile = true }
        compose.onNodeWithText("АК").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.profile_direction).uppercase()).assertIsDisplayed()
        assertEquals(score.spheres.first { it.id == data.topSphere }.percent, data.percent)
        compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo(requireNotNull(data.percent) / 100f, 0f..1f))).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.open_result)).performScrollTo().performClick()
        assertEquals("completed", opened)
        compose.onNodeWithText(compose.activity.getString(R.string.path_cta_collection)).performClick()
        assertEquals(2, collections)
    }

    @Test fun startIsAvailableWithoutConsentAndDraftCanContinue() {
        var session by mutableStateOf<TestSessionState?>(null)
        var starts = 0
        compose.setContent { AppTheme { SessionIntro(session, { starts++ }) } }
        val start = compose.onNodeWithTag("test_start_or_continue")
        start.assertIsNotEnabled()
        compose.runOnIdle { session = TestSessionState(1, sessionId = "new") }
        start.assertIsEnabled().performScrollTo().performClick()
        assertEquals(1, starts)
        compose.runOnIdle { session = session!!.copy(answers = TestAnswers(open = mapOf("OPEN01" to "saved"))) }
        start.assertIsEnabled().performClick()
        assertEquals(2, starts)
    }

    @Test fun tasksResetScrollOnNextAndBackAndKeepSelectionOnCurrentQuestion() {
        val tasks = config.parts.flatMap { it.questions }.filter { it.id in (6..10).map { n -> "TSK%02d".format(n) } }
        var index by mutableIntStateOf(0)
        val answers = mutableStateMapOf<String, String>()
        compose.setContent {
            AppTheme {
                val q = tasks[index]
                TestQuestionScreen(q, index, tasks.size, answers[q.id], { answers[q.id] = it },
                    { index-- }, { index++ }, {}, isLastScreen = false, part = "tasks", overallPercent = 60 + index)
            }
        }
        tasks.forEachIndexed { i, q ->
            compose.onNodeWithTag("quiz_progress").assertIsDisplayed()
            val progress = compose.onNodeWithTag("quiz_progress").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("question_${q.id}").fetchSemanticsNode().boundsInRoot
            assertTrue(progress.top >= viewport.top && progress.bottom <= viewport.bottom)
            val heading = compose.onNodeWithText(requireNotNull(q.prompt)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(heading.top >= progress.bottom && heading.bottom <= viewport.bottom)
            compose.onNodeWithTag("choice_${q.options.first().id}").performScrollTo().performClick()
            compose.onNodeWithTag("choice_${q.options.first().id}").assertIsSelected().assertIsDisplayed()
            if (i < tasks.lastIndex) compose.onNodeWithText(compose.activity.getString(R.string.test_next)).performClick()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.test_back)).performClick()
        compose.onNodeWithTag("quiz_progress").assertIsDisplayed()
        compose.onNodeWithTag("choice_${tasks[3].options.first().id}").performScrollTo().assertIsSelected()
        assertEquals(5, answers.size)
    }

    @Test fun allTabLabelsFitAtLargeFontAndTabSelectionWorks() {
        var route by mutableStateOf("result/session")
        compose.setContent { AppTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                AppBottomBar(route) { route = it }
            }
        } }
        listOf("path" to R.string.tab_path, "opportunities" to R.string.tab_opportunities,
            "saved" to R.string.tab_saved, "deadlines" to R.string.tab_deadlines, "profile" to R.string.tab_profile).forEach { (tab, label) ->
            val node = compose.onNodeWithText(compose.activity.getString(label), useUnmergedTree = true)
            node.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("${layout.layoutInput.text}: size=${layout.size}, paragraph=${layout.multiParagraph.width} x ${layout.multiParagraph.height}, lines=${layout.lineCount}, width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}", layout.hasVisualOverflow)
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val panel = compose.onNodeWithTag("bottom_bar").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.top >= panel.top && bounds.bottom <= panel.bottom && bounds.left >= 0 && bounds.right <= 360)
            compose.onNodeWithTag("tab_$tab").performClick().assertIsSelected()
            assertEquals(tab, route)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun roomUpdatesPathAndRestoresDraftAndOpensUnchangedResultFromBothScreens() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val dbName = "an11-regression.db"
        compose.activity.deleteDatabase(dbName)
        var db = Room.databaseBuilder(compose.activity, TestDatabase::class.java, dbName).build()
        fun repository() = RoomTestSessionRepository(db.testDao(), config.version.toString(), config, Dispatchers.IO)
        var sessions = repository()
        val path = PathViewModel(sessions, config)
        var showProfile by mutableStateOf(false)
        var opened: String? = null
        compose.setContent { AppTheme {
            val state by path.state.collectAsState()
            val draft by path.hasDraft.collectAsState()
            if (showProfile) ProfileScreen(state, {}, {}, hasDraft = draft, onResult = { opened = it })
            else PathScreen(state, {}, {}, hasDraft = draft, onResult = { opened = it })
        } }
        try {
            compose.waitUntil(10_000) { path.state.value == PathUiState.Empty }
            compose.onNodeWithText(compose.activity.getString(R.string.path_test_description)).assertIsDisplayed()
            val demo = compose.activity.assets.open("demo_answers.json").bufferedReader().use { Json.decodeFromString<Map<String, String>>(it.readText()) }
            val answers = scoringAnswers(demo, config)
            val old = sessions.save(TestSessionState(101, answers))
            val score = ScoringEngine.compute(answers, config)
            val result = sessions.finish(requireNotNull(old.sessionId), Json.encodeToString(score), score.mismatches)
            val oldRows = sessions.answers(result.sessionId)
            RoomSavedRepository(db.savedItemDao()).save(SavedItem("saved-item", 1))
            compose.waitUntil(10_000) { (path.state.value as? PathUiState.Content)?.sessionId == old.sessionId }
            compose.onNodeWithTag("path_step_1_complete").assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(R.string.path_cta_collection)).assertIsDisplayed()
            compose.onNodeWithText(compose.activity.getString(sphereResource("it"))).assertIsDisplayed()
            val experiment = (path.state.value as PathUiState.Content).data.experiment
            assertTrue(experiment.isNotBlank())
            compose.onNodeWithText(experiment).performScrollTo().assertIsDisplayed()
            val repeat = ProfileViewModel(sessions, config).startNewSession()
            assertNotEquals(old.sessionId, repeat)
            val newQuiz = QuizViewModel(config, sessions, false, requestedSessionId = repeat)
            compose.activity.setContent { AppTheme { QuizRoute({}, {}, newQuiz) } }
            compose.waitUntil(10_000) { newQuiz.state.value.session != null }
            compose.onNodeWithTag("test_start_or_continue").performScrollTo().performClick()
            for ((id, value) in mapOf("PRF01" to "AN11Resume", "PRF03" to "Math", "PRF04" to "Vivo")) {
                compose.onNodeWithTag("text_$id").performScrollTo().performTextInput(value)
                newQuiz.awaitSaved()
            }
            val profileQuestions = config.parts.first { it.id == "profile" }.questions
            for (id in listOf("PRF02", "PRF06")) {
                val option = profileQuestions.first { it.id == id }.options.first()
                compose.onNodeWithTag("choice_${option.id}").performScrollTo().performClick()
                newQuiz.awaitSaved()
            }
            compose.onNodeWithTag("PRF05_6").performScrollTo().performClick()
            newQuiz.awaitSaved()
            compose.onNodeWithText(compose.activity.getString(R.string.test_next)).performClick()
            compose.waitUntil(10_000) { newQuiz.state.value.screen?.part == "interests" }
            val draft = requireNotNull(sessions.load(repeat))
            db.close()
            db = Room.databaseBuilder(compose.activity, TestDatabase::class.java, dbName).build()
            sessions = repository()
            val resumedId = ProfileViewModel(sessions, config).startNewSession()
            assertEquals(repeat, resumedId)
            val quiz = QuizViewModel(config, sessions, false, requestedSessionId = resumedId)
            compose.waitUntil(10_000) { quiz.state.value.session != null }
            quiz.awaitSaved()
            assertEquals(repeat, quiz.state.value.session?.sessionId)
            assertEquals("interests", quiz.state.value.screen?.part)
            quiz.back()
            compose.waitUntil(10_000) { quiz.state.value.screen?.part == "profile" }
            assertEquals(draft.answers.profile, quiz.state.value.session?.answers?.profile)
            val coldPath = PathViewModel(sessions, config)
            compose.waitUntil(10_000) { coldPath.state.value is PathUiState.Content && coldPath.hasDraft.value }
            compose.activity.setContent { AppTheme {
                val state by coldPath.state.collectAsState()
                if (showProfile) ProfileScreen(state, {}, {}, hasDraft = true, onResult = { opened = it })
                else PathScreen(state, {}, {}, hasDraft = true, onResult = { opened = it })
            } }
            for (profile in listOf(false, true)) {
                compose.runOnIdle { showProfile = profile }
                val open = compose.onNodeWithText(compose.activity.getString(R.string.open_result))
                if (profile) open.performScrollTo()
                open.assertIsDisplayed().performClick()
                assertEquals(result.sessionId, opened)
                val vm = ResultViewModel(sessions, config, SavedStateHandle(mapOf("sessionId" to opened)))
                compose.waitUntil(10_000) { vm.state.value is ResultUiState.Content }
                val content = vm.state.value as ResultUiState.Content
                assertEquals(score, content.result)
                assertEquals(answers, content.answers)
                assertEquals(result.computedAt, content.computedAt)
                assertEquals("Аня", content.name)
                val continueTest = compose.onNodeWithText(compose.activity.getString(R.string.continue_test))
                if (profile) continueTest.performScrollTo()
                continueTest.assertIsDisplayed()
            }
            assertEquals(result, sessions.latestResult())
            assertEquals(oldRows, sessions.answers(result.sessionId))
            assertEquals(listOf("saved-item"), RoomSavedRepository(db.savedItemDao()).observeAll().first().map { it.item_id })
            assertEquals(2, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM test_session").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(1, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM test_result").use { it.moveToFirst(); it.getInt(0) })
            val secondAnswers = answers.copy(profile = answers.profile + ("PRF01" to kotlinx.serialization.json.JsonPrimitive("AN11Second")))
            sessions.save(draft.copy(answers = secondAnswers))
            val second = sessions.finish(repeat, Json.encodeToString(score), score.mismatches)
            compose.waitUntil(10_000) { (coldPath.state.value as? PathUiState.Content)?.sessionId == second.sessionId }
            assertEquals("AN11Second", (coldPath.state.value as PathUiState.Content).data.name)
            compose.onNodeWithText("AN11Second").performScrollTo().assertIsDisplayed()
            assertFalse(coldPath.hasDraft.value)
            assertEquals(result, sessions.result(result.sessionId))
            assertEquals(oldRows, sessions.answers(result.sessionId))
            assertEquals(listOf("saved-item"), RoomSavedRepository(db.savedItemDao()).observeAll().first().map { it.item_id })
        } finally { db.close(); compose.activity.deleteDatabase(dbName); Dispatchers.resetMain() }
    }
}
