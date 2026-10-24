package com.example.damthoai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.damthoai.R

/**
 * Type.kt (BẢN CHUẨN) - font Be Vietnam Pro đúng như HTML. CẦN tải font về trước (xem README_KIT.md, mục 2).
 * Quy đổi: 1px trong HTML = 1sp ở đây.
 */
val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
    Font(R.font.be_vietnam_pro_extrabold, FontWeight.ExtraBold),
)

object DamType {
    val Title = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.02).em)   // h1
    val TitleIntro = Title.copy(fontSize = 30.sp, lineHeight = 34.5.sp)                                                                             // tiêu đề slide giới thiệu
    val Heading = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 15.sp)                                              // h2: tên kênh, tiêu đề thẻ
    val Body = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Normal, fontSize = 14.sp)
    val Muted = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Normal, fontSize = 12.5.sp)                                            // chữ phụ
    val Small = Muted.copy(fontSize = 11.5.sp)
    val Label = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.09.em)                      // nhãn HOA
    val Tag = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.04.em)                   // ƯU TIÊN, HẠ ÂM 80%
    val Chip = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val Button = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    val PinBig = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 53.sp, letterSpacing = 0.14.em)
}

val DamThoaiTypography = Typography(
    headlineLarge = DamType.Title, titleMedium = DamType.Heading, bodyLarge = DamType.Body,
    bodyMedium = DamType.Muted, labelSmall = DamType.Label, labelLarge = DamType.Button,
)

val MonospacePinStyle = DamType.PinBig   // tên cũ, giữ để code cũ build được
