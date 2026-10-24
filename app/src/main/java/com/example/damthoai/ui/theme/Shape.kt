package com.example.damthoai.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape.kt (BẢN CHUẨN) - bo góc đúng CSS. LƯU Ý: nút chính KHÔNG phải pill, chỉ bo 18dp.
 */
val CardShape = RoundedCornerShape(24.dp)        // thẻ kính
val ButtonShape = RoundedCornerShape(18.dp)      // PrimaryButton / SecondaryButton / DangerButton
val IconBoxShape = RoundedCornerShape(14.dp)     // ô icon vuông 40dp, nút quay lại
val InputShape = RoundedCornerShape(18.dp)       // ô nhập chữ
val TagShape = RoundedCornerShape(7.dp)          // ƯU TIÊN, CẤP 1...
val ChipShape = RoundedCornerShape(50)           // chip lọc, viên thuốc trạng thái mạng
val SheetShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
val DialogShape = RoundedCornerShape(22.dp)
val NavBarShape = RoundedCornerShape(32.dp)
val BackButtonShape = IconBoxShape
val GlassSheetShape = SheetShape
val DamThoaiShapes = Shapes(extraSmall = RoundedCornerShape(7.dp), small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(30.dp))
val PillShape = CircleShape                      // chỉ dùng cho chip tròn / avatar / nút PTT

// ---- Kích thước chuẩn (dp) ----
object Dim {
    val ScreenPadH = 20.dp; val ScreenPadTop = 16.dp; val Gap = 12.dp
    val CardPad = 14.dp; val ButtonMinHeight = 54.dp; val IconBox = 40.dp; val MicSmall = 38.dp
    val AvatarList = 38.dp; val AvatarBig = 72.dp
    val PttDefault = 140.dp; val PttPocket = 250.dp; val PttEmergency = 150.dp
    val NavHeight = 70.dp; val NavSideMargin = 44.dp; val NavBottom = 16.dp; val NavOrb = 64.dp; val NavOrbLift = 26.dp
    val SliderTrack = 6.dp; val SliderThumb = 16.dp; val SwitchW = 46.dp; val SwitchH = 28.dp; val SwitchThumb = 22.dp
}
