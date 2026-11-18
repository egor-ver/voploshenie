package ru.voploshchenie.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import ru.voploshchenie.app.domain.scoring.CompletionRules
import ru.voploshchenie.app.domain.scoring.ExperienceAnswer
import ru.voploshchenie.app.domain.scoring.TestAnswers

data class TestScreen(
    val id: String,
    val part: String,
    val questions: List<ConfigQuestion>,
    val step: ConfigStep? = null,
    val pairTypes: List<String> = emptyList(),
) {
    fun isScreenComplete(answers: TestAnswers): Boolean = CompletionRules.isScreenComplete(this, answers)
}

@Serializable
data class ValueSelection(val top: List<String> = emptyList(), val least: List<String> = emptyList())

fun rankingAnswer(answer: String?): ValueSelection = answer?.let { Json.decodeFromString<ValueSelection>(it) } ?: ValueSelection()
fun selectedIds(answer: String?): List<String> = answer?.let { Json.decodeFromString<List<String>>(it) } ?: emptyList()
fun experienceAnswer(answer: String?): ExperienceAnswer = answer?.let { Json.decodeFromString<ExperienceAnswer>(it) } ?: ExperienceAnswer("", 0)
fun profileAnswer(answer: String?): Map<String, String> = answer?.let { Json.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
fun textLimit(question: ConfigQuestion): Int = question.max ?: when (question.id) {
    "PRF01" -> 60
    "PRF03", "PRF04" -> 120
    else -> 1000
}

fun quizQuestions(config: TestConfig): List<ConfigQuestion> = config.parts.flatMap { part ->
    if (part.questions.isEmpty()) return@flatMap emptyList()
    when (part.id) {
        "profile" -> listOf(ConfigQuestion(part.id, emptyMap(), "profile"))
        "values" -> listOf(ConfigQuestion(part.id, emptyMap(), "rank_values"))
        "needs" -> part.questions + requireNotNull(part.steps).filter { it.type == "pick3" }.map {
            ConfigQuestion("needs/${it.id}", emptyMap(), it.type, it.options, it.prompt, min = it.min, max = it.max)
        }
        else -> part.questions
    }
}

fun validQuizAnswer(question: ConfigQuestion, answer: String?, questions: List<ConfigQuestion>, pairValues: List<String> = listOf("A", "B")): Boolean = when (question.type) {
    "profile" -> {
        val values = profileAnswer(answer)
        CompletionRules.isScreenComplete("profile", TestAnswers(profile = values.mapValues { JsonPrimitive(it.value) })) &&
            questions.all { q -> when (q.type) {
                "single_choice" -> q.options.any { it.id == values[q.id] }
                "scale", "scale10" -> values[q.id]?.toIntOrNull() in requireNotNull(q.scale).let { it.min..it.max }
                else -> true
            } }
    }
    "rank_values" -> rankingAnswer(answer).let {
        CompletionRules.isScreenComplete(TestScreen("values", "values", questions), TestAnswers(valuesTop3 = it.top, valuesLeast2 = it.least))
    }
    "pick3" -> selectedIds(answer).let { ids -> ids.size == question.min && ids.distinct().size == ids.size && ids.all { id -> question.options.any { it.id == id } } }
    "text" -> answer.orEmpty().length <= textLimit(question) && (question.required != true || !answer.isNullOrBlank())
    "text_energy5" -> CompletionRules.isScreenComplete("experience", TestAnswers(experience = mapOf(question.id to experienceAnswer(answer))))
    else -> if (question.type == "pair") answer in pairValues else validChoice(question, answer)
}

internal fun validChoice(question: ConfigQuestion, answer: String?): Boolean = when (question.type) {
    "pair" -> answer == "A" || answer == "B"
    "single_choice" -> question.options.any { it.id == answer }
    else -> (question.scale ?: question.energy)?.let { answer?.toIntOrNull() in it.min..it.max } == true
}

