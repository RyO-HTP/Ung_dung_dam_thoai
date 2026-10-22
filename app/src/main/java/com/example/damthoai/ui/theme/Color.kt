/**
 * Path: app/src/main/java/com/example/damthoai/ui/theme/Color.kt
 *
 * Khối KDoc tiếng Việt:
 * Bảng màu Design System cho ứng dụng bộ đàm "Đàm Thoại".
 * Phong cách: Dark mode "bầu trời đêm / không gian", kính mờ (glassmorphism),
 * điểm xuyết gradient xanh-tím, cyan, amber, mint và đỏ khẩn cấp.
 */
package com.example.damthoai.ui.theme

import androidx.compose.ui.graphics.Color

// --- NỀN (BACKGROUND & GLOWS) ---
// Gradient dọc nền chính
val BgGradientTop = Color(0xFF060A1F)      // Xanh navy rất đậm phía trên
val BgGradientBottom = Color(0xFF0B1240)   // Xanh navy sâu phía dưới
val GlowPurple = Color(0xFF3B2A8C)         // Vệt sáng tím góc phải trên
val GlowBlue = Color(0xFF14308A)           // Vệt xanh dương góc trái giữa
val PocketBg = Color(0xFF030511)           // Nền màn hình Bỏ túi (Pocket) tối gần đen

// --- NÚT CHÍNH & GRADIENT (PRIMARY BUTTONS) ---
val PrimaryStart = Color(0xFF3366FF)       // Xanh dương bứt phá
val PrimaryEnd = Color(0xFF6B3BFF)         // Tím hiện đại
val PrimaryGlow = Color(0x663366FF)        // Đổ bóng hào quang nút chính

// --- NÚT KHẨN CẤP / NGUY HIỂM (DANGER BUTTONS) ---
val DangerStart = Color(0xFFFF3D6E)        // Hồng đỏ khẩn cấp
val DangerEnd = Color(0xFFB3153C)          // Đỏ sẫm
val DangerText = Color(0xFFFF9AAE)         // Chữ hồng nhạt cho link nguy hiểm (Rời sự kiện, Xóa...)

// --- THẺ KÍNH MỜ (GLASSMORPHISM) ---
val GlassBg = Color(0x14FFFFFF)            // Trắng alpha 8% cho nền thẻ
val GlassBorder = Color(0x1FFFFFFF)        // Trắng alpha 12% cho viền thẻ
val GlassBgHover = Color(0x24FFFFFF)       // Trắng alpha 14% khi nhấn

// --- MÀU NHẤN & TRẠNG THÁI (ACCENT COLORS) ---
val AccentCyan = Color(0xFF4DDCFF)         // Cyan: tab active, icon kênh, sóng âm đang nói, mã PIN
val AccentAmber = Color(0xFFFFC857)        // Vàng amber: nhãn ƯU TIÊN, LIVE CUE, HẠ ÂM 80%, CẤP 1, cảnh báo
val AccentMint = Color(0xFF6EE7B7)         // Xanh mint: trạng thái mạng tốt (Internet · 42 ms)
val AccentRed = Color(0xFFFF4D6D)          // Đỏ: offline, khẩn cấp, bị ngắt mic

// --- MÀU CHỮ (TYPOGRAPHY COLORS) ---
val TextPrimary = Color(0xFFF4F6FF)        // Trắng ngà cho tiêu đề và chữ chính
val TextSecondary = Color(0xFFA9B4E8)      // Tím xanh nhạt cho thông tin phụ/mô tả
val TextMuted = Color(0xFF6A75A0)          // Tím xám mờ cho thông tin bổ sung
