package com.aditya.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import com.aditya.music.ui.components.AdityaLogo
import com.aditya.music.ui.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenQueue) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue")
                    }
                }
            )
        }
    ) { padding ->
        currentSong?.let { song ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Big stylized album artwork card
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AdityaLogo(size = 140.dp)
                }

                // Song Title & Artist
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    )
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Scrub Bar & Time
                Column(modifier = Modifier.fillMaxWidth()) {
                    var draggingProgress by remember { mutableStateOf<Float?>(null) }
                    val actualProgress = if (song.duration > 0) currentPosition.toFloat() / song.duration else 0f
                    val shownProgress = draggingProgress ?: actualProgress
                    val shownPositionMs = draggingProgress?.let { (it * song.duration).toLong() } ?: currentPosition

                    Slider(
                        value = shownProgress.coerceIn(0f, 1f),
                        onValueChange = { draggingProgress = it },
                        onValueChangeFinished = {
                            draggingProgress?.let { viewModel.seekTo((it * song.duration).toLong()) }
                            draggingProgress = null
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(shownPositionMs), style = MaterialTheme.typography.bodySmall)
                        Text(song.formattedDuration, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { viewModel.playPrevious() }) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                    }

                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.playNext() }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                    }

                    IconButton(onClick = { viewModel.toggleRepeat() }) {
                        Icon(
                            Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            tint = if (repeatMode != "off") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No song loaded")
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val sec = ms / 1000
    return String.format("%d:%02d", sec / 60, sec % 60)
}
