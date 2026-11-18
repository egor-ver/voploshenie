package ru.voploshchenie.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.voploshchenie.app.data.repository.AssetCatalogRepository
import ru.voploshchenie.app.domain.repository.CatalogRepository
import ru.voploshchenie.app.data.repository.RoomSavedRepository
import ru.voploshchenie.app.data.repository.RoomTestSessionRepository
import ru.voploshchenie.app.domain.repository.SavedRepository
import ru.voploshchenie.app.domain.repository.TestSessionRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun catalogRepository(repository: ru.voploshchenie.app.data.repository.OpenCatalogRepository): CatalogRepository

    @Binds
    @Singleton
    abstract fun testRepository(repository: RoomTestSessionRepository): TestSessionRepository

    @Binds
    @Singleton
    abstract fun savedRepository(repository: RoomSavedRepository): SavedRepository
}
