package ru.voploshchenie.app.domain.usecase

import kotlinx.serialization.json.*
import ru.voploshchenie.app.domain.scoring.TestAnswers

internal val storedAnswerSections = mapOf("profile" to "PRF", "pairs" to "INT", "interest" to "SPH",
    "tasks" to "TSK", "experience" to "EXP", "nad" to "NAD", "open" to "OPEN", "checks" to "CHK")

internal fun decodeStoredAnswers(stored: Map<String, JsonElement>): TestAnswers {
    val answers = JsonObject(buildMap {
        storedAnswerSections.forEach { (field, prefix) ->
            put(field, JsonObject(stored.filterKeys { it.startsWith(prefix) }))
        }
        for (field in listOf("valuesTop3", "valuesLeast2")) stored[field]?.let { put(field, it) }
        put("needs", JsonObject(buildMap {
            put("ratings", JsonObject(stored.filterKeys { it.startsWith("NEED") }))
            stored["needsTop3"]?.let { put("top3", it) }
        }))
    })
    return Json.decodeFromJsonElement<TestAnswers>(answers)
}

