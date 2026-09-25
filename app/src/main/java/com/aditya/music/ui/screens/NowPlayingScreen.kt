import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aditya.music.R
import com.aditya.music.media.player.PRESET_CLASSICAL
import com.aditya.music.media.player.PRESET_CUSTOM
import com.aditya.music.media.player.PRESET_DANCE
import com.aditya.music.media.player.PRESET_FLAT
import com.aditya.music.media.player.PRESET_POP
import com.aditya.music.media.player.PRESET_ROCK
import com.aditya.music.media.player.PRESET_VOCAL
import com.aditya.music.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

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

    Box(Modifier.fillMaxSize()) {
        AnimatedPlayerBackdrop()

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Now Playing", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showEqualizer = true }) {
                            Icon(Icons.Rounded.Equalizer, "Equalizer")
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, "More")
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
                                        Text(
                                            if (sleepRemainingMs > 0L) "Change sleep timer"
                                            else "Sleep timer"
                                        )
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            currentSong?.let { song ->
                var swipeOffset by remember(song.id) { mutableFloatStateOf(0f) }
                val density = androidx.compose.ui.platform.LocalDensity.current
                val swipeThresholdPx = with(density) { 72.dp.toPx() }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(maxWidth = 340.dp)
                            .aspectRatio(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        val cardWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                                .shadow(
                                    elevation = 28.dp,
                                    shape = RoundedCornerShape(30.dp),
                                    clip = false
                                )
                                .clip(RoundedCornerShape(30.dp))
                                .background(Color(0xFF0D1426))
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        swipeOffset = (swipeOffset + delta * 0.92f)
                                            .coerceIn(-cardWidthPx * 0.88f, cardWidthPx * 0.88f)
                                    },
                                    onDragStopped = { velocity ->
                                        val current = swipeOffset
                                        val goNext = current < -swipeThresholdPx ||
                                            (current < 0f && velocity < -1300f)
                                        val goPrevious = current > swipeThresholdPx ||
                                            (current > 0f && velocity > 1300f)
                                        val settle = Animatable(current)

                                        when {
                                            goNext -> {
                                                settle.animateTo(-cardWidthPx * 1.12f, tween(180)) {
                                                    swipeOffset = value
                                                }
                                                viewModel.playNext()
                                                swipeOffset = 0f
                                            }
                                            goPrevious -> {
                                                settle.animateTo(cardWidthPx * 1.12f, tween(180)) {
                                                    swipeOffset = value
                                                }
                                                viewModel.playPreviousTrack()
                                                swipeOffset = 0f
                                            }
                                            else -> {
                                                settle.animateTo(0f, tween(230)) {
                                                    swipeOffset = value
                                                }
                                            }
                                        }
                                    }
                                )
                        ) {
                            // The player's hero tile is intentionally the app logo, not a
                            // changing album-art tile, so the new Music identity is always visible.
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.music_logo),
                                contentDescription = "Music logo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }

                        // Subtle floating depth layer.
                        Box(
                            Modifier
                                .matchParentSize()
                                .padding(8.dp)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().basicMarquee()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = song.artist,
                            color = Color(0xFF9DDCFF),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().basicMarquee()
                        )
                        Text(
                            text = song.album,
                            color = Color.White.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.bodyMedium,
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
                                draggingProgress?.let {
                                    viewModel.seekTo((it * duration).toLong())
                                }
                                draggingProgress = null
                            },
                            enabled = duration > 0L,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF7DD3FC),
                                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                                thumbColor = Color.White,
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatDuration(shownPositionMs),
                                color = Color.White.copy(alpha = 0.82f),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                formatDuration(duration),
                                color = Color.White.copy(alpha = 0.82f),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (sleepRemainingMs > 0L) {
                        AssistChip(
                            onClick = { showSleepDialog = true },
                            label = { Text("Sleep ${formatTimer(sleepRemainingMs)}") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Bedtime, null, Modifier.size(18.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.White.copy(alpha = 0.10f),
                                labelColor = Color.White,
                                leadingIconContentColor = Color(0xFFFFD166)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.playPrevious() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                "Previous",
                                Modifier.size(36.dp),
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(24.dp))
                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(76.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF0A1020)
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(Modifier.width(24.dp))
                        IconButton(
                            onClick = { viewModel.playNext() },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                "Next",
                                Modifier.size(36.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { currentSong?.id?.let(viewModel::toggleFavorite) }
                        ) {
                            Icon(
                                if (isCurrentFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                "Favorite",
                                tint = if (isCurrentFavorite) Color(0xFFFF6B8A)
                                else Color.White.copy(alpha = 0.80f)
                            )
                        }
                        IconButton(onClick = { viewModel.toggleShuffle() }) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                "Shuffle",
                                tint = if (isShuffle) Color(0xFF7DD3FC)
                                else Color.White.copy(alpha = 0.80f)
                            )
                        }
                        IconButton(onClick = { viewModel.toggleRepeat() }) {
                            Icon(
                                when (repeatMode) {
                                    "one" -> Icons.Rounded.RepeatOne
                                    else -> Icons.Rounded.Repeat
                                },
                                "Repeat",
                                tint = if (repeatMode != "off") Color(0xFF7DD3FC)
                                else Color.White.copy(alpha = 0.80f)
                            )
                        }
                        IconButton(onClick = onOpenQueue) {
                            Icon(
                                Icons.Rounded.QueueMusic,
                                "Queue",
                                tint = Color.White.copy(alpha = 0.80f)
                            )
                        }
                    }
                }
            } ?: Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No song loaded", color = Color.White)
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

