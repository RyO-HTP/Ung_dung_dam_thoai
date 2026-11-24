package com.example.damthoai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.damthoai.R

val BeVietnamPro = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
    Font(R.font.be_vietnam_pro_extrabold, FontWeight.ExtraBold),
)

object DamType {
    val Title = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.02).em)
    val TitleIntro = Title.copy(fontSize = 30.sp, lineHeight = 34.5.sp)
    val Title20 = Title.copy(fontSize = 20.sp)
    val Title21 = Title.copy(fontSize = 21.sp, lineHeight = 26.sp)
    val Title22 = Title.copy(fontSize = 22.sp)
    val Title24 = Title.copy(fontSize = 24.sp, lineHeight = 29.sp)
    val Title26 = Title.copy(fontSize = 26.sp, lineHeight = 32.sp)
    val Title28Tall = Title.copy(lineHeight = 34.sp)
    val Heading = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    val Heading13_5 = Heading.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    val Heading14 = Heading.copy(fontSize = 14.sp)
    val Heading16 = Heading.copy(fontSize = 16.sp)
    val Heading17 = Heading.copy(fontSize = 17.sp)
    val Heading18 = Heading.copy(fontSize = 18.sp)
    val Heading20 = Heading.copy(fontSize = 20.sp)
    val Body = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Normal, fontSize = 14.sp)
    val Body10Bold = Body.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val Body13 = Body.copy(fontSize = 13.sp)
    val Body13Line19 = Body.copy(fontSize = 13.sp, lineHeight = 19.sp)
    val Body13Line19_5 = Body.copy(fontSize = 13.sp, lineHeight = 19.5.sp)
    val Body13_5Line20 = Body.copy(fontSize = 13.5.sp, lineHeight = 20.sp)
    val Body14Line21 = Body.copy(lineHeight = 21.sp)
    val Body14SemiBold = Body.copy(fontWeight = FontWeight.SemiBold)
    val Body14Bold = Body.copy(fontWeight = FontWeight.Bold)
    val Body15 = Body.copy(fontSize = 15.sp)
    val Body15Line22 = Body15.copy(lineHeight = 22.sp)
    val Body16SemiBold = Body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val Body13Bold = Body13.copy(fontWeight = FontWeight.Bold)
    val BodyLine21 = Body.copy(lineHeight = 21.sp)
    val Muted = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Normal, fontSize = 12.5.sp)
    val Small = Muted.copy(fontSize = 11.5.sp)
    val Small12 = Small.copy(fontSize = 12.sp)
    val Small11Bold = Small.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val Small13Bold = Small.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val SmallLine16 = Small.copy(lineHeight = 16.sp)
    val SmallLine17 = Small.copy(lineHeight = 17.sp)
    val Label = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.09.em)
    val Tag = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.04.em)
    val Tag11 = Tag.copy(fontSize = 11.sp)
    val Tag12 = Tag.copy(fontSize = 12.sp)
    val Chip = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val Button = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val Input = Body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val InputHint = Input
    val DialogButton = Small.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val NavLabel = TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    val Mono16Tight = Mono.copy(fontSize = 16.sp, letterSpacing = 0.sp)
    val MonoInput = Mono.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val PinBig = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 53.sp, letterSpacing = 0.14.em)
    val PinKey = PinBig.copy(fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = 0.sp)

    fun avatar(size: Dp) = Heading.copy(
        fontSize = (size.value * 0.375f).sp,
        fontWeight = FontWeight.ExtraBold
    )
}

val DamThoaiTypography = Typography(
    headlineLarge = DamType.Title,
    titleMedium = DamType.Heading,
    bodyLarge = DamType.Body,
    bodyMedium = DamType.Muted,
    labelSmall = DamType.Label,
    labelLarge = DamType.Button,
)

val MonospacePinStyle = DamType.PinBig
