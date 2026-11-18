package ru.voploshchenie.app.domain.usecase

import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.model.SavedTestResult
import ru.voploshchenie.app.domain.repository.TestSessionRepository
import ru.voploshchenie.app.domain.scoring.missingForCompletion
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.scoring.NeedsAnswers
import ru.voploshchenie.app.domain.scoring.ScoringEngine
import ru.voploshchenie.app.domain.model.experienceAnswer
import ru.voploshchenie.app.domain.model.profileAnswer
import ru.voploshchenie.app.domain.model.quizQuestions
import ru.voploshchenie.app.domain.model.rankingAnswer
import ru.voploshchenie.app.domain.model.selectedIds
import ru.voploshchenie.app.domain.model.validQuizAnswer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

internal fun scoringAnswers(values: Map<String, String>, config: TestConfig): TestAnswers {
    fun section(part: String) = config.parts.first { it.id == part }.questions.mapNotNull { q ->
        values[q.id]?.let { q.id to it }
    }.toMap()
    fun pairs(part: String) = config.parts.first { it.id == part }.questions.mapNotNull { q ->
        values[q.id]?.let { q.id to requireNotNull(if (it == "A") q.type_a else q.type_b) }
    }.toMap()
    val ranking = rankingAnswer(values["values"])
    return TestAnswers(
        profile = profileAnswer(values["profile"]).mapValues { JsonPrimitive(it.value) },
        pairs = pairs("interests"), checks = pairs("consistency"),
        valuesTop3 = ranking.top, valuesLeast2 = ranking.least,
        interest = section("sphere_interest").mapValues { it.value.toInt() },
        tasks = section("tasks"),
        experience = section("experience").mapValues { experienceAnswer(it.value) },
        nad = section("constraints"),
        needs = NeedsAnswers(section("needs").mapValues { it.value.toInt() }, selectedIds(values["needs/pick3"])),
        open = section("open") + section("happiness"),
    )
}

internal suspend fun completeQuiz(
    values: Map<String, String>, config: TestConfig, repository: TestSessionRepository, sessionId: String,
): SavedTestResult? {
    val answers = scoringAnswers(values, config)
    if (missingForCompletion(answers).isNotEmpty()) return null
    val groups = config.parts.associate { it.id to it.questions }
    if (quizQuestions(config).any { !validQuizAnswer(it, values[it.id], groups[it.id].orEmpty()) }) return null
    val result = ScoringEngine.compute(answers, config)
    return repository.finish(sessionId, Json.encodeToString(result), result.mismatches)
}
