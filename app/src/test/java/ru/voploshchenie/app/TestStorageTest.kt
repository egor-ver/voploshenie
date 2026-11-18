package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.db.TestDatabase
import ru.voploshchenie.app.data.local.db.entity.TestAnswerEntity
import ru.voploshchenie.app.data.repository.RoomTestSessionRepository
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class T01StorageTest {
    private lateinit var database: TestDatabase
    private lateinit var repository: RoomTestSessionRepository
    private var time = 100L

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TestDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomTestSessionRepository(database.testDao()) { time++ }
    }

    @After fun tearDown() = database.close()

    @Test fun savesAndRestoresScreenAndAnswersIdempotently() = runBlocking {
        val session = repository.start("cfg-1", 42)
        repository.saveScreen(session.id, "profile", mapOf("PRF01" to "???", "PRF02" to "3"))
        val firstSave = database.testDao().answers(session.id)
        repository.saveScreen(session.id, "profile", mapOf("PRF01" to "???", "PRF02" to "4"))
        val resumed = repository.continueTest()!!

        assertEquals("profile", resumed.first.currentScreen)
        assertEquals(42L, resumed.first.shuffleSeed)
        assertEquals(listOf("???", "4"), resumed.second.map { it.value })
        assertEquals(2, resumed.second.size)
        assertNotEquals(firstSave.last().answeredAt, resumed.second.last().answeredAt)
    }

    @Test fun attemptsAndCompletedResultVersionsRemainSeparate() = runBlocking {
        val first = repository.start("old-config", 1)
        val second = repository.start("new-config", 2)
        val result = repository.finish(first.id, "{\"score\":7}", 2)

        assertEquals("old-config", result.configVersion)
        assertEquals(result, repository.latestResult())
        assertNull(database.testDao().session(first.id)?.let { if (it.status == "in_progress") it else null })
        assertEquals(second.id, repository.continueTest()!!.first.id)
        assertNotNull(database.testDao().session(second.id))
        assertFalse(first.id == second.id)
        assertNull(database.testDao().result(second.id))
    }

    @Test fun savingAnswersForMissingSessionDoesNotPersistPartialProgress() = runBlocking {
        try {
            repository.saveScreen("missing", "profile", mapOf("PRF01" to "???"))
            throw AssertionError("Saving a missing session must fail")
        } catch (expected: IllegalArgumentException) {
            assertEquals(emptyList<TestAnswerEntity>(), database.testDao().answers("missing"))
        }
    }
}
