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

    // Multi-select state. Selection mode is simply "one or more ids selected".
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val isSelectionMode = selectedIds.isNotEmpty()
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun clearSelection() { selectedIds = emptySet() }
    fun toggleSelection(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    // Android 10+ requires user confirmation (a system dialog) before this app can delete
    // songs it didn't create itself. This launcher shows that dialog when needed, and it now
    // covers every selected song in ONE dialog instead of one prompt per song.
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeletePermissionGranted()
        } else {
            viewModel.onDeletePermissionDenied()
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
                if (success) "Deleted" else "Delete cancelled",
                Toast.LENGTH_SHORT
            ).show()
            if (success) clearSelection()
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { clearSelection() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectedIds = songs.map { it.id }.toSet() }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select all")
                        }
                        IconButton(onClick = {
                            viewModel.shareSongs(songs.filter { it.id in selectedIds }, context)
                        }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = {
                            Toast.makeText(context, "Playlist feature coming soon", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to playlist")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Songs (${songs.size})", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showSortMenu = !showSortMenu }) {
                            Icon(Icons.Rounded.Sort, contentDescription = "Sort")
                        }
                    }
                )
            }
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
                    val isSelected = song.id in selectedIds
                    ListItem(
                        headlineContent = {
                            Text(song.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        },
                        supportingContent = {
                            Text("${song.artist} • ${song.formattedDuration}", maxLines = 1)
                        },
                        leadingContent = {
                            if (isSelectionMode) {
                                Checkbox(checked = isSelected, onCheckedChange = { toggleSelection(song.id) })
                            } else {
                                AdityaLogo(size = 38.dp)
                            }
                        },
                        trailingContent = {
                            if (!isSelectionMode) {
                                IconButton(onClick = { viewModel.toggleFavorite(song.id) }) {
                                    Icon(
                                        imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = if (isSelected) {
                            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            ListItemDefaults.colors()
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (isSelectionMode) toggleSelection(song.id)
                                else viewModel.playSongs(songs, index)
                            },
                            // Hold a song for ~1 second to enter selection mode - matches the
                            // familiar "hold to select" gesture from gallery/file-manager apps.
                            onLongClick = { toggleSelection(song.id) }
                        )
                    )
                }
            }
        }
    }

    // Delete confirmation dialog (covers every currently selected song)
    if (showDeleteDialog) {
        val toDelete = songs.filter { it.id in selectedIds }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(if (toDelete.size == 1) "Delete song?" else "Delete ${toDelete.size} songs?") },
            text = {
                Text(
                    if (toDelete.size == 1) {
                        "\"${toDelete.first().title}\" will be permanently deleted from your device."
                    } else {
                        "These ${toDelete.size} songs will be permanently deleted from your device."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSongs(toDelete)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
