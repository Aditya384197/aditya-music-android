package com.aditya.music.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aditya.music.data.model.Playlist
import com.aditya.music.data.model.Song
import com.aditya.music.ui.components.AdityaLogo
import com.aditya.music.ui.viewmodel.MusicViewModel

private enum class HomeFilter {
    Recent,
    Favorites,
    Albums
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onNavigateToSettings: () -> Unit
) {
    val songs by viewModel.allSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf<HomeFilter?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var songMenu by remember { mutableStateOf<Song?>(null) }
    var playlistSong by remember { mutableStateOf<Song?>(null) }

    val isSelectionMode = selectedIds.isNotEmpty()

    val deletePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onDeletePermissionGranted()
        else viewModel.onDeletePermissionDenied()
    }

    LaunchedEffect(Unit) {
        viewModel.deletePermissionRequest.collect { intentSender ->
            deletePermissionLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deleteResultEvents.collect { success ->
            Toast.makeText(
                context,
                if (success) "Deleted" else "Delete cancelled",
                Toast.LENGTH_SHORT
            ).show()
            if (success) selectedIds = emptySet()
        }
    }

    fun toggleSelection(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    val filterSongs = when (selectedFilter) {
        HomeFilter.Recent -> recentlyPlayed
        HomeFilter.Favorites -> favorites
        null, HomeFilter.Albums -> songs
    }

    val orderedSongs = remember(
        songs, recentlyPlayed, favorites, currentSong?.id, selectedFilter, searchQuery
    ) {
        val query = searchQuery.trim()
        val base = if (query.isBlank()) filterSongs else filterSongs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }

        if (selectedFilter == null) {
            val recentOrder = recentlyPlayed.mapIndexed { index, song -> song.id to index }.toMap()
            base.sortedWith(
                compareBy<Song> { if (it.id == currentSong?.id) 0 else 1 }
                    .thenBy { recentOrder[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
        } else base
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = ::clearSelection) {
                            Icon(Icons.Rounded.ArrowBack, "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectedIds = orderedSongs.map { it.id }.toSet() }) {
                            Icon(Icons.Rounded.SelectAll, "Select all")
                        }
                        IconButton(onClick = {
                            viewModel.shareSongs(orderedSongs.filter { it.id in selectedIds }, context)
                        }) {
                            Icon(Icons.Rounded.Share, "Share")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Rounded.Delete, "Delete")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AdityaLogo(size = 32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Aditya Music", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("My Music", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::refreshLibrary) {
                            Icon(Icons.Rounded.Refresh, "Refresh library")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Rounded.Settings, "Settings")
                        }
                    }
                )
            }
        }
    ) { padding ->
        when {
            isScanning -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            songs.isEmpty() -> EmptyHome(Modifier.fillMaxSize().padding(padding), viewModel::refreshLibrary)

            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                if (!isSelectionMode) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(27.dp), MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("My Music", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${songs.size} ${if (songs.size == 1) "song" else "songs"} on this device",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalIconButton(onClick = { viewModel.playSongs(orderedSongs, 0) }) {
                                    Icon(Icons.Rounded.PlayArrow, "Play all")
                                }
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = {
                                    if (orderedSongs.isNotEmpty()) viewModel.playSongs(orderedSongs.shuffled(), 0)
                                }) {
                                    Icon(Icons.Rounded.Shuffle, "Shuffle all")
                                }
                            }
                        }

                        Spacer(Modifier.height(9.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search songs, artists or albums") },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Rounded.Close, "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.large
                        )

                        Spacer(Modifier.height(9.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            HomeFilterChip("Recent", Icons.Rounded.History, selectedFilter == HomeFilter.Recent) {
                                selectedFilter = if (selectedFilter == HomeFilter.Recent) null else HomeFilter.Recent
                            }
                            HomeFilterChip("Favorites", Icons.Rounded.Favorite, selectedFilter == HomeFilter.Favorites) {
                                selectedFilter = if (selectedFilter == HomeFilter.Favorites) null else HomeFilter.Favorites
                            }
                            HomeFilterChip("Albums", Icons.Rounded.Album, selectedFilter == HomeFilter.Albums) {
                                selectedFilter = if (selectedFilter == HomeFilter.Albums) null else HomeFilter.Albums
                            }
                        }
                    }
                }

                if (selectedFilter == HomeFilter.Albums && !isSelectionMode) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 145.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        gridItems(albums, key = { it.id }) { album ->
                            Card(shape = RoundedCornerShape(18.dp)) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.playSongs(album.songs, 0) }
                                        .padding(10.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (album.artworkUri != null) {
                                            coil.compose.AsyncImage(
                                                model = album.artworkUri,
                                                contentDescription = album.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else AdityaLogo(size = 54.dp)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(album.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${album.artist} • ${album.songCount} songs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else if (orderedSongs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(Icons.Rounded.MusicOff, null, Modifier.size(54.dp), MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                when (selectedFilter) {
                                    HomeFilter.Recent -> "No recently played music"
                                    HomeFilter.Favorites -> "No favorite songs yet"
                                    else -> "No music matches your search"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 112.dp)
                    ) {
                        item {
                            Text(
                                when (selectedFilter) {
                                    HomeFilter.Recent -> "Recently Played"
                                    HomeFilter.Favorites -> "Favorites"
                                    else -> "All Music"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        itemsIndexed(orderedSongs, key = { _, song -> song.id }) { index, song ->
                            val isSelected = song.id in selectedIds
                            val isCurrent = song.id == currentSong?.id
                            ListItem(
                                headlineContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            song.title,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isCurrent) {
                                            Spacer(Modifier.width(7.dp))
                                            Icon(Icons.Rounded.GraphicEq, "Currently playing", Modifier.size(17.dp), MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                },
                                supportingContent = {
                                    Text("${song.artist} • ${song.formattedDuration}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = {
                                    if (isSelectionMode) Checkbox(checked = isSelected, onCheckedChange = { toggleSelection(song.id) })
                                    else if (song.albumArtUri != null) {
                                        coil.compose.AsyncImage(
                                            model = song.albumArtUri,
                                            contentDescription = song.album,
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else AdityaLogo(size = 42.dp)
                                },
                                trailingContent = {
                                    if (!isSelectionMode) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { viewModel.toggleFavorite(song.id) }) {
                                                Icon(
                                                    if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                    if (song.isFavorite) "Remove favorite" else "Favorite",
                                                    tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { songMenu = song }) {
                                                Icon(Icons.Rounded.MoreVert, "More actions")
                                            }
                                        }
                                    }
                                },
                                colors = if (isSelected || isCurrent) {
                                    ListItemDefaults.colors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    )
                                } else ListItemDefaults.colors(),
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) toggleSelection(song.id)
                                        else viewModel.playSongs(orderedSongs, index)
                                    },
                                    onLongClick = { toggleSelection(song.id) }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    songMenu?.let { song ->
        SongMenuDialog(
            song = song,
            onDismiss = { songMenu = null },
            onPlayNext = { viewModel.playNextSong(song); songMenu = null },
            onAddQueue = { viewModel.enqueueSong(song); songMenu = null },
            onFavorite = { viewModel.toggleFavorite(song.id); songMenu = null },
            onPlaylist = { playlistSong = song; songMenu = null },
            onShare = { viewModel.shareSong(song, context); songMenu = null },
            onDelete = { selectedIds = setOf(song.id); showDeleteDialog = true; songMenu = null }
        )
    }

    playlistSong?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onDismiss = { playlistSong = null },
            onAdd = { playlistId ->
                viewModel.addSongToPlaylist(playlistId, song.id)
                playlistSong = null
            },
            onCreate = { name ->
                viewModel.createPlaylistAndAddSong(name, song.id)
                playlistSong = null
            }
        )
    }

    if (showDeleteDialog) {
        val toDelete = orderedSongs.filter { it.id in selectedIds }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(if (toDelete.size == 1) "Delete song?" else "Delete ${toDelete.size} songs?") },
            text = {
                Text(
                    if (toDelete.size == 1) "\"${toDelete.first().title}\" will be permanently deleted from your device."
                    else "These ${toDelete.size} songs will be permanently deleted from your device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSongs(toDelete)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RowScope.HomeFilterChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.weight(1f),
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        label = { Text(label, maxLines = 1) }
    )
}

@Composable
private fun SongMenuDialog(
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddQueue: () -> Unit,
    onFavorite: () -> Unit,
    onPlaylist: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DialogAction(Icons.Rounded.PlaylistPlay, "Play next", onPlayNext)
                DialogAction(Icons.Rounded.QueueMusic, "Add to queue", onAddQueue)
                DialogAction(if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (song.isFavorite) "Remove favorite" else "Favorite", onFavorite)
                DialogAction(Icons.Rounded.PlaylistAdd, "Add to playlist", onPlaylist)
                DialogAction(Icons.Rounded.Share, "Share", onShare)
                DialogAction(Icons.Rounded.DeleteOutline, "Delete from device", onDelete, MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun DialogAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Text(text, color = tint)
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit,
    onCreate: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (playlists.isEmpty()) Text("Create your first playlist below.")
                playlists.forEach { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name, fontWeight = FontWeight.SemiBold) },
                        leadingContent = { Icon(Icons.Rounded.QueueMusic, null) },
                        modifier = Modifier.clickable { onAdd(playlist.id) }
                    )
                }
                Spacer(Modifier.height(5.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("New playlist") },
                    trailingIcon = {
                        IconButton(
                            onClick = { if (newName.isNotBlank()) onCreate(newName) },
                            enabled = newName.isNotBlank()
                        ) { Icon(Icons.Rounded.Add, "Create playlist") }
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun EmptyHome(modifier: Modifier, onRefresh: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Rounded.MusicOff, null, Modifier.size(64.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("No music found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Add audio files to your device storage and scan again.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Scan Audio Files")
            }
        }
    }
}
