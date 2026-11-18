package ru.voploshchenie.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ru.voploshchenie.app.data.local.db.entity.TestSession
import ru.voploshchenie.app.data.local.db.entity.TestAnswerEntity
import ru.voploshchenie.app.data.local.db.entity.TestResultEntity

@Dao
interface TestDao {
    @Query("SELECT * FROM test_session ORDER BY updatedAt DESC")
    fun observeSessions(): kotlinx.coroutines.flow.Flow<List<TestSession>>

    @Insert
    suspend fun insertSession(session: TestSession)

    @Query("SELECT * FROM test_session WHERE id = :id")
    suspend fun session(id: String): TestSession?

    @Upsert
    suspend fun updateSession(session: TestSession)

    @Query("SELECT * FROM test_session WHERE status = 'in_progress' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun activeSession(): TestSession?

    @Upsert
    suspend fun saveAnswers(answers: List<TestAnswerEntity>)

    @Query("DELETE FROM answer WHERE sessionId = :sessionId")
    suspend fun deleteAnswers(sessionId: String)

    @Transaction
    suspend fun saveScreen(sessionId: String, screen: String, answers: List<TestAnswerEntity>, updatedAt: Long) {
        val session = requireNotNull(session(sessionId))
        saveAnswers(answers)
        updateSession(session.copy(currentScreen = screen, updatedAt = updatedAt))
    }

    @Transaction
    suspend fun replaceScreen(sessionId: String, screen: String, answers: List<TestAnswerEntity>, updatedAt: Long) {
        requireNotNull(session(sessionId))
        deleteAnswers(sessionId)
        saveScreen(sessionId, screen, answers, updatedAt)
    }

    @Query("SELECT * FROM answer WHERE sessionId = :sessionId ORDER BY questionId")
    suspend fun answers(sessionId: String): List<TestAnswerEntity>

    @Insert
    suspend fun insertResult(result: TestResultEntity)

    @Query("UPDATE test_session SET status = 'completed', updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun markCompleted(sessionId: String, updatedAt: Long)

    @Query("SELECT * FROM test_result WHERE sessionId = :sessionId")
    suspend fun result(sessionId: String): TestResultEntity?

    @Query("SELECT * FROM test_result ORDER BY computedAt DESC LIMIT 1")
    suspend fun latestResult(): TestResultEntity?

    @Transaction
    suspend fun complete(result: TestResultEntity) {
        insertResult(result)
        markCompleted(result.sessionId, result.computedAt)
    }
}
