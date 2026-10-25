package com.example.damthoai.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(24.dp)
val ButtonShape = RoundedCornerShape(18.dp)
val IconBoxShape = RoundedCornerShape(14.dp)
val InputShape = RoundedCornerShape(18.dp)
val TagShape = RoundedCornerShape(7.dp)
val ChipShape = RoundedCornerShape(50)
val SheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
val DialogShape = RoundedCornerShape(22.dp)
val NavBarShape = RoundedCornerShape(32.dp)
val BackButtonShape = IconBoxShape
val GlassSheetShape = SheetShape
val PillShape = CircleShape

val DamThoaiShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

object Dim {
    val ScreenPadH = 20.dp
    val ScreenPadTop = 16.dp
    val Gap = 12.dp
    val CardPad = 14.dp
    val ButtonMinHeight = 54.dp
    val IconBox = 40.dp
    val MicSmall = 38.dp
    val AvatarList = 38.dp
    val AvatarBig = 72.dp
    val PttDefault = 140.dp
    val PttPocket = 250.dp
    val PttEmergency = 150.dp
    val NavHeight = 70.dp
    val NavSideMargin = 44.dp
    val NavBottom = 16.dp
    val NavOrb = 64.dp
    val NavOrbLift = 26.dp
    val SliderTrack = 6.dp
    val SliderThumb = 16.dp
    val SwitchW = 46.dp
    val SwitchH = 28.dp
    val SwitchThumb = 22.dp
}
