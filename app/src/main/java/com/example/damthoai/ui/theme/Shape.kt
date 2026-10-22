/**
 * Path: app/src/main/java/com/example/damthoai/ui/theme/Shape.kt
 *
 * Khối KDoc tiếng Việt:
 * Định nghĩa Bo góc (Shapes) theo Design System Đàm Thoại.
 * Card bo góc lớn 24dp, nút dạng pill (CircleShape / RoundedCornerShape(50)),
 * nút quay lại bo góc 12dp.
 */
package com.example.damthoai.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(24.dp)
val PillShape = CircleShape
val BackButtonShape = RoundedCornerShape(12.dp)
val GlassSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

val DamThoaiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = CardShape,
    extraLarge = GlassSheetShape
)
