package com.aditya.music.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.aditya.music.data.model.Song
import com.aditya.music.ui.components.AdityaLogo
import com.aditya.music.ui.components.ArtworkView
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
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf<HomeFilter?>(null) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                            AdityaLogo(size = 30.dp)
                            Spacer(Modifier.width(9.dp))
                            Column {
                                Text(
                                    "AK Music",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
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
            ) {
                CircularProgressIndicator()
            }

            songs.isEmpty() -> EmptyHome(
                Modifier.fillMaxSize().padding(padding),
                viewModel::refreshLibrary
            )

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (!isSelectionMode) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
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

                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gridItems(albums, key = { it.id }) { album ->
                            Card(shape = RoundedCornerShape(16.dp)) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { viewModel.playSongs(album.songs, 0) },
                                            onLongClick = {}
                                        )
                                        .padding(9.dp)
                                ) {
                                    ArtworkView(
                                        artworkUri = album.artworkUri,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f),
                                        logoSize = 54.dp,
                                        imageSizePx = 360,
                                        contentDescription = album.name,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(Modifier.height(7.dp))
                                    Text(
                                        album.name,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.MusicOff,
                                null,
                                Modifier.size(54.dp),
                                MaterialTheme.colorScheme.primary
                            )
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
                        contentPadding = PaddingValues(start = 10.dp, top = 2.dp, end = 10.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        itemsIndexed(
                            orderedSongs,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "song_row" }
                        ) { index, song ->
                            val isSelected = song.id in selectedIds
                            val isCurrent = song.id == currentSong?.id

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) toggleSelection(song.id)
                                            else viewModel.playSongs(orderedSongs, index)
                                        },
                                        onLongClick = { toggleSelection(song.id) }
                                    ),
                                shape = RoundedCornerShape(13.dp),
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isCurrent -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                tonalElevation = if (isCurrent || isSelected) 2.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(59.dp)
                                        .padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { toggleSelection(song.id) }
                                        )
                                        Spacer(Modifier.width(3.dp))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(47.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AdityaLogo(size = 27.dp)
                                    }

                                    Spacer(Modifier.width(11.dp))
                                    Text(
                                        text = song.title,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
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
private fun EmptyHome(modifier: Modifier, onRefresh: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            AdityaLogo(size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text("No music found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add audio files to your device storage and scan again.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("Scan Audio Files")
            }
        }
    }
}
