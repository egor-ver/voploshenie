package ru.voploshchenie.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ConfigSphere(
    val id: String,
    val riasec: List<String>,
    val domains: List<String>,
    val values: List<String>,
    val needs: List<String>,
    val questions: List<String>,
)
