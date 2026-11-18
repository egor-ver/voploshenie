package ru.voploshchenie.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.voploshchenie.app.data.local.db.entity.SavedItemEntity

@Dao
interface SavedItemDao {
    @Query("SELECT * FROM saved_item ORDER BY saved_at DESC") fun observeAll(): Flow<List<SavedItemEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM saved_item WHERE item_id = :id)") fun observeSaved(id: String): Flow<Boolean>
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun save(item: SavedItemEntity)
    @Query("DELETE FROM saved_item WHERE item_id = :id") suspend fun remove(id: String)
}
