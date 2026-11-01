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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.damthoai.R
import com.example.damthoai.data.fake.Channel
import com.example.damthoai.data.fake.Event
import com.example.damthoai.data.fake.FakeMultiLevelConnectionData
import com.example.damthoai.data.fake.FakeRemainingUiData
import com.example.damthoai.data.fake.FakeRepositories
import com.example.damthoai.data.fake.LiveCue
import com.example.damthoai.data.fake.Member
import com.example.damthoai.data.fake.MemberLevel
import com.example.damthoai.ui.channels.ChannelItemUiState
import com.example.damthoai.ui.channels.ChannelMenuSheet
import com.example.damthoai.ui.event.EventMembersUiState
import com.example.damthoai.ui.event.EventMembersViewModel
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.BgVariant
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.DangerButton
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.AvatarCircle
import com.example.damthoai.ui.components.GlassTextField
import com.example.damthoai.ui.components.KitBottomSheet
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
import com.example.damthoai.ui.theme.ChipInk
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DangerText
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PrimaryStart
import com.example.damthoai.ui.theme.SelectedChannelBg
import com.example.damthoai.ui.theme.TagNeutralBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextPlaceholder
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
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassBg)
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(member.name, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                member.name,
                color = TextPrimary,
                style = DamType.Body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
            Text(
                member.roleTitle,
                color = TextSecondary,
                style = DamType.Small.copy(fontSize = 11.5.sp)
            )
        }
        LevelTag(member.level)
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chev),
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
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
fun MultiLevelConnectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by FakeMultiLevelConnectionData.uiState.collectAsState()
    MultiLevelConnectionContent(
        onBack = onBack,
        modifier = modifier,
        internetEnabled = uiState.internetEnabled,
        localNetworkEnabled = uiState.localNetworkEnabled,
        bluetoothDirectEnabled = uiState.bluetoothDirectEnabled,
        onInternetChange = FakeMultiLevelConnectionData::setInternetEnabled,
        onLocalNetworkChange = FakeMultiLevelConnectionData::setLocalNetworkEnabled,
        onBluetoothDirectChange = FakeMultiLevelConnectionData::setBluetoothDirectEnabled
    )
}

@Composable
private fun MultiLevelConnectionContent(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    internetEnabled: Boolean,
    localNetworkEnabled: Boolean,
    bluetoothDirectEnabled: Boolean,
    onInternetChange: (Boolean) -> Unit,
    onLocalNetworkChange: (Boolean) -> Unit,
    onBluetoothDirectChange: (Boolean) -> Unit
) {
    ScreenFrame("Kết nối đa tầng", onBack, modifier) {
        Text(
            text = "App tự chuyển giữa các tầng khi mạng yếu. Bạn chỉ cần bật hoặc tắt từng tầng.",
            color = TextSecondary,
            style = DamType.Body.copy(fontSize = 13.sp, lineHeight = 19.5.sp)
        )
        ConnectionLayerCard(
            iconRes = R.drawable.ic_radio,
            title = "Internet",
            status = if (internetEnabled) "ĐANG DÙNG" else "TẮT",
            statusColor = if (internetEnabled) AccentCyan else TextSecondary,
            statusBackground = if (internetEnabled) null else TagNeutralBg,
            description = "4G/5G, Wi-Fi qua máy chủ SFU · 42 ms",
            checked = internetEnabled,
            onCheckedChange = onInternetChange
        )
        ConnectionLayerCard(
            iconRes = R.drawable.ic_wifi,
            title = "Mạng nội bộ",
            status = if (localNetworkEnabled) "SẴN SÀNG" else "TẮT",
            statusColor = TextSecondary,
            statusBackground = TagNeutralBg,
            description = "Wi-Fi hoặc hotspot, tự tìm máy (mDNS) · thấy 12 máy",
            checked = localNetworkEnabled,
            onCheckedChange = onLocalNetworkChange
        )
        ConnectionLayerCard(
            iconRes = R.drawable.ic_bt,
            title = "Bluetooth LE / Wi-Fi Direct",
            description = "Ngang hàng 30–100 m khi không có mạng",
            checked = bluetoothDirectEnabled,
            onCheckedChange = onBluetoothDirectChange
        )
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 14.dp,
                vertical = 12.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionLayerIcon(R.drawable.ic_save)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hàng đợi ngoại tuyến", color = TextPrimary, style = DamType.Heading)
                    Text(
                        "Mất sóng thì ghi âm lưu lại, có mạng tự gửi",
                        color = TextSecondary,
                        style = DamType.Small
                    )
                }
                Text("0 tin chờ", color = TextSecondary, style = DamType.Small)
            }
        }
    }
}

