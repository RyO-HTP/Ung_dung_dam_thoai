package com.example.damthoai.ui.screens

import android.app.Activity
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.damthoai.R
import com.example.damthoai.data.fake.Event
import com.example.damthoai.data.fake.FakeEventRepository
import com.example.damthoai.data.fake.FakeNetworkStatus
import com.example.damthoai.data.fake.FakeRemainingUiData
import com.example.damthoai.data.fake.NetworkStatus
import com.example.damthoai.permission.PermissionManager
import com.example.damthoai.ui.components.AvatarCircle
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.KitDialog
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.components.TagLabel
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.DangerText
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PrimaryBrush
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    onProfile: () -> Unit,
    onManageEvent: () -> Unit,
    onLeaveEvent: () -> Unit,
    onMultiLevel: () -> Unit,
    onHandsFree: () -> Unit,
    onPttDebug: () -> Unit,
    modifier: Modifier = Modifier,
    onReviewOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val isDebug = remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    val networkStatus by FakeNetworkStatus.status.collectAsState()
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
        onPttDebug = onPttDebug,
        onReviewOnboarding = onReviewOnboarding,
        modifier = modifier,
        isDebug = isDebug,
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
    onPttDebug: () -> Unit,
    onReviewOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    isDebug: Boolean,
    event: Event,
    networkDescription: String
) {
    var showLeaveEventDialog by remember { mutableStateOf(false) }
    var showOnboardingDialog by remember { mutableStateOf(false) }

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
            Text(
                text = "Cài đặt",
                color = TextPrimary,
                style = DamType.Title
            )
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp,
                    vertical = 12.dp
                )
            ) {
                Row(
                    modifier = Modifier.clickable(onClick = onProfile),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarCircle("LP", size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Lan Phương",
                            color = TextPrimary,
                            style = DamType.Heading16
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
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            SectionLabel("SỰ KIỆN HIỆN TẠI")
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 14.dp,
                    vertical = 12.dp
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable(onClick = onManageEvent),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_cal),
                                contentDescription = null,
                                tint = com.example.damthoai.ui.theme.AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Quản lý sự kiện",
                                color = com.example.damthoai.ui.theme.AccentCyan,
                                style = DamType.Body14Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
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
            if (isDebug) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("CÔNG CỤ KIỂM THỬ")
                    TagLabel("DEBUG", color = AccentAmber)
                }
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.8f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)
                ) {
                    Column {
                        SettingsDebugActionRow(
                            iconRes = R.drawable.ic_home,
                            title = "Xem lại phần giới thiệu",
                            subtitle = "Quay về màn Giới thiệu, không xoá dữ liệu sự kiện",
                            onClick = { showOnboardingDialog = true }
                        )
                        SettingsDebugActionRow(
                            iconRes = R.drawable.ic_mic,
                            title = "Thử phím cứng và công cụ debug",
                            subtitle = "Kiểm tra phím âm lượng, PTT và log",
                            onClick = onPttDebug
                        )
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
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
    if (showOnboardingDialog) {
        ReviewOnboardingDialog(
            onConfirm = {
                showOnboardingDialog = false
                onReviewOnboarding()
            },
            onDismiss = { showOnboardingDialog = false }
        )
    }
}

@Composable
private fun SettingsDebugActionRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = com.example.damthoai.ui.theme.AccentCyan,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                style = DamType.Body13.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(text = subtitle, color = TextSecondary, style = DamType.Small)
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
private fun ReviewOnboardingDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    KitDialog(
        title = "Xem lại phần giới thiệu?",
        description = "Ứng dụng sẽ quay về màn Giới thiệu. Dữ liệu sự kiện không bị xoá.",
        primaryButtonText = "Xem lại",
        secondaryButtonText = "Hủy",
        onPrimaryClick = onConfirm,
        onDismissRequest = onDismiss,
        primaryBrush = PrimaryBrush
    )
}

@Composable
private fun SettingsActionRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassBg)
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = com.example.damthoai.ui.theme.AccentCyan,
                modifier = Modifier.size(20.dp)
            )
        }
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
private fun SettingsReleasePreview() {
    DamThoaiTheme {
        SettingsContent(
            onProfile = {},
            onManageEvent = {},
            onLeaveEvent = {},
            onMultiLevel = {},
            onHandsFree = {},
            onPttDebug = {},
            onReviewOnboarding = {},
            isDebug = false,
            event = FakeEventRepository.sampleEvent,
            networkDescription = "Internet đang dùng · 42 ms"
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsDebugPreview() {
    DamThoaiTheme {
        SettingsContent(
            onProfile = {},
            onManageEvent = {},
            onLeaveEvent = {},
            onMultiLevel = {},
            onHandsFree = {},
            onPttDebug = {},
            onReviewOnboarding = {},
            isDebug = true,
            event = FakeEventRepository.sampleEvent,
            networkDescription = "Internet đang dùng · 42 ms"
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ReviewOnboardingDialogPreview() {
    DamThoaiTheme {
        ReviewOnboardingDialog(onConfirm = {}, onDismiss = {})
    }
}
