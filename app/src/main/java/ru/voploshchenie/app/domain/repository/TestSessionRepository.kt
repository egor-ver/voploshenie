package ru.voploshchenie.app.domain.repository

import ru.voploshchenie.app.domain.model.TestSessionState
import java.util.UUID
import ru.voploshchenie.app.domain.model.TestSession
import ru.voploshchenie.app.domain.model.StoredTestAnswer
import ru.voploshchenie.app.domain.model.SavedTestResult

interface TestSessionRepository {
    fun observeChanges(): kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.flowOf(Unit)
    suspend fun load(): TestSessionState?
    suspend fun load(sessionId: String): TestSessionState? = load()?.takeIf { it.sessionId == sessionId }
    suspend fun save(session: TestSessionState): TestSessionState
    suspend fun start(configVersion: String, shuffleSeed: Long = UUID.randomUUID().mostSignificantBits): TestSession
    suspend fun saveScreen(sessionId: String, screen: String, answers: Map<String, String>, replaceAnswers: Boolean = false)
    suspend fun continueTest(): Pair<TestSession, List<StoredTestAnswer>>?
    suspend fun finish(sessionId: String, payload: String, mismatches: Int): SavedTestResult
    suspend fun latestResult(): SavedTestResult?
    suspend fun result(sessionId: String): SavedTestResult?
    suspend fun answers(sessionId: String): List<StoredTestAnswer>
    suspend fun session(sessionId: String): TestSession?
}
