package com.aditya.music.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clip
import com.aditya.music.data.model.Song
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var offsetY by remember(song.id) { mutableFloatStateOf(0f) }

    val dragState = rememberDraggableState { delta ->
        offsetY = (offsetY + delta).coerceAtLeast(0f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .graphicsLayer {
                alpha = (1f - (offsetY / 180f)).coerceIn(0.25f, 1f)
            }
            .clip(RoundedCornerShape(16.dp))
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = {
                    if (offsetY >= 84f) {
                        offsetY = 0f
                        onDismiss()
                    } else {
                        offsetY = 0f
                    }
                }
            )
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    AdityaLogo(size = 28.dp)
                }

                Spacer(modifier = Modifier.width(12.dp))

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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next Track"
                    )
                }
            }
        }
    }
}
