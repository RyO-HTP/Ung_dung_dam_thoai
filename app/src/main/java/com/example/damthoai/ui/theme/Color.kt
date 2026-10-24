package com.example.damthoai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color.kt (BẢN CHUẨN) - màu lấy TRỰC TIẾP từ CSS trong file HTML thiết kế, KHÔNG phải ước lượng từ ảnh PDF.
 * Thay toàn bộ file Color.kt cũ bằng file này. Giữ tên cũ ở cuối file để code cũ không bị lỗi.
 */
// ---- NỀN ----
val BgTop = Color(0xFF070B24)  // gradient dọc: 0%
val BgMid = Color(0xFF0B1245)  // gradient dọc: 70%
val BgBottom = Color(0xFF120C45)  // gradient dọc: 100%
val GlowPurple = Color(0x8C6C3BFF)  // vệt tím góc phải trên
val GlowBlue = Color(0x592F6BFF)  // vệt xanh trái giữa
val PocketBase = Color(0xFF040618)
val PocketGlow = Color(0x612F6BFF)
val EmergGlow = Color(0x80FF3D6B)
val EmergTop = Color(0xFF1A0820)
// ---- NÚT CHÍNH ----
val PrimaryStart = Color(0xFF2F6BFF)
val PrimaryEnd = Color(0xFF6C3BFF)
val PrimaryShadow = Color(0x664F64FF)
val DangerStart = Color(0xFFFF3D6B)
val DangerEnd = Color(0xFFB0123A)
val DangerShadow = Color(0x73FF3D6B)
val DangerText = Color(0xFFFF9DB0)  // chữ link nguy hiểm
// ---- KÍNH MỜ ----
val GlassBg = Color(0x12FFFFFF)
val GlassBorder = Color(0x21FFFFFF)
val IconBoxBg = Color(0x17FFFFFF)
val IconBoxBorder = Color(0x1FFFFFFF)
val GhostBtnBg = Color(0x17FFFFFF)
val GhostBtnBorder = Color(0x24FFFFFF)
val TrackBg = Color(0x26FFFFFF)  // rãnh slider
val SwitchOff = Color(0x2EFFFFFF)
val NavBg = Color(0xD90E1446)
val NavBorder = Color(0x24FFFFFF)
val NavGlow = Color(0x2E4FA3FF)
val NavShadow = Color(0x73000000)
val StarBlue = Color(0xFFCFE0FF)
val MicDeniedBg = Color(0x24FF5C7A)
val MicDeniedBorder = Color(0x80FF5C7A)
val SheetBg = Color(0xFA121A56)
val Scrim = Color(0xA6040618)
val LcdBg = Color(0xFF050A24)
val LcdBorder = Color(0x595EE1FF)
// ---- NHẤN ----
val AccentCyan = Color(0xFF5EE1FF)
val AccentAmber = Color(0xFFFFC857)
val AccentMint = Color(0xFF7CF0C5)
val AccentRed = Color(0xFFFF5C7A)
val TagCyanBg = Color(0x265EE1FF)
val TagAmberBg = Color(0x2EFFC857)
val TagRedBg = Color(0x33FF5C7A)
val TagRedText = Color(0xFFFF9DB0)
val CueBorder = Color(0x66FFC857)
// ---- CHỮ ----
val TextPrimary = Color(0xFFF4F7FF)
val TextSecondary = Color(0xFF9FB0E6)  // chữ phụ, nhãn
val TextPlaceholder = Color(0xFF8F9FD6)
// ---- AVATAR (gradient chéo 135 độ) ----
val AvatarGradients = listOf(
    Color(0xFF4FA3FF) to Color(0xFF6C5CFF),
    Color(0xFFFF8FB1) to Color(0xFFFF5C7A),
    Color(0xFF5EE1FF) to Color(0xFF2F6BFF),
    Color(0xFFFFC857) to Color(0xFFFF8A3D),
    Color(0xFF7CF0C5) to Color(0xFF2BA3A3),
    Color(0xFFC9A8FF) to Color(0xFF7A4DFF),
)

// ---- TÊN CŨ (alias) để code tuần 1-2 vẫn build ----
val PrimaryGlow = PrimaryShadow
val GlassBgHover = Color(0x24FFFFFF)
val BgGradientTop = BgTop
val BgGradientBottom = BgBottom
val PocketBg = PocketBase
val TextMuted = TextSecondary