@Composable
private fun ConnectionLayerCard(
    iconRes: Int,
    title: String,
    description: String,
    status: String? = null,
    statusColor: androidx.compose.ui.graphics.Color = TextSecondary,
    statusBackground: androidx.compose.ui.graphics.Color? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConnectionLayerIcon(iconRes)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(title, color = TextPrimary, style = DamType.Heading)
                    if (status != null) {
                        TagLabel(
                            text = status,
                            color = statusColor,
                            backgroundColor = statusBackground
                        )
                    }
                }
                Text(description, color = TextSecondary, style = DamType.Small)
            }
            Spacer(Modifier.width(8.dp))
            KitSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ConnectionLayerIcon(iconRes: Int) {
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(20.dp)
        )
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
    viewModel: EventMembersViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    val selectedMember = uiState.members.firstOrNull { it.id == selectedMemberId }

    EventMembersContent(
        onBack = onBack,
        modifier = modifier,
        uiState = uiState,
        selectedMember = selectedMember,
        onSearchQueryChange = viewModel::setSearchQuery,
        onLevelSelected = viewModel::selectLevel,
        onMemberSelected = { selectedMemberId = it.id },
        onDismissSheet = { selectedMemberId = null },
        onSaveMember = { member ->
            viewModel.updateMember(member)
            selectedMemberId = null
        },
        onRemoveMember = { member ->
            viewModel.removeMember(member.id)
            selectedMemberId = null
        }
    )
}

@Composable
private fun EventMembersContent(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    uiState: EventMembersUiState,
    selectedMember: Member? = null,
    initialDraftLevel: MemberLevel? = null,
    initialRoleTitle: String? = null,
    onSearchQueryChange: (String) -> Unit = {},
    onLevelSelected: (MemberLevel?) -> Unit = {},
    onMemberSelected: (Member) -> Unit = {},
    onDismissSheet: () -> Unit = {},
    onSaveMember: (Member) -> Unit = {},
    onRemoveMember: (Member) -> Unit = {}
) {
    DamThoaiBackground(variant = BgVariant.Default, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onClick = onBack)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Thành viên",
                        color = TextPrimary,
                        style = DamType.Heading.copy(fontSize = 18.sp)
                    )
                    Text(
                        text = "48 người trong sự kiện",
                        color = TextSecondary,
                        style = DamType.Small
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            MemberSearchField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemberFilterChip(
                    text = "Tất cả",
                    selected = uiState.selectedLevel == null,
                    onClick = { onLevelSelected(null) }
                )
                MemberLevel.entries.forEach { level ->
                    MemberFilterChip(
                        text = "Cấp ${level.code}",
                        selected = uiState.selectedLevel == level,
                        onClick = { onLevelSelected(level) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.filteredMembers.forEach { member ->
                    MemberRow(member) { onMemberSelected(member) }
                }
            }
        }
        selectedMember?.let { member ->
            MemberEditSheet(
                member = member,
                initialDraftLevel = initialDraftLevel,
                initialRoleTitle = initialRoleTitle,
                onDismissRequest = onDismissSheet,
                onSave = onSaveMember,
                onRemove = onRemoveMember
            )
        }
    }
}

