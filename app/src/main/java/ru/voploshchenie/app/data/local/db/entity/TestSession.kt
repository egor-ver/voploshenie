package ru.voploshchenie.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "test_session")
data class TestSession(
    @PrimaryKey val id: String,
    val status: String,
    val currentScreen: String,
    val shuffleSeed: Long,
    val configVersion: String,
    val createdAt: Long,
    val updatedAt: Long,
)
