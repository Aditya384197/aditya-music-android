package com.aditya.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aditya.music.data.model.Song
import com.aditya.music.ui.viewmodel.MusicViewModel
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 88.dp.toPx() }
    var offsetY by remember(song.id) { mutableFloatStateOf(0f) }

    val dragState = rememberDraggableState { delta ->
        offsetY = (offsetY + delta).coerceAtLeast(0f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .graphicsLayer {
                alpha = (1f - (offsetY / 180f)).coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(15.dp))
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = {
                    if (offsetY >= dismissThresholdPx) {
                        offsetY = 0f
                        onDismiss()
                    } else {
                        offsetY = 0f
                    }
                }
            )
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 5.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (song.duration > 0L) {
                LinearProgressIndicator(
                    progress = (positionMs.toFloat() / song.duration.toFloat()).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkView(
                    artworkUri = song.albumArtUri,
                    modifier = Modifier.size(44.dp),
                    logoSize = 26.dp,
                    imageSizePx = 144,
                    contentDescription = song.title,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, "Next Track")
                }
            }
        }
    }
}

@Composable
fun ConnectedMiniPlayer(
    viewModel: MusicViewModel,
    visible: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val song by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val positionMs by viewModel.currentPosition.collectAsState()

    song?.let {
        MiniPlayer(
            song = it,
            isPlaying = isPlaying,
            positionMs = positionMs,
            onPlayPause = viewModel::togglePlayPause,
            onNext = viewModel::playNext,
            onClick = onClick,
            onDismiss = onDismiss
        )
    }
}
