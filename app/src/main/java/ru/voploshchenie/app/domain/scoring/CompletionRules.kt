package ru.voploshchenie.app.domain.scoring

import ru.voploshchenie.app.domain.model.ConfigQuestion
import ru.voploshchenie.app.domain.model.TestScreen
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object CompletionRules {
    fun isScreenComplete(screen: TestScreen, answers: TestAnswers): Boolean = when (screen.part) {
        "profile" -> isScreenComplete("profile", answers)
        "values" -> isScreenComplete("values", answers) &&
            (answers.valuesTop3 + answers.valuesLeast2).all { id -> screen.questions.any { it.id == id } }
        "needs" -> if (screen.step?.type == "pick3") {
            answers.needs.top3.size == screen.step.min && answers.needs.top3.distinct().size == answers.needs.top3.size &&
                answers.needs.top3.all { id -> screen.step.options.any { it.id == id } }
        } else screen.questions.all { answers.needs.ratings[it.id] in it.scaleRange }
        else -> screen.questions.all { question ->
            when (question.type) {
                "pair" -> (if (screen.part == "consistency") answers.checks[question.id] else answers.pairs[question.id])
                    .let { it != null && it in listOf(question.type_a, question.type_b) }
                "likert5" -> answers.interest[question.id] in question.scaleRange
                "single_choice" -> (if (screen.part == "tasks") answers.tasks[question.id] else answers.nad[question.id])
                    .let { value -> question.options.any { it.id == value } }
                "text_energy5" -> isScreenComplete("experience", answers.copy(
                    experience = answers.experience.filterKeys { it == question.id }))
                "text" -> if (screen.part == "constraints") (answers.nad[question.id]?.length ?: 0) <= 1000
                    else isScreenComplete("open", answers.copy(open = answers.open.filterKeys { it == question.id }))
                else -> false
            }
        }
    }

    private val ConfigQuestion.scaleRange: IntRange get() = scale?.let { it.min..it.max } ?: 1..5

    fun isScreenComplete(screen: String, answers: TestAnswers): Boolean = missingForCompletion(screen, answers).isEmpty()

    fun missingForCompletion(answers: TestAnswers): List<String> =
        listOf("profile", "interests", "sphere_interest", "values", "tasks", "experience", "nad", "needs", "checks", "open")
            .flatMap { missingForCompletion(it, answers) }

    private fun missingForCompletion(screen: String, answers: TestAnswers): List<String> = when (screen) {
        "profile" -> buildList {
            if ((answers.profile["PRF01"]?.text?.length ?: 0) > 60) add("PRF01")
            if (!validText(answers.profile["PRF03"]?.text, 120)) add("PRF03")
            if (!validText(answers.profile["PRF04"]?.text, 120)) add("PRF04")
            if (answers.profile["PRF02"] == null) add("PRF02")
            if (answers.profile["PRF05"] == null) add("PRF05")
            val request = answers.profile["PRF06"]?.text
            val other = request.equals("другое", true) ||
                request.equals("другое (с полем текста до 200 символов)", true)
            if (request.isNullOrBlank() || (other && !validText(answers.profile["PRF06_other"]?.text, 200))) add("PRF06")
        }
        "interests" -> (1..15).map { "INT%02d".format(it) }.filter { answers.pairs[it].isNullOrBlank() }
        "sphere_interest" -> (1..14).map { "SPH%02d".format(it) }.filter { it !in answers.interest }
        "values" -> buildList {
            if (answers.valuesTop3.size != 3 || answers.valuesTop3.toSet().size != 3 || answers.valuesLeast2.size != 2 || answers.valuesLeast2.toSet().size != 2 || answers.valuesTop3.toSet().intersect(answers.valuesLeast2.toSet()).isNotEmpty()) add("1Б")
        }
        "tasks" -> (1..10).map { "TSK%02d".format(it) }.filter { answers.tasks[it].isNullOrBlank() }
        "experience" -> answers.experience.filterValues { it.text.isNotBlank() && it.energy !in 1..5 }.keys.toList()
            .plus(answers.experience.filterValues { it.text.length > 1000 }.keys)
            .distinct()
        "nad" -> buildList {
            addAll((1..6).map { "NAD%02d".format(it) }.filter { answers.nad[it].isNullOrBlank() })
            if ((answers.nad["NAD07"]?.length ?: 0) > 1000) add("NAD07")
        }
        "needs" -> buildList {
            if ((1..8).any { "NEED%02d".format(it) !in answers.needs.ratings || answers.needs.ratings["NEED%02d".format(it)] !in 1..5 }) add("4А_оценки")
            if (answers.needs.top3.size != 3 || answers.needs.top3.toSet().size != 3) add("4А_важное")
        }
        "checks" -> (1..5).map { "CHK%02d".format(it) }.filter { answers.checks[it].isNullOrBlank() }
        "open" -> answers.open.filterValues { it.length > 1000 }.keys.toList()
        else -> listOf(screen)
    }

    private fun validText(value: String?, limit: Int): Boolean = value != null && value.length <= limit && value.isNotBlank()
    private val kotlinx.serialization.json.JsonElement.text: String? get() = jsonPrimitive.contentOrNull
}

fun isScreenComplete(screen: String, answers: TestAnswers): Boolean = CompletionRules.isScreenComplete(screen, answers)

fun missingForCompletion(answers: TestAnswers): List<String> = CompletionRules.missingForCompletion(answers)
