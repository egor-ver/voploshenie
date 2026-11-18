package ru.voploshchenie.app

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton
import ru.voploshchenie.app.data.local.db.TestDatabase
import ru.voploshchenie.app.di.DatabaseModule

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TestDatabase =
        Room.inMemoryDatabaseBuilder(context, TestDatabase::class.java).build()

    @Provides
    @Singleton
    fun testDao(database: TestDatabase) = database.testDao()

    @Provides
    @Singleton
    fun savedItemDao(database: TestDatabase) = database.savedItemDao()
}
