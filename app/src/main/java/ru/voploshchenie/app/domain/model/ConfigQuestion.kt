package ru.voploshchenie.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ConfigQuestion(
    val id: String,
    val fields: Map<String, String>,
    val type: String,
    val options: List<ConfigOption> = emptyList(),
    val prompt: String? = null,
    val ranking: ConfigRanking? = null,
    val energy: ConfigScale? = null,
    val min: Int? = null,
    val max: Int? = null,
    val required: Boolean? = null,
    val type_a: String? = null,
    val type_b: String? = null,
    val correct: String? = null,
    val domain: String? = null,
    val linked: String? = null,
    val sphere_id: String? = null,
    val scale: ConfigScale? = null,
)
