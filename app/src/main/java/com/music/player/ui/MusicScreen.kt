package com.music.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.player.data.Song
import com.music.player.data.formatDuration
import androidx.media3.common.Player

private enum class LibrarySection(val label: String) {
    Songs("Songs"), Albums("Albums"), Artists("Artists"), Favorites("Favorites")
}

@Composable
fun MusicRoot(
    viewModel: MusicViewModel,
    onRequestAudioPermission: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val hasAudioPermission by viewModel.hasAudioPermission.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var sectionIndex by rememberSaveable { mutableStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isPlayerOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    BackHandler(enabled = isPlayerOpen && currentSong != null) {
        isPlayerOpen = false
    }

    MusicTheme {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isPlayerOpen && currentSong != null) {
                    MiniPlayerBar(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        positionMs = currentPosition,
                        durationMs = if (duration > 0L) duration else currentSong!!.durationMs,
                        onOpen = { isPlayerOpen = true },
                        onTogglePlay = viewModel::togglePlayPause,
                        onNext = viewModel::skipNext
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF11141C), Color(0xFF0A0B0F))))
                    .padding(padding)
            ) {
                if (isPlayerOpen && currentSong != null) {
                    NowPlayingScreen(
                        song = currentSong!!,
                        isFavorite = currentSong!!.id in favoriteIds,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        positionMs = currentPosition,
                        durationMs = if (duration > 0L) duration else currentSong!!.durationMs,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        onBack = { isPlayerOpen = false },
                        onToggleFavorite = { viewModel.toggleFavorite(currentSong!!.id) },
                        onTogglePlay = viewModel::togglePlayPause,
                        onPrevious = viewModel::skipPrevious,
                        onNext = viewModel::skipNext,
                        onSeek = viewModel::seekTo,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeat = viewModel::cycleRepeatMode
                    )
                } else {
                    MusicLibraryScreen(
                        songs = songs,
                        favoriteIds = favoriteIds,
                        hasAudioPermission = hasAudioPermission,
                        isScanning = isScanning,
                        currentSongId = currentSong?.id,
                        isPlaying = isPlaying,
                        sectionIndex = sectionIndex,
                        searchQuery = searchQuery,
                        onSectionChange = { sectionIndex = it },
                        onSearchChange = { searchQuery = it },
                        onRequestPermission = onRequestAudioPermission,
                        onRefresh = viewModel::refreshLibrary,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onPlaySongs = { queue, index ->
                            viewModel.playSongs(queue, index)
                            isPlayerOpen = true
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicLibraryScreen(
    songs: List<Song>,
    favoriteIds: Set<Long>,
    hasAudioPermission: Boolean,
    isScanning: Boolean,
    currentSongId: Long?,
    isPlaying: Boolean,
    sectionIndex: Int,
    searchQuery: String,
    onSectionChange: (Int) -> Unit,
    onSearchChange: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit
) {
    val sections = LibrarySection.entries
    val selectedSection = sections[sectionIndex.coerceIn(sections.indices)]
    val filteredSongs = remember(songs, favoriteIds, selectedSection, searchQuery) {
        val sectionSongs = if (selectedSection == LibrarySection.Favorites) {
            songs.filter { it.id in favoriteIds }
        } else songs
        val query = searchQuery.trim()
        if (query.isBlank()) sectionSongs else sectionSongs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 14.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MUSIC",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "Your library",
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onRefresh, enabled = hasAudioPermission && !isScanning) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh music library")
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            placeholder = { Text("Search songs, artists or albums") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    TextButton(onClick = { onSearchChange("") }) { Text("Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFF30333D),
                focusedContainerColor = Color(0xFF151821),
                unfocusedContainerColor = Color(0xFF151821)
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            sections.forEachIndexed { index, section ->
                FilterChip(
                    selected = sectionIndex == index,
                    onClick = { onSectionChange(index) },
                    label = { Text(section.label) }
                )
            }
        }

        when {
            !hasAudioPermission -> PermissionPrompt(
                modifier = Modifier.weight(1f),
                onRequestPermission = onRequestPermission
            )
            isScanning -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Scanning music on this device", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            songs.isEmpty() -> EmptyLibrary(
                modifier = Modifier.weight(1f),
                onRefresh = onRefresh
            )
            selectedSection == LibrarySection.Albums -> AlbumsList(
                songs = songs,
                query = searchQuery,
                onPlaySongs = onPlaySongs
            )
            selectedSection == LibrarySection.Artists -> ArtistsList(
                songs = songs,
                query = searchQuery,
                onPlaySongs = onPlaySongs
            )
            filteredSongs.isEmpty() -> EmptySearch(
                modifier = Modifier.weight(1f),
                query = searchQuery,
                isFavorites = selectedSection == LibrarySection.Favorites
            )
            else -> {
                if (selectedSection == LibrarySection.Songs) {
                    Text(
                        text = "${filteredSongs.size} tracks",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 22.dp, top = 8.dp, bottom = 4.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(filteredSongs, key = { it.id }) { song ->
                        MusicSongRow(
                            song = song,
                            isFavorite = song.id in favoriteIds,
                            isCurrent = song.id == currentSongId,
                            isPlaying = isPlaying && song.id == currentSongId,
                            onClick = { onPlaySongs(filteredSongs, filteredSongs.indexOf(song)) },
                            onFavorite = { onToggleFavorite(song.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(modifier: Modifier = Modifier, onRequestPermission: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(92.dp).clip(CircleShape).background(Color(0xFF252B34)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Let Music find your songs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Music only reads audio files stored on this device. Your library stays private and offline.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequestPermission) { Text("Grant music access") }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier, onRefresh: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(15.dp))
        Text("No music found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Add audio files to your phone, then scan again.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onRefresh) { Text("Scan again") }
    }
}

@Composable
private fun EmptySearch(modifier: Modifier = Modifier, query: String, isFavorites: Boolean) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            if (isFavorites) Icons.Rounded.FavoriteBorder else Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(if (isFavorites) "No favourites yet" else "No matching tracks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (isFavorites) "Tap the heart on any song to save it here." else "Try another search for \"$query\".",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColumnScope.AlbumsList(songs: List<Song>, query: String, onPlaySongs: (List<Song>, Int) -> Unit) {
    val albums = remember(songs, query) {
        songs.groupBy { it.albumId to it.album }
            .values
            .map { group -> group to group.first() }
            .filter { (group, first) ->
                query.isBlank() || first.album.contains(query, true) || first.artist.contains(query, true) || group.any { it.title.contains(query, true) }
            }
            .sortedBy { it.second.album.lowercase() }
    }
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(albums, key = { (group, first) -> "${first.albumId}:${first.album}:${group.size}" }) { (group, first) ->
            BrowseRow(
                title = first.album,
                subtitle = "${first.artist} · ${group.size} ${if (group.size == 1) "track" else "tracks"}",
                artwork = first.artworkUri,
                onClick = { onPlaySongs(group, 0) }
            )
        }
    }
}

@Composable
private fun ColumnScope.ArtistsList(songs: List<Song>, query: String, onPlaySongs: (List<Song>, Int) -> Unit) {
    val artists = remember(songs, query) {
        songs.groupBy { it.artist }
            .map { (artist, group) -> artist to group }
            .filter { (artist, group) -> query.isBlank() || artist.contains(query, true) || group.any { it.album.contains(query, true) || it.title.contains(query, true) } }
            .sortedBy { it.first.lowercase() }
    }
    LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(artists, key = { it.first }) { (artist, group) ->
            BrowseRow(
                title = artist,
                subtitle = "${group.size} ${if (group.size == 1) "track" else "tracks"}",
                artwork = group.firstOrNull()?.artworkUri,
                onClick = { onPlaySongs(group, 0) },
                isArtist = true
            )
        }
    }
}

@Composable
private fun MusicSongRow(
    song: Song,
    isFavorite: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isCurrent) Color(0xFF222A1B) else Color(0xFF14161D))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkTile(song.artworkUri, Modifier.size(54.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${song.artist}  ·  ${song.durationLabel}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isPlaying) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BrowseRow(
    title: String,
    subtitle: String,
    artwork: android.net.Uri?,
    onClick: () -> Unit,
    isArtist: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF14161D))
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkTile(artwork, Modifier.size(58.dp), rounded = !isArtist)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(if (isArtist) Icons.Rounded.Person else Icons.Rounded.Album, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
    }
}

@Composable
private fun ArtworkTile(artwork: android.net.Uri?, modifier: Modifier = Modifier, rounded: Boolean = true) {
    val shape = if (rounded) RoundedCornerShape(14.dp) else CircleShape
    Box(
        modifier = modifier.clip(shape).background(Brush.linearGradient(listOf(Color(0xFF283141), Color(0xFF171A23))), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), modifier = Modifier.size(26.dp))
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF171A22))
            .clickable(onClick = onOpen)
            .padding(horizontal = 13.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtworkTile(song.artworkUri, Modifier.size(46.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next track")
            }
        }
        val safeDuration = durationMs.coerceAtLeast(0L)
        val progress = if (safeDuration > 0L) (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(Color(0xFF363A44))) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun NowPlayingScreen(
    song: Song,
    isFavorite: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    var draggingPosition by remember(song.id) { mutableStateOf<Float?>(null) }
    val actualProgress = if (safeDuration > 0L) (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f) else 0f
    val shownProgress = draggingPosition ?: actualProgress
    val shownPosition = if (draggingPosition != null) (draggingPosition!! * safeDuration).toLong() else positionMs

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back to library")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NOW PLAYING", fontSize = 10.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Music player", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, contentDescription = "Favourite", tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(18.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val coverSize = minOf(maxWidth * 0.82f, 330.dp)
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2B3545), Color(0xFF151821)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(coverSize * 0.30f))
                if (song.artworkUri != null) {
                    AsyncImage(
                        model = song.artworkUri,
                        contentDescription = "Album artwork for ${song.title}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.album, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (isBuffering) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }

        Spacer(Modifier.height(17.dp))
        Slider(
            value = shownProgress,
            onValueChange = { draggingPosition = it },
            onValueChangeFinished = {
                draggingPosition?.let { onSeek((it * safeDuration).toLong()) }
                draggingPosition = null
            },
            enabled = safeDuration > 0L
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shownPosition), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(safeDuration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(15.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(34.dp))
            }
            FilledIconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(72.dp),
                shape = CircleShape
            ) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (isPlaying) "Pause" else "Play", modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next track", modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Repeat, contentDescription = "Repeat", tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (repeatMode == Player.REPEAT_MODE_ONE) Text("1", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Surface(color = Color(0xFF171A22), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Clean playback", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text("Audio is handled by Android's media engine.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
