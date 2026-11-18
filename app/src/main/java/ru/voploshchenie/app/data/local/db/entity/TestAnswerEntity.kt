package ru.voploshchenie.app.data.local.db.entity

import androidx.room.Entity

@Entity(tableName = "answer", primaryKeys = ["sessionId", "questionId"])
data class TestAnswerEntity(
    val sessionId: String,
    val questionId: String,
    val value: String,
    val answeredAt: Long,
)
