package ru.voploshchenie.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.voploshchenie.app.data.local.db.TestDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TestDatabase =
        Room.databaseBuilder(context, TestDatabase::class.java, "test.db")
            .addMigrations(object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS saved_item (item_id TEXT NOT NULL, saved_at INTEGER NOT NULL, PRIMARY KEY(item_id))")
                }
            }).build()

    @Provides
    @Singleton
    fun testDao(database: TestDatabase) = database.testDao()

    @Provides
    @Singleton
    fun savedItemDao(database: TestDatabase) = database.savedItemDao()
}
