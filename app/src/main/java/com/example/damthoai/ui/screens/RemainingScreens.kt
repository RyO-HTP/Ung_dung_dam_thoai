package com.example.damthoai.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.R
import com.example.damthoai.data.fake.Channel
import com.example.damthoai.data.fake.Event
import com.example.damthoai.data.fake.FakeRemainingUiData
import com.example.damthoai.data.fake.FakeRepositories
import com.example.damthoai.data.fake.LiveCue
import com.example.damthoai.data.fake.Member
import com.example.damthoai.data.fake.MemberLevel
import com.example.damthoai.ui.channels.ChannelItemUiState
import com.example.damthoai.ui.channels.ChannelMenuSheet
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.BgVariant
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.DangerButton
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.AvatarCircle
import com.example.damthoai.ui.components.GlassTextField
import com.example.damthoai.ui.components.KitSwitch
import com.example.damthoai.ui.components.LevelTag
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SecondaryButton
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.components.TagLabel
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DangerEnd
import com.example.damthoai.ui.theme.DangerStart
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PrimaryStart
import com.example.damthoai.ui.theme.SelectedChannelBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

@Composable
private fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    variant: BgVariant = BgVariant.Default,
    content: @Composable ColumnScope.() -> Unit
) {
    DamThoaiBackground(modifier = modifier, variant = variant) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 12.dp)
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
                    style = DamType.Title.copy(fontSize = 21.sp, lineHeight = 26.sp)
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
private fun PageIntro(title: String, subtitle: String? = null) {
    Text(text = title, color = TextPrimary, style = DamType.Heading.copy(fontSize = 18.sp))
    if (subtitle != null) {
        Text(text = subtitle, color = TextSecondary, style = DamType.Body)
    }
}

@Composable
private fun ScreenIcon(
    iconRes: Int,
    tint: androidx.compose.ui.graphics.Color = AccentCyan,
    modifier: Modifier = Modifier,
    boxSize: androidx.compose.ui.unit.Dp = 42.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
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
private fun ChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int? = null
) {
    val surface = if (selected) SelectedChannelBg else GlassBg
    val outline = if (selected) AccentCyan.copy(alpha = 0.6f) else GlassBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(surface)
            .border(1.dp, outline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            ScreenIcon(iconRes = iconRes)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = DamType.Heading)
            Text(subtitle, color = TextSecondary, style = DamType.Small)
        }
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "Đã chọn",
                tint = AccentCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ActionRow(
    iconRes: Int,
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
                painter = painterResource(R.drawable.ic_chev),
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MemberRow(member: Member, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(member.initials, size = 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(member.name, color = TextPrimary, style = DamType.Heading)
            Text(member.roleTitle, color = TextSecondary, style = DamType.Small)
        }
        LevelTag(member.level)
    }
}

@Composable
private fun LabeledInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    GlassTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label,
        placeholder = placeholder
    )
}

@Composable
private fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) PrimaryStart else GlassBg
    val borderColor = if (selected) AccentCyan.copy(alpha = 0.7f) else GlassBorder
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextPrimary, style = DamType.Chip)
    }
}

@Composable
private fun EmergencyPttButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(DangerStart, DangerEnd)
                )
            )
            .border(6.dp, DangerStart.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = "Nói trong cuộc gọi khẩn cấp",
            tint = TextPrimary,
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text("GIỮ ĐỂ NÓI", color = TextPrimary, style = DamType.Button)
    }
}

@Composable
fun ChannelMenuRouteScreen(
    onDismiss: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onLeaveChannel: () -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    DamThoaiBackground {
        ChannelMenuSheet(
            channelItem = ChannelItemUiState(
                channel = FakeRepositories.sampleChannels[1],
                isMuted = isMuted
            ),
            onDismiss = onDismiss,
            onToggleMute = {
                isMuted = it
                onToggleMute(it)
            },
            onLeaveChannel = onLeaveChannel
        )
    }
}

