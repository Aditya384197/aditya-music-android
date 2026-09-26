package com.aditya.music.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aditya.music.data.model.Song
import com.aditya.music.media.player.PRESET_CLASSICAL
import com.aditya.music.media.player.PRESET_CUSTOM
import com.aditya.music.media.player.PRESET_DANCE
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .sizeIn(maxWidth = 356.dp, maxHeight = 356.dp)
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
 * Ambient glow behind the whole Now Playing page - decorative and low-alpha so it never
 * competes with the artwork or controls sitting on top of it. Rather than one single repeating
 * pattern, this slowly drifts through a handful of different looks (a rotating sweep, a soft
 * breathing glow, a wandering pair of blobs, a gentle diagonal wash) and cross-fades between them
 * so gradually that the switch itself is never noticeable - only that the background is always
 * quietly alive.
 */
@Composable
private fun AmbientAnimatedBackground(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "ambientBg")

    // Continuous motions that keep running no matter which scene is currently showing, so a
    // scene never looks "reset" the moment it fades back in.
    val rotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing)),
        label = "rotation"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val wander by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(19_000, easing = LinearEasing)),
        label = "wander"
    )
    // Which scene is showing, as a continuous position: sceneIndex.fraction. A whole loop
    // through every scene takes sceneCount * secondsPerScene, and it's a perfect loop (ends
    // exactly where it started) so the restart is invisible too.
    val sceneCount = 4
    val secondsPerScene = 24_000
    val scenePosition by infinite.animateFloat(
        initialValue = 0f, targetValue = sceneCount.toFloat(),
        animationSpec = infiniteRepeatable(tween(secondsPerScene * sceneCount, easing = LinearEasing)),
        label = "scenePosition"
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val background = MaterialTheme.colorScheme.background

    Box(modifier = modifier.background(background)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val sceneIndex = scenePosition.toInt().coerceIn(0, sceneCount - 1)
            val nextIndex = (sceneIndex + 1) % sceneCount
            val localT = scenePosition - sceneIndex
            // Only actually cross-fade during the last third of each scene's turn - the rest of
            // the time only one scene is drawn at full strength, so there's no visible fade
            // happening most of the time, just an occasional, very slow hand-off.
            val blend = ((localT - 0.66f) / 0.34f).coerceIn(0f, 1f)

            drawAmbientScene(sceneIndex, 1f - blend, rotation, pulse, wander, primary, secondary, tertiary, background)
            drawAmbientScene(nextIndex, blend, rotation, pulse, wander, primary, secondary, tertiary, background)
        }
    }
}

private fun DrawScope.drawAmbientScene(
    index: Int,
    alpha: Float,
    rotation: Float,
    pulse: Float,
    wander: Float,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color
) {
    if (alpha <= 0f) return
    when (index) {
        0 -> drawSweepScene(rotation, alpha, primary, secondary, tertiary, background)
        1 -> drawPulseScene(pulse, alpha, primary, tertiary)
        2 -> drawWanderScene(wander, alpha, primary, secondary)
        else -> drawDiagonalScene(wander, alpha, secondary, tertiary)
    }
}

private fun DrawScope.drawSweepScene(
    angleDeg: Float,
    alpha: Float,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color
) {
    rotate(angleDeg) {
        drawRect(
            brush = Brush.sweepGradient(
                listOf(
                    primary.copy(alpha = 0.20f * alpha),
                    tertiary.copy(alpha = 0.14f * alpha),
                    secondary.copy(alpha = 0.16f * alpha),
                    background.copy(alpha = 0f),
                    primary.copy(alpha = 0.20f * alpha)
                )
            ),
            size = androidx.compose.ui.geometry.Size(size.width * 1.6f, size.height * 1.6f),
            topLeft = androidx.compose.ui.geometry.Offset(-size.width * 0.3f, -size.height * 0.3f)
        )
    }
}

