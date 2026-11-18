package ru.voploshchenie.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Singleton
import ru.voploshchenie.app.data.games.AssetGameContentRepository
import ru.voploshchenie.app.data.games.PrefsGameProgressRepository
import ru.voploshchenie.app.domain.repository.GameContentRepository
import ru.voploshchenie.app.domain.repository.GameProgressRepository
import ru.voploshchenie.app.domain.repository.TodayProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class GameModule {
    @Binds
    @Singleton
    abstract fun content(repository: AssetGameContentRepository): GameContentRepository

    @Binds
    @Singleton
    abstract fun progress(repository: PrefsGameProgressRepository): GameProgressRepository

    companion object {
        @Provides
        @Singleton
        fun today(): TodayProvider = TodayProvider { LocalDate.now() }
    }
}