@Composable
fun EmergencyCallSelectScreen(
    onBack: () -> Unit,
    onSendAlert: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialSelectedTarget: String = "Hậu cần",
    initialSelectedMemberId: String? = null
) {
    var selectedTarget by remember { mutableStateOf(initialSelectedTarget) }
    var selectedMemberId by remember { mutableStateOf(initialSelectedMemberId) }
    ScreenFrame("Gọi khẩn cấp", onBack, modifier, BgVariant.Emergency) {
        PageIntro("Chỉ người được chọn nhận cảnh báo")
        ChoiceRow(
            "Tổng đạo diễn",
            "Gửi nhanh tới Lan Phương",
            selectedTarget == "Tổng đạo diễn",
            { selectedTarget = "Tổng đạo diễn" },
            R.drawable.ic_crown
        )
        ChoiceRow(
            "Cả kênh Hậu cần",
            "Thành viên trong kênh nhận cảnh báo",
            selectedTarget == "Hậu cần",
            { selectedTarget = "Hậu cần" },
            R.drawable.ic_shield
        )
        ChoiceRow(
            "Toàn sự kiện",
            "Gửi cảnh báo tới tất cả thành viên",
            selectedTarget == "Toàn sự kiện",
            { selectedTarget = "Toàn sự kiện" },
            R.drawable.ic_users
        )
        SectionLabel("Hoặc chọn người")
        FakeRemainingUiData.eventMembers.take(4).forEachIndexed { index, member ->
            ChoiceRow(
                member.name,
                member.roleTitle,
                selectedMemberId == member.id,
                { selectedMemberId = member.id },
                R.drawable.ic_user
            )
        }
        DangerButton(
            text = "Gửi cảnh báo khẩn cấp",
            onClick = { onSendAlert(selectedMemberId ?: selectedTarget) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun EmergencyAlertIncomingScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScreenFrame(
        title = "Cảnh báo khẩn cấp",
        onBack = onDecline,
        modifier = modifier,
        variant = BgVariant.Emergency
    ) {
        Spacer(Modifier.height(20.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ScreenIcon(
                    R.drawable.ic_alert,
                    tint = AccentRed,
                    boxSize = 72.dp,
                    iconSize = 34.dp,
                    shape = CircleShape
                )
                TagLabel(text = "CẢNH BÁO KHẨN CẤP", color = AccentRed)
                Text(
                    "Lan Phương gọi khẩn cấp",
                    color = TextPrimary,
                    style = DamType.Title.copy(fontSize = 22.sp),
                    textAlign = TextAlign.Center
                )
                Text(
                    "Tổng đạo diễn · gọi riêng bạn",
                    color = TextSecondary,
                    style = DamType.Body,
                    textAlign = TextAlign.Center
                )
                TagLabel(text = "ĐANG RUNG", color = AccentRed)
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = "Nhận cuộc gọi",
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth()
        )
        SecondaryButton(
            text = "Từ chối",
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun EmergencyActiveCallScreen(
    onEndCall: () -> Unit,
    noAnswer: Boolean = false,
    onCallAgain: () -> Unit = onEndCall,
    onMessageChannel: () -> Unit = onEndCall,
    onTalk: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ScreenFrame(
        title = "Cuộc gọi khẩn cấp",
        onBack = onEndCall,
        modifier = modifier,
        variant = BgVariant.Emergency
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AvatarCircle("Lan Phương", size = 72.dp)
                Text("Lan Phương", color = TextPrimary, style = DamType.Title.copy(fontSize = 22.sp))
                Text(
                    if (noAnswer) "Chưa ai trả lời" else "Cuộc gọi khẩn cấp",
                    color = if (noAnswer) AccentAmber else AccentRed,
                    style = DamType.Heading
                )
                Text(
                    if (noAnswer) {
                        "Đã gọi Lan Phương 30 giây nhưng máy chưa được nhấc. " +
                            "Bạn có thể gọi lại hoặc nhắn qua kênh."
                    } else {
                        "Lan Phương · Minh Hậu (bạn) · Quốc Hùng"
                    },
                    color = TextSecondary,
                    style = DamType.Body,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (!noAnswer) {
            EmergencyPttButton(onClick = onTalk)
            DangerButton(
                text = "Kết thúc cuộc gọi",
                onClick = onEndCall,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            PrimaryButton(
                text = "Gọi lại",
                onClick = onCallAgain,
                modifier = Modifier.fillMaxWidth()
            )
            SecondaryButton(
                text = "Nhắn qua kênh",
                onClick = onMessageChannel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SettingsHubScreen(
    onBack: () -> Unit,
    onProfile: () -> Unit,
    onMultiLevel: () -> Unit,
    onHandsFree: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScreenFrame("Cài đặt", onBack, modifier) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    FakeRemainingUiData.event.name,
                    color = TextPrimary,
                    style = DamType.Heading
                )
                Text("Bạn là Cấp 1 · quyền cao nhất", color = TextSecondary, style = DamType.Small)
            }
        }
        SectionLabel("ỨNG DỤNG")
        ActionRow(
            R.drawable.ic_user,
            "Hồ sơ của bạn",
            "Tên hiển thị và chức vụ",
            onProfile
        )
        ActionRow(
            R.drawable.ic_wifi,
            "Kết nối đa tầng",
            "Internet, mạng nội bộ và hàng đợi ngoại tuyến",
            onMultiLevel
        )
        ActionRow(
            R.drawable.ic_head,
            "Rảnh tay và âm thanh",
            "Tai nghe, phím âm lượng, lọc ồn",
            onHandsFree
        )
    }
}

@Composable
fun MultiLevelConnectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var localNetwork by remember { mutableStateOf(true) }
    var bluetooth by remember { mutableStateOf(true) }
    var offlineQueue by remember { mutableStateOf(true) }
    ScreenFrame("Kết nối đa tầng", onBack, modifier) {
        SectionLabel("ỨNG DỤNG")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScreenIcon(R.drawable.ic_wifi, tint = AccentMint)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Internet", color = TextPrimary, style = DamType.Heading)
                    Text("Đang dùng · 42 ms", color = AccentMint, style = DamType.Small)
                }
                TagLabel("ĐANG DÙNG", color = AccentCyan)
            }
        }
        ConnectionSwitchRow(
            iconRes = R.drawable.ic_radio,
            title = "Mạng nội bộ",
            description = "Tự chuyển khi nghẽn mạng",
            checked = localNetwork,
            onCheckedChange = { localNetwork = it }
        )
        ConnectionSwitchRow(
            iconRes = R.drawable.ic_bt,
            title = "Bluetooth LE / Wi-Fi Direct",
            description = "Kết nối trực tiếp ở gần",
            checked = bluetooth,
            onCheckedChange = { bluetooth = it }
        )
        SectionLabel("KHI MẤT KẾT NỐI")
        ConnectionSwitchRow(
            iconRes = R.drawable.ic_save,
            title = "Hàng đợi ngoại tuyến",
            description = "Lưu nội dung, tự gửi khi có mạng",
            checked = offlineQueue,
            onCheckedChange = { offlineQueue = it }
        )
    }
}

@Composable
private fun ConnectionSwitchRow(
    iconRes: Int,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassBg)
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScreenIcon(iconRes)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = DamType.Heading)
            Text(description, color = TextSecondary, style = DamType.Small)
        }
        KitSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun UserProfileScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by remember { mutableStateOf("Lan Phương") }
    var role by remember { mutableStateOf("Tổng đạo diễn") }
    ScreenFrame("Hồ sơ của bạn", onBack, modifier) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle("Lan Phương", size = 58.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Cấp 1", color = AccentAmber, style = DamType.Tag)
                    Text("Đêm nhạc Chào Tân sinh viên", color = TextSecondary, style = DamType.Small)
                }
            }
        }
        LabeledInput(
            label = "TÊN HIỂN THỊ",
            value = displayName,
            onValueChange = { displayName = it },
            placeholder = "Nhập tên hiển thị"
        )
        LabeledInput(
            label = "CHỨC VỤ",
            value = role,
            onValueChange = { role = it },
            placeholder = "Nhập chức vụ"
        )
        PrimaryButton(
            text = "Lưu thay đổi",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ManageEventScreen(
    onBack: () -> Unit,
    onQrCode: () -> Unit,
    onMembers: () -> Unit,
    onLiveCue: () -> Unit,
    onEndEvent: () -> Unit,
    modifier: Modifier = Modifier,
    event: Event = FakeRemainingUiData.event,
    channels: List<Channel> = FakeRemainingUiData.managementChannels
) {
    ScreenFrame("Quản lý sự kiện", onBack, modifier) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(event.name, color = TextPrimary, style = DamType.Heading)
                Text("Bạn là Cấp 1 · quyền cao nhất", color = TextSecondary, style = DamType.Small)
            }
        }
        SectionLabel("KÊNH VÀ MÃ VÀO NHANH")
        channels.forEach { channel ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassBg)
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        if (channel.isPriority) R.drawable.ic_crown else R.drawable.ic_radio
                    ),
                    contentDescription = null,
                    tint = if (channel.isPriority) AccentAmber else AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(channel.name, color = TextPrimary, style = DamType.Body)
                    if (channel.id == "ch_01") {
                        Text(
                            "Mọi người tự vào khi vào ban",
                            color = TextSecondary,
                            style = DamType.Small
                        )
                    }
                }
            }
        }
        ActionRow(
            R.drawable.ic_qr,
            "Mã QR của ban",
            "Chia sẻ quyền vào nhanh",
            onQrCode
        )
        ActionRow(
            R.drawable.ic_users,
            "Thành viên · 48 người",
            "Quản lý cấp và chức vụ",
            onMembers
        )
        ActionRow(
            R.drawable.ic_zap,
            "Live Cue",
            FakeRemainingUiData.liveCue.title,
            onLiveCue,
            "ƯU TIÊN"
        )
        DangerButton(
            text = "Kết thúc sự kiện",
            onClick = onEndEvent,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MyQrCodeScreen(
    onBack: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    event: Event = FakeRemainingUiData.event
) {
    ScreenFrame("Mã QR của ban", onBack, modifier) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(event.name, color = TextPrimary, style = DamType.Heading, textAlign = TextAlign.Center)
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(TextPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_qr),
                        contentDescription = "Mã QR",
                        tint = PrimaryStart,
                        modifier = Modifier.size(140.dp)
                    )
                }
                SectionLabel("MÃ PIN CỦA SỰ KIỆN")
                Text(event.pinCode, color = AccentCyan, style = DamType.PinBig)
                Text(
                    "Quét mã QR của ban hoặc nhập PIN để vào sự kiện.",
                    color = TextSecondary,
                    style = DamType.Body,
                    textAlign = TextAlign.Center
                )
            }
        }
        SecondaryButton(
            text = "Chia sẻ mã",
            onClick = onShare,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun EventMembersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    members: List<Member> = FakeRemainingUiData.eventMembers,
    initiallySelectedMember: Member? = null,
    onMemberRoleChanged: (Member, MemberLevel) -> Unit = { _, _ -> },
    onRemoveMember: (Member) -> Unit = {}
) {
    var selectedMember by remember { mutableStateOf(initiallySelectedMember) }
    var selectedLevel by remember(selectedMember) {
        mutableStateOf(selectedMember?.level ?: MemberLevel.LEVEL_3)
    }
    ScreenFrame("Thành viên", onBack, modifier) {
        SectionLabel("THÀNH VIÊN · 48 NGƯỜI")
        Text("Chọn thành viên để sửa cấp và chức vụ.", color = TextSecondary, style = DamType.Small)
        selectedMember?.let { member ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("SỬA THÀNH VIÊN")
                            Text(member.name, color = TextPrimary, style = DamType.Heading)
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_x),
                            contentDescription = "Đóng",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { selectedMember = null }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemberLevel.entries.forEach { level ->
                            SelectableChip(
                                text = level.shortLabel,
                                selected = selectedLevel == level,
                                onClick = { selectedLevel = level }
                            )
                        }
                    }
                    PrimaryButton(
                        text = "Lưu thay đổi",
                        onClick = { onMemberRoleChanged(member, selectedLevel) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 48.dp
                    )
                    DangerButton(
                        text = "Xóa khỏi sự kiện",
                        onClick = {
                            onRemoveMember(member)
                            selectedMember = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        height = 48.dp
                    )
                }
            }
        }
        members.forEach { member ->
            MemberRow(member) {
                selectedMember = member
                selectedLevel = member.level
            }
        }
    }
}

