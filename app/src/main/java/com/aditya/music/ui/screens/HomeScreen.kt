package com.aditya.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aditya.music.data.model.Song
import com.aditya.music.ui.components.AdityaLogo
import com.aditya.music.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onNavigateToSongs: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    val songs by viewModel.allSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AdityaLogo(size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Aditya Music", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("By Aditya", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshLibrary) { Icon(Icons.Rounded.Refresh, "Refresh Library") }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                }
            )
        }
    ) { padding ->
        when {
            isScanning -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            songs.isEmpty() -> EmptyHome(Modifier.fillMaxSize().padding(padding), viewModel::refreshLibrary)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 112.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.playSongs(songs, 0) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Quick Play", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${songs.size} offline tracks available", style = MaterialTheme.typography.bodyMedium)
                            }
                            FilledIconButton(onClick = { viewModel.playSongs(songs, 0) }) { Icon(Icons.Rounded.PlayArrow, "Play") }
                        }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recently Played", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${recentlyPlayed.size}/20", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (recentlyPlayed.isEmpty()) {
                    item { Text("Play songs and your latest 20 unique tracks will stay here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(recentlyPlayed, key = { _, song -> song.id }) { index, song -> RecentSongCard(song) {
                                viewModel.playSongs(recentlyPlayed, index)
                            } }
                        }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onNavigateToPlaylists) { Text(if (playlists.isEmpty()) "Add" else "View all") }
                    }
                }
                if (playlists.isEmpty()) item { TextButton(onClick = onNavigateToPlaylists) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add Playlist") } }
                else items(playlists.take(5), key = { it.id }) { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("Playlist") },
                        leadingContent = { Icon(Icons.Rounded.QueueMusic, null) },
                        modifier = Modifier.clickable(onClick = onNavigateToPlaylists)
                    )
                }

                if (favorites.isNotEmpty()) {
                    item { Text("Favorites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    itemsIndexed(favorites.take(5), key = { _, song -> song.id }) { favIndex, song ->
                        ListItem(
                            headlineContent = { Text(song.title, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(song.artist) },
                            leadingContent = { AdityaLogo(size = 36.dp) },
                            trailingContent = { IconButton(onClick = { viewModel.playSongs(favorites, favIndex) }) { Icon(Icons.Rounded.PlayArrow, "Play") } },
                            modifier = Modifier.clickable { viewModel.playSongs(favorites, favIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSongCard(song: Song, onClick: () -> Unit) {
    Card(modifier = Modifier.width(150.dp).clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { AdityaLogo(size = 42.dp) }
            Spacer(Modifier.height(8.dp))
            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyHome(modifier: Modifier, onRefresh: () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Rounded.MusicOff, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp)); Text("No music found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp)); Text("Add audio files to your device storage and refresh your library.")
            Spacer(Modifier.height(20.dp)); Button(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Scan Audio Files") }
        }
    }
}
