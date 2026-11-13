package com.example.damthoai.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.damthoai.R
import com.example.damthoai.data.model.Event
import com.example.damthoai.data.fake.FakeEventRepository
import com.example.damthoai.data.fake.FakeRemainingUiData
import com.example.damthoai.data.model.MemberLevel
import com.example.damthoai.data.fake.NetworkStatus
import com.example.damthoai.network.NetworkStatusProvider
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.ui.components.AvatarCircle
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.KitDialog
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DangerText
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    onProfile: () -> Unit,
    onManageEvent: () -> Unit,
    onLeaveEvent: () -> Unit,
    onMultiLevel: () -> Unit,
    onHandsFree: () -> Unit,
    modifier: Modifier = Modifier
) {
    val networkStatus by NetworkStatusProvider.source.status.collectAsState()
    val currentEvent by FakeEventRepository.currentEvent.collectAsStateWithLifecycle()
    val networkDescription = when (val status = networkStatus) {
        is NetworkStatus.Good -> "Internet đang dùng · ${status.ms} ms"
        is NetworkStatus.Weak -> "Internet yếu · ${status.ms} ms"
        NetworkStatus.Offline -> "Mất kết nối Internet"
    }

    SettingsContent(
        onProfile = onProfile,
        onManageEvent = onManageEvent,
        onLeaveEvent = onLeaveEvent,
        onMultiLevel = onMultiLevel,
        onHandsFree = onHandsFree,
        modifier = modifier,
        event = currentEvent ?: FakeRemainingUiData.event,
        networkDescription = networkDescription
    )
}

@Composable
private fun SettingsContent(
    onProfile: () -> Unit,
    onManageEvent: () -> Unit,
    onLeaveEvent: () -> Unit,
    onMultiLevel: () -> Unit,
    onHandsFree: () -> Unit,
    modifier: Modifier = Modifier,
    event: Event,
    networkDescription: String
) {
    var showLeaveEventDialog by remember { mutableStateOf(false) }

    val bottomBarPadding = com.example.damthoai.ui.navigation.LocalBottomBarPadding.current

    DamThoaiBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = bottomBarPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Cài đặt",
                color = TextPrimary,
                style = DamType.Title
            )
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 10.dp
                )
            ) {
                Row(
                    modifier = Modifier.clickable(onClick = onProfile),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarCircle("LP", size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
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
                    Icon(
                        painter = painterResource(R.drawable.ic_chev),
                        contentDescription = "Sửa hồ sơ",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            SectionLabel("SỰ KIỆN HIỆN TẠI")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 10.dp
                )
            ) {
                val currentMember = event.members.firstOrNull { it.id == event.ownerId }
                    ?: FakeRemainingUiData.eventMembers.first { it.level == MemberLevel.LEVEL_1 }
                val isLevel1 = currentMember.level == MemberLevel.LEVEL_1

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = event.name,
                        color = TextPrimary,
                        style = DamType.Heading
                    )
                    Text(
                        text = "Đang diễn · ${event.members.size} người",
                        color = AccentMint,
                        style = DamType.Small
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(GlassBorder.copy(alpha = 0.75f))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isLevel1) Arrangement.SpaceBetween else Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLevel1) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .clickable(onClick = onManageEvent),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_cal),
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Quản lý sự kiện",
                                    color = AccentCyan,
                                    style = DamType.Body14Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .clickable { showLeaveEventDialog = true },
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "Rời sự kiện",
                                color = DangerText,
                                style = DamType.Small.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
            SectionLabel("ỨNG DỤNG")
            SettingsActionRow(
                iconRes = R.drawable.ic_wifi,
                title = "Kết nối đa tầng",
                subtitle = networkDescription,
                onClick = onMultiLevel
            )
            SettingsActionRow(
                iconRes = R.drawable.ic_head,
                title = "Rảnh tay và âm thanh",
                subtitle = "Tai nghe, phím âm lượng, lọc ồn",
                onClick = onHandsFree
            )
            Spacer(Modifier.height(100.dp))
        }
    }

    if (showLeaveEventDialog) {
        KitDialog(
            title = "Rời sự kiện?",
            description = "Bạn sẽ cần quét mã QR hoặc nhập PIN để vào lại sự kiện.",
            primaryButtonText = "Rời sự kiện",
            secondaryButtonText = "Ở lại",
            onPrimaryClick = {
                showLeaveEventDialog = false
                onLeaveEvent()
            },
            onDismissRequest = { showLeaveEventDialog = false }
        )
    }
}

@Composable
private fun SettingsActionRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassBg)
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = DamType.Heading)
            Text(subtitle, color = TextSecondary, style = DamType.Small)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chev),
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
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
private fun SettingsReleasePreview() {
    DamThoaiTheme {
        SettingsContent(
            onProfile = {},
            onManageEvent = {},
            onLeaveEvent = {},
            onMultiLevel = {},
            onHandsFree = {},
            event = FakeEventRepository.sampleEvent,
            networkDescription = "Internet đang dùng · 42 ms"
        )
    }
}