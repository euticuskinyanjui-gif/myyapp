package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.Song
import com.example.data.SongRepository
import com.example.ui.MusicViewModel
import com.example.ui.MusicViewModelFactory
import com.example.ui.RecommendationsUiState
import com.example.ui.components.MusicVisualizer
import com.example.ui.components.ProceduralAlbumArt
import com.example.ui.theme.*
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initializing the SQLite Room Persistence context
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = SongRepository(database.songDao())
        val factory = MusicViewModelFactory(repository)

        setContent {
            MyApplicationTheme {
                val viewModel: MusicViewModel = viewModel(factory = factory)
                MusicPlayerApp(viewModel)
            }
        }
    }
}

@Composable
fun MusicPlayerApp(viewModel: MusicViewModel) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val filteredSongs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val positionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()

    var showAddSongDialog by remember { mutableStateOf(false) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTablet = maxWidth > 600.dp

            if (isTablet) {
                // Expanded adaptive tablet interface (Side-by-side permanent layout)
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left list column
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    ) {
                        SongListPanel(
                            viewModel = viewModel,
                            songs = filteredSongs,
                            selectedCategory = selectedCategory,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            isTablet = true,
                            onCategoryChange = { viewModel.changeCategory(it) },
                            onSongSelect = { viewModel.selectSong(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onDeleteSong = { viewModel.deleteSong(it) },
                            onOpenDialog = { showAddSongDialog = true }
                        )
                    }

                    // Vertical Divider Separator
                    Spacer(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(BorderDarkLight)
                    )

                    // Right full-bleed detailed visualizer console
                    Box(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                    ) {
                        ExpandedPlayerContent(
                            viewModel = viewModel,
                            song = currentSong,
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            isTablet = true,
                            onCollapse = {}
                        )
                    }
                }
            } else {
                // Mobile layout with slideable full-bleed deck and mini playback bar
                Box(modifier = Modifier.fillMaxSize()) {
                    SongListPanel(
                        viewModel = viewModel,
                        songs = filteredSongs,
                        selectedCategory = selectedCategory,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        isTablet = false,
                        onCategoryChange = { viewModel.changeCategory(it) },
                        onSongSelect = { 
                            viewModel.selectSong(it)
                            isPlayerExpanded = true // Auto expand to deck on tap for immersive feel
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDeleteSong = { viewModel.deleteSong(it) },
                        onOpenDialog = { showAddSongDialog = true }
                    )

                    // Compact Floating Bottom Player overlay
                    if (currentSong != null && !isPlayerExpanded) {
                        MiniPlayerBar(
                            song = currentSong!!,
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            onTap = { isPlayerExpanded = true },
                            onPlayPause = { viewModel.togglePlayPause() },
                            onNext = { viewModel.skipNext() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    // Immersive Full-Bleed slide-up Player Deck
                    AnimatedVisibility(
                        visible = isPlayerExpanded && currentSong != null,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
                        ),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                        )
                    ) {
                        ExpandedPlayerContent(
                            viewModel = viewModel,
                            song = currentSong,
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            isTablet = false,
                            onCollapse = { isPlayerExpanded = false }
                        )
                    }
                }
            }
        }

        // Add Custom Track Dialog
        if (showAddSongDialog) {
            AddSongDialog(
                onDismiss = { showAddSongDialog = false },
                onAddSong = { title, artist, duration, category ->
                    viewModel.addNewSong(title, artist, duration, category)
                    showAddSongDialog = false
                }
            )
        }
    }
}

