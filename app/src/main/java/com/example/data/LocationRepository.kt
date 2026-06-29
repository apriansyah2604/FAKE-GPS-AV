package com.example.data

import kotlinx.coroutines.flow.Flow

class LocationRepository(private val locationDao: LocationDao) {
    val favorites: Flow<List<SavedLocation>> = locationDao.getFavorites()
    val history: Flow<List<SavedLocation>> = locationDao.getHistory()

    suspend fun insert(location: SavedLocation) {
        locationDao.insert(location)
    }

    suspend fun deleteById(id: Int) {
        locationDao.deleteById(id)
    }

    suspend fun clearHistory() {
        locationDao.clearHistory()
    }
}
