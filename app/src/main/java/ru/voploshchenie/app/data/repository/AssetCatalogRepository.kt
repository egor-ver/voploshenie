package ru.voploshchenie.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.voploshchenie.app.data.local.asset.CatalogLoader
import ru.voploshchenie.app.di.IoDispatcher
import ru.voploshchenie.app.domain.repository.CatalogRepository

class AssetCatalogRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
) : CatalogRepository {
    override suspend fun all() = withContext(dispatcher) { CatalogLoader.load(context) }
}
