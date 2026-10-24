/**
 * Path: app/src/main/java/com/example/damthoai/ui/components/CommonComponents.kt
 *
 * Khối KDoc tiếng Việt:
 * Các thành phần giao diện (Components) dùng chung chuẩn Design System Đàm Thoại.
 * Bao gồm: GlassCard, PrimaryButton, SecondaryButton, DangerButton,
 * SectionLabel, BackButton, StatusPill.
 */
package com.example.damthoai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.damthoai.R
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.BackButtonShape
import com.example.damthoai.ui.theme.ButtonShape
import com.example.damthoai.ui.theme.CardShape
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DangerBrush
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PillShape
import com.example.damthoai.ui.theme.PrimaryGlow
import com.example.damthoai.ui.theme.PrimaryBrush
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import com.example.damthoai.ui.theme.TagShape
import com.example.damthoai.ui.theme.SwitchOnBrush
import com.example.damthoai.ui.theme.SwitchOff

/**
 * Thẻ kính mờ Glassmorphism (Card)
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = CardShape,
    backgroundColor: Color = GlassBg,
    borderColor: Color = GlassBorder,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

/**
 * Nút nhấn CHÍNH (Primary) - Hình viên thuốc pill gradient xanh dương -> tím
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp
) {
    val gradient = if (enabled) PrimaryBrush else Brush.horizontalGradient(listOf(Color.Gray, Color.DarkGray))

    Box(
        modifier = modifier
            .height(height)
            .shadow(elevation = 10.dp, shape = ButtonShape, spotColor = PrimaryGlow)
            .clip(ButtonShape)
            .background(gradient)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            style = DamType.Button
        )
    }
}

/**
 * Nút nhấn PHỤ (Secondary) - Hình viên thuốc pill nền kính mờ
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(ButtonShape)
            .background(GlassBg)
            .border(1.dp, GlassBorder, ButtonShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            style = DamType.Button
        )
    }
}

/**
 * Nút NGUY HIỂM / KHẨN CẤP (Danger) - Gradient hồng đỏ
 */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(ButtonShape)
            .background(DangerBrush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            style = DamType.Button
        )
    }
}

/**
 * Nhãn VIẾT HOA nhỏ (Section Label)
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = DamType.Label
    )
}

/**
 * Nút Quay lại (Back Button) ô vuông kính mờ bo góc 12dp
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(BackButtonShape)
            .background(GlassBg)
            .border(1.dp, GlassBorder, BackButtonShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_back),
            contentDescription = "Quay lại",
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Trạng thái viên thuốc nhỏ (StatusPill)
 * Ví dụ: "Internet · 42 ms", "ƯU TIÊN", "HẠ ÂM 80%", "CẤP 1"
 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    statusColor: Color = AccentMint,
    backgroundColor: Color = GlassBg
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(backgroundColor)
            .border(1.dp, GlassBorder, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = TextPrimary,
            style = DamType.Small.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
fun TagLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AccentAmber,
    backgroundColor: Color = color.copy(alpha = 0.16f)
) {
    Text(
        text = text,
        modifier = modifier
            .clip(TagShape)
            .background(backgroundColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        color = color,
        style = DamType.Tag
    )
}

@Composable
fun KitSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackBrush = if (checked) {
        SwitchOnBrush
    } else {
        Brush.horizontalGradient(listOf(SwitchOff, SwitchOff))
    }
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(trackBrush)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

fun Modifier.dashedBorder(
    color: Color,
    width: Dp = 1.dp,
    cornerRadius: Dp = 0.dp
): Modifier = drawWithContent {
    drawContent()
    val strokeWidth = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2),
        size = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        )
    )
}

// ================= PREVIEWS =================
@Preview(name = "Common Components Preview", showBackground = true, backgroundColor = 0xFF060A1F)
@Composable
private fun CommonComponentsPreview() {
    DamThoaiTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BackButton(onClick = {})
            SectionLabel(text = "TẦN SỐ ẢO · PIN 6 SỐ")
            StatusPill(text = "Internet · 42 ms", statusColor = AccentMint)
            StatusPill(text = "ƯU TIÊN", statusColor = AccentAmber)
            StatusPill(text = "CẤP 1", statusColor = AccentCyan)

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Thẻ kính mờ GlassCard chuẩn thiết kế Đàm Thoại", color = TextPrimary)
            }

            PrimaryButton(text = "Vào sự kiện ngay", onClick = {}, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = "Quét mã QR", onClick = {}, modifier = Modifier.fillMaxWidth())
            DangerButton(text = "Cuộc gọi khẩn cấp", onClick = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
