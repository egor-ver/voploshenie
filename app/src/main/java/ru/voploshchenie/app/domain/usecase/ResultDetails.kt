package ru.voploshchenie.app.domain.usecase

import kotlinx.serialization.json.*
import ru.voploshchenie.app.domain.model.SavedTestResult
import ru.voploshchenie.app.domain.model.StoredTestAnswer
import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.scoring.ScoringResult
import ru.voploshchenie.app.domain.scoring.TestAnswers

internal data class ResultDetails(val result: ScoringResult, val answers: TestAnswers, val name: String?, val computedAt: Long)

internal fun SavedTestResult.resultDetails(rows: List<StoredTestAnswer>, config: TestConfig): ResultDetails {
    val decoded = resultAnswers(rows, config)
    return ResultDetails(Json.decodeFromString<ScoringResult>(payload), decoded, resultName(rows, decoded), computedAt)
}

internal fun resultAnswers(rows: List<StoredTestAnswer>, config: TestConfig): TestAnswers {
    val stored = rows.associate { it.questionId to it.value }
    if ("__format" !in stored) return scoringAnswers(stored, config)
    return decodeStoredAnswers(stored.mapValues { Json.parseToJsonElement(it.value) })
}

internal fun resultName(rows: List<StoredTestAnswer>, answers: TestAnswers): String? =
    answers.profile["PRF01"]?.jsonPrimitive?.contentOrNull
        ?: rows.firstOrNull { it.questionId == "profile" }?.value?.let { raw ->
            Json.parseToJsonElement(raw).jsonObject["PRF01"]?.jsonPrimitive?.contentOrNull
        }

