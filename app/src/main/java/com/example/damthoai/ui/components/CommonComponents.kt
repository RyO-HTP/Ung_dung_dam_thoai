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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.BackButtonShape
import com.example.damthoai.ui.theme.CardShape
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DangerEnd
import com.example.damthoai.ui.theme.DangerStart
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PillShape
import com.example.damthoai.ui.theme.PrimaryEnd
import com.example.damthoai.ui.theme.PrimaryGlow
import com.example.damthoai.ui.theme.PrimaryStart
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

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
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, shape)
            .padding(16.dp)
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
    height: Dp = 56.dp
) {
    val gradient = Brush.horizontalGradient(
        colors = if (enabled) listOf(PrimaryStart, PrimaryEnd) else listOf(Color.Gray, Color.DarkGray)
    )

    Box(
        modifier = modifier
            .height(height)
            .shadow(elevation = 8.dp, shape = PillShape, spotColor = PrimaryGlow)
            .clip(PillShape)
            .background(gradient)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
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
    height: Dp = 56.dp
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(PillShape)
            .background(GlassBg)
            .border(1.dp, GlassBorder, PillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
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
    height: Dp = 56.dp
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(DangerStart, DangerEnd)
    )

    Box(
        modifier = modifier
            .height(height)
            .clip(PillShape)
            .background(gradient)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
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
        fontWeight = FontWeight.ExtraBold,
        fontSize = 11.sp,
        letterSpacing = 1.25.sp
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
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            .clip(CircleShape)
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
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
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
