package com.aditya.music.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import com.aditya.music.data.model.Song
import com.aditya.music.media.player.PRESET_CUSTOM
import com.aditya.music.media.player.PRESET_DANCE
import com.aditya.music.media.player.PRESET_DJ
import com.aditya.music.media.player.PRESET_FLAT
import com.aditya.music.media.player.PRESET_POP
import com.aditya.music.media.player.PRESET_ROCK
import com.aditya.music.media.player.PRESET_VOCAL
import com.aditya.music.ui.components.ArtworkView
import com.aditya.music.ui.viewmodel.MusicViewModel
import kotlin.math.roundToInt

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
    val sleepRemainingMs by viewModel.sleepTimerRemainingMs.collectAsState()
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val isCurrentFavorite = currentSong?.id?.let { id -> favoriteSongs.any { it.id == id } } == true

    var showEqualizer by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showEqualizer) {
        if (showEqualizer) viewModel.initializeEqualizer()
    }

    // The whole page (background, top bar and Scaffold) is transparent so the ambient animated
    // backdrop drawn below shows through everywhere, with the controls floating on top of it.
    Box(modifier = Modifier.fillMaxSize()) {
        AmbientAnimatedBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Now Playing", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showEqualizer = true }) {
                            Icon(
                                Icons.Rounded.Equalizer,
                                contentDescription = "Equalizer",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Playing queue") },
                                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onOpenQueue()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(if (sleepRemainingMs > 0L) "Change sleep timer" else "Sleep timer")
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Bedtime, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showSleepDialog = true
                                    }
                                )
                                if (sleepRemainingMs > 0L) {
                                    DropdownMenuItem(
                                        text = { Text("Cancel sleep timer") },
                                        leadingIcon = { Icon(Icons.Rounded.TimerOff, null) },
                                        onClick = {
                                            viewModel.cancelSleepTimer()
                                            showMoreMenu = false
                                        }
                                    )
                                }
                            }
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
                        .padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    FloatingArtwork(
                        song = song,
                        onSwipeNext = { viewModel.playNext() },
                        onSwipePrevious = { viewModel.playPrevious() },
                        // A single fixed, definitely-square size - no fillMaxWidth/aspectRatio/
                        // sizeIn combination that can end up unequal on a shorter screen, which is
                        // what was letting the artwork render non-square and get cropped oddly.
                        modifier = Modifier.size(320.dp)
                    )

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
                        Spacer(Modifier.height(5.dp))
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        var draggingProgress by remember(song.id) { mutableStateOf<Float?>(null) }
                        val duration = song.duration.coerceAtLeast(0L)
                        val actualProgress = if (duration > 0L) {
                            (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                        } else 0f
                        val shownProgress = draggingProgress ?: actualProgress
                        val shownPositionMs = if (draggingProgress != null) {
                            (draggingProgress!! * duration).toLong()
                        } else {
                            currentPosition
                        }

                        Slider(
                            value = shownProgress,
                            onValueChange = { draggingProgress = it },
                            onValueChangeFinished = {
                                draggingProgress?.let { viewModel.seekTo((it * duration).toLong()) }
                                draggingProgress = null
                            },
                            enabled = duration > 0L
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDuration(shownPositionMs), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (sleepRemainingMs > 0L) {
                        AssistChip(
                            onClick = { showSleepDialog = true },
                            label = { Text("Sleep ${formatTimer(sleepRemainingMs)}") },
                            leadingIcon = { Icon(Icons.Rounded.Bedtime, null, Modifier.size(18.dp)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.playPrevious() },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(34.dp))
                        }
                        Spacer(Modifier.width(22.dp))
                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(Modifier.width(22.dp))
                        IconButton(
                            onClick = { viewModel.playNext() },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(34.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentSong?.id?.let(viewModel::toggleFavorite) }) {
                            Icon(
                                if (isCurrentFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                "Favorite",
                                tint = if (isCurrentFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.toggleShuffle() }) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                "Shuffle",
                                tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.toggleRepeat() }) {
                            Icon(
                                when (repeatMode) {
                                    "one" -> Icons.Rounded.RepeatOne
                                    else -> Icons.Rounded.Repeat
                                },
                                "Repeat",
                                tint = if (repeatMode != "off") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onOpenQueue) {
                            Icon(Icons.Rounded.QueueMusic, "Queue")
                        }
                    }
                }
            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No song loaded")
            }
        }
    }

    if (showEqualizer) {
        EqualizerBottomSheet(
            viewModel = viewModel,
            supported = equalizerState.supported,
            onDismiss = { showEqualizer = false }
        )
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            remainingMs = sleepRemainingMs,
            onSelectMinutes = {
                viewModel.startSleepTimer(it)
                showSleepDialog = false
            },
            onCancel = {
                viewModel.cancelSleepTimer()
                showSleepDialog = false
            },
            onDismiss = { showSleepDialog = false }
        )
    }
}

