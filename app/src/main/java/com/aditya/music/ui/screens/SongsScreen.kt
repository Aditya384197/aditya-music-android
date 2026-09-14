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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aditya.music.data.model.Song
import com.aditya.music.ui.components.AdityaLogo
import com.aditya.music.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SongsScreen(viewModel: MusicViewModel) {
    val songs by viewModel.filteredSongs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Long-press menu state
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }

    // Android 10+ requires user confirmation (a system dialog) before this app can delete a
    // song it didn't create itself. This launcher shows that dialog when needed.
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeletePermissionGranted()
        }
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
                if (success) "Song deleted" else "Couldn't delete song",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Songs (${songs.size})", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSortMenu = !showSortMenu }) {
                        Icon(Icons.Rounded.Sort, contentDescription = "Sort")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search songs, artists, albums...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    // Anchored to this exact row (a Box wraps both the row and its menu) so the
                    // dropdown opens next to the song that was long-pressed instead of drifting to
                    // a fixed corner of the screen.
                    Box {
                        ListItem(
                            headlineContent = {
                                Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            },
                            supportingContent = {
                                Text("${song.artist} • ${song.formattedDuration}", maxLines = 1)
                            },
                            leadingContent = {
                                AdityaLogo(size = 38.dp)
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.toggleFavorite(song.id) }) {
                                    Icon(
                                        imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { viewModel.playSongs(songs, index) },
                                onLongClick = { menuSong = song }
                            )
                        )

                        DropdownMenu(
                            expanded = menuSong?.id == song.id,
                            onDismissRequest = { menuSong = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play") },
                                leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                                onClick = {
                                    viewModel.playSongs(songs, index)
                                    menuSong = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                onClick = {
                                    viewModel.shareSong(song, context)
                                    menuSong = null
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Add to Playlist") },
                                leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                                onClick = {
                                    Toast.makeText(context, "Playlist feature coming soon", Toast.LENGTH_SHORT).show()
                                    menuSong = null
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    songToDelete = song
                                    showDeleteDialog = true
                                    menuSong = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        songToDelete?.let { song ->
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false; songToDelete = null },
                title = { Text("Delete song?") },
                text = { Text("\"${song.title}\" will be permanently deleted from your device.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteSong(song)
                            showDeleteDialog = false
                            songToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false; songToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}
