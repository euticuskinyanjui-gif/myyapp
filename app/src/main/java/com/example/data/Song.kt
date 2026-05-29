package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val category: String, // e.g., "Lofi Beats", "Synthwave", "Chill Acoustic", "Workout Mix"
    val isFavorite: Boolean = false,
    val isCustom: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L
)
