package com.aditya.music.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom Signature Aditya Music Logo.
 * Stylized letter 'A' converging with musical pulse waves and play indicator.
 *
 * Performance note: this used to be a `Canvas` that allocated a new `Path` and a new
 * gradient `Brush` on EVERY draw call. In the song list (one logo per row) fast scrolling
 * allocated hundreds of objects per second and caused GC churn / jank on budget devices.
 * The exact same artwork is now built ONCE as a static ImageVector and rendered through
 * the regular vector pipeline, which is cached and GPU-friendly.
 */
private val AdityaLogoVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "AdityaLogo",
        defaultWidth = 48.dp,
        defaultHeight = 48.dp,
        viewportWidth = 100f,
        viewportHeight = 100f
    ).apply {
        // Stylized outer 'A' with the inner counter-triangle (same shape as before,
        // scaled from the original relative coordinates to a 100x100 viewport).
        addPath(
            pathData = listOf(
                PathNode.MoveTo(50f, 12f),
                PathNode.LineTo(18f, 88f),
                PathNode.LineTo(32f, 88f),
                PathNode.LineTo(42f, 62f),
                PathNode.LineTo(58f, 62f),
                PathNode.LineTo(68f, 88f),
                PathNode.LineTo(82f, 88f),
                PathNode.Close,
                PathNode.MoveTo(50f, 32f),
                PathNode.LineTo(55f, 52f),
                PathNode.LineTo(45f, 52f),
                PathNode.Close
            ),
            fill = Brush.linearGradient(
                colors = listOf(Color(0xFF6366F1), Color(0xFFEC4899)),
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(100f, 100f)
            )
        )
        // Dynamic soundwave dot (circle approximated with cubic curves).
        addPath(
            pathData = listOf(
                PathNode.MoveTo(64f, 45f),
                PathNode.CurveTo(64f, 40.6f, 67.6f, 37f, 72f, 37f),
                PathNode.CurveTo(76.4f, 37f, 80f, 40.6f, 80f, 45f),
                PathNode.CurveTo(80f, 49.4f, 76.4f, 53f, 72f, 53f),
                PathNode.CurveTo(67.6f, 53f, 64f, 49.4f, 64f, 45f),
                PathNode.Close
            ),
            fill = SolidColor(Color(0xFFF59E0B))
        )
    }.build()
}

@Composable
fun AdityaLogo(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Image(
        imageVector = AdityaLogoVector,
        contentDescription = null,
        modifier = modifier.size(size)
    )
}
