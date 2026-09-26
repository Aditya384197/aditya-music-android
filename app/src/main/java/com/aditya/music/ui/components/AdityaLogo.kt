package com.aditya.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aditya.music.R

/**
 * Single source of truth for the app's main music mark.
 * The artwork is the cleaned square logo supplied for the app, with the stray
 * outer sparkle removed and a restrained blue/violet/amber color treatment.
 */
@Composable
fun AdityaLogo(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Image(
        painter = painterResource(R.drawable.music_logo),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}
