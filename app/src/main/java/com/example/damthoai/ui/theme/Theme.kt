/**
 * Path: app/src/main/java/com/example/damthoai/ui/theme/Theme.kt
 *
 * Khối KDoc tiếng Việt:
 * Master Theme composable cho toàn bộ ứng dụng Đàm Thoại.
 * Luôn áp dụng Dark Theme không gian đêm, kết hợp màu sắc, font chữ và hình dạng đồng nhất.
 */
package com.example.damthoai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryStart,
    onPrimary = TextPrimary,
    primaryContainer = PrimaryGlow,
    secondary = AccentCyan,
    onSecondary = BgGradientTop,
    background = BgGradientTop,
    onBackground = TextPrimary,
    surface = GlassBg,
    onSurface = TextPrimary,
    error = AccentRed,
    onError = TextPrimary
)

@Composable
fun DamThoaiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = DamThoaiTypography,
        shapes = DamThoaiShapes,
        content = content
    )
}
