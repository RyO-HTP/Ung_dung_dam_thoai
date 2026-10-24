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

fun cssGradient(angleDeg: Float, startColor: Color, endColor: Color): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val angle = Math.toRadians(angleDeg.toDouble())
        val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
        val halfLength = (abs(size.width * direction.x) + abs(size.height * direction.y)) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        return LinearGradientShader(
            center - direction * halfLength,
            center + direction * halfLength,
            listOf(startColor, endColor)
        )
    }
}

val PrimaryBrush: Brush get() = cssGradient(135f, PrimaryStart, PrimaryEnd)
val DangerBrush: Brush get() = cssGradient(135f, DangerStart, DangerEnd)
val SwitchOnBrush: Brush get() = cssGradient(135f, PrimaryStart, AccentCyan)
