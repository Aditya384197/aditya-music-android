package com.aditya.music.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
 * Artwork renderer with a reliable Aditya logo fallback.
 *
 * The fallback logo fills this view exactly the way real album art would (matchParentSize +
 * ContentScale.Crop) - it used to sit as a small, fixed-size image centered inside a bigger
 * neutral-coloured box, which showed up as an unwanted "square inside a square": a visible outer
 * frame around a smaller logo. There's only ever one square now, same as when real artwork loads.
 */
@Composable
fun ArtworkView(
    artworkUri: Uri?,
    modifier: Modifier = Modifier,
    logoSize: Dp = Dp.Unspecified,
    imageSizePx: Int,
    contentDescription: String? = null,
    cropScale: Float = 1f,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp)
) {
    val context = LocalContext.current
    var artworkFailed by remember(artworkUri) { mutableStateOf(artworkUri == null) }

    Box(modifier = modifier.clip(shape)) {
        Image(
            painter = painterResource(id = R.drawable.aditya_logo),
            contentDescription = contentDescription,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    // The supplied album/logo artwork contains a small outer halo/padding.
                    // Scale only the artwork inside the already-clipped square so that the
                    // unwanted outer square is no longer visible.
                    scaleX = cropScale
                    scaleY = cropScale
                },
            contentScale = ContentScale.Crop
        )

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
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        scaleX = cropScale
                        scaleY = cropScale
                    },
                contentScale = ContentScale.Crop,
                onSuccess = { artworkFailed = false },
                onError = { artworkFailed = true }
            )
        }
    }
}
