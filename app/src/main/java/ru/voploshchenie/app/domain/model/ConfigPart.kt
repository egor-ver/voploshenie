package ru.voploshchenie.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ConfigPart(val id: String, val questions: List<ConfigQuestion>, val steps: List<ConfigStep>? = null)
