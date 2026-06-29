package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM saved_locations WHERE isHistory = 0 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations WHERE isHistory = 1 ORDER BY timestamp DESC LIMIT 20")
    fun getHistory(): Flow<List<SavedLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: SavedLocation)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM saved_locations WHERE isHistory = 1")
    suspend fun clearHistory()
}
