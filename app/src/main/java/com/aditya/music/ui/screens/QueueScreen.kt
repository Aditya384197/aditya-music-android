package com.aditya.music.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aditya.music.ui.components.AdityaLogo
import com.aditya.music.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val queue by viewModel.playbackQueue.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playing Queue (${queue.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearQueue() }) {
                        Icon(Icons.Rounded.ClearAll, contentDescription = "Clear Queue")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            itemsIndexed(queue, key = { _, song -> song.id }) { index, song ->
                val isCurrent = song.id == currentSong?.id
                ListItem(
                    headlineContent = {
                        Text(
                            song.title,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supportingContent = { Text(song.artist) },
                    leadingContent = { AdityaLogo(size = 32.dp) },
                    trailingContent = { Text(song.formattedDuration) },
                    modifier = Modifier.clickable { viewModel.playQueueIndex(index) }
                )
            }
        }
    }
}