@Composable
private fun AnimatedPlayerBackdrop() {
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000))
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF050816),
                        Color(0xFF101A37),
                        Color(0xFF160D2A),
                        Color(0xFF040713)
                    )
                )
            )
    ) {
        Text(
            text = "M",
            color = Color.White.copy(alpha = 0.035f),
            fontSize = 300.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer(
                    translationX = sin(phase * Math.PI * 2.0).toFloat() * 28f
                )
        )

        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val t = phase * Math.PI * 2.0

            drawCircle(
                color = Color(0xFF3268FF).copy(alpha = 0.13f),
                radius = w * 0.34f,
                center = Offset(
                    x = w * (0.18f + 0.09f * sin(t).toFloat()),
                    y = h * 0.28f
                )
            )
            drawCircle(
                color = Color(0xFF9C4DFF).copy(alpha = 0.11f),
                radius = w * 0.30f,
                center = Offset(
                    x = w * (0.82f + 0.08f * sin(t + 1.5f).toFloat()),
                    y = h * 0.70f
                )
            )

            drawMusicWave(
                drawScope = this,
                phase = t,
                baseline = h * 0.42f,
                amplitude = h * 0.055f,
                color = Color.White.copy(alpha = 0.10f)
            )
            drawMusicWave(
                drawScope = this,
                phase = -t * 1.25,
                baseline = h * 0.55f,
                amplitude = h * 0.035f,
                color = Color(0xFF7DD3FC).copy(alpha = 0.11f)
            )
        }
    }
}

private fun drawMusicWave(
    drawScope: DrawScope,
    phase: Double,
    baseline: Float,
    amplitude: Float,
    color: Color
) {
    with(drawScope) {
        val path = Path()
        val width = size.width
        val steps = 90
        for (i in 0..steps) {
            val x = width * (i / steps.toFloat())
            val angle = phase + (i / steps.toFloat()) * Math.PI * 4.0
            val y = baseline + sin(angle).toFloat() * amplitude
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
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
    val trackHeight = 178.dp
    val thumbSize = 22.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val travelPx = with(density) {
        (trackHeight - thumbSize).toPx().coerceAtLeast(1f)
    }
    val span = (maxMb - minMb).coerceAtLeast(1).toFloat()
    var dragValue by remember(valueMb) { mutableFloatStateOf(valueMb.toFloat()) }

    fun offsetFor(value: Float): Dp {
        val normalized = ((value - minMb) / span).coerceIn(0f, 1f)
        return (trackHeight - thumbSize) * (1f - normalized)
    }

    val thumbOffset = offsetFor(dragValue)
    val zeroOffset = offsetFor(0f)

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

        Box(
            modifier = Modifier
                .width(48.dp)
                .height(trackHeight)
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Vertical,
                    startDragImmediately = true,
                    state = rememberDraggableState { delta ->
                        val next = (dragValue - (delta / travelPx) * span)
                            .coerceIn(minMb.toFloat(), maxMb.toFloat())
                        dragValue = next
                        onValueChange(next.roundToInt())
                    }
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .width(5.dp)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = zeroOffset + (thumbSize / 2f) - 1.dp)
                    .width(24.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbOffset)
                    .size(thumbSize),
                shape = CircleShape,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shadowElevation = if (enabled) 3.dp else 0.dp
            ) {}
        }

        Spacer(Modifier.height(7.dp))
        Text(
            formatGain(valueMb.toShort()),
            style = MaterialTheme.typography.labelSmall,
            color = if (valueMb == 0) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary,
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
