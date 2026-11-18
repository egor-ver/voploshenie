package ru.voploshchenie.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "test_result")
data class TestResultEntity(
    @PrimaryKey val sessionId: String,
    val computedAt: Long,
    val configVersion: String,
    val payload: String,
    val mismatches: Int,
)
