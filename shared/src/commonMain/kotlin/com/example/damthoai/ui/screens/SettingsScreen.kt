package com.example.damthoai.ui.screens

import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.damthoai.resources.*
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.BgVariant
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.KitDialog
import com.example.damthoai.ui.components.TagLabel
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DangerBrush
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    variant: BgVariant = BgVariant.Default,
    bottomPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    DamThoaiBackground(modifier = modifier, variant = variant) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = bottomPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    BackButton(onClick = onBack)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = TextPrimary,
                    style = DamType.Title21
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
fun ScreenIcon(
    iconRes: DrawableResource,
    tint: androidx.compose.ui.graphics.Color = AccentCyan,
    modifier: Modifier = Modifier,
    boxSize: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    Box(
        modifier = modifier
            .size(boxSize)
            .clip(shape)
            .background(GlassBg)
            .border(1.dp, GlassBorder, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun ActionRow(
    iconRes: DrawableResource,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScreenIcon(iconRes)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = DamType.Heading)
            Text(subtitle, color = TextSecondary, style = DamType.Small)
        }
        if (tag != null) {
            TagLabel(text = tag, color = AccentAmber)
        } else {
            Icon(
                painter = painterResource(Res.drawable.ic_chev),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        style = DamType.Heading,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

/**
 * Màn hình Cài đặt chính của ứng dụng (SettingsScreen)
 */
@Composable
fun SettingsScreen(
    onProfile: () -> Unit,
    onManageEvent: () -> Unit,
    onLeaveEvent: () -> Unit,
    onMultiLevel: () -> Unit,
    onHandsFree: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLeaveDialog by rememberSaveable { mutableStateOf(false) }

    ScreenFrame(title = "Cài đặt", onBack = null, modifier = modifier) {
        SectionLabel("TÀI KHOẢN & SỰ KIỆN")
        ActionRow(
            iconRes = Res.drawable.ic_user,
            title = "Hồ sơ cá nhân",
            subtitle = "Tên hiển thị, cấp bậc và mã định danh",
            onClick = onProfile
        )
        ActionRow(
            iconRes = Res.drawable.ic_crown,
            title = "Quản lý sự kiện",
            subtitle = "Cấu hình kênh, mã pin và thành viên",
            onClick = onManageEvent
        )

        Spacer(Modifier.height(4.dp))
        SectionLabel("KẾT NỐI & ÂM THANH")
        ActionRow(
            iconRes = Res.drawable.ic_wifi,
            title = "Kết nối đa tầng",
            subtitle = "Internet, Mạng nội bộ & Bluetooth LE",
            onClick = onMultiLevel
        )
        ActionRow(
            iconRes = Res.drawable.ic_mic,
            title = "Âm thanh rảnh tay & Khử ồn",
            subtitle = "Tối ưu hóa PTT và truyền giọng nói",
            onClick = onHandsFree
        )

        Spacer(Modifier.height(4.dp))
        SectionLabel("HỆ THỐNG")
        ActionRow(
            iconRes = Res.drawable.ic_chev,
            title = "Rời khỏi sự kiện",
            subtitle = "Thoát khỏi sự kiện hiện tại và chọn sự kiện mới",
            onClick = { showLeaveDialog = true }
        )
    }

    if (showLeaveDialog) {
        KitDialog(
            title = "Rời khỏi sự kiện?",
            description = "Bạn sẽ cần quét mã QR hoặc nhập mã PIN để tham gia lại sự kiện này.",
            primaryButtonText = "Rời sự kiện",
            secondaryButtonText = "Ở lại",
            onPrimaryClick = {
                showLeaveDialog = false
                onLeaveEvent()
            },
            onDismissRequest = { showLeaveDialog = false },
            primaryBrush = DangerBrush
        )
    }
}