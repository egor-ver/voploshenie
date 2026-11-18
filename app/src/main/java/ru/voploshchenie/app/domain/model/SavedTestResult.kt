package ru.voploshchenie.app.domain.model

data class SavedTestResult(
    val sessionId: String,
    val computedAt: Long,
    val configVersion: String,
    val payload: String,
    val mismatches: Int,
)
