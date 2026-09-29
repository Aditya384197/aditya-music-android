package com.aditya.music.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.aditya.music.R

/**
 * Single-layer artwork renderer.
 *
 * The real Aditya logo is used directly as the fallback image. No artificial crop, zoom, glow,
 * shadow or second square is added around the artwork.
 */
@Composable
fun ArtworkView(
    artworkUri: Uri?,
    modifier: Modifier = Modifier,
    logoSize: Dp = Dp.Unspecified,
    imageSizePx: Int,
    contentDescription: String? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp)
) {
    val context = LocalContext.current
    var artworkFailed by remember(artworkUri) { mutableStateOf(artworkUri == null) }

    Box(modifier = modifier.clip(shape)) {
        if (artworkFailed || artworkUri == null) {
            Image(
                painter = painterResource(id = R.drawable.aditya_logo),
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit
            )
        } else {
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
                contentScale = ContentScale.Fit,
                onSuccess = { artworkFailed = false },
                onError = { artworkFailed = true }
            )
        }
    }
}
