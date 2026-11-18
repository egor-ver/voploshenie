package ru.voploshchenie.app.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.voploshchenie.app.domain.model.SavedItem

interface SavedRepository {
    fun observeAll(): Flow<List<SavedItem>>
    fun observeSaved(id: String): Flow<Boolean>
    suspend fun save(item: SavedItem)
    suspend fun remove(id: String)
}
