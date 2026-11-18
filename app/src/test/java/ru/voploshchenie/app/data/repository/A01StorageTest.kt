package ru.voploshchenie.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.voploshchenie.app.data.local.db.TestDatabase
import ru.voploshchenie.app.domain.model.SavedItem
import ru.voploshchenie.app.domain.repository.TestSessionRepository

@RunWith(RobolectricTestRunner::class)
class A01StorageTest {
    private lateinit var database: TestDatabase
    private lateinit var repository: TestSessionRepository
    private var time = 100L

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TestDatabase::class.java).build()
        repository = RoomTestSessionRepository(database.testDao()) { time++ }
    }

    @After fun tearDown() = database.close()

    @Test fun domainSessionAnswersAndResultPreserveStoredFields() = runBlocking {
        assertNull(repository.continueTest())
        assertNull(repository.result("missing"))
        assertNull(repository.session("missing"))
        assertEquals(emptyList<Any>(), repository.answers("missing"))
        val session = repository.start("cfg-1", Long.MIN_VALUE)
        assertEquals(session, repository.session(session.id))
        assertEquals(100L, session.createdAt)
        assertEquals(100L, session.updatedAt)
        repository.saveScreen(session.id, "PRF01", mapOf("PRF01" to "\"Имя\"", "PRF02" to ""))
        val restored = requireNotNull(repository.continueTest())
        assertEquals(session.copy(currentScreen = "PRF01", updatedAt = 101), restored.first)
        assertEquals(listOf("\"Имя\"", ""), restored.second.map { it.value })
        assertTrue(restored.second.all { it.sessionId == session.id && it.answeredAt == 101L })
        repository.saveScreen(session.id, "INT01", emptyMap(), replaceAnswers = true)
        assertTrue(repository.answers(session.id).isEmpty())
        val result = repository.finish(session.id, "{\"score\":7}", 2)
        assertEquals(session.id, result.sessionId)
        assertEquals(103L, result.computedAt)
        assertEquals("cfg-1", result.configVersion)
        assertEquals("{\"score\":7}", result.payload)
        assertEquals(2, result.mismatches)
        assertEquals(result, repository.latestResult())
        assertEquals(result, repository.result(session.id))
        assertEquals(session.copy(status = "completed", currentScreen = "INT01", updatedAt = 103), repository.session(session.id))
        assertNull(repository.continueTest())
        try {
            repository.finish(session.id, "different", 0)
            fail("A second result must not overwrite the stored result")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            assertEquals(result, repository.result(session.id))
            assertEquals(103L, repository.session(session.id)?.updatedAt)
        }
    }

    @Test fun savedRepositoryMapsOrdersReplacesAndRemovesItems() = runBlocking {
        val saved = RoomSavedRepository(database.savedItemDao())
        assertTrue(saved.observeAll().first().isEmpty())
        assertFalse(saved.observeSaved("first").first())
        saved.save(SavedItem("first", 1))
        saved.save(SavedItem("second", 2))
        assertEquals(listOf(SavedItem("second", 2), SavedItem("first", 1)), saved.observeAll().first())
        saved.save(SavedItem("first", 3))
        assertEquals(listOf(SavedItem("first", 3), SavedItem("second", 2)), saved.observeAll().first())
        assertTrue(saved.observeSaved("first").first())
        saved.remove("first")
        saved.remove("missing")
        assertFalse(saved.observeSaved("first").first())
        assertEquals(listOf(SavedItem("second", 2)), saved.observeAll().first())
    }
}
