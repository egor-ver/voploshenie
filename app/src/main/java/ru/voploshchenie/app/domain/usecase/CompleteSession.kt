package ru.voploshchenie.app.domain.usecase

import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.model.TestSessionState
import ru.voploshchenie.app.domain.repository.TestSessionRepository
import ru.voploshchenie.app.domain.scoring.ScoringEngine

class CompleteSession @Inject constructor(private val config: TestConfig, private val repository: TestSessionRepository) {
    suspend operator fun invoke(session: TestSessionState): String {
        val id = requireNotNull(session.sessionId)
        val result = ScoringEngine.compute(session.answers, config)
        repository.finish(id, Json.encodeToString(result), result.mismatches)
        return id
    }
}