private fun DrawScope.drawPulseScene(phase: Float, alpha: Float, primary: Color, tertiary: Color) {
    val scale = 0.85f + 0.3f * phase
    drawCircle(
        brush = Brush.radialGradient(
            listOf(primary.copy(alpha = 0.22f * alpha), tertiary.copy(alpha = 0.10f * alpha), Color.Transparent),
            center = center,
            radius = size.minDimension * 0.7f * scale
        ),
        radius = size.minDimension * 0.7f * scale,
        center = center
    )
}

private fun DrawScope.drawWanderScene(angleDeg: Float, alpha: Float, primary: Color, secondary: Color) {
    val rad1 = Math.toRadians(angleDeg.toDouble())
    val rad2 = Math.toRadians((angleDeg + 150f).toDouble())
    val radius = size.minDimension * 0.32f
    val c1 = androidx.compose.ui.geometry.Offset(
        (size.width * 0.5f + (kotlin.math.cos(rad1) * size.width * 0.28f)).toFloat(),
        (size.height * 0.5f + (kotlin.math.sin(rad1) * size.height * 0.22f)).toFloat()
    )
    val c2 = androidx.compose.ui.geometry.Offset(
        (size.width * 0.5f + (kotlin.math.cos(rad2) * size.width * 0.26f)).toFloat(),
        (size.height * 0.5f + (kotlin.math.sin(rad2) * size.height * 0.24f)).toFloat()
    )
    drawCircle(
        brush = Brush.radialGradient(listOf(primary.copy(alpha = 0.20f * alpha), Color.Transparent), center = c1, radius = radius),
        radius = radius, center = c1
    )
    drawCircle(
        brush = Brush.radialGradient(listOf(secondary.copy(alpha = 0.18f * alpha), Color.Transparent), center = c2, radius = radius * 0.85f),
        radius = radius * 0.85f, center = c2
    )
}

private fun DrawScope.drawDiagonalScene(angleDeg: Float, alpha: Float, secondary: Color, tertiary: Color) {
    val t = (kotlin.math.sin(Math.toRadians(angleDeg.toDouble())).toFloat() + 1f) / 2f
    val start = androidx.compose.ui.geometry.Offset(size.width * (0.1f + 0.2f * t), 0f)
    val end = androidx.compose.ui.geometry.Offset(size.width * (0.7f - 0.2f * t), size.height)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                secondary.copy(alpha = 0.16f * alpha),
                tertiary.copy(alpha = 0.12f * alpha),
                Color.Transparent
            ),
            start = start,
            end = end
        )
    )
}

/**
 * The square artwork "box": gently bobs up and down to feel like it's floating above the
 * background, casts a soft coloured shadow for depth, and can be swiped left/right to skip to
 * the next/previous song without needing to reach for the buttons below.
 */