@Composable
fun SongListPanel(
    viewModel: MusicViewModel,
    songs: List<Song>,
    selectedCategory: String,
    currentSong: Song?,
    isPlaying: Boolean,
    isTablet: Boolean,
    onCategoryChange: (String) -> Unit,
    onSongSelect: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onOpenDialog: () -> Unit
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenDialog,
                containerColor = SecondaryNeon,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .testTag("add_song_fab")
                    .padding(bottom = if (currentSong != null && !isTablet) 72.dp else 16.dp) // Offset to clear mini player nicely
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add custom song",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Elegant Welcome Header with soft Neon highlights
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "VibePulse",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryNeon,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "Your Offline Personal Acoustics",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMutedGrey
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = OnyxSurface,
                    border = BorderStroke(1.dp, BorderDarkLight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(6.dp),
                            shape = CircleShape,
                            color = if (isPlaying) PrimaryNeon else Color.Gray
                        ) {}
                        Text(
                            text = if (isPlaying) "PLAYING" else "STANDBY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPlaying) PrimaryNeon else TextMutedGrey
                        )
                    }
                }
            }

            // Categories horizontal scrollable filter strip
            val categories = listOf("All", "Lofi Beats", "Synthwave", "Chill Acoustic", "Workout Mix", "AI Picks", "Favorites")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category

                    Box(
                        modifier = Modifier
                            .testTag("category_chip_$category")
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) DeepGreyCard else OnyxSurface)
                            .then(
                                if (isSelected) Modifier else Modifier.border(
                                    1.dp,
                                    BorderDarkLight,
                                    RoundedCornerShape(20.dp)
                                )
                            )
                            .clickable { onCategoryChange(category) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = category,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) TextWhiteRegular else TextMutedGrey
                        )
                    }
                }
            }

            // Scrollable tracks list
            if (selectedCategory == "AI Picks") {
                RecommendationPanel(
                    viewModel = viewModel,
                    onSongSelect = onSongSelect
                )
            } else if (songs.isEmpty()) {
                ContainerEmptyState(isFavoritesCategory = selectedCategory == "Favorites")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(songs, key = { it.id }) { song ->
                        val isCurrent = currentSong?.id == song.id
                        SongRowCard(
                            song = song,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            onTap = { onSongSelect(song) },
                            onToggleFavorite = { onToggleFavorite(song) },
                            onDelete = { onDeleteSong(song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SongRowCard(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onTap: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 360.dp
        val artSize = if (isNarrow) 44.dp else 54.dp
        val textSpacing = if (isNarrow) 8.dp else 14.dp
        val titleSize = if (isNarrow) 13.sp else 15.sp
        val infoSpacing = if (isNarrow) 4.dp else 6.dp
        val horizontalPadding = if (isNarrow) 8.dp else 12.dp

        Surface(
            modifier = Modifier
                .testTag("song_item_card_${song.id}")
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onTap() },
            color = if (isCurrent) DeepGreyCard else OnyxSurface,
            border = BorderStroke(
                1.dp,
                if (isCurrent) PrimaryNeon.copy(alpha = 0.4f) else BorderDarkLight
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontalPadding)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Procedural Mini Artboard
                ProceduralAlbumArt(
                    category = song.category,
                    isPlaying = isPlaying,
                    isMini = true,
                    modifier = Modifier.size(artSize)
                )

                Spacer(modifier = Modifier.width(textSpacing))

                // Info column
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = song.title,
                            fontSize = titleSize,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) PrimaryNeon else TextWhiteRegular,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(infoSpacing)
                    ) {
                        Text(
                            text = song.artist,
                            fontSize = if (isNarrow) 10.sp else 12.sp,
                            color = TextMutedGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(text = "•", fontSize = 10.sp, color = BorderDarkLight)
                        Text(
                            text = song.category,
                            fontSize = if (isNarrow) 9.sp else 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (song.category) {
                                "Lofi Beats" -> Color(0xFFB19CD9)
                                "Synthwave" -> Color(0xFFE1007E)
                                "Chill Acoustic" -> Color(0xFFC68B59)
                                "Workout Mix" -> Color(0xFF22C55E)
                                else -> TextMutedGrey
                            }
                        )
                    }
                }

                // Real-time mini pulse visualizer if playing
                if (isCurrent) {
                    MusicVisualizer(
                        isPlaying = isPlaying,
                        barCount = if (isNarrow) 3 else 4,
                        color = PrimaryNeon,
                        modifier = Modifier
                            .size(width = if (isNarrow) 12.dp else 16.dp, height = 20.dp)
                            .padding(end = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(if (isNarrow) 6.dp else 10.dp))
                } else {
                    Text(
                        text = formatDuration(song.durationSeconds),
                        fontSize = if (isNarrow) 11.sp else 12.sp,
                        color = TextMutedGrey,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(if (isNarrow) 6.dp else 10.dp))
                }

                // Action: Favorite heart
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .testTag("favorite_toggle_${song.id}")
                        .size(if (isNarrow) 36.dp else 40.dp)
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle favorite",
                        tint = if (song.isFavorite) SecondaryNeon else TextMutedGrey,
                        modifier = Modifier.size(if (isNarrow) 18.dp else 20.dp)
                    )
                }

                // Action: Delete custom tracks
                if (song.isCustom) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(if (isNarrow) 36.dp else 40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete custom song",
                            tint = SecondaryNeon.copy(alpha = 0.8f),
                            modifier = Modifier.size(if (isNarrow) 18.dp else 20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    song: Song,
    isPlaying: Boolean,
    positionMs: Int,
    onTap: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationMs = song.durationSeconds * 1000
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 360.dp
        val artSize = if (isNarrow) 34.dp else 42.dp
        val textSpacing = if (isNarrow) 6.dp else 10.dp
        val textPaddingVertical = if (isNarrow) 6.dp else 10.dp
        val textPaddingHorizontal = if (isNarrow) 8.dp else 12.dp

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { onTap() },
            color = DeepGreyCard.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, BorderDarkLight),
            tonalElevation = 6.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .padding(horizontal = textPaddingHorizontal, vertical = textPaddingVertical)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Micro Album cover
                    ProceduralAlbumArt(
                        category = song.category,
                        isPlaying = isPlaying,
                        isMini = true,
                        modifier = Modifier.size(artSize)
                    )

                    Spacer(modifier = Modifier.width(textSpacing))

                    // Info titles
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            fontSize = if (isNarrow) 12.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhiteRegular,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = song.artist,
                            fontSize = if (isNarrow) 10.sp else 11.sp,
                            color = TextMutedGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Small equalizer indicator - hide on very narrow to preserve horizontal title space
                    if (!isNarrow) {
                        MusicVisualizer(
                            isPlaying = isPlaying,
                            barCount = 6,
                            color = PrimaryNeon,
                            modifier = Modifier
                                .height(18.dp)
                                .width(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Play pausing clicker
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .testTag("mini_play_pause")
                            .size(if (isNarrow) 30.dp else 36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/pause toggle",
                            tint = PrimaryNeon,
                            modifier = Modifier.size(if (isNarrow) 20.dp else 24.dp)
                        )
                    }

                    // Skip forward clicker
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(if (isNarrow) 30.dp else 36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next song",
                            tint = TextWhiteRegular,
                            modifier = Modifier.size(if (isNarrow) 18.dp else 22.dp)
                        )
                    }
                }

                // Real progress indicator line at base of mini bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = PrimaryNeon,
                    trackColor = BorderDarkLight
                )
            }
        }
    }
}

