package ru.voploshchenie.app.data.local.asset

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.voploshchenie.app.domain.model.TestConfig

object ConfigLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): TestConfig = context.assets.open("test_config.json").bufferedReader().use { decode(it.readText()) }

    fun decode(source: String): TestConfig {
        val root = json.parseToJsonElement(source) as JsonObject
        val parts = root.getValue("parts").jsonArray.map { raw ->
            val part = raw as JsonObject
            val prefix = part.getValue("id").jsonPrimitive.content
            val normalized = part.getValue("questions").jsonArray.map { normalizeQuestion(it as JsonObject, prefix) }
            JsonObject(part.toMutableMap().apply { put("questions", JsonArray(normalized)) })
        }
        val questionMap = root.getValue("questions").jsonObject.mapValues { (key, entries) ->
            JsonArray(entries.jsonArray.map { normalizeQuestion(it as JsonObject, key) })
        }
        return json.decodeFromString(JsonObject(root.toMutableMap().apply {
            put("parts", JsonArray(parts))
            put("questions", JsonObject(questionMap))
        }).toString())
    }

    private fun normalizeQuestion(question: JsonObject, part: String): JsonObject {
        val rawOptions = question["options"]?.jsonArray ?: return question
        val normalized = rawOptions.mapIndexed { index, option ->
            when {
                option is JsonObject -> option
                part == "tasks" -> buildJsonObject {
                    put("id", listOf("а", "б", "в", "г").getOrElse(index) { index.toString() })
                    put("text", option.jsonPrimitive.content)
                }
                part == "profile" -> buildJsonObject {
                    put("id", option.jsonPrimitive.content)
                    put("text", option.jsonPrimitive.content)
                }
                else -> option
            }
        }
        return JsonObject(question.toMutableMap().apply { put("options", JsonArray(normalized)) })
    }
}
