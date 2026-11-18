package ru.voploshchenie.app.data.mapper

import ru.voploshchenie.app.data.local.db.entity.TestSession as TestSessionEntity
import ru.voploshchenie.app.data.local.db.entity.TestAnswerEntity
import ru.voploshchenie.app.data.local.db.entity.TestResultEntity
import ru.voploshchenie.app.data.local.db.entity.SavedItemEntity
import ru.voploshchenie.app.domain.model.TestSession
import ru.voploshchenie.app.domain.model.StoredTestAnswer
import ru.voploshchenie.app.domain.model.SavedTestResult
import ru.voploshchenie.app.domain.model.SavedItem

internal fun TestSessionEntity.toDomain() = TestSession(id, status, currentScreen, shuffleSeed, configVersion, createdAt, updatedAt)
internal fun TestSession.toEntity() = TestSessionEntity(id, status, currentScreen, shuffleSeed, configVersion, createdAt, updatedAt)
internal fun TestAnswerEntity.toDomain() = StoredTestAnswer(sessionId, questionId, value, answeredAt)
internal fun StoredTestAnswer.toEntity() = TestAnswerEntity(sessionId, questionId, value, answeredAt)
internal fun TestResultEntity.toDomain() = SavedTestResult(sessionId, computedAt, configVersion, payload, mismatches)
internal fun SavedTestResult.toEntity() = TestResultEntity(sessionId, computedAt, configVersion, payload, mismatches)
internal fun SavedItemEntity.toDomain() = SavedItem(item_id, saved_at)
internal fun SavedItem.toEntity() = SavedItemEntity(item_id, saved_at)
