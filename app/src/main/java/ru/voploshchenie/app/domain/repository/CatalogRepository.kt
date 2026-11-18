package ru.voploshchenie.app.domain.repository

import ru.voploshchenie.app.domain.model.CatalogItem

interface CatalogRepository {
    suspend fun all(): List<CatalogItem>
}
