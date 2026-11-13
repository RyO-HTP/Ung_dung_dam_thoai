package com.example.damthoai.ui.screens

import com.example.damthoai.ui.components.BackButton
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.damthoai.ui.components.QrCodeImage
import com.example.damthoai.ui.theme.PrimaryBrush
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.damthoai.R
import com.example.damthoai.data.model.Channel
import com.example.damthoai.data.fake.ChannelMutationResult
import com.example.damthoai.data.fake.ChannelValidationError
import com.example.damthoai.data.model.Event
import com.example.damthoai.data.fake.FakeEventRepository
import com.example.damthoai.data.fake.FakeMultiLevelConnectionData
import com.example.damthoai.data.fake.FakeRemainingUiData
import com.example.damthoai.data.fake.FakeRepositories
import com.example.damthoai.data.model.Member
import com.example.damthoai.data.model.MemberLevel
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
import com.example.damthoai.ui.components.IconBox
import com.example.damthoai.ui.components.KitBottomSheet
import com.example.damthoai.ui.components.KitDialog
import com.example.damthoai.ui.components.KitSwitch
import com.example.damthoai.ui.components.LevelTag
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SecondaryButton
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.components.TagLabel
import com.example.damthoai.ui.components.dashedBorder
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.ChipInk
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DangerText
import com.example.damthoai.ui.theme.DangerBrush
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PrimaryStart
import com.example.damthoai.ui.theme.SelectedChannelBg
import com.example.damthoai.ui.theme.ChannelAddBorder
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
private fun PageIntro(title: String, subtitle: String? = null) {
    Text(text = title, color = TextPrimary, style = DamType.Heading18)
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
                style = DamType.Body14SemiBold
            )
            Text(
                member.roleTitle,
                color = TextSecondary,
                style = DamType.Small
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
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(background)
                .border(1.dp, borderColor, CircleShape)
                .padding(horizontal = 13.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = TextPrimary, style = DamType.Chip)
        }
    }
}

