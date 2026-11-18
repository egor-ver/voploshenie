package ru.voploshchenie.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ConfigStep(
    val id: String,
    val type: String,
    val question_ids: List<String> = emptyList(),
    val prompt: String? = null,
    val required: Boolean? = null,
    val min: Int? = null,
    val max: Int? = null,
    val options: List<ConfigOption> = emptyList(),
)
