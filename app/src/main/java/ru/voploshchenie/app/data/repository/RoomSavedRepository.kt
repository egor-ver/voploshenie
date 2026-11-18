package ru.voploshchenie.app.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.map
import ru.voploshchenie.app.data.local.db.dao.SavedItemDao
import ru.voploshchenie.app.data.mapper.toDomain
import ru.voploshchenie.app.data.mapper.toEntity
import ru.voploshchenie.app.domain.model.SavedItem
import ru.voploshchenie.app.domain.repository.SavedRepository

class RoomSavedRepository @Inject constructor(private val dao: SavedItemDao) : SavedRepository {
    override fun observeAll() = dao.observeAll().map { items -> items.map { it.toDomain() } }
    override fun observeSaved(id: String) = dao.observeSaved(id)
    override suspend fun save(item: SavedItem) = dao.save(item.toEntity())
    override suspend fun remove(id: String) = dao.remove(id)
}
