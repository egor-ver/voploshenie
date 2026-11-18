package ru.voploshchenie.app.domain.model

data class TestSession(
    val id: String,
    val status: String,
    val currentScreen: String,
    val shuffleSeed: Long,
    val configVersion: String,
    val createdAt: Long,
    val updatedAt: Long,
)
