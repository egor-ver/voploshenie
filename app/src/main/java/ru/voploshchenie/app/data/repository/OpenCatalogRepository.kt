package ru.voploshchenie.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.voploshchenie.app.data.local.asset.CatalogLoader
import ru.voploshchenie.app.di.IoDispatcher
import ru.voploshchenie.app.domain.model.CatalogItem
import ru.voploshchenie.app.domain.repository.CatalogRepository
import ru.voploshchenie.app.domain.repository.TodayProvider
import ru.voploshchenie.app.domain.usecase.opportunityClosed

/**
 * Каталог для приложения: основной список + дополнительные позиции (assets/catalog_extra.json),
 * из которого автоматически убираются завершившиеся возможности. Основной файл остаётся неизменным.
 */
@Singleton
class OpenCatalogRepository @Inject constructor(
    private val base: AssetCatalogRepository,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val today: TodayProvider,
) : CatalogRepository {
    override suspend fun all(): List<CatalogItem> {
        val extra = withContext(dispatcher) { runCatching { CatalogLoader.loadExtra(context) }.getOrDefault(emptyList()) }
        val now = today.today()
        return (base.all() + extra).filterNot { opportunityClosed(it, now) }
    }
}
