package com.example.damthoai.ui.screens

import android.app.Activity
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.R
import com.example.damthoai.data.fake.FakeRemainingUiData
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.ui.components.AvatarCircle
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.DangerText
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onManageEvent: () -> Unit,
    onLeaveEvent: () -> Unit,
    onMultiLevel: () -> Unit,
    onHandsFree: () -> Unit,
    onPttDebug: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val showDebugTools = remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Cài đặt",
                    color = TextPrimary,
                    style = DamType.Title.copy(fontSize = 22.sp, lineHeight = 28.sp)
                )
            }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.clickable(onClick = onProfile),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarCircle("Lan Phương", size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Lan Phương",
                            color = TextPrimary,
                            style = DamType.Heading
                        )
                        Text(
                            text = "Tổng đạo diễn · Cấp 1 · Chủ sự kiện",
                            color = TextSecondary,
                            style = DamType.Small
                        )
                    }
                }
            }
            SectionLabel("SỰ KIỆN HIỆN TẠI")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = FakeRemainingUiData.event.name,
                        color = TextPrimary,
                        style = DamType.Heading
                    )
                    Text(
                        text = "Đang diễn · 48 người",
                        color = AccentMint,
                        style = DamType.Small
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quản lý sự kiện",
                            modifier = Modifier.clickable(onClick = onManageEvent),
                            color = TextPrimary,
                            style = DamType.Body.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Rời sự kiện",
                            modifier = Modifier.clickable(onClick = onLeaveEvent),
                            color = DangerText,
                            style = DamType.Small.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            SectionLabel("ỨNG DỤNG")
            SettingsActionRow(
                iconRes = R.drawable.ic_user,
                title = "Hồ sơ của bạn",
                subtitle = "Tên hiển thị và chức vụ",
                onClick = onProfile
            )
            SettingsActionRow(
                iconRes = R.drawable.ic_wifi,
                title = "Kết nối đa tầng",
                subtitle = "Internet đang dùng · 42 ms",
                onClick = onMultiLevel
            )
            SettingsActionRow(
                iconRes = R.drawable.ic_head,
                title = "Rảnh tay và âm thanh",
                subtitle = "Tai nghe, phím âm lượng, lọc ồn",
                onClick = onHandsFree
            )
            if (showDebugTools) {
                Text(
                    text = "Công cụ kiểm thử",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPttDebug)
                        .padding(vertical = 6.dp),
                    color = TextSecondary,
                    style = DamType.Small.copy(fontSize = 12.sp)
                )
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassBg)
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = com.example.damthoai.ui.theme.AccentCyan,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = DamType.Heading)
            Text(subtitle, color = TextSecondary, style = DamType.Small)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chev),
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SystemPermissionsSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val hasAudioCamera = remember { PermissionManager.hasAudioAndCameraPermissions(context) }
    val hasNetworkBt = remember { PermissionManager.hasNetworkAndBluetoothPermissions(context) }
    val hasBackground = remember { PermissionManager.hasBackgroundExecutionPermissions(context) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("QUẢN LÝ QUYỀN HỆ THỐNG")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Trạng thái phân quyền Android",
                    color = TextPrimary,
                    style = DamType.Heading
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Micro & Camera: ${if (hasAudioCamera) "Đã cấp" else "Chưa cấp"}",
                    color = TextSecondary,
                    style = DamType.Small
                )
                Text(
                    text = "Mạng & Bluetooth: ${if (hasNetworkBt) "Đã cấp" else "Chưa cấp"}",
                    color = TextSecondary,
                    style = DamType.Small
                )
                Text(
                    text = "Chạy nền & Tối ưu pin: ${if (hasBackground) "Đã cấp" else "Chưa cấp"}",
                    color = TextSecondary,
                    style = DamType.Small
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        activity?.let {
                            PermissionManager.requestPermissions(it, 1001)
                            if (!PermissionManager.isIgnoringBatteryOptimizations(it)) {
                                PermissionManager.requestIgnoreBatteryOptimizations(it)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Yêu cầu cấp lại quyền hệ thống",
                        style = DamType.Body.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsScreenPreview() {
    DamThoaiTheme {
        SettingsScreen({}, {}, {}, {}, {}, {}, {})
    }
}
