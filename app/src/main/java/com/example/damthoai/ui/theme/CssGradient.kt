package com.example.damthoai.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gradient tuyến tính ĐÚNG KIỂU CSS `linear-gradient(<góc>deg, ...)`.
 * Brush.linearGradient mặc định của Compose đi góc-đến-góc nên khác CSS với nút hình chữ nhật dài.
 * Góc CSS: 0 = lên trên, 90 = sang phải, 135 = chéo xuống phải (dùng cho nút chính, avatar, công tắc).
 */
fun cssGradient(angleDeg: Float, colors: List<Color>): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val a = Math.toRadians(angleDeg.toDouble())
        val dx = sin(a).toFloat()
        val dy = (-cos(a)).toFloat()
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        return LinearGradientShader(c - Offset(dx * half, dy * half), c + Offset(dx * half, dy * half), colors)
    }
}

val PrimaryBrush: Brush get() = cssGradient(135f, listOf(PrimaryStart, PrimaryEnd))
val DangerBrush: Brush get() = cssGradient(135f, listOf(DangerStart, DangerEnd))
val SwitchOnBrush: Brush get() = cssGradient(135f, listOf(PrimaryStart, AccentCyan))
