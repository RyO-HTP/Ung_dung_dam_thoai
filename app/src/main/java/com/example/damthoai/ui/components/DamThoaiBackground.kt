/**
 * Path: app/src/main/java/com/example/damthoai/ui/components/DamThoaiBackground.kt
 *
 * Khối KDoc tiếng Việt:
 * Composable nền dùng chung toàn app "Đàm Thoại".
 * Hiển thị gradient dọc xanh navy sâu, vệt sáng màu tím/dương kính mờ,
 * rải sao lung linh nhẹ. Màn "Bỏ túi" (Pocket) sẽ có nền tối sâu hơn.
 */
package com.example.damthoai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.damthoai.ui.theme.BgGradientBottom
import com.example.damthoai.ui.theme.BgGradientTop
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.GlowBlue
import com.example.damthoai.ui.theme.GlowPurple
import com.example.damthoai.ui.theme.PocketBg

/**
 * Nền ứng dụng Đàm Thoại chuẩn Design System
 *
 * @param modifier Modifier tuỳ chỉnh
 * @param isPocketMode true nếu là màn hình Bỏ túi (nền tối đen hơn)
 * @param content Khung chứa giao diện con bên trong
 */
@Composable
fun DamThoaiBackground(
    modifier: Modifier = Modifier,
    isPocketMode: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val backgroundBrush = if (isPocketMode) {
        Brush.verticalGradient(
            colors = listOf(PocketBg, Color(0xFF010208))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(BgGradientTop, BgGradientBottom)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (!isPocketMode) {
            // Vệt sáng hiệu ứng không gian (Glows & Stars)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Vệt sáng tím góc phải trên
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GlowPurple.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(width * 0.85f, height * 0.15f),
                        radius = width * 0.7f
                    ),
                    center = Offset(width * 0.85f, height * 0.15f),
                    radius = width * 0.7f
                )

                // Vệt sáng xanh dương góc trái giữa
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GlowBlue.copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(width * 0.15f, height * 0.5f),
                        radius = width * 0.8f
                    ),
                    center = Offset(width * 0.15f, height * 0.5f),
                    radius = width * 0.8f
                )

                // Rải các chấm sao nhỏ lung linh
                val starPositions = listOf(
                    Offset(width * 0.12f, height * 0.08f),
                    Offset(width * 0.82f, height * 0.22f),
                    Offset(width * 0.35f, height * 0.35f),
                    Offset(width * 0.75f, height * 0.48f),
                    Offset(width * 0.20f, height * 0.65f),
                    Offset(width * 0.88f, height * 0.78f),
                    Offset(width * 0.45f, height * 0.88f)
                )

                starPositions.forEachIndexed { index, pos ->
                    val alpha = if (index % 2 == 0) 0.6f else 0.35f
                    val radius = if (index % 3 == 0) 2.5f else 1.5f
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = radius,
                        center = pos
                    )
                }
            }
        }

        // Đặt nội dung giao diện lên trên nền
        content()
    }
}

@Preview(name = "Normal Background")
@Composable
private fun DamThoaiBackgroundPreview() {
    DamThoaiTheme {
        DamThoaiBackground {}
    }
}

@Preview(name = "Pocket Mode Background")
@Composable
private fun DamThoaiBackgroundPocketPreview() {
    DamThoaiTheme {
        DamThoaiBackground(isPocketMode = true) {}
    }
}
