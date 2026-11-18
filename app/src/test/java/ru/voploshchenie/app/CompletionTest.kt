package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.db.TestDatabase
import ru.voploshchenie.app.domain.model.StoredTestAnswer
import ru.voploshchenie.app.data.repository.RoomTestSessionRepository
import ru.voploshchenie.app.domain.scoring.missingForCompletion
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.usecase.scoringAnswers
import ru.voploshchenie.app.domain.usecase.completeQuiz
import ru.voploshchenie.app.ui.quiz.QuizRoute
import ru.voploshchenie.app.domain.usecase.resultName
import ru.voploshchenie.app.ui.theme.AppTheme
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "w390dp-h844dp-mdpi")
class T05CompletionTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @javax.inject.Inject lateinit var database: ru.voploshchenie.app.data.local.db.TestDatabase
    @javax.inject.Inject lateinit var testConfig: ru.voploshchenie.app.domain.model.TestConfig
    @org.junit.Before fun inject() {
        hilt.inject()
    }
    @org.junit.After fun closeDatabase() {
        database.close()
    }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun resultNameUsesSeparateProfileAndKeepsLegacyFallback() {
        val rows = listOf(StoredTestAnswer("session", "PRF01", "Имя из профиля", 0))
        assertEquals("Имя из профиля", resultName(rows, TestAnswers(profile = mapOf("PRF01" to kotlinx.serialization.json.JsonPrimitive("Имя из профиля")))))
        assertEquals("Имя legacy", resultName(
            listOf(StoredTestAnswer("session", "profile", "{\"PRF01\":\"Имя legacy\"}", 0)), TestAnswers(),
        ))
        assertEquals("", resultName(rows, TestAnswers(profile = mapOf("PRF01" to kotlinx.serialization.json.JsonPrimitive("")))))
    }

    @Test fun demoButtonAbsentFromIntro() {
        compose.runOnUiThread { compose.activity.setContent {
            AppTheme { QuizRoute({}, {}) }
        } }
        compose.onNodeWithText("Заполнить демо-ответами").assertDoesNotExist()
        compose.onNodeWithText("Профориентационный тест", substring = true).assertExists()
        // Сессия грузится из базы асинхронно: дожидаемся её, чтобы тест не закончился раньше закрытия БД.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("test_start_or_continue").fetchSemanticsNodes().any { !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) } }
    }

    @Test fun missingAndInvalidAnswersDoNotSaveResults() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(compose.activity, TestDatabase::class.java).build()
        try {
            val repository = RoomTestSessionRepository(database.testDao())
            val config = testConfig
            val session = repository.start(config.version.toString())
            val demo = compose.activity.assets.open("demo_answers.json").bufferedReader().use {
                Json.decodeFromString<Map<String, String>>(it.readText())
            }
            assertTrue(missingForCompletion(scoringAnswers(demo, config)).isEmpty())
            for (invalid in listOf(demo - "TSK01", demo - "profile", demo + ("SPH01" to "6"),
                demo + ("CHK05" to "invalid"), demo + ("OPEN01" to "x".repeat(1001)))) {
                assertNull(completeQuiz(invalid, config, repository, session.id))
                assertNull(repository.result(session.id))
                assertEquals(session.id, repository.continueTest()?.first?.id)
            }
            val saved = requireNotNull(completeQuiz(demo, config, repository, session.id))
            assertEquals(session.id, saved.sessionId)
            assertNull(repository.continueTest())
        } finally { database.close() }
    }
}