@Composable
private fun MemberSearchField(
    value: String,
    onValueChange: (String) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassBg)
            .border(1.dp, GlassBorder, shape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = DamType.Body.copy(fontSize = 14.sp, color = TextPrimary),
            cursorBrush = SolidColor(AccentCyan),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Tìm theo tên hoặc chức vụ",
                            color = TextPlaceholder,
                            style = DamType.Body.copy(fontSize = 14.sp)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun MemberFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) TextPrimary else GlassBg
    val foreground = if (selected) ChipInk else TextPrimary
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, if (selected) TextPrimary else GlassBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = foreground, style = DamType.Chip)
    }
}

@Composable
private fun MemberEditSheet(
    member: Member,
    initialDraftLevel: MemberLevel?,
    initialRoleTitle: String?,
    onDismissRequest: () -> Unit,
    onSave: (Member) -> Unit,
    onRemove: (Member) -> Unit
) {
    val defaultDraftLevel = initialDraftLevel ?: if (
        member.name == "Thu Hà" && member.roleTitle == "CTV Kỹ thuật"
    ) {
        MemberLevel.LEVEL_2
    } else {
        member.level
    }
    val defaultRoleTitle = initialRoleTitle ?: if (
        member.name == "Thu Hà" && member.roleTitle == "CTV Kỹ thuật"
    ) {
        "Trưởng ban Kỹ thuật"
    } else {
        member.roleTitle
    }
    var selectedLevel by remember(member.id, initialDraftLevel) {
        mutableStateOf(defaultDraftLevel)
    }
    var roleTitle by remember(member.id, initialRoleTitle) {
        mutableStateOf(defaultRoleTitle)
    }

    KitBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(member.name, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = member.name,
                        color = TextPrimary,
                        style = DamType.Heading.copy(fontSize = 17.sp)
                    )
                    Text(
                        text = "Tự điền: ${member.roleTitle} · Cấp ${member.level.code}",
                        color = TextSecondary,
                        style = DamType.Small
                    )
                }
            }
            Text("Cấp", color = TextSecondary, style = DamType.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemberLevel.entries.forEach { level ->
                    val isSelected = selectedLevel == level
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) TextPrimary else GlassBg)
                            .border(
                                1.dp,
                                if (isSelected) TextPrimary else GlassBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { selectedLevel = level }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cấp ${level.code}",
                            color = if (isSelected) ChipInk else TextPrimary,
                            style = DamType.Chip
                        )
                    }
                }
            }
            Text(
                text = "1 Tổng đạo diễn · 2 Trưởng ban · 3 Cộng tác viên",
                color = TextSecondary,
                style = DamType.Small
            )
            GlassTextField(
                value = roleTitle,
                onValueChange = { roleTitle = it },
                label = "Chức vụ",
                placeholder = "Nhập chức vụ"
            )
            PrimaryButton(
                text = "Lưu thay đổi",
                onClick = { onSave(member.copy(level = selectedLevel, roleTitle = roleTitle)) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Xóa khỏi sự kiện",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRemove(member) }
                    .padding(vertical = 2.dp),
                color = DangerText,
                style = DamType.Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
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
private fun MultiLevelConnectionScreenPreview() {
    DamThoaiTheme {
        MultiLevelConnectionContent(
            onBack = {},
            internetEnabled = true,
            localNetworkEnabled = true,
            bluetoothDirectEnabled = false,
            onInternetChange = {},
            onLocalNetworkChange = {},
            onBluetoothDirectChange = {}
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MultiLevelConnectionDisabledPreview() {
    DamThoaiTheme {
        MultiLevelConnectionContent(
            onBack = {},
            internetEnabled = false,
            localNetworkEnabled = false,
            bluetoothDirectEnabled = true,
            onInternetChange = {},
            onLocalNetworkChange = {},
            onBluetoothDirectChange = {}
        )
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
        EventMembersContent(
            onBack = {},
            uiState = EventMembersUiState()
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun EventMembersEditScreenPreview() {
    DamThoaiTheme {
        EventMembersContent(
            onBack = {},
            uiState = EventMembersUiState(),
            selectedMember = FakeRemainingUiData.eventMembers.first { it.name == "Thu Hà" },
            initialDraftLevel = MemberLevel.LEVEL_2,
            initialRoleTitle = "Trưởng ban Kỹ thuật"
        )
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
