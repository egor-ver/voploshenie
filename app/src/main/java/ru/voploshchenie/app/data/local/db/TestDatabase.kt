package ru.voploshchenie.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.voploshchenie.app.data.local.db.entity.TestSession
import ru.voploshchenie.app.data.local.db.entity.TestAnswerEntity
import ru.voploshchenie.app.data.local.db.entity.TestResultEntity
import ru.voploshchenie.app.data.local.db.entity.SavedItemEntity
import ru.voploshchenie.app.data.local.db.dao.SavedItemDao
import ru.voploshchenie.app.data.local.db.dao.TestDao

@Database(entities = [TestSession::class, TestAnswerEntity::class, TestResultEntity::class, SavedItemEntity::class], version = 2, exportSchema = true)
abstract class TestDatabase : RoomDatabase() {
    abstract fun testDao(): TestDao
    abstract fun savedItemDao(): SavedItemDao
}
