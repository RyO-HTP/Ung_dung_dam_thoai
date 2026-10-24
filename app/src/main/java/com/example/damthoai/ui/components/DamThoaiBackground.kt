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
import com.example.damthoai.ui.theme.*
import com.example.damthoai.ui.theme.StarBlue

/** Ba kiểu nền trong thiết kế: mặc định, khẩn cấp (đỏ), bỏ túi (gần đen). */
enum class BgVariant { Default, Emergency, Pocket }

/** Vẽ một vệt sáng hình elip, tương đương CSS `radial-gradient(rxW% ryH% at cx% cy%, màu, transparent)`. */
private fun DrawScope.glow(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) {
    withTransform({ translate(cx, cy); scale(1f, ry / rx, pivot = Offset.Zero) }) {
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset.Zero, radius = rx), radius = rx, center = Offset.Zero)
    }
}

/** Ngôi sao nhỏ: chấm trắng mờ dần ra rìa, vị trí tính theo % màn hình (đúng CSS). */
private fun DrawScope.star(px: Float, py: Float, r: Float, color: Color = Color.White) {
    val c = Offset(size.width * px, size.height * py)
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = r), radius = r, center = c)
}

/**
 * DamThoaiBackground (BẢN CHUẨN): đúng CSS nền của thiết kế.
 *  - gradient dọc BgTop -> BgMid (70%) -> BgBottom
 *  - vệt tím elip 70% x 38% ở góc phải trên, vệt xanh 60% x 34% ở mép trái 38% chiều cao
 *  - 5 ngôi sao rải theo % cố định
 */
@Composable
fun DamThoaiBackground(modifier: Modifier = Modifier, variant: BgVariant = BgVariant.Default, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            when (variant) {
                BgVariant.Default -> {
                    drawRect(Brush.verticalGradient(0f to BgTop, 0.7f to BgMid, 1f to BgBottom))
                    glow(w, 0f, w * 0.70f, h * 0.38f, GlowPurple)
                    glow(0f, h * 0.38f, w * 0.60f, h * 0.34f, GlowBlue)
                }
                BgVariant.Emergency -> {
                    drawRect(Brush.verticalGradient(listOf(EmergTop, BgMid)))
                    glow(w * 0.5f, 0f, w * 0.75f, h * 0.38f, EmergGlow)
                }
                BgVariant.Pocket -> {
                    drawRect(PocketBase)
                    glow(w * 0.5f, h * 0.58f, w * 0.60f, h * 0.40f, PocketGlow)
                }
            }
            val d = density
            star(0.18f, 0.12f, 1.5f * d); star(0.76f, 0.20f, 1f * d); star(0.44f, 0.60f, 1f * d, StarBlue)
            star(0.88f, 0.72f, 1.5f * d); star(0.08f, 0.84f, 1f * d)
        }
        content()
    }
}
