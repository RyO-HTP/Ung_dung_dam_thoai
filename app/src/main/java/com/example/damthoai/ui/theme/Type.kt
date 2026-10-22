/**
 * Path: app/src/main/java/com/example/damthoai/ui/theme/Type.kt
 *
 * Khối KDoc tiếng Việt:
 * Cấu hình Kiểu chữ (Typography) cho ứng dụng Đàm Thoại.
 * Định nghĩa font chữ sans-serif hiện đại hỗ trợ đầy đủ tiếng Việt,
 * bao gồm kiểu chữ hoa nhãn (Section Label) và monospace cho mã PIN/mã quản trị.
 */
package com.example.damthoai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DamThoaiTypography = Typography(
    // Tiêu đề cực lớn (ĐÀM THOẠI, Tên sự kiện chính)
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = TextPrimary
    ),
    // Tiêu đề màn hình
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = TextPrimary
    ),
    // Tiêu đề mục / Thẻ
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    // Nội dung chính
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = TextPrimary
    ),
    // Nội dung phụ
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextSecondary
    ),
    // Nhãn nút nhấn
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    // Nhãn VIẾT HOA nhỏ (letter-spacing) cho các Section
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.25.sp,
        color = TextSecondary
    )
)

// Monospace style chuyên dùng cho mã PIN 6 số và Mã Quản Trị
val MonospacePinStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    letterSpacing = 4.sp,
    color = AccentCyan
)
