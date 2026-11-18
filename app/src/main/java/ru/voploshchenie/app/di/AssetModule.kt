package ru.voploshchenie.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.voploshchenie.app.data.local.asset.ConfigLoader

@Module
@InstallIn(SingletonComponent::class)
object AssetModule {
    @Provides
    @Singleton
    fun testConfig(@ApplicationContext context: Context) = ConfigLoader.load(context)
}
