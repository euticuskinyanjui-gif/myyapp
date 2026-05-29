package com.example.data

import kotlinx.coroutines.flow.Flow

class SongRepository(private val songDao: SongDao) {

    val allSongs: Flow<List<Song>> = songDao.getAllSongs()

    suspend fun insertSong(song: Song) {
        songDao.insertSong(song)
    }

    suspend fun updateSong(song: Song) {
        songDao.updateSong(song)
    }

    suspend fun deleteSong(song: Song) {
        songDao.deleteSong(song)
    }

    suspend fun ensureDefaultTracks() {
        if (songDao.getSongsCount() == 0) {
            val defaults = listOf(
                // Lofi Beats
                Song(title = "Late Night Study", artist = "Code & Coffee", durationSeconds = 180, category = "Lofi Beats"),
                Song(title = "Raindrops on Glass", artist = "Sleepy Head", durationSeconds = 210, category = "Lofi Beats"),
                Song(title = "Neon Cozy Cafe", artist = "Cafe Chill", durationSeconds = 165, category = "Lofi Beats"),
                
                // Synthwave
                Song(title = "Cyber Runner", artist = "Retro Vector", durationSeconds = 240, category = "Synthwave"),
                Song(title = "Grid Acceleration", artist = "Vector Highway", durationSeconds = 195, category = "Synthwave"),
                Song(title = "Midnight Drive", artist = "Tokyo Shift", durationSeconds = 225, category = "Synthwave"),
                
                // Chill Acoustic
                Song(title = "Warm Campfire", artist = "Woody Oak", durationSeconds = 185, category = "Chill Acoustic"),
                Song(title = "Ocean Breeze", artist = "Sandy Strings", durationSeconds = 200, category = "Chill Acoustic"),
                Song(title = "Morning Walk", artist = "Sunny Pick", durationSeconds = 170, category = "Chill Acoustic"),
                
                // Workout Mix
                Song(title = "Power Pulse", artist = "Circuit Beat", durationSeconds = 150, category = "Workout Mix"),
                Song(title = "Adrenaline Rush", artist = "Hyper Grid", durationSeconds = 180, category = "Workout Mix"),
                Song(title = "Neon Sprint", artist = "Turbo Core", durationSeconds = 160, category = "Workout Mix")
            )
            songDao.insertSongs(defaults)
        }
    }
}