@Composable
private fun FloatingArtwork(
    song: Song,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "floatBob")
    val bobOffset by infinite.animateFloat(
        initialValue = -13f,
        targetValue = 13f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 90.dp.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val primary = MaterialTheme.colorScheme.primary
    val cornerShape = RoundedCornerShape(40.dp)

    Crossfade(targetState = song.id, label = "artworkCrossfade") {
        Box(
            modifier = modifier
                .graphicsLayer { translationY = bobOffset }
                .shadow(
                    elevation = 30.dp,
                    shape = cornerShape,
                    ambientColor = primary.copy(alpha = 0.38f),
                    spotColor = primary.copy(alpha = 0.5f)
                )
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta -> dragAccum += delta },
                    onDragStopped = {
                        when {
                            dragAccum <= -swipeThresholdPx -> onSwipeNext()
                            dragAccum >= swipeThresholdPx -> onSwipePrevious()
                        }
                        dragAccum = 0f
                    }
                )
        ) {
            ArtworkView(
                artworkUri = song.albumArtUri,
                modifier = Modifier.fillMaxSize(),
                logoSize = 168.dp,
                imageSizePx = 720,
                contentDescription = "Album artwork",
                shape = cornerShape
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
    val presets = listOf(PRESET_FLAT, PRESET_POP, PRESET_ROCK, PRESET_DANCE, PRESET_CLASSICAL, PRESET_VOCAL)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (supported) "Pure sound bypass is the default"
                        else "Equalizer is not available for this audio session",
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

            if (state.headsetProfileName != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (state.headsetProfileActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            if (state.headsetProfileActive) "Auto-tuned headset" else "Headset profile selected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            state.headsetProfileName!!,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        state.headsetProfileSource?.let { source ->
                            Text(
                                "Measured source: $source",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (!state.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (state.enabled) Icons.Rounded.GraphicEq else Icons.Rounded.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (state.enabled) "EQ is shaping the sound" else "Bypass: source audio is left unshaped",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
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
                    FilterChip(selected = true, onClick = {}, enabled = false, label = { Text(PRESET_CUSTOM) })
                }
            }

            Spacer(Modifier.height(18.dp))
            ToneSlider("Bass", state.bassDb, supported) { viewModel.setEqualizerBassDb(it) }
            ToneSlider("Mid", state.midDb, supported) { viewModel.setEqualizerMidDb(it) }
            ToneSlider("Treble", state.trebleDb, supported) { viewModel.setEqualizerTrebleDb(it) }

            if (state.bandLevelsMb.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Fine bands", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Vertical controls for precise frequency tuning. 0 dB leaves that band unchanged.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(9.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    state.bandLevelsMb.forEachIndexed { index, level ->
                        val frequency = state.bandFrequenciesHz.getOrNull(index) ?: 0
                        VerticalBandSlider(
                            frequency = formatFrequency(frequency),
                            valueMb = level.toInt(),
                            minMb = state.bandLevelMinMb.toInt(),
                            maxMb = state.bandLevelMaxMb.toInt(),
                            enabled = supported,
                            onValueChange = { viewModel.setEqualizerBand(index, it) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.resetEqualizer() },
                enabled = supported,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.RestartAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Reset to pure sound")
            }
        }
    }
}

@Composable
private fun VerticalBandSlider(
    frequency: String,
    valueMb: Int,
    minMb: Int,
    maxMb: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    val trackLength = 178.dp
    val safeMin = minMb.coerceAtMost(maxMb - 1)

    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            frequency,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(Modifier.height(7.dp))

        // Same Material3 Slider component used for Bass/Mid/Treble above - just rotated 90
        // degrees. It already handles drag tracking, clamping and touch physics correctly (that's
        // why Bass/Mid/Treble never had this problem); a hand-rolled drag gesture for these
        // vertical bands was what kept getting stuck partway, sticking to the bottom, or refusing
        // to reach the top. Reusing the same reliable widget fixes it for good instead of trying
        // to patch the custom math again.
        Slider(
            value = valueMb.toFloat().coerceIn(safeMin.toFloat(), maxMb.toFloat()),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = safeMin.toFloat()..maxMb.toFloat(),
            enabled = enabled,
            modifier = Modifier
                .width(trackLength)
                .graphicsLayer {
                    rotationZ = 270f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        androidx.compose.ui.unit.Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(-placeable.width, 0)
                    }
                }
        )

        Spacer(Modifier.height(7.dp))
        Text(
            formatGain(valueMb.toShort()),
            style = MaterialTheme.typography.labelSmall,
            color = if (valueMb == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ToneSlider(
    label: String,
    valueDb: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit
) {
    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Slider(
        value = valueDb.coerceIn(-6f, 6f),
        onValueChange = onChange,
        valueRange = -6f..6f,
        steps = 23,
        enabled = enabled
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("-6 dB", style = MaterialTheme.typography.labelSmall)
        Text("${formatDb(valueDb)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text("+6 dB", style = MaterialTheme.typography.labelSmall)
    }
    Spacer(Modifier.height(8.dp))
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

private fun formatGain(mb: Short): String {
    val db = mb / 100f
    return if (db > 0f) "+%.1f".format(db) else "%.1f".format(db)
}

private fun formatDb(db: Float): String =
    if (db > 0.05f) "+%.1f dB".format(db) else if (db < -0.05f) "%.1f dB".format(db) else "0 dB"

private fun formatFrequency(hz: Int): String = when {
    hz >= 1000 && hz % 1000 == 0 -> "${hz / 1000} kHz"
    hz >= 1000 -> "%.1f kHz".format(hz / 1000f)
    hz > 0 -> "$hz Hz"
    else -> "Band"
}