/**
 * Lightweight backdrop: a single static gradient replaces the previous continuously animated
 * canvas scenes. It keeps the page premium-looking without consuming a render loop while music is
 * playing.
 */
@Composable
private fun AmbientAnimatedBackground(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.15f),
                    background.copy(alpha = 0.98f),
                    background
                )
            )
        )
    )
}

/**
 * One clean square only. The artwork is drawn at its natural aspect ratio with no scale-up crop,
 * no glow and no shadow layer behind it. When artwork is missing, ArtworkView places the real
 * Aditya app logo directly into this same square.
 */
@Composable
private fun FloatingArtwork(
    song: Song,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeThresholdPx = with(density) { 90.dp.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    Crossfade(targetState = song.id, label = "artworkCrossfade") {
        ArtworkView(
            artworkUri = song.albumArtUri,
            modifier = modifier.draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> dragAccum += delta },
                onDragStopped = {
                    when {
                        dragAccum <= -swipeThresholdPx -> onSwipeNext()
                        dragAccum >= swipeThresholdPx -> onSwipePrevious()
                    }
                    dragAccum = 0f
                }
            ),
            imageSizePx = 512,
            contentDescription = "Album artwork",
            shape = RoundedCornerShape(40.dp)
        )
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
        PRESET_DJ,
        PRESET_VOCAL
    )
    val ready = supported && state.bandLevelsMb.isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 30.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "DJ Equalizer",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            !supported -> "Hardware equalizer is not available for this audio session"
                            !ready -> "Preparing the device equalizer…"
                            else -> "Real audio-session EQ • 10-band control when supported"
                        },
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

            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (state.enabled) "DJ shaping is active" else "Bypass — original audio path is left untouched",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("DJ presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = { viewModel.applyEqualizerPreset(preset) },
                        enabled = supported,
                        label = { Text(preset) }
                    )
                }
                if (state.preset == PRESET_CUSTOM) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        enabled = false,
                        label = { Text(PRESET_CUSTOM) }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Frequency faders", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Move each fader like a DJ mixer. 0 dB keeps that band flat.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            if (ready) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    state.bandLevelsMb.forEachIndexed { index, level ->
                        DjBandFader(
                            frequency = formatFrequency(state.bandFrequenciesHz.getOrNull(index) ?: 0),
                            valueMb = level.toInt(),
                            minMb = state.bandLevelMinMb,
                            maxMb = state.bandLevelMaxMb,
                            enabled = supported,
                            onValueChange = { viewModel.setEqualizerBand(index, it) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = { viewModel.resetEqualizer() },
                enabled = supported,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.RestartAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Reset to Flat")
            }
        }
    }
}

@Composable
private fun DjBandFader(
    frequency: String,
    valueMb: Int,
    minMb: Int,
    maxMb: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    val safeMin = minMb.coerceAtMost(maxMb - 1)
    val safeValue = valueMb.coerceIn(safeMin, maxMb)

    Column(
        modifier = Modifier.width(54.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            frequency,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(172.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = safeValue.toFloat(),
                onValueChange = { onValueChange(it.roundToInt()) },
                valueRange = safeMin.toFloat()..maxMb.toFloat(),
                enabled = enabled,
                modifier = Modifier
                    .width(160.dp)
                    .rotate(-90f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            formatGain(safeValue),
            style = MaterialTheme.typography.labelSmall,
            color = if (safeValue == 0) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SleepTimerDialog(
    remainingMs: Long,
    onSelectMinutes: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (remainingMs > 0L) {
                    Text("Current: ${formatTimer(remainingMs)}", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                }
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    FilledTonalButton(
                        onClick = { onSelectMinutes(minutes) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop after $minutes minutes")
                    }
                }
                if (remainingMs > 0L) {
                    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel timer")
                    }
                }
            }
        },
        confirmButton = {}
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatTimer(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun formatGain(mb: Int): String {
    val db = mb / 100f
    return if (db > 0f) "+%.1f".format(db) else "%.1f".format(db)
}


private fun formatFrequency(hz: Int): String = when {
    hz >= 1000 && hz % 1000 == 0 -> "${hz / 1000} kHz"
    hz >= 1000 -> "%.1f kHz".format(hz / 1000f)
    hz > 0 -> "$hz Hz"
    else -> "Band"
}
