package ru.voploshchenie.app.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TestConfig(
    val version: Int,
    val parts: List<ConfigPart>,
    val questions: Map<String, List<ConfigQuestion>>,
    val spheres: List<ConfigSphere>,
    val levels: Map<String, JsonElement> = emptyMap(),
    val hints: List<JsonElement> = emptyList(),
    val texts: Map<String, JsonElement> = emptyMap(),
    val anchors: Map<String, JsonElement> = emptyMap(),
    val analyst: Map<String, JsonElement> = emptyMap(),
)