@Composable
fun ExpandedPlayerContent(
    viewModel: MusicViewModel,
    song: Song?,
    isPlaying: Boolean,
    positionMs: Int,
    isTablet: Boolean,
    onCollapse: () -> Unit
) {
    if (song == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Select a vibe to start playing",
                color = TextMutedGrey,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
        return
    }

    val maxMs = song.durationSeconds * 1000
    val progressFraction = if (maxMs > 0) positionMs.toFloat() / maxMs else 0f
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()

    // Lyrics syncing calculation
    val lyricsList = viewModel.getDynamicLyricsForCurrent()
    val activeLyricIndex = remember(positionMs, lyricsList) {
        val frac = if (maxMs > 0) positionMs.toFloat() / maxMs else 0f
        (frac * lyricsList.size).toInt().coerceIn(0, lyricsList.lastIndex)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val playerHeight = maxHeight
        val playerWidth = maxWidth
        
        // Define sizes based on heights
        val isShort = playerHeight < 640.dp
        val isVeryShort = playerHeight < 520.dp
        
        val artMaxSize = when {
            isVeryShort -> 120.dp
            isShort -> 180.dp
            else -> 270.dp
        }
        
        val lyricsHeight = when {
            isVeryShort -> 0.dp // Hide completely
            isShort -> 54.dp    // Super compact
            else -> 90.dp
        }
        
        val titleTextSize = when {
            isVeryShort -> 18.sp
            isShort -> 22.sp
            else -> 26.sp
        }
        
        val artistTextSize = when {
            isVeryShort -> 12.sp
            isShort -> 13.sp
            else -> 15.sp
        }
        
        val playBtnContainerSize = if (isShort) 56.dp else 76.dp
        val playBtnIconSize = if (isShort) 26.dp else 34.dp
        val controlBtnSize = if (isShort) 40.dp else 48.dp
        
        val spacerHeight1 = if (isShort) 4.dp else 12.dp
        val spacerHeight2 = if (isShort) 4.dp else 10.dp
        val spacerHeight3 = if (isShort) 6.dp else 12.dp
        val spacerHeight4 = if (isShort) 4.dp else 10.dp
        val spacerHeight5 = if (isShort) 4.dp else 10.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LuxuryBlackBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = if (isTablet) 16.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Immersive Top command row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!isTablet) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier
                            .testTag("collapse_btn")
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse player",
                            tint = TextWhiteRegular,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NOW PLAYING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMutedGrey,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = song.category.uppercase(Locale.getDefault()),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryNeon,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = OnyxSurface,
                    border = BorderStroke(1.dp, BorderDarkLight)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Details",
                            tint = TextMutedGrey,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacerHeight1))

            // Giant spinning vector canvas Record
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ProceduralAlbumArt(
                    category = song.category,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .sizeIn(maxHeight = artMaxSize, maxWidth = artMaxSize)
                        .aspectRatio(1f)
                )
            }

            Spacer(modifier = Modifier.height(spacerHeight2))

            // Bouncing animated multi-bar Equalizer panel
            if (!isVeryShort) {
                MusicVisualizer(
                    isPlaying = isPlaying,
                    barCount = if (isShort) 12 else 18,
                    color = if (song.category == "Workout Mix") Color(0xFF22C55E) else PrimaryNeon,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isShort) 20.dp else 30.dp)
                        .padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(spacerHeight3))
            }

            // Track Name & Artist panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontSize = titleTextSize,
                        fontWeight = FontWeight.Black,
                        color = TextWhiteRegular,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        fontSize = artistTextSize,
                        fontWeight = FontWeight.Bold,
                        color = TextMutedGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleFavorite(song) },
                    modifier = Modifier.size(if (isShort) 40.dp else 48.dp)
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Add to favorites",
                        tint = if (song.isFavorite) SecondaryNeon else TextMutedGrey,
                        modifier = Modifier.size(if (isShort) 22.dp else 26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacerHeight4))

            // Sliding Translucent scrolling lyrics viewport
            if (lyricsHeight > 0.dp) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lyricsHeight)
                        .clip(RoundedCornerShape(16.dp)),
                    color = OnyxSurface.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, BorderDarkLight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val previousLyric = if (activeLyricIndex > 0) lyricsList[activeLyricIndex - 1] else ""
                        val currentLyric = if (lyricsList.isNotEmpty()) lyricsList[activeLyricIndex] else "Vibing in silence..."
                        val nextLyric = if (activeLyricIndex < lyricsList.lastIndex) lyricsList[activeLyricIndex + 1] else ""

                        if (previousLyric.isNotEmpty() && lyricsHeight > 60.dp) {
                            Text(
                                text = previousLyric,
                                fontSize = 11.sp,
                                color = TextMutedGrey.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                        if (lyricsHeight > 60.dp) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            text = currentLyric,
                            fontSize = if (isShort) 12.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPlaying) PrimaryNeon else TextWhiteRegular,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        if (lyricsHeight > 60.dp) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        if (nextLyric.isNotEmpty() && lyricsHeight > 60.dp) {
                            Text(
                                text = nextLyric,
                                fontSize = 11.sp,
                                color = TextMutedGrey.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacerHeight5))
            }

            // Seekable progression slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = positionMs.toFloat(),
                    onValueChange = { viewModel.seekProjectMs(it.toInt()) },
                    valueRange = 0f..maxMs.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playback_slider"),
                    colors = SliderDefaults.colors(
                        activeTrackColor = PrimaryNeon,
                        inactiveTrackColor = BorderDarkLight,
                        thumbColor = PrimaryNeon
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(positionMs / 1000),
                        fontSize = 11.sp,
                        color = TextMutedGrey,
                        fontWeight = FontWeight.Medium
                    )
                    val remainingSec = (maxMs - positionMs) / 1000
                    Text(
                        text = "-${formatDuration(remainingSec.coerceAtLeast(0))}",
                        fontSize = 11.sp,
                        color = TextMutedGrey,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacerHeight5))

            // Advanced Player control decks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SHUFFLE controller trigger button
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier
                        .testTag("shuffle_mode_button")
                        .size(controlBtnSize)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Toggle Shuffle",
                            tint = if (isShuffle) PrimaryNeon else TextMutedGrey,
                            modifier = Modifier.size(if (isShort) 18.dp else 20.dp)
                        )
                        if (isShuffle && !isShort) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                modifier = Modifier.size(4.dp),
                                shape = CircleShape,
                                color = PrimaryNeon
                            ) {}
                        }
                    }
                }

                // SKIP PREVIOUS button
                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    modifier = Modifier
                        .testTag("skip_prev_button")
                        .size(controlBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous track",
                        tint = TextWhiteRegular,
                        modifier = Modifier.size(if (isShort) 24.dp else 28.dp)
                    )
                }

                // MASTER PLAY/PAUSE bold square disk button
                Box(
                    modifier = Modifier
                        .testTag("play_pause_button")
                        .size(playBtnContainerSize)
                        .clip(RoundedCornerShape(if (isShort) 18.dp else 24.dp))
                    .background(DeepGreyCard)
                    .clickable { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle playback state",
                        tint = TextWhiteRegular,
                        modifier = Modifier.size(playBtnIconSize)
                    )
                }

                // SKIP NEXT button
                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier
                        .testTag("skip_next_button")
                        .size(controlBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next track",
                        tint = TextWhiteRegular,
                        modifier = Modifier.size(if (isShort) 24.dp else 28.dp)
                    )
                }

                // REPEAT controller button
                IconButton(
                    onClick = { viewModel.cycleRepeatMode() },
                    modifier = Modifier
                        .testTag("repeat_mode_button")
                        .size(controlBtnSize)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Cycle repeat mode",
                            tint = if (repeatMode > 0) {
                                if (repeatMode == 2) SecondaryNeon else PrimaryNeon
                            } else {
                                TextMutedGrey
                            },
                            modifier = Modifier.size(if (isShort) 18.dp else 20.dp)
                        )
                        if (repeatMode > 0 && !isShort) {
                            Text(
                                text = if (repeatMode == 2) "ONE" else "ALL",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (repeatMode == 2) SecondaryNeon else PrimaryNeon
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContainerEmptyState(isFavoritesCategory: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = OnyxSurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isFavoritesCategory) Icons.Default.FavoriteBorder else Icons.Default.MusicNote,
                        contentDescription = "Empty",
                        tint = TextMutedGrey,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = if (isFavoritesCategory) "No favorited tracks yet" else "No matching tracks",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhiteRegular
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isFavoritesCategory) {
                    "Tap the Heart icon on any track to add it to your premium library!"
                } else {
                    "Create your own customized song using the + button below!"
                },
                fontSize = 12.sp,
                color = TextMutedGrey,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
fun AddSongDialog(
    onDismiss: () -> Unit,
    onAddSong: (title: String, artist: String, durationSeconds: Int, category: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Lofi Beats") }
    var durationSeconds by remember { mutableStateOf(150) } // default 2:30

    val categories = listOf("Lofi Beats", "Synthwave", "Chill Acoustic", "Workout Mix")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Custom Song",
                fontWeight = FontWeight.Bold,
                color = PrimaryNeon,
                fontSize = 20.sp
            )
        },
        containerColor = DeepGreyCard,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Song Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryNeon,
                        unfocusedBorderColor = BorderDarkLight,
                        focusedLabelColor = PrimaryNeon,
                        unfocusedLabelColor = TextMutedGrey,
                        focusedTextColor = TextWhiteRegular,
                        unfocusedTextColor = TextWhiteRegular
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Artist Field
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryNeon,
                        unfocusedBorderColor = BorderDarkLight,
                        focusedLabelColor = PrimaryNeon,
                        unfocusedLabelColor = TextMutedGrey,
                        focusedTextColor = TextWhiteRegular,
                        unfocusedTextColor = TextWhiteRegular
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Category selection
                Column {
                    Text(
                        text = "Vibe Style",
                        fontSize = 12.sp,
                        color = TextMutedGrey,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val isSel = category == cat
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { category = cat },
                                color = if (isSel) PrimaryNeon else OnyxSurface,
                                border = BorderStroke(1.dp, if (isSel) PrimaryNeon else BorderDarkLight)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) Color.White else TextMutedGrey,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Duration seek bar
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Track Length",
                            fontSize = 12.sp,
                            color = TextMutedGrey,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatDuration(durationSeconds),
                            fontSize = 12.sp,
                            color = PrimaryNeon,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = durationSeconds.toFloat(),
                        onValueChange = { durationSeconds = it.toInt() },
                        valueRange = 60f..300f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = PrimaryNeon,
                            inactiveTrackColor = BorderDarkLight,
                            thumbColor = PrimaryNeon
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank() && artist.isNotBlank()) {
                        onAddSong(title, artist, durationSeconds, category)
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = PrimaryNeon)
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextMutedGrey)
            ) {
                Text("Cancel")
            }
        }
    )
}

fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

@Composable
fun RecommendationPanel(
    viewModel: MusicViewModel,
    onSongSelect: (Song) -> Unit
) {
    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val recState by viewModel.recommendationsState.collectAsStateWithLifecycle()
    val offlineRecs = remember(songs) { viewModel.getOfflineRecommendations() }

    // Aggregate statistics
    val totalPlayCount = remember(songs) { songs.sumOf { it.playCount } }
    
    val topCategory = remember(songs) {
        if (songs.isEmpty()) "None"
        else {
            val categoryScores = songs.groupBy { it.category }.mapValues { entry ->
                val favScore = entry.value.count { it.isFavorite } * 5
                val playScore = entry.value.sumOf { it.playCount }
                favScore + playScore
            }
            categoryScores.maxByOrNull { it.value }?.key ?: "Lofi Beats"
        }
    }

    val topArtist = remember(songs) {
        if (songs.isEmpty()) "None"
        else {
            val artistPlays = songs.groupBy { it.artist }.mapValues { entry ->
                entry.value.sumOf { it.playCount }
            }
            artistPlays.maxByOrNull { it.value }?.key ?: "None"
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth
        val isNarrow = width < 360.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("recommendation_panel"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Listening Vibe Stats Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = OnyxSurface),
                    border = BorderStroke(1.dp, BorderDarkLight)
                ) {
                    Column(modifier = Modifier.padding(if (isNarrow) 10.dp else 16.dp)) {
                        Text(
                            text = "Your Listening Profile",
                            fontSize = if (isNarrow) 15.sp else 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhiteRegular
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (isNarrow) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Stat 1
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DeepGreyCard, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Plays", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMutedGrey)
                                    Text("$totalPlayCount", fontSize = 14.sp, fontWeight = FontWeight.Black, color = TextWhiteRegular)
                                }

                                // Stat 2
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DeepGreyCard, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Primary Vibe", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMutedGrey)
                                    Text(topCategory, fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextWhiteRegular, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                // Stat 3
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DeepGreyCard, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Top Artist", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMutedGrey)
                                    Text(topArtist, fontSize = 12.sp, fontWeight = FontWeight.Black, color = TextWhiteRegular, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Stat 1: Total Plays
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(DeepGreyCard, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Total Plays", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMutedGrey)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("$totalPlayCount", fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextWhiteRegular)
                                }

                                // Stat 2: Favorite Genre
                                Column(
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .background(DeepGreyCard, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Primary Vibe", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMutedGrey)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(topCategory, fontSize = 13.sp, fontWeight = FontWeight.Black, color = TextWhiteRegular, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                // Stat 3: Top Artist
                                Column(
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .background(DeepGreyCard, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Top Artist", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMutedGrey)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(topArtist, fontSize = 13.sp, fontWeight = FontWeight.Black, color = TextWhiteRegular, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

        // Heuristic Unplayed Offline Discoveries
        if (offlineRecs.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Offline Library Discovery",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhiteRegular,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Gems in your cozy pool you haven't played yet",
                        fontSize = 11.sp,
                        color = TextMutedGrey,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        offlineRecs.forEach { song ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.selectSong(song) },
                                color = OnyxSurface,
                                border = BorderStroke(1.dp, BorderDarkLight)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProceduralAlbumArt(
                                        category = song.category,
                                        isPlaying = viewModel.currentSong.value?.id == song.id && viewModel.isPlaying.value,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextWhiteRegular,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${song.artist} • ${song.category}",
                                            fontSize = 11.sp,
                                            color = TextMutedGrey,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play song",
                                        tint = PrimaryNeon,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // AI Personalized Section (Gemini)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = OnyxSurface),
                border = BorderStroke(1.dp, BorderDarkLight)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Powered",
                            tint = PrimaryNeon,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Gemini Predictive Recommendations",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhiteRegular
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connects listening density, favorite songs, and artist ratios to synthesize 3 bespoke suggestions.",
                        fontSize = 12.sp,
                        color = TextMutedGrey
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    when (val state = recState) {
                        is RecommendationsUiState.Idle -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = { viewModel.fetchAiRecommendations() },
                                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryNeon),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("generate_suggestions_button")
                                ) {
                                    Text("Analyze Profile with Gemini", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        is RecommendationsUiState.Loading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = PrimaryNeon, modifier = Modifier.size(36.dp))
                                Text(
                                    text = "Synthesizing Acoustic Profile...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMutedGrey
                                )
                            }
                        }
                        is RecommendationsUiState.Success -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Dynamic Insight Quote
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = DeepGreyCard.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, BorderDarkLight)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FormatQuote,
                                            contentDescription = "Insight",
                                            tint = PrimaryNeon,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = state.insight,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextWhiteRegular,
                                            style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                        )
                                    }
                                }

                                // Suggestions list
                                state.suggestions.forEach { rec ->
                                    val alreadyImported = songs.any { it.title.equals(rec.title, ignoreCase = true) && it.artist.equals(rec.artist, ignoreCase = true) }

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = LuxuryBlackBg,
                                        border = BorderStroke(1.dp, BorderDarkLight)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ProceduralAlbumArt(
                                                category = rec.category,
                                                isPlaying = false,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = rec.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextWhiteRegular,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${rec.artist} • ${rec.category}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryNeon
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = rec.description,
                                                    fontSize = 11.sp,
                                                    color = TextMutedGrey,
                                                    lineHeight = 14.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Import or Done Button
                                            if (alreadyImported) {
                                                IconButton(onClick = {}) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Added to library",
                                                        tint = Color(0xFF4CAF50),
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = { viewModel.importRecommendedSong(rec) },
                                                    modifier = Modifier.testTag("import_rec_${rec.title}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AddCircle,
                                                        contentDescription = "Import recommended song",
                                                        tint = SecondaryNeon,
                                                        modifier = Modifier.size(30.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Rerun profile button
                                OutlinedButton(
                                    onClick = { viewModel.fetchAiRecommendations() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryNeon),
                                    border = BorderStroke(1.dp, PrimaryNeon),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Regenerate Predictions", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        is RecommendationsUiState.Error -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = state.message,
                                    color = PrimaryNeon,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Button(
                                    onClick = { viewModel.fetchAiRecommendations() },
                                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryNeon),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Retry Synthesis", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
