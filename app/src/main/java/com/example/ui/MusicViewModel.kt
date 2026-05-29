package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.Song
import com.example.data.SongRepository
import com.example.data.AIRecommendationsContainer
import com.example.data.AISuggestedSong
import com.example.data.GenerateContentRequest
import com.example.data.GenerationConfig
import com.example.data.Content
import com.example.data.Part
import com.example.data.GeminiRetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// --- Sealed State for Recommendation Engine UI ---
sealed interface RecommendationsUiState {
    object Idle : RecommendationsUiState
    object Loading : RecommendationsUiState
    data class Success(
        val suggestions: List<AISuggestedSong>,
        val insight: String
    ) : RecommendationsUiState
    data class Error(val message: String) : RecommendationsUiState
}

class MusicViewModel(private val repository: SongRepository) : ViewModel() {

    // Filter categories
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Database Songs list
    val allSongs: StateFlow<List<Song>> = repository.allSongs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Filtered songs (with safety check for "AI Picks" category)
    val filteredSongs: StateFlow<List<Song>> = combine(allSongs, _selectedCategory) { songs, category ->
        when (category) {
            "All" -> songs
            "Favorites" -> songs.filter { it.isFavorite }
            "AI Picks" -> emptyList() // The UI will render its own custom recommendation UI for this category
            else -> songs.filter { it.category == category }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current State
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0)
    val playbackPositionMs: StateFlow<Int> = _playbackPositionMs.asStateFlow()

    // Mode States
    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    // 0 = Off, 1 = Repeat All, 2 = Repeat One
    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // Recommendation Engine States
    private val _recommendationsState = MutableStateFlow<RecommendationsUiState>(RecommendationsUiState.Idle)
    val recommendationsState: StateFlow<RecommendationsUiState> = _recommendationsState.asStateFlow()

    private var timerJob: Job? = null

    init {
        // Enforce DB onboarding prepopulation on startup
        viewModelScope.launch {
            repository.ensureDefaultTracks()
            
            // Set first track as default standby once populated
            allSongs.first { it.isNotEmpty() }.let { list ->
                if (_currentSong.value == null && list.isNotEmpty()) {
                    _currentSong.value = list.first()
                }
            }
        }
        startPlaybackSimulation()
    }

    private fun startPlaybackSimulation() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                if (_isPlaying.value) {
                    val song = _currentSong.value
                    if (song != null) {
                        val maxMs = song.durationSeconds * 1000
                        if (_playbackPositionMs.value < maxMs) {
                            _playbackPositionMs.value += 200
                        } else {
                            handleSongCompletion()
                        }
                    }
                }
                delay(200)
            }
        }
    }

    private fun handleSongCompletion() {
        when (_repeatMode.value) {
            2 -> {
                // Repeat One
                _playbackPositionMs.value = 0
            }
            1 -> {
                // Repeat All - advance to next (and circle back if active)
                skipNext(isAutoEnd = true)
            }
            else -> {
                // No repeat - advance to next. If it was the end of the list, stop playback.
                val activeList = if (_selectedCategory.value == "AI Picks") allSongs.value else filteredSongs.value
                val index = activeList.indexOfFirst { it.id == _currentSong.value?.id }
                if (index != -1 && index < activeList.lastIndex) {
                    skipNext(isAutoEnd = false)
                } else {
                    _isPlaying.value = false
                    _playbackPositionMs.value = 0
                }
            }
        }
    }

    // Increments play statistics inside Room database for analytics
    private fun incrementPlayCount(song: Song) {
        viewModelScope.launch {
            val updated = song.copy(
                playCount = song.playCount + 1,
                lastPlayedAt = System.currentTimeMillis()
            )
            repository.updateSong(updated)
            // If it's the current song, keep current reference in sync
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = updated
            }
        }
    }

    // Playback control interfaces
    fun togglePlayPause() {
        val current = _currentSong.value
        if (current == null && allSongs.value.isNotEmpty()) {
            val firstSong = allSongs.value.first()
            _currentSong.value = firstSong
            incrementPlayCount(firstSong)
        } else if (current != null && !_isPlaying.value) {
            incrementPlayCount(current)
        }
        _isPlaying.value = !_isPlaying.value
    }

    fun selectSong(song: Song) {
        _currentSong.value = song
        _playbackPositionMs.value = 0
        _isPlaying.value = true
        incrementPlayCount(song)
    }

    fun seekProjectMs(posMs: Int) {
        val song = _currentSong.value ?: return
        val maxMs = song.durationSeconds * 1000
        _playbackPositionMs.value = posMs.coerceIn(0, maxMs)
    }

    fun skipNext(isAutoEnd: Boolean = false) {
        val list = if (_selectedCategory.value == "AI Picks") allSongs.value else filteredSongs.value
        if (list.isEmpty()) return

        val current = _currentSong.value
        val nextSong = if (_isShuffle.value) {
            // Pick a random track excluding current if list size > 1
            if (list.size > 1) {
                list.filter { it.id != current?.id }.random()
            } else {
                list.random()
            }
        } else {
            val idx = list.indexOfFirst { it.id == current?.id }
            if (idx == -1) {
                list.first()
            } else if (idx == list.lastIndex) {
                if (isAutoEnd && _repeatMode.value == 0) {
                    // Stop at end
                    _isPlaying.value = false
                    _playbackPositionMs.value = 0
                    return
                }
                list.first() // circle back to start
            } else {
                list[idx + 1]
            }
        }
        _currentSong.value = nextSong
        _playbackPositionMs.value = 0
        incrementPlayCount(nextSong)
        if (!isAutoEnd) {
            _isPlaying.value = true
        }
    }

    fun skipPrevious() {
        val list = if (_selectedCategory.value == "AI Picks") allSongs.value else filteredSongs.value
        if (list.isEmpty()) return

        val current = _currentSong.value
        val prevSong = if (_isShuffle.value) {
            if (list.size > 1) {
                list.filter { it.id != current?.id }.random()
            } else {
                list.random()
            }
        } else {
            val idx = list.indexOfFirst { it.id == current?.id }
            if (idx == -1) {
                list.first()
            } else if (idx == 0) {
                list.last() // wrap around to end
            } else {
                list[idx - 1]
            }
        }
        _currentSong.value = prevSong
        _playbackPositionMs.value = 0
        incrementPlayCount(prevSong)
        _isPlaying.value = true
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun cycleRepeatMode() {
        // Cycle 0 -> 1 -> 2 -> 0
        _repeatMode.value = (_repeatMode.value + 1) % 3
    }

    fun changeCategory(category: String) {
        _selectedCategory.value = category
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val updated = song.copy(isFavorite = !song.isFavorite)
            repository.updateSong(updated)
            // Sync with current playing if modified
            if (_currentSong.value?.id == song.id) {
                _currentSong.value = updated
            }
        }
    }

    fun addNewSong(title: String, artist: String, durationSeconds: Int, category: String) {
        viewModelScope.launch {
            val newSong = Song(
                title = title,
                artist = artist,
                durationSeconds = durationSeconds,
                category = category,
                isCustom = true
            )
            repository.insertSong(newSong)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            if (_currentSong.value?.id == song.id) {
                _isPlaying.value = false
                _playbackPositionMs.value = 0
                // Find another song to slot in
                val remaining = allSongs.value.filter { it.id != song.id }
                _currentSong.value = if (remaining.isNotEmpty()) remaining.first() else null
            }
            repository.deleteSong(song)
        }
    }

    // --- Gemini Based Personalized Recommendation Analysis ---
    fun fetchAiRecommendations() {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _recommendationsState.value = RecommendationsUiState.Error(
                "Gemini API Key is missing. Please configure GEMINI_API_KEY inside the Secrets panel " +
                "to unlock deep learning analytics."
            )
            return
        }

        _recommendationsState.value = RecommendationsUiState.Loading

        viewModelScope.launch {
            try {
                val songsList = allSongs.value
                val totalPlays = songsList.sumOf { it.playCount }
                val categoryPlays = songsList.groupBy { it.category }.mapValues { entry ->
                    entry.value.sumOf { it.playCount }
                }
                val favoritesSummary = songsList.filter { it.isFavorite }
                    .take(12)
                    .joinToString { "${it.title} by ${it.artist} (${it.category})" }
                val topPlayed = songsList.sortedByDescending { it.playCount }
                    .take(4)
                    .joinToString { "${it.title} by ${it.artist} (${it.playCount} plays)" }

                val prompt = """
                    You are a premium AI music recommendation and analysis system built into a music app.
                    Your objective is to generate exactly 3 custom recommended tracks for a user based on their listening history.
                    
                    User profile data:
                    - Cumulative play count: ${totalPlays}
                    - Distribution per genre: $categoryPlays
                    - Favorite tracks: [${if (favoritesSummary.isEmpty()) "None yet" else favoritesSummary}]
                    - Most active repeat tracks: [${if (topPlayed.isEmpty()) "None yet" else topPlayed}]

                    Suggest EXACTLY 3 cinematic/fictional tracks matching these user patterns. Use the user's categories.
                    Return EXACTLY a JSON structure matching the schema below. Keep descriptions and the insight concise and inspiring.
                    Address the user directly in 'insight' with friendly style. Never use markdown codeblocks wrap. Only clean raw JSON.

                    Schema:
                    {
                      "insight": "Address the user's habits (e.g., 'Since you have a soft spot for acoustic campfire strings and cozy lofi coffee shops, we generated these custom vibrations...').",
                      "suggestions": [
                        {
                          "title": "Inspiring track title",
                          "artist": "Fictional artist name",
                          "category": "Pick exactly one of: Lofi Beats | Synthwave | Chill Acoustic | Workout Mix",
                          "durationSeconds": 180,
                          "description": "Short, vivid audio detail (10-15 words)"
                        }
                      ]
                    }
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.75f
                    )
                )

                val response = GeminiRetrofitClient.service.generateRecommendations(apiKey, request)
                val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                if (textResponse != null) {
                    var cleanJson = textResponse.trim()
                    if (cleanJson.startsWith("```")) {
                        cleanJson = cleanJson.substringAfter("```")
                        if (cleanJson.startsWith("json")) {
                            cleanJson = cleanJson.substringAfter("json")
                        }
                    }
                    if (cleanJson.endsWith("```")) {
                        cleanJson = cleanJson.substringBeforeLast("```")
                    }
                    cleanJson = cleanJson.trim()

                    val adapter = GeminiRetrofitClient.moshiInstance.adapter(AIRecommendationsContainer::class.java)
                    val container = adapter.fromJson(cleanJson)

                    if (container != null) {
                        _recommendationsState.value = RecommendationsUiState.Success(
                            suggestions = container.suggestions,
                            insight = container.insight
                        )
                    } else {
                        _recommendationsState.value = RecommendationsUiState.Error("Failed to parse recommendation schema.")
                    }
                } else {
                    _recommendationsState.value = RecommendationsUiState.Error("No recommendations could be generated.")
                }
            } catch (e: Exception) {
                _recommendationsState.value = RecommendationsUiState.Error("Recommendation engine error: ${e.localizedMessage ?: "Network Timeout"}")
            }
        }
    }

    // Reset recommendation state
    fun clearRecommendations() {
        _recommendationsState.value = RecommendationsUiState.Idle
    }

    // Import a recommended song directly into library
    fun importRecommendedSong(rec: AISuggestedSong) {
        viewModelScope.launch {
            val isDuplicate = allSongs.value.any { it.title.equals(rec.title, ignoreCase = true) && it.artist.equals(rec.artist, ignoreCase = true) }
            if (!isDuplicate) {
                val newSong = Song(
                    title = rec.title,
                    artist = rec.artist,
                    durationSeconds = rec.durationSeconds,
                    category = rec.category,
                    isCustom = true,
                    playCount = 0
                )
                repository.insertSong(newSong)
            }
        }
    }

    // --- Offline Heuristic Recommendation Generator ---
    fun getOfflineRecommendations(): List<Song> {
        val songsList = allSongs.value
        if (songsList.isEmpty()) return emptyList()

        // Score based on isFavorite (weights 5) + playCount
        val categoryScores = songsList.groupBy { it.category }.mapValues { entry ->
            val favScore = entry.value.count { it.isFavorite } * 5
            val playScore = entry.value.sumOf { it.playCount }
            favScore + playScore
        }

        val favoriteCategory = categoryScores.maxByOrNull { it.value }?.key ?: "Lofi Beats"
        
        // Suggest unplayed tracks in that favorite category
        return songsList.filter { it.category == favoriteCategory && it.playCount == 0 }.take(4)
    }

    // Dynamic Lyrics generation matching categories
    fun getDynamicLyricsForCurrent(): List<String> {
        val song = _currentSong.value ?: return emptyList()
        return when (song.category) {
            "Lofi Beats" -> listOf(
                "Cold coffee fumes brewing softly",
                "Late night desk under amber lighting",
                "Raindrops rhythmically drumming on screen",
                "Code lines scrolling on pixel displays",
                "Endless thoughts floating away",
                "Chilled vibes keeping us company",
                "Warm blankets on windy nights"
            )
            "Synthwave" -> listOf(
                "Accelerating on glowing grid circuits",
                "Laser speedways cut through the dark",
                "Cyber runners speeding past Tokyo shifts",
                "Electric dreams flash in the driving rain",
                "Neon horizons stretching into infinity",
                "Frequencies pulsing through deep networks",
                "Retro future starts to unfold"
            )
            "Chill Acoustic" -> listOf(
                "Smokey campfire sparks dancing upwards",
                "Warm acoustic timber vibrating true",
                "Gentle evening breeze rustling mahogany logs",
                "Walking down silent pine forest paths",
                "Soft vocal whispers guide the way",
                "Simple chords telling clean raw stories",
                "Peace and quiet, returning home"
            )
            "Workout Mix" -> listOf(
                "Pushing physical limits with each power bar",
                "Bass current pumping rich drive in",
                "Sweat drops highlighting maximum velocity",
                "Sprint past the virtual fluorescent walls",
                "Break individual limits, hold the peak",
                "Heartbeats sync'd with synthetic kicks",
                "One more sprint to break the ceiling"
            )
            else -> listOf(
                "Tuning in a custom organic stream",
                "Acoustic frequencies expanding outwards",
                "This is your personalized custom beat",
                "Add your favorite artists to paint stories",
                "Enjoying the simplicity of client-side sound",
                "Vibes generated purely offline",
                "Music Player is playing your customized track"
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

class MusicViewModelFactory(private val repository: SongRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MusicViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
