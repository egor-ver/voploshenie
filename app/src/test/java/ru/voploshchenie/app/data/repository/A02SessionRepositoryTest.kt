package ru.voploshchenie.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.voploshchenie.app.data.local.db.TestDatabase
import ru.voploshchenie.app.domain.model.TestSessionState
import ru.voploshchenie.app.domain.repository.TestSessionRepository
import ru.voploshchenie.app.domain.scoring.TestAnswers

@RunWith(RobolectricTestRunner::class)
class A02SessionRepositoryTest {
    private lateinit var database: TestDatabase
    private lateinit var repository: TestSessionRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TestDatabase::class.java).build()
        repository = RoomTestSessionRepository(database.testDao(), "original")
    }

    @After fun tearDown() = database.close()

    @Test fun resumeAndFinishShareAttemptAnswersAndOriginalConfigVersion() = runBlocking {
        val answers = TestAnswers(open = mapOf("OPEN01" to "draft"))
        val saved = repository.save(TestSessionState(Long.MIN_VALUE, answers, setOf("profile"), "OPEN01"))
        val id = requireNotNull(saved.sessionId)
        val reopened: TestSessionRepository = RoomTestSessionRepository(database.testDao(), "updated")
        assertEquals(saved, reopened.load())
        assertEquals(id, reopened.continueTest()?.first?.id)
        val final = reopened.save(saved.copy(answers = answers.copy(open = mapOf("OPEN01" to "final")), finished = true))
        assertEquals(id, final.sessionId)
        assertEquals(final, repository.load())
        assertEquals("\"final\"", repository.answers(id).single { it.questionId == "OPEN01" }.value)
        val result = reopened.finish(id, "{\"score\":7}", 2)
        assertEquals("original", result.configVersion)
        assertEquals(result, repository.result(id))
        assertEquals(result, repository.latestResult())
        assertEquals("completed", repository.session(id)?.status)
        assertNull(repository.load())
        assertNull(repository.continueTest())
        val next = repository.save(TestSessionState(42, currentScreenId = "profile"))
        assertNotEquals(id, next.sessionId)
        assertEquals(next, reopened.load())
        assertEquals(result, reopened.result(id))
    }

    @Test fun failedReplacementDoesNotAffectActiveAttempt() = runBlocking {
        val saved = repository.save(TestSessionState(73, TestAnswers(pairs = mapOf("INT01" to "R")), currentScreenId = "INT02"))
        val rows = repository.answers(requireNotNull(saved.sessionId))
        try {
            repository.save(saved.copy(sessionId = "missing", answers = TestAnswers()))
            fail("Saving a missing attempt must fail")
        } catch (expected: IllegalArgumentException) {
            assertEquals(saved, repository.load())
            assertEquals(rows, repository.answers(saved.sessionId))
            assertTrue(repository.answers("missing").isEmpty())
        }
    }
    @Test fun corruptCurrentAnswersFailWithoutReplacingStoredRows() = runBlocking {
        val saved = repository.save(TestSessionState(73, TestAnswers(open = mapOf("OPEN01" to "draft")), currentScreenId = "OPEN01"))
        val id = requireNotNull(saved.sessionId)
        for (invalid in listOf("{", "[]")) {
            repository.saveScreen(id, "OPEN01", mapOf("OPEN01" to invalid))
            val rows = repository.answers(id)
            try {
                repository.load()
                fail("Corrupt current answers must not become an empty test")
            } catch (expected: kotlinx.serialization.SerializationException) {
                assertEquals(rows, repository.answers(id))
                assertEquals(id, repository.continueTest()?.first?.id)
            }
        }
    }

    @Test fun currentFormatMarkerAllowsJsonWhitespace() = runBlocking {
        val saved = repository.save(TestSessionState(73, TestAnswers(open = mapOf("OPEN01" to "draft")), currentScreenId = "OPEN01"))
        repository.saveScreen(requireNotNull(saved.sessionId), "OPEN01", mapOf("__format" to "  \"T02\"  "))
        assertEquals(saved, repository.load())
    }

}