@Composable
fun LiveCueDraftScreen(
    onBack: () -> Unit,
    onSendCue: (LiveCue) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(FakeRemainingUiData.liveCue.title) }
    var message by remember { mutableStateOf(FakeRemainingUiData.liveCue.message) }
    var channel by remember { mutableStateOf(FakeRemainingUiData.liveCue.channelName) }
    var priority by remember { mutableStateOf(true) }
    ScreenFrame("Live Cue", onBack, modifier) {
        PageIntro("Thông báo nổi bật", "Gửi lệnh đạo diễn ghim lên đầu kênh.")
        LabeledInput(
            "TIÊU ĐỀ",
            title,
            { title = it },
            "Nhập tiêu đề Live Cue"
        )
        LabeledInput(
            "NỘI DUNG",
            message,
            { message = it },
            "Nhập nội dung Live Cue"
        )
        SectionLabel("GỬI TỚI KÊNH")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Toàn sự kiện", "Sân khấu", "Hậu cần").forEach { option ->
                SelectableChip(
                    text = option,
                    selected = channel == option,
                    onClick = { channel = option }
                )
            }
        }
        ChoiceRow(
            "Đánh dấu ưu tiên",
            "Làm nổi bật Live Cue trên màn hình",
            priority,
            { priority = !priority },
            R.drawable.ic_zap
        )
        PrimaryButton(
            text = "Đẩy lên mọi máy",
            onClick = {
                onSendCue(
                    LiveCue(
                        title = title,
                        message = message,
                        channelName = channel,
                        isPriority = priority
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun EventEndedScreen(
    onReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScreenFrame("Sự kiện đã kết thúc", onBack = null, modifier = modifier) {
        Spacer(Modifier.height(28.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ScreenIcon(
                    R.drawable.ic_check,
                    tint = AccentAmber,
                    boxSize = 72.dp,
                    iconSize = 34.dp,
                    shape = CircleShape
                )
                Text("Sự kiện đã kết thúc", color = TextPrimary, style = DamType.Title, textAlign = TextAlign.Center)
                Text(
                    FakeRemainingUiData.event.name,
                    color = TextSecondary,
                    style = DamType.Body,
                    textAlign = TextAlign.Center
                )
            }
        }
        PrimaryButton(
            text = "Về màn Vào kênh",
            onClick = onReturn,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun RemovedFromEventScreen(
    onReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScreenFrame("Bạn đã bị xóa khỏi sự kiện", onBack = null, modifier = modifier) {
        Spacer(Modifier.height(28.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ScreenIcon(
                    R.drawable.ic_del,
                    tint = AccentRed,
                    boxSize = 72.dp,
                    iconSize = 34.dp,
                    shape = CircleShape
                )
                Text(
                    "Bạn đã bị xóa khỏi sự kiện",
                    color = TextPrimary,
                    style = DamType.Title.copy(fontSize = 22.sp),
                    textAlign = TextAlign.Center
                )
                Text(
                    "Tổng đạo diễn đã xóa bạn khỏi ${FakeRemainingUiData.event.name}. " +
                        "Bạn cần quét lại mã nếu muốn vào lại.",
                    color = TextSecondary,
                    style = DamType.Body,
                    textAlign = TextAlign.Center
                )
            }
        }
        PrimaryButton(
            text = "Về màn Vào kênh",
            onClick = onReturn,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ChannelMenuRouteScreenPreview() {
    DamThoaiTheme {
        ChannelMenuRouteScreen({}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EmergencyCallSelectScreenPreview() {
    DamThoaiTheme {
        EmergencyCallSelectScreen({}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EmergencyCallSelectPersonPreview() {
    DamThoaiTheme {
        EmergencyCallSelectScreen(
            onBack = {},
            onSendAlert = {},
            initialSelectedTarget = "",
            initialSelectedMemberId = FakeRemainingUiData.eventMembers[1].id
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EmergencyAlertIncomingScreenPreview() {
    DamThoaiTheme {
        EmergencyAlertIncomingScreen({}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EmergencyActiveCallScreenPreview() {
    DamThoaiTheme {
        EmergencyActiveCallScreen({})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EmergencyActiveCallNoAnswerPreview() {
    DamThoaiTheme {
        EmergencyActiveCallScreen({}, noAnswer = true)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsHubScreenPreview() {
    DamThoaiTheme {
        SettingsHubScreen({}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MultiLevelConnectionScreenPreview() {
    DamThoaiTheme {
        MultiLevelConnectionScreen({})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun UserProfileScreenPreview() {
    DamThoaiTheme {
        UserProfileScreen({}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ManageEventScreenPreview() {
    DamThoaiTheme {
        ManageEventScreen({}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MyQrCodeScreenPreview() {
    DamThoaiTheme {
        MyQrCodeScreen({}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EventMembersScreenPreview() {
    DamThoaiTheme {
        EventMembersScreen({})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EventMembersEditScreenPreview() {
    DamThoaiTheme {
        EventMembersScreen({}, initiallySelectedMember = FakeRemainingUiData.eventMembers.first())
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LiveCueDraftScreenPreview() {
    DamThoaiTheme {
        LiveCueDraftScreen({}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EventEndedScreenPreview() {
    DamThoaiTheme {
        EventEndedScreen({})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun RemovedFromEventScreenPreview() {
    DamThoaiTheme {
        RemovedFromEventScreen({})
    }
}
