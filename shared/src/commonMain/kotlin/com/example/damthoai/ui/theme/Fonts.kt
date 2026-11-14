package com.example.damthoai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.damthoai.resources.*
import org.jetbrains.compose.resources.Font

/**
 * Font Be Vietnam Pro. Với Compose Multiplatform, Font(Res.font.x) là hàm @Composable nên không thể
 * tạo FontFamily ở cấp file như trên Android. [DamThoaiTheme] gọi [rememberBeVietnamPro] và gán vào
 * đây TRƯỚC khi bất kỳ kiểu chữ nào trong [DamType] / [DamThoaiTypography] được đọc lần đầu.
 */
internal object DamFonts {
    var family: FontFamily = FontFamily.Default
}

val BeVietnamPro: FontFamily get() = DamFonts.family

@Composable
internal fun rememberBeVietnamPro(): FontFamily {
    val regular = Font(Res.font.be_vietnam_pro_regular, FontWeight.Normal)
    val medium = Font(Res.font.be_vietnam_pro_medium, FontWeight.Medium)
    val bold = Font(Res.font.be_vietnam_pro_bold, FontWeight.Bold)
    val extraBold = Font(Res.font.be_vietnam_pro_extrabold, FontWeight.ExtraBold)
    return remember(regular, medium, bold, extraBold) { FontFamily(regular, medium, bold, extraBold) }
}

