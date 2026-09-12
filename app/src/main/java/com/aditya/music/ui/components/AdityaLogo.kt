package com.aditya.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom Signature Aditya Music Logo.
 * Stylized letter 'A' converging with musical pulse waves and play indicator.
 */
@Composable
fun AdityaLogo(modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val primaryGradient = Brush.linearGradient(
            colors = listOf(Color(0xFF6366F1), Color(0xFFEC4899)),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        // Draw stylized outer 'A'
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            lineTo(w * 0.18f, h * 0.88f)
            lineTo(w * 0.32f, h * 0.88f)
            lineTo(w * 0.42f, h * 0.62f)
            lineTo(w * 0.58f, h * 0.62f)
            lineTo(w * 0.68f, h * 0.88f)
            lineTo(w * 0.82f, h * 0.88f)
            close()

            moveTo(w * 0.5f, h * 0.32f)
            lineTo(w * 0.55f, h * 0.52f)
            lineTo(w * 0.45f, h * 0.52f)
            close()
        }
        drawPath(path, primaryGradient, style = Fill)

        // Dynamic soundwave dot
        drawCircle(
            color = Color(0xFFF59E0B),
            radius = w * 0.08f,
            center = Offset(w * 0.72f, h * 0.45f)
        )
    }
}
