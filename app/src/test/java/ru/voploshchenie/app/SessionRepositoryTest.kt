package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.data.local.db.TestDatabase
import ru.voploshchenie.app.data.repository.RoomTestSessionRepository
import ru.voploshchenie.app.domain.repository.TestSessionRepository
import ru.voploshchenie.app.domain.model.TestSessionState
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.scoring.ExperienceAnswer
import ru.voploshchenie.app.domain.scoring.NeedsAnswers
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class T02SessionRepositoryTest {
    private lateinit var database: TestDatabase
    private lateinit var storage: TestSessionRepository
    private lateinit var repository: TestSessionRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TestDatabase::class.java)
            .allowMainThreadQueries().build()
        storage = RoomTestSessionRepository(database.testDao())
        repository = RoomTestSessionRepository(database.testDao(), "1")
    }

    @After fun tearDown() = database.close()

    @Test fun restoresEveryAnswerTypeAndNavigation() = runBlocking {
        assertNull(repository.load())
        val answers = TestAnswers(
            profile = mapOf("PRF01" to JsonPrimitive("Имя"), "PRF05" to JsonPrimitive(6)),
            pairs = mapOf("INT01" to "R"), checks = mapOf("CHK01" to "I"),
            valuesTop3 = listOf("VAL03", "VAL01", "VAL02"), valuesLeast2 = listOf("VAL04", "VAL05"),
            interest = mapOf("SPH01" to 5), tasks = mapOf("TSK01" to "B"),
            experience = mapOf("EXP01" to ExperienceAnswer("Кейс", 3)),
            nad = mapOf("NAD01" to "Ответ", "NAD07" to "Текст"),
            needs = NeedsAnswers(mapOf("NEED01" to 4), listOf("NEED02", "NEED03", "NEED01")),
            open = mapOf("OPEN01" to "Слова"),
        )
        val saved = repository.save(TestSessionState(73, answers, setOf("profile", "INT01"), "INT02"))
        assertNotNull(saved.sessionId)
        val restored = RoomTestSessionRepository(database.testDao(), "2").load()
        assertEquals(saved, restored)
        val persisted = requireNotNull(storage.continueTest())
        assertEquals(saved.sessionId, persisted.first.id)
        assertEquals("1", persisted.first.configVersion)
        assertEquals(73L, persisted.first.shuffleSeed)
        assertEquals("INT02", persisted.first.currentScreen)
        assertEquals("\"R\"", persisted.second.single { it.questionId == "INT01" }.value)
    }

    @Test fun clearingAnswersDoesNotRestoreStaleRowsOrCreateAnotherAttempt() = runBlocking {
        val saved = repository.save(TestSessionState(73,
            TestAnswers(pairs = mapOf("INT01" to "R"), open = mapOf("OPEN01" to "Слова")),
            setOf("profile"), "INT01"))
        val cleared = repository.save(saved.copy(answers = TestAnswers(), finished = true))
        assertEquals(saved.sessionId, cleared.sessionId)
        assertEquals(cleared, repository.load())
        assertFalse(database.testDao().answers(requireNotNull(saved.sessionId)).any { it.questionId in listOf("INT01", "OPEN01") })
        assertNull(database.testDao().result(requireNotNull(saved.sessionId)))
    }

    @Test fun legacyPairMigratesOnceAndKeepsAttemptIdentity() = runBlocking {
        val config = ConfigLoader.load(ApplicationProvider.getApplicationContext())
        val old = storage.start("1", 73)
        storage.saveScreen(old.id, "INT01", mapOf("INT01" to "A", "CHK01" to "B", "OPEN01" to "draft"))
        val migratedRepository = RoomTestSessionRepository(database.testDao(), "1", config)
        val first = requireNotNull(migratedRepository.load())
        assertEquals(old.id, first.sessionId)
        assertEquals(73L, first.shuffle_seed)
        assertEquals(config.parts.first { it.id == "interests" }.questions.first { it.id == "INT01" }.type_a,
            first.answers.pairs["INT01"])
        assertEquals(config.parts.first { it.id == "consistency" }.questions.first { it.id == "CHK01" }.type_b, first.answers.checks["CHK01"])
        assertEquals(first, migratedRepository.load())
        assertEquals("T02", database.testDao().answers(old.id).single { it.questionId == "__format" }.value.trim('"'))
    }

    @Test fun legacyGroupedAnswersMigrateWithoutLosingTextOrOverwritingAttempt() = runBlocking {
        val config = ConfigLoader.load(ApplicationProvider.getApplicationContext())
        val old = storage.start("1", 91)
        storage.saveScreen(old.id, "profile", mapOf(
            "profile" to "{\"PRF01\":\"Имя\\\" с\\\\путём\\nи строкой\",\"PRF05\":7}",
            "values" to "{\"top\":[\"VAL01\",\"VAL02\",\"VAL03\"],\"least\":[\"VAL04\",\"VAL05\"]}",
            "experience" to "{\"EXP01\":{\"text\":\"Сделал проект\"},\"EXP02\":{\"text\":\"С энергией\",\"energy\":4}}",
            "INT01" to "A", "CHK01" to "B", "OPEN01" to "черновик"
        ))
        val migrated = RoomTestSessionRepository(database.testDao(), "1", config)
        val state = requireNotNull(migrated.load())
        assertEquals(old.id, state.sessionId)
        assertEquals(91L, state.shuffle_seed)
        assertEquals(JsonPrimitive("Имя\" с\\путём\nи строкой"), state.answers.profile.getValue("PRF01"))
        assertEquals(config.parts.first { it.id == "interests" }.questions.first { it.id == "INT01" }.type_a,
            state.answers.pairs["INT01"])
        assertEquals(config.parts.first { it.id == "consistency" }.questions.first { it.id == "CHK01" }.type_b,
            state.answers.checks["CHK01"])
        assertEquals("черновик", state.answers.open["OPEN01"])
        assertEquals(state, migrated.load())
        assertEquals(ExperienceAnswer("Сделал проект", 0), state.answers.experience["EXP01"])
        assertEquals(ExperienceAnswer("С энергией", 4), state.answers.experience["EXP02"])
        assertEquals(state, migrated.load())
        assertEquals("T02", database.testDao().answers(old.id).single { it.questionId == "__format" }.value.trim('"'))
    }

    @Test fun legacyDirectExperienceKeepsIdDefaultsEnergyAndFailedMigrationPreservesAttempt() = runBlocking {
        val config = ConfigLoader.load(ApplicationProvider.getApplicationContext())
        val old = storage.start("1", 92)
        storage.saveScreen(old.id, "EXP01", mapOf(
            "EXP01" to "{\"text\":\"Без оценки\"}",
            "EXP02" to "{\"text\":\"Полный ответ\",\"energy\":5}"
        ))
        val migrated = RoomTestSessionRepository(database.testDao(), "1", config)
        val state = requireNotNull(migrated.load())
        assertEquals(ExperienceAnswer("Без оценки", 0), state.answers.experience["EXP01"])
        assertEquals(ExperienceAnswer("Полный ответ", 5), state.answers.experience["EXP02"])
        assertEquals(state, migrated.load())
        assertEquals("T02", database.testDao().answers(old.id).single { it.questionId == "__format" }.value.trim('"'))

        val failed = storage.start("1", 93)
        storage.saveScreen(failed.id, "EXP01", mapOf("EXP01" to "{\"text\":\"draft\"}", "unknown" to "x"))
        val failedRows = database.testDao().answers(failed.id).toList()
        assertNull(runCatching { RoomTestSessionRepository(database.testDao(), "1", config).load() }.getOrNull())
        assertEquals(failedRows, database.testDao().answers(failed.id).toList())
    }
}
