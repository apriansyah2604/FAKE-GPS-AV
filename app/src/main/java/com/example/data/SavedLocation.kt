package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val isHistory: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
