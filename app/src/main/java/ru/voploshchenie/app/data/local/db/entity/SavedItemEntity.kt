package ru.voploshchenie.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_item")
data class SavedItemEntity(@PrimaryKey val item_id: String, val saved_at: Long)
