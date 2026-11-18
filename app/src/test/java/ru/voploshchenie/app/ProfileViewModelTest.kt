package ru.voploshchenie.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.data.local.db.TestDatabase
import ru.voploshchenie.app.data.repository.RoomSavedRepository
import ru.voploshchenie.app.data.repository.RoomTestSessionRepository
import ru.voploshchenie.app.domain.model.SavedItem
import ru.voploshchenie.app.domain.model.TestSessionState
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.scoring.ScoringEngine
import ru.voploshchenie.app.domain.usecase.scoringAnswers
import ru.voploshchenie.app.ui.profile.ProfileViewModel
import ru.voploshchenie.app.ui.quiz.QuizViewModel

@RunWith(RobolectricTestRunner::class)
class V02ProfileViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val config = ConfigLoader.load(context)
    private val database = Room.inMemoryDatabaseBuilder(context, TestDatabase::class.java).build()

    @After fun closeDatabase() { database.close() }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun requestedOlderAttemptRestoresItsAnswersAndDoesNotChangeNewerAttemptResultOrSavedItems() = runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val sessions = RoomTestSessionRepository(database.testDao(), config.version.toString(), config)
            val saved = RoomSavedRepository(database.savedItemDao())
            val answersA = TestAnswers(open = mapOf("OPEN01" to "answer A"))
            val sessionA = sessions.start(config.version.toString(), 101)
            sessions.save(TestSessionState(101, answersA, setOf("INT01", "INT02"), "INT02", sessionId = sessionA.id))
            val resultA = sessions.finish(sessionA.id, "prior result", 0)
            saved.save(SavedItem("saved-item", 1))

            val answersB = TestAnswers(open = mapOf("OPEN01" to "answer B"))
            val sessionB = sessions.start(config.version.toString(), 202)
            val stateB = sessions.save(TestSessionState(202, answersB, setOf("PRF01"), "PRF01", sessionId = sessionB.id))
            val rowsB = sessions.answers(sessionB.id)

            val restored = QuizViewModel(config, sessions, resumeCurrentScreen = false, requestedSessionId = sessionA.id)
            advanceUntilIdle()
            restored.awaitSaved()
            val afterInit = requireNotNull(sessions.load(sessionA.id))
            assertEquals(answersA, afterInit.answers)
            assertEquals(101L, afterInit.shuffle_seed)
            assertEquals(restored.state.value.screen?.id, afterInit.currentScreenId)
            assertEquals(setOf("INT01", "INT02"), afterInit.visitedScreens)
            assertEquals(stateB, sessions.load())
            assertEquals(rowsB, sessions.answers(sessionB.id))

            restored.updateAnswers { it.copy(open = it.open + ("OPEN02" to "answer A continued")) }
            advanceUntilIdle()
            restored.awaitSaved()
            val afterSave = requireNotNull(sessions.load(sessionA.id))
            assertEquals("answer A", afterSave.answers.open["OPEN01"])
            assertEquals("answer A continued", afterSave.answers.open["OPEN02"])
            assertEquals(101L, afterSave.shuffle_seed)
            assertEquals(restored.state.value.screen?.id, afterSave.currentScreenId)
            assertEquals(setOf("INT01", "INT02"), afterSave.visitedScreens)
            assertEquals(stateB, sessions.load())
            assertEquals(rowsB, sessions.answers(sessionB.id))
            assertEquals(resultA, sessions.result(sessionA.id))
            assertEquals(listOf("saved-item"), saved.observeAll().first().map { it.item_id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun missingRequestedAttemptSetsErrorWithoutWritingActiveAttempt() = runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val sessions = RoomTestSessionRepository(database.testDao(), config.version.toString(), config)
            val active = sessions.save(TestSessionState(303, TestAnswers(open = mapOf("OPEN01" to "keep")), currentScreenId = "OPEN01"))
            val before = sessions.answers(requireNotNull(active.sessionId))
            val restored = QuizViewModel(config, sessions, resumeCurrentScreen = false, requestedSessionId = "missing")
            advanceUntilIdle()
            restored.awaitSaved()
            assertNotNull(restored.state.value.error)
            assertNull(restored.state.value.session)
            assertEquals(active, sessions.load())
            assertEquals(before, sessions.answers(requireNotNull(active.sessionId)))
            assertTrue(sessions.answers("missing").isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun repeatCreatesEmptySessionWithNewIdWithoutChangingResultOrSavedItems() = runBlocking {
        val sessions = RoomTestSessionRepository(database.testDao(), config.version.toString(), config)
        val saved = RoomSavedRepository(database.savedItemDao())
        val demo = context.assets.open("demo_answers.json").bufferedReader().use {
            Json.decodeFromString<Map<String, String>>(it.readText())
        }
        val answers = scoringAnswers(demo, config)
        val old = sessions.start(config.version.toString())
        sessions.save(TestSessionState(old.shuffleSeed, answers, currentScreenId = "profile", sessionId = old.id))
        val score = ScoringEngine.compute(answers, config)
        sessions.finish(old.id, Json.encodeToString(score), score.mismatches)
        saved.save(SavedItem("saved-item", 1))
        val newId = ProfileViewModel(sessions, config).startNewSession()
        assertNotEquals(old.id, newId)
        assertEquals(TestSessionState(sessions.session(newId)!!.shuffleSeed, sessionId = newId), sessions.load())
        assertEquals(old.id, sessions.latestResult()!!.sessionId)
        assertEquals(listOf("saved-item"), saved.observeAll().first().map { it.item_id })
    }
}
