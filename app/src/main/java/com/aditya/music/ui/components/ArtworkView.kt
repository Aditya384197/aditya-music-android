package com.aditya.music.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.ui.platform.LocalContext

/**
 * Artwork renderer with a reliable Aditya logo fallback.
 * Failed/invalid embedded artwork never leaves a blank tile behind.
 */
@Composable
fun ArtworkView(
    artworkUri: Uri?,
    modifier: Modifier = Modifier,
    logoSize: Dp,
    imageSizePx: Int,
    contentDescription: String? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp)
) {
    val context = LocalContext.current
    var artworkFailed by remember(artworkUri) { mutableStateOf(artworkUri == null) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AdityaLogo(size = logoSize)

        if (!artworkFailed && artworkUri != null) {
            AsyncImage(
                model = remember(artworkUri, imageSizePx) {
                    ImageRequest.Builder(context)
                        .data(artworkUri)
                        .size(Size(imageSizePx, imageSizePx))
                        .crossfade(false)
                        .build()
                },
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { artworkFailed = false },
                onError = { artworkFailed = true }
            )
        }
    }
}
