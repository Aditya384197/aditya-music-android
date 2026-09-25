package com.aditya.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aditya.music.R

/**
 * Single source for the Music app mark.
 *
 * The supplied production logo is used everywhere this component appears so the launcher,
 * artwork fallback, home rows and player surface no longer mix the old vector mark with the
 * new Music identity.
 */
@Composable
fun AdityaLogo(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Image(
        painter = painterResource(R.drawable.music_logo),
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}
