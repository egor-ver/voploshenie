package ru.voploshchenie.app.data.repository

import ru.voploshchenie.app.domain.repository.TestSessionRepository
import ru.voploshchenie.app.data.local.db.dao.TestDao
import ru.voploshchenie.app.data.mapper.toDomain
import ru.voploshchenie.app.data.mapper.toEntity
import ru.voploshchenie.app.domain.model.TestSession
import ru.voploshchenie.app.domain.model.StoredTestAnswer
import ru.voploshchenie.app.domain.model.SavedTestResult
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import ru.voploshchenie.app.di.IoDispatcher
import ru.voploshchenie.app.domain.model.TestSessionState

import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.usecase.decodeStoredAnswers
import ru.voploshchenie.app.domain.usecase.storedAnswerSections
import ru.voploshchenie.app.domain.usecase.scoringAnswers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

class RoomTestSessionRepository(
    private val dao: TestDao,
    private val configVersion: String? = null,
    private val config: TestConfig? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : TestSessionRepository {
    @Inject
    constructor(dao: TestDao, config: TestConfig,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(dao, config.version.toString(), config, ioDispatcher = ioDispatcher)

    override fun observeChanges() = dao.observeSessions().map { Unit }

    override suspend fun start(configVersion: String, shuffleSeed: Long): TestSession {
        val time = now()
        val session = TestSession(UUID.randomUUID().toString(), "in_progress", "", shuffleSeed, configVersion, time, time)
        dao.insertSession(session.toEntity())
        return session
    }

    override suspend fun saveScreen(sessionId: String, screen: String, answers: Map<String, String>, replaceAnswers: Boolean) {
        val time = now()
        val entities = answers.map { (id, value) -> StoredTestAnswer(sessionId, id, value, time).toEntity() }
        if (replaceAnswers) dao.replaceScreen(sessionId, screen, entities, time)
        else dao.saveScreen(sessionId, screen, entities, time)
    }

    override suspend fun continueTest(): Pair<TestSession, List<StoredTestAnswer>>? = dao.activeSession()?.let { it.toDomain() to dao.answers(it.id).map { row -> row.toDomain() } }

    override suspend fun finish(sessionId: String, payload: String, mismatches: Int): SavedTestResult {
        val session = requireNotNull(dao.session(sessionId))
        return SavedTestResult(sessionId, now(), session.configVersion, payload, mismatches).also { dao.complete(it.toEntity()) }
    }

    override suspend fun latestResult(): SavedTestResult? = dao.latestResult()?.toDomain()
    override suspend fun result(sessionId: String): SavedTestResult? = dao.result(sessionId)?.toDomain()
    override suspend fun answers(sessionId: String): List<StoredTestAnswer> = dao.answers(sessionId).map { it.toDomain() }
    override suspend fun session(sessionId: String): TestSession? = dao.session(sessionId)?.toDomain()

    override suspend fun load(): TestSessionState? = withContext(ioDispatcher) {
        continueTest()?.let { (session, rows) ->
            val needsMigration = rows.firstOrNull { it.questionId == FORMAT_KEY }?.value?.let {
                Json.parseToJsonElement(it).jsonPrimitive.contentOrNull
            } != FORMAT
            val stored = rows.associate { row -> row.questionId to try {
                Json.parseToJsonElement(row.value)
            } catch (error: SerializationException) {
                // Legacy answers contain unquoted choice IDs and free text.
                if (!needsMigration) throw error
                Json.encodeToJsonElement(row.value)
            } }
            val current = session.currentScreen.takeIf { it.isNotEmpty() }
            val migrated = if (needsMigration && config != null) try {
                val legacy = buildMap {
                        stored.filterKeys { it != FORMAT_KEY && it !in setOf("visitedScreens", "finished") }
                        .forEach { (key, value) ->
                            val group = value as? JsonObject
                            if (group != null && key == "profile") put(key, Json.encodeToString(group.mapValues { it.value.primitiveOrJson() }))
                            else if (group != null && key in setOf("values", "needs")) put(key, Json.encodeToString(value))
                            else if (group != null && key == "experience") group.forEach { (id, answer) -> put(id, legacyExperience(answer)) }
                            else if (key.startsWith("EXP")) put(key, legacyExperience(value))
                            else if (group != null) group.forEach { (id, answer) -> put(id, answer.primitiveOrJson()) }
                            else put(key, value.primitiveOrJson())
                        }
                }
                val knownIds = requireNotNull(config).parts.flatMap { it.questions }.map { it.id }.toSet() +
                    setOf("profile", "values", "needs/pick3", "needsTop3")
                require(legacy.keys.all { it in knownIds })
                scoringAnswers(legacy, requireNotNull(config))
            } catch (error: Exception) {
                throw IllegalStateException("Legacy test session could not be migrated", error)
            } else null
            val state = TestSessionState(session.shuffleSeed, migrated ?: decodeStoredAnswers(stored),
                stored["visitedScreens"]?.let { Json.decodeFromJsonElement<Set<String>>(it) } ?: emptySet(),
                current, stored["finished"]?.let { Json.decodeFromJsonElement<Boolean>(it) } ?: false, session.id)
            if (migrated != null) save(state)
            state
        }
    }

    override suspend fun load(sessionId: String): TestSessionState? = withContext(ioDispatcher) {
        val session = dao.session(sessionId)?.toDomain() ?: return@withContext null
        val stored = dao.answers(sessionId).associate { row -> row.questionId to try {
            Json.parseToJsonElement(row.value)
        } catch (error: SerializationException) {
            Json.encodeToJsonElement(row.value)
        } }
        val answers = decodeStoredAnswers(stored)
        val visited = stored["visitedScreens"]?.let { Json.decodeFromJsonElement<Set<String>>(it) } ?: emptySet()
        val finished = stored["finished"]?.let { Json.decodeFromJsonElement<Boolean>(it) } ?: false
        TestSessionState(session.shuffleSeed, answers, visited, session.currentScreen, finished, sessionId)
    }

    override suspend fun save(session: TestSessionState): TestSessionState = withContext(ioDispatcher) {
        val id = session.sessionId ?: start(requireNotNull(configVersion), session.shuffle_seed).id
        val answers = Json.encodeToJsonElement(session.answers).jsonObject
        val stored = buildMap {
            storedAnswerSections.keys.forEach { field -> answers[field]?.jsonObject?.let { putAll(it) } }
            for (field in listOf("valuesTop3", "valuesLeast2")) answers[field]?.let { put(field, it) }
            answers["needs"]?.jsonObject?.let { needs ->
                needs["ratings"]?.jsonObject?.let { putAll(it) }
                needs["top3"]?.let { put("needsTop3", it) }
            }
            put("visitedScreens", Json.encodeToJsonElement(session.visitedScreens))
            put("finished", Json.encodeToJsonElement(session.finished))
            put(FORMAT_KEY, Json.encodeToJsonElement(FORMAT))
        }.mapValues { (_, value) -> Json.encodeToString(value) }
        saveScreen(id, session.currentScreenId.orEmpty(), stored, replaceAnswers = true)
        session.copy(sessionId = id)
    }

    private companion object {
        const val FORMAT_KEY = "__format"
        const val FORMAT = "T02"
    }
}

private fun kotlinx.serialization.json.JsonElement.primitiveOrJson(): String =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: Json.encodeToString(this)

private fun legacyExperience(value: kotlinx.serialization.json.JsonElement): String {
    val fields = value.jsonObject.toMutableMap()
    fields.putIfAbsent("energy", kotlinx.serialization.json.JsonPrimitive(0))
    return Json.encodeToString(JsonObject(fields))
}
