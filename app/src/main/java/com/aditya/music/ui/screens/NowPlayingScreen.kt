package com.aditya.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aditya.music.media.player.PRESET_CLASSICAL
import com.aditya.music.media.player.PRESET_CUSTOM
import com.aditya.music.media.player.PRESET_DANCE
import com.aditya.music.media.player.PRESET_FLAT
import com.aditya.music.media.player.PRESET_POP
import com.aditya.music.media.player.PRESET_ROCK
import com.aditya.music.media.player.PRESET_VOCAL
import com.aditya.music.media.player.ROOM_NONE
import com.aditya.music.media.player.ROOM_SMALL
import com.aditya.music.media.player.ROOM_MEDIUM
import com.aditya.music.media.player.ROOM_LARGE
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
    val equalizerState by viewModel.equalizerState.collectAsState()
    var showEqualizer by remember { mutableStateOf(false) }

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
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AdityaLogo(size = 140.dp)

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(52.dp)
                            .clickable { showEqualizer = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = "Equalizer",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().basicMarquee()
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

                Column(modifier = Modifier.fillMaxWidth()) {
                    var draggingProgress by remember { mutableStateOf<Float?>(null) }
                    val actualProgress = if (song.duration > 0) {
                        currentPosition.toFloat() / song.duration
                    } else 0f
                    val shownProgress = draggingProgress ?: actualProgress
                    val shownPositionMs = draggingProgress?.let {
                        (it * song.duration).toLong()
                    } ?: currentPosition

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
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
                            tint = if (repeatMode != "off") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } ?: run {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No song loaded")
            }
        }

        if (showEqualizer) {
            EqualizerBottomSheet(
                viewModel = viewModel,
                supported = equalizerState.supported,
                onDismiss = { showEqualizer = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EqualizerBottomSheet(
    viewModel: MusicViewModel,
    supported: Boolean,
    onDismiss: () -> Unit
) {
    val state by viewModel.equalizerState.collectAsState()
    val presets = listOf(
        PRESET_FLAT,
        PRESET_POP,
        PRESET_ROCK,
        PRESET_DANCE,
        PRESET_CLASSICAL,
        PRESET_VOCAL,
        PRESET_CUSTOM
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Equalizer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (supported) "Playback sound shaping"
                        else "Not supported by this device/session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = viewModel::setEqualizerEnabled,
                    enabled = supported
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = { viewModel.applyEqualizerPreset(preset) },
                        enabled = supported && preset != PRESET_CUSTOM,
                        label = { Text(preset) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text("Bass", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Slider(
                value = state.bassStrength / 1000f,
                onValueChange = { viewModel.setEqualizerBass((it * 1000).toInt()) },
                enabled = supported
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = MaterialTheme.typography.labelSmall)
                Text("${state.bassStrength / 10}%", style = MaterialTheme.typography.labelSmall)
                Text("100%", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Clarity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Slider(value = state.clarityStrength / 1000f, onValueChange = { viewModel.setEqualizerClarity((it * 1000).toInt()) }, enabled = supported)
            Text("Vocals and mid-range detail", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(14.dp))
            Text("Room", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ROOM_NONE, ROOM_SMALL, ROOM_MEDIUM, ROOM_LARGE).forEach { room ->
                    FilterChip(selected = state.room == room, onClick = { viewModel.setEqualizerRoom(room) }, enabled = supported, label = { Text(room) })
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text("Frequency bands", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Drag each vertical band up or down", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(270.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.bandLevelsMb.forEachIndexed { index, level ->
                    val frequency = state.bandFrequenciesHz.getOrNull(index) ?: 0
                    val minMb = state.bandLevelMinMb.toFloat()
                    val maxMb = state.bandLevelMaxMb.toFloat()
                    val span = (maxMb - minMb).coerceAtLeast(1f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp).fillMaxHeight()) {
                        Text(formatGain(level), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Box(modifier = Modifier.weight(1f).width(72.dp), contentAlignment = Alignment.Center) {
                            Slider(
                                value = ((level.toFloat() - minMb) / span).coerceIn(0f, 1f),
                                onValueChange = { viewModel.setEqualizerBand(index, (minMb + it * span).toInt()) },
                                enabled = supported,
                                modifier = Modifier.width(220.dp).rotate(-90f)
                            )
                        }
                        Text(formatFrequency(frequency), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }

            Text(
                "Tip: EQ can only shape the audio that is already in the file. It cannot create missing detail from a low-quality source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatFrequency(hz: Int): String = when {
    hz >= 1000 -> {
        val khz = hz / 1000f
        if (khz == khz.toInt().toFloat()) "${khz.toInt()} kHz" else "%.1f kHz".format(khz)
    }
    hz > 0 -> "$hz Hz"
    else -> "Band"
}

private fun formatGain(mb: Short): String {
    val db = mb / 100f
    return if (db > 0f) "+%.1f dB".format(db) else "%.1f dB".format(db)
}

private fun formatDuration(ms: Long): String {
    val sec = (ms.coerceAtLeast(0L)) / 1000
    return String.format("%d:%02d", sec / 60, sec % 60)
}
