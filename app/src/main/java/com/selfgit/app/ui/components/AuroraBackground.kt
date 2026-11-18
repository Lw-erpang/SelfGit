package com.selfgit.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.selfgit.app.ui.theme.LocalPalette

/**
 * 极光背景：基础渐变 + 缓慢漂移的柔光团。
 * 玻璃面板叠在上面，半透明后才能"透"出这些颜色。
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, animated: Boolean = true) {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "aurora")

    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (animated) 21000 else 1, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift"
    )
    val drift2 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (animated) 27000 else 1, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drift2"
    )

    val primaryAlpha = if (palette.dark) 0.50f else 0.42f
    val accentAlpha = if (palette.dark) 0.34f else 0.30f
    val mintAlpha = if (palette.dark) 0.26f else 0.24f

    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                listOf(
                    palette.backgroundTop,
                    palette.backgroundMiddle,
                    palette.backgroundBottom
                )
            )
        )

        val w = size.width
        val h = size.height

        fun glow(center: Offset, radius: Float, color: Color, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        glow(
            center = Offset(w * (0.06f + 0.16f * drift), h * (0.02f + 0.10f * drift2)),
            radius = w * 0.95f,
            color = palette.blobPrimary,
            alpha = primaryAlpha
        )

        glow(
            center = Offset(w * (0.86f - 0.14f * drift2), h * (0.10f + 0.08f * drift)),
            radius = w * 0.62f,
            color = palette.blobAccent,
            alpha = accentAlpha
        )

        glow(
            center = Offset(w * (0.08f + 0.10f * drift2), h * (0.86f - 0.06f * drift)),
            radius = w * 0.75f,
            color = palette.blobMint,
            alpha = mintAlpha
        )
    }
}
