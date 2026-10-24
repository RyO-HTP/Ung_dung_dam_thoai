package com.example.damthoai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.example.damthoai.ui.theme.BgBottom
import com.example.damthoai.ui.theme.BgMid
import com.example.damthoai.ui.theme.BgTop
import com.example.damthoai.ui.theme.EmergGlow
import com.example.damthoai.ui.theme.EmergTop
import com.example.damthoai.ui.theme.GlowBlue
import com.example.damthoai.ui.theme.GlowPurple
import com.example.damthoai.ui.theme.PocketBase
import com.example.damthoai.ui.theme.PocketGlow
import com.example.damthoai.ui.theme.StarTint

enum class BgVariant { Default, Emergency, Pocket }

private fun DrawScope.glow(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) {
    withTransform({ translate(cx, cy); scale(1f, ry / rx, pivot = Offset.Zero) }) {
        drawCircle(
            Brush.radialGradient(listOf(color, Color.Transparent), center = Offset.Zero, radius = rx),
            radius = rx,
            center = Offset.Zero
        )
    }
}

private fun DrawScope.star(px: Float, py: Float, radius: Float, color: Color = Color.White) {
    val center = Offset(size.width * px, size.height * py)
    drawCircle(
        Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center
    )
}

@Composable
fun DamThoaiBackground(
    modifier: Modifier = Modifier,
    variant: BgVariant = BgVariant.Default,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            when (variant) {
                BgVariant.Default -> {
                    drawRect(Brush.verticalGradient(0f to BgTop, 0.7f to BgMid, 1f to BgBottom))
                    glow(width, 0f, width * 0.70f, height * 0.38f, GlowPurple)
                    glow(0f, height * 0.38f, width * 0.60f, height * 0.34f, GlowBlue)
                }
                BgVariant.Emergency -> {
                    drawRect(Brush.verticalGradient(listOf(EmergTop, BgMid)))
                    glow(width * 0.5f, 0f, width * 0.75f, height * 0.38f, EmergGlow)
                }
                BgVariant.Pocket -> {
                    drawRect(PocketBase)
                    glow(width * 0.5f, height * 0.58f, width * 0.60f, height * 0.40f, PocketGlow)
                }
            }
            val density = density
            star(0.18f, 0.12f, 1.5f * density)
            star(0.76f, 0.20f, 1f * density)
            star(0.44f, 0.60f, 1f * density, StarTint)
            star(0.88f, 0.72f, 1.5f * density)
            star(0.08f, 0.84f, 1f * density)
        }
        content()
    }
}
