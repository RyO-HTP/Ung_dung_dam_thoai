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
import com.example.damthoai.data.qr.BackendConfig
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
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
import com.example.damthoai.data.model.Member
import com.example.damthoai.data.model.MemberLevel
import com.example.damthoai.data.remote.EventSession
import com.example.damthoai.data.remote.RemoteEventRepository
import com.example.damthoai.network.NetworkStatus
import com.example.damthoai.ui.preview.PreviewSamples
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
    modifier: Modifier = Modifier,
    onNetworkTest: () -> Unit = {}
) {
    val networkStatus by NetworkStatusProvider.source.status.collectAsState()
    val currentEvent by RemoteEventRepository.currentEvent.collectAsStateWithLifecycle()
    val currentMember by RemoteEventRepository.currentMember.collectAsStateWithLifecycle()
    val networkDescription = when (val status = networkStatus) {
        is NetworkStatus.Good -> "Internet đang dùng · ${status.ms} ms"
        is NetworkStatus.Weak -> "Internet yếu · ${status.ms} ms"
        NetworkStatus.Offline -> "Mất kết nối Internet"
    }

    val session = remember(currentEvent, currentMember) { EventSession.getSession() }
    val displayEvent = currentEvent ?: session?.let {
        Event(
            id = it.eventId.toString(),
            name = it.eventName,
            pinCode = it.pinCode
        )
    } ?: Event(id = "", name = "Chưa tham gia sự kiện", pinCode = "")

    val displayMember = currentMember ?: session?.let {
        Member(
            id = it.userId,
            name = it.displayName,
            roleTitle = it.roleTitle,
            initials = it.displayName.take(2).uppercase(),
            level = when (it.level) {
                1 -> MemberLevel.LEVEL_1
                2 -> MemberLevel.LEVEL_2
                else -> MemberLevel.LEVEL_3
            }
        )
    }

    SettingsContent(
        onProfile = onProfile,
        onManageEvent = onManageEvent,
        onLeaveEvent = onLeaveEvent,
        onMultiLevel = onMultiLevel,
        onHandsFree = onHandsFree,
        onNetworkTest = onNetworkTest,
        modifier = modifier,
        event = displayEvent,
        currentMember = displayMember,
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
    onNetworkTest: () -> Unit = {},
    modifier: Modifier = Modifier,
    event: Event,
    currentMember: Member?,
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
                val initials = currentMember?.initials ?: (currentMember?.name?.take(2)?.uppercase() ?: "--")
                val displayName = currentMember?.name ?: "Chưa đăng nhập"
                val roleDesc = buildString {
                    append(currentMember?.roleTitle ?: "Thành viên")
                    append(" · Cấp ${currentMember?.level?.code ?: 3}")
                    if (event.isOwner || currentMember?.level == MemberLevel.LEVEL_1) {
                        append(" · Chủ sự kiện")
                    }
                }
                Row(
                    modifier = Modifier.clickable(onClick = onProfile),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarCircle(initials, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = displayName,
                            color = TextPrimary,
                            style = DamType.Heading
                        )
                        Text(
                            text = roleDesc,
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
                val isLevel1 = (currentMember?.level == MemberLevel.LEVEL_1) || event.isOwner

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

            SectionLabel("MÁY CHỦ BACKEND")
            BackendUrlSettingsCard()
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
            SettingsActionRow(
                iconRes = R.drawable.ic_radio,
                title = "Kiểm tra mạng",
                subtitle = "Gọi thử PTT thật giữa 2 máy, xem STUN/TURN, độ trễ",
                onClick = onNetworkTest
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
private fun BackendUrlSettingsCard() {
    val context = LocalContext.current
    var urlText by remember {
        mutableStateOf(BackendConfig.baseUrl ?: BackendConfig.DEFAULT_BASE_URL)
    }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var statusOk by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Địa chỉ Backend API",
                color = TextPrimary,
                style = DamType.Heading
            )
            Text(
                text = "Cloud Render hiện có thể tắt (404). Chạy backend local rồi nhập http://IP-máy:8080 (cùng Wi‑Fi).",
                color = TextSecondary,
                style = DamType.Small
            )
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("http://192.168.1.10:8080", color = TextSecondary) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = GlassBorder,
                    cursorColor = AccentCyan
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val trimmed = urlText.trim().removeSuffix("/")
                        if (trimmed.isEmpty()) {
                            statusOk = false
                            statusMsg = "URL không được để trống"
                            return@Button
                        }
                        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                            statusOk = false
                            statusMsg = "URL phải bắt đầu bằng http:// hoặc https://"
                            return@Button
                        }
                        BackendConfig.setBaseUrl(context, trimmed)
                        urlText = trimmed
                        statusOk = true
                        statusMsg = "Đã lưu: $trimmed"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Lưu URL", style = DamType.Body.copy(fontWeight = FontWeight.Bold))
                }
                Button(
                    onClick = {
                        BackendConfig.setBaseUrl(context, null)
                        urlText = BackendConfig.DEFAULT_BASE_URL
                        statusOk = true
                        statusMsg = "Đã về mặc định build"
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mặc định", style = DamType.Body.copy(fontWeight = FontWeight.Bold))
                }
            }
            statusMsg?.let { msg ->
                Text(
                    text = msg,
                    color = if (statusOk) AccentMint else DangerText,
                    style = DamType.Small
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
            event = PreviewSamples.sampleEvent,
            currentMember = PreviewSamples.sampleMembers.first(),
            networkDescription = "Internet đang dùng · 42 ms"
        )
    }
}