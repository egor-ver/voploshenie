package ru.voploshchenie.app.domain.model

data class StoredTestAnswer(
    val sessionId: String,
    val questionId: String,
    val value: String,
    val answeredAt: Long,
)