@Composable
fun ChannelMenuRouteScreen(
    isMuted: Boolean,
    onDismiss: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onLeaveChannel: () -> Unit
) {
    DamThoaiBackground {
        ChannelMenuSheet(
            channelItem = ChannelItemUiState(
                channel = FakeRepositories.sampleChannels[1],
                isMuted = isMuted
            ),
            onDismiss = onDismiss,
            onToggleMute = onToggleMute,
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
            style = DamType.Body13Line19_5
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically
        ) {
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
fun ManageEventScreen(
    onBack: () -> Unit,
    onQrCode: () -> Unit,
    onMembers: () -> Unit,
    onLiveCue: () -> Unit,
    onEndEvent: () -> Unit,
    modifier: Modifier = Modifier,
    event: Event = FakeEventRepository.sampleEvent,
    channels: List<Channel> = FakeEventRepository.sampleEvent.channels,
    onChannelQrCode: (String) -> Unit = { onQrCode() },
    onNavigateToActiveCall: () -> Unit = {}
) {
    val repositoryEvent by FakeEventRepository.currentEvent.collectAsStateWithLifecycle()
    val displayedEvent = repositoryEvent ?: event
    val displayedChannels = repositoryEvent?.channels ?: channels
    ManageEventContent(
        onBack = onBack,
        onQrCode = onQrCode,
        onChannelQrCode = onChannelQrCode,
        onMembers = onMembers,
        onLiveCue = onLiveCue,
        onEndEvent = onEndEvent,
        event = displayedEvent,
        channels = displayedChannels,
        modifier = modifier,
        onNavigateToActiveCall = onNavigateToActiveCall
    )
}

@Composable
private fun ManageEventContent(
    onBack: () -> Unit,
    onQrCode: () -> Unit,
    onChannelQrCode: (String) -> Unit,
    onMembers: () -> Unit,
    onLiveCue: () -> Unit,
    onEndEvent: () -> Unit,
    event: Event,
    channels: List<Channel>,
    modifier: Modifier = Modifier,
    onNavigateToActiveCall: () -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var showAdminCode by rememberSaveable { mutableStateOf(false) }
    var showEndEventDialog by rememberSaveable { mutableStateOf(false) }
    var showAddChannelSheet by rememberSaveable { mutableStateOf(false) }
    var channelMenuId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var channelName by rememberSaveable { mutableStateOf("") }
    var channelError by rememberSaveable { mutableStateOf<ChannelValidationError?>(null) }
    val validationError = FakeEventRepository.validateChannelName(
        channelName,
        excludingChannelId = renameChannelId
    )
    val shownChannelError = channelError
        ?: validationError.takeIf { channelName.isNotBlank() }
    val channelErrorMessage = channelValidationMessage(shownChannelError)
    val selectedMenuChannel = channels.firstOrNull { it.id == channelMenuId }
    val channelToRename = channels.firstOrNull { it.id == renameChannelId }
    val channelToDelete = channels.firstOrNull { it.id == deleteChannelId }
    val eventOwner = event.members.firstOrNull { it.id == event.ownerId }

    ScreenFrame("Quản lý sự kiện", onBack, modifier, bottomPadding = 26.dp) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(event.name, color = TextPrimary, style = DamType.Heading)
                Text(
                    eventOwner?.let { "Bạn là Cấp ${it.level.code} · quyền cao nhất" }
                        ?: "Quản lý sự kiện",
                    color = TextSecondary,
                    style = DamType.Small
                )
                val code = event.adminCode
                if (!code.isNullOrBlank()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(GlassBorder.copy(alpha = 0.5f))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Mã quản trị: ",
                                color = TextSecondary,
                                style = DamType.Small
                            )
                            Text(
                                text = if (showAdminCode) code else "••••••••",
                                color = AccentCyan,
                                style = DamType.Mono.copy(fontSize = 13.sp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { showAdminCode = !showAdminCode },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(if (showAdminCode) R.drawable.ic_x else R.drawable.ic_eye),
                                    contentDescription = if (showAdminCode) "Ẩn mã" else "Hiện mã",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(code))
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_copy),
                                    contentDescription = "Sao chép mã",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
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
                    .clickable { onNavigateToActiveCall() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        if (channel.isDefault || channel.isPriority) {
                            R.drawable.ic_crown
                        } else {
                            R.drawable.ic_radio
                        }
                    ),
                    contentDescription = null,
                    tint = if (channel.isDefault || channel.isPriority) {
                        AccentAmber
                    } else {
                        AccentCyan
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(channel.name, color = TextPrimary, style = DamType.Body)
                    if (channel.isDefault) {
                        Text(
                            "Mọi người tự vào khi vào ban",
                            color = TextSecondary,
                            style = DamType.Small
                        )
                    }
                }
                IconBox(
                    iconRes = R.drawable.ic_dots,
                    contentDescription = "Tùy chọn ${channel.name}",
                    onClick = { channelMenuId = channel.id }
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(GlassBg)
                .dashedBorder(ChannelAddBorder, 1.dp, 22.dp)
                .clickable {
                    channelName = ""
                    channelError = null
                    showAddChannelSheet = true
                },
            contentAlignment = Alignment.Center
        ) {
            Text("+ Thêm kênh", color = TextSecondary, style = DamType.Body13Bold)
        }
        ActionRow(
            R.drawable.ic_qr,
            "Mã QR của ban",
            "Chia sẻ quyền vào nhanh",
            onQrCode
        )
        ActionRow(
            R.drawable.ic_users,
            "Thành viên · ${event.members.size} người",
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
            onClick = { showEndEventDialog = true },
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (showEndEventDialog) {
        KitDialog(
            title = "Kết thúc sự kiện?",
            description = "Mọi người sẽ không thể tiếp tục tham gia sự kiện này.",
            primaryButtonText = "Kết thúc",
            secondaryButtonText = "Ở lại",
            onPrimaryClick = {
                showEndEventDialog = false
                onEndEvent()
            },
            onDismissRequest = { showEndEventDialog = false },
            primaryBrush = com.example.damthoai.ui.theme.DangerBrush
        )
    }
    selectedMenuChannel?.let { channel ->
        KitBottomSheet(onDismissRequest = { channelMenuId = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Tùy chọn kênh", color = TextPrimary, style = DamType.Heading)
                ChannelMenuAction("Đổi tên") {
                    channelName = channel.name
                    channelError = null
                    renameChannelId = channel.id
                    channelMenuId = null
                }
                if (!channel.isDefault) {
                    ChannelMenuAction("Xoá kênh") {
                        deleteChannelId = channel.id
                        channelMenuId = null
                    }
                }
            }
        }
    }
    if (showAddChannelSheet || channelToRename != null) {
        val isRenaming = channelToRename != null
        ChannelNameSheet(
            title = if (isRenaming) "Đổi tên kênh" else "Thêm kênh",
            actionText = if (isRenaming) "Lưu" else "Tạo kênh",
            value = channelName,
            onValueChange = {
                channelName = it
                channelError = null
            },
            errorMessage = channelErrorMessage,
            submitEnabled = validationError == null,
            onDismiss = {
                showAddChannelSheet = false
                renameChannelId = null
                channelError = null
            },
            onSubmit = {
                val result = if (isRenaming) {
                    channelToRename?.let {
                        FakeEventRepository.renameChannel(it.id, channelName)
                    } ?: ChannelMutationResult.Rejected(ChannelValidationError.CHANNEL_NOT_FOUND)
                } else {
                    FakeEventRepository.addChannel(channelName)
                }
                when (result) {
                    is ChannelMutationResult.Success -> {
                        showAddChannelSheet = false
                        renameChannelId = null
                        channelError = null
                    }
                    is ChannelMutationResult.Rejected -> {
                        channelError = result.reason
                    }
                }
            }
        )
    }
    channelToDelete?.let { channel ->
        KitDialog(
            title = "Xoá kênh ${channel.name}?",
            description = "Người trong kênh sẽ bị đưa ra khỏi kênh. Mã QR và PIN của kênh ngừng dùng được.",
            primaryButtonText = "Xoá",
            secondaryButtonText = "Hủy",
            onPrimaryClick = {
                FakeEventRepository.removeChannel(channel.id)
                deleteChannelId = null
            },
            onDismissRequest = { deleteChannelId = null },
            primaryBrush = DangerBrush
        )
    }
}

@Composable
private fun ChannelMenuAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, color = TextPrimary, style = DamType.Body)
    }
}

@Composable
private fun ChannelNameSheet(
    title: String,
    actionText: String,
    value: String,
    onValueChange: (String) -> Unit,
    errorMessage: String?,
    submitEnabled: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    KitBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = TextPrimary, style = DamType.Heading16)
            GlassTextField(
                value = value,
                onValueChange = onValueChange,
                label = "Tên kênh",
                placeholder = "Nhập tên kênh",
                autoFocus = true
            )
            errorMessage?.let { message ->
                Text(message, color = AccentRed, style = DamType.Small)
            }
            PrimaryButton(
                text = actionText,
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = submitEnabled
            )
            SecondaryButton(
                text = "Hủy",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun channelValidationMessage(error: ChannelValidationError?): String? =
    when (error) {
        ChannelValidationError.EMPTY_NAME -> "Tên kênh không được để trống."
        ChannelValidationError.DUPLICATE_NAME -> "Tên kênh đã tồn tại."
        ChannelValidationError.CHANNEL_LIMIT_REACHED -> "Sự kiện chỉ được có tối đa 8 kênh."
        ChannelValidationError.EVENT_NOT_FOUND -> "Không tìm thấy sự kiện."
        ChannelValidationError.CHANNEL_NOT_FOUND -> "Không tìm thấy kênh."
        null -> null
    }

@Composable
fun MyQrCodeScreen(
    onBack: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    event: Event = FakeEventRepository.currentEvent.value ?: FakeRemainingUiData.event,
    channel: Channel? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentEvent = FakeEventRepository.currentEvent.value ?: event
    val currentMember = currentEvent.members.firstOrNull { it.id == currentEvent.ownerId }
        ?: currentEvent.members.firstOrNull()
        ?: FakeRemainingUiData.eventMembers.first { it.level == MemberLevel.LEVEL_1 }
    val userLevel = currentMember.level

    if (userLevel == MemberLevel.LEVEL_3) {
        LaunchedEffect(Unit) {
            android.widget.Toast.makeText(context, "Cấp 3 không được phép truy cập màn hình này.", android.widget.Toast.LENGTH_SHORT).show()
            onBack()
        }
        return
    }

    val allChannels = currentEvent.channels.ifEmpty { FakeRemainingUiData.event.channels }
    val channels = when (userLevel) {
        MemberLevel.LEVEL_1 -> allChannels
        MemberLevel.LEVEL_2 -> allChannels.filter { it.name.contains(currentMember.roleTitle, ignoreCase = true) || it.isDefault }
        else -> emptyList()
    }.ifEmpty { allChannels }

    var selectedChannelId by rememberSaveable { mutableStateOf(channel?.id ?: channels.firstOrNull()?.id ?: "") }
    val selectedChannel = channels.firstOrNull { it.id == selectedChannelId } ?: channels.firstOrNull() ?: allChannels.first()

    ScreenFrame("Mã QR của ban", onBack, modifier) {
        if (channels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                channels.forEach { ch ->
                    val selected = ch.id == selectedChannel.id
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(if (selected) AccentCyan else GlassBg)
                            .border(1.dp, if (selected) AccentCyan else GlassBorder, CircleShape)
                            .clickable { selectedChannelId = ch.id }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ch.name,
                            color = if (selected) TextPrimary else TextSecondary,
                            style = DamType.Small.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Mã vào kênh ${selectedChannel.name}",
                    color = TextPrimary,
                    style = DamType.Heading.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val qrContent = selectedChannel.qrPayload.ifBlank { "damthoai://join?channel=${selectedChannel.id}&pin=${selectedChannel.pin}" }
                QrCodeImage(
                    content = qrContent,
                    size = 200.dp,
                    contentDescription = "Mã QR kênh ${selectedChannel.name}"
                )

                Text(
                    text = "MÃ PIN",
                    color = TextSecondary,
                    style = DamType.Small.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = selectedChannel.pin.ifBlank { currentEvent.pinCode },
                    color = AccentCyan,
                    style = DamType.PinBig
                )
                Text(
                    text = "Quét mã QR hoặc nhập PIN để tham gia kênh ${selectedChannel.name}.",
                    color = TextSecondary,
                    style = DamType.Body,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(GlassBg)
                    .border(1.5.dp, AccentCyan, RoundedCornerShape(18.dp))
                    .clickable {
                        val link = selectedChannel.qrPayload.ifBlank { "damthoai://join?channel=${selectedChannel.id}&pin=${selectedChannel.pin}" }
                        clipboardManager.setText(AnnotatedString(link))
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_copy),
                        contentDescription = "Sao chép liên kết",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Sao chép",
                        color = AccentCyan,
                        style = DamType.Button.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(PrimaryBrush)
                    .clickable {
                        val link = selectedChannel.qrPayload.ifBlank { "damthoai://join?channel=${selectedChannel.id}&pin=${selectedChannel.pin}" }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, link)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ mã QR kênh"))
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = "Chia sẻ",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Chia sẻ",
                        color = TextPrimary,
                        style = DamType.Button.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun EventMembersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventMembersViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val repositoryEvent by FakeEventRepository.currentEvent.collectAsStateWithLifecycle()
    val displayedUiState = repositoryEvent?.let { uiState.copy(members = it.members) } ?: uiState
    var selectedMemberId by remember { mutableStateOf<String?>(null) }
    val selectedMember = displayedUiState.members.firstOrNull { it.id == selectedMemberId }

    EventMembersContent(
        onBack = onBack,
        modifier = modifier,
        uiState = displayedUiState,
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
                        style = DamType.Heading18
                    )
                    Text(
                        text = "${uiState.members.size} người trong sự kiện",
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
            textStyle = DamType.Body.copy(color = TextPrimary),
            cursorBrush = SolidColor(AccentCyan),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "Tìm theo tên hoặc chức vụ",
                            color = TextPlaceholder,
                            style = DamType.Body
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
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(background)
                .border(1.dp, if (selected) TextPrimary else GlassBorder, CircleShape)
                .padding(horizontal = 13.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = foreground, style = DamType.Chip)
        }
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
                        style = DamType.Heading17
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
                            .heightIn(min = 48.dp)
                            .clickable { selectedLevel = level },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) TextPrimary else GlassBg)
                                .border(
                                    1.dp,
                                    if (isSelected) TextPrimary else GlassBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 11.dp),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onRemove(member) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Xóa khỏi sự kiện",
                    color = DangerText,
                    style = DamType.Body13Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
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
                    style = DamType.Title22,
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
        ChannelMenuRouteScreen(false, {}, {}, {})
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
private fun ManageEventScreenPreview() {
    DamThoaiTheme {
        ManageEventScreen({}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ManageEventNewEventPreview() {
    val owner = FakeRepositories.sampleMembers.first { it.level == MemberLevel.LEVEL_1 }
    val channel = FakeEventRepository.sampleEvent.channels.first()
    val event = FakeEventRepository.sampleEvent.copy(
        name = "Sự kiện mới",
        channels = listOf(channel),
        members = listOf(owner),
        ownerId = owner.id
    )
    DamThoaiTheme {
        ManageEventContent(
            onBack = {},
            onQrCode = {},
            onChannelQrCode = {},
            onMembers = {},
            onLiveCue = {},
            onEndEvent = {},
            event = event,
            channels = event.channels
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ManageEventFullPreview() {
    val channels = FakeRepositories.sampleChannels + listOf(
        Channel(id = "preview_ch_04", name = "Kỹ thuật"),
        Channel(id = "preview_ch_05", name = "Đón tiếp")
    )
    val members = List(48) { index ->
        Member(
            id = "preview_member_$index",
            name = "Thành viên ${index + 1}",
            roleTitle = "Thành viên",
            initials = "TV",
            level = MemberLevel.entries[index % MemberLevel.entries.size]
        )
    }
    val event = FakeEventRepository.sampleEvent.copy(
        name = "Sự kiện mẫu đầy đủ",
        channels = channels,
        members = members,
        ownerId = members.first().id
    )
    DamThoaiTheme {
        ManageEventContent(
            onBack = {},
            onQrCode = {},
            onChannelQrCode = {},
            onMembers = {},
            onLiveCue = {},
            onEndEvent = {},
            event = event,
            channels = channels
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun AddChannelSheetPreview() {
    DamThoaiTheme {
        ChannelNameSheet(
            title = "Thêm kênh",
            actionText = "Tạo kênh",
            value = "Hậu cần",
            onValueChange = {},
            errorMessage = null,
            submitEnabled = true,
            onDismiss = {},
            onSubmit = {}
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DeleteChannelDialogPreview() {
    DamThoaiTheme {
        KitDialog(
            title = "Xoá kênh Hậu cần?",
            description = "Người trong kênh sẽ bị đưa ra khỏi kênh. Mã QR và PIN của kênh ngừng dùng được.",
            primaryButtonText = "Xoá",
            secondaryButtonText = "Hủy",
            onPrimaryClick = {},
            onDismissRequest = {},
            primaryBrush = DangerBrush
        )
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
