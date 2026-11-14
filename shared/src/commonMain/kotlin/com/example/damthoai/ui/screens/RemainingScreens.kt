package com.example.damthoai.ui.screens

import org.jetbrains.compose.resources.DrawableResource
import com.example.damthoai.ui.components.AppToast
import androidx.compose.foundation.background
import io.ktor.http.encodeURLParameter
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import com.example.damthoai.platform.LocalPlatformUi
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.damthoai.resources.*
import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.data.Channel
import com.example.damthoai.data.Event
import com.example.damthoai.data.Member
import com.example.damthoai.data.MemberLevel
import com.example.damthoai.ui.channels.ChannelItemUiState
import com.example.damthoai.ui.channels.ChannelMenuSheet
import com.example.damthoai.ui.components.AvatarCircle
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.BgVariant
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.DangerButton
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.GlassTextField
import com.example.damthoai.ui.components.IconBox
import com.example.damthoai.ui.components.KitBottomSheet
import com.example.damthoai.ui.components.KitDialog
import com.example.damthoai.ui.components.KitSwitch
import com.example.damthoai.ui.components.LevelTag
import com.example.damthoai.ui.components.PrimaryButton
import com.example.damthoai.ui.components.SecondaryButton
import com.example.damthoai.ui.components.QrCodeImage
import com.example.damthoai.ui.components.TagLabel
import com.example.damthoai.ui.event.EventMembersUiState
import com.example.damthoai.ui.event.EventMembersViewModel
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.ChipInk
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.DangerBrush
import com.example.damthoai.ui.theme.DangerText
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.GlassBorder
import com.example.damthoai.ui.theme.PrimaryBrush
import com.example.damthoai.ui.theme.TagNeutralBg
import com.example.damthoai.ui.theme.TextPlaceholder
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Quản lý trạng thái kết nối đa tầng thực tế
object ConnectionManager {
    private val _internetEnabled = MutableStateFlow(true)
    val internetEnabled: StateFlow<Boolean> = _internetEnabled.asStateFlow()

    private val _localNetworkEnabled = MutableStateFlow(true)
    val localNetworkEnabled: StateFlow<Boolean> = _localNetworkEnabled.asStateFlow()

    private val _bluetoothDirectEnabled = MutableStateFlow(false)
    val bluetoothDirectEnabled: StateFlow<Boolean> = _bluetoothDirectEnabled.asStateFlow()

    fun setInternetEnabled(enabled: Boolean) { _internetEnabled.value = enabled }
    fun setLocalNetworkEnabled(enabled: Boolean) { _localNetworkEnabled.value = enabled }
    fun setBluetoothDirectEnabled(enabled: Boolean) { _bluetoothDirectEnabled.value = enabled }
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
            Text(member.name, color = TextPrimary, style = DamType.Body14SemiBold)
            Text(member.roleTitle, color = TextSecondary, style = DamType.Small)
        }
        LevelTag(member.level)
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(Res.drawable.ic_chev),
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
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
                channel = Channel(id = "ch_01", name = "Toàn sự kiện"),
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
    val internetEnabled by ConnectionManager.internetEnabled.collectAsStateWithLifecycle()
    val localNetworkEnabled by ConnectionManager.localNetworkEnabled.collectAsStateWithLifecycle()
    val bluetoothDirectEnabled by ConnectionManager.bluetoothDirectEnabled.collectAsStateWithLifecycle()

    MultiLevelConnectionContent(
        onBack = onBack,
        modifier = modifier,
        internetEnabled = internetEnabled,
        localNetworkEnabled = localNetworkEnabled,
        bluetoothDirectEnabled = bluetoothDirectEnabled,
        onInternetChange = ConnectionManager::setInternetEnabled,
        onLocalNetworkChange = ConnectionManager::setLocalNetworkEnabled,
        onBluetoothDirectChange = ConnectionManager::setBluetoothDirectEnabled
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
    var signalingUrl by remember { mutableStateOf(AppStartPrefs.getManualServerUrl()) }

    ScreenFrame("Kết nối đa tầng", onBack, modifier) {
        Text(
            text = "App tự chuyển giữa các tầng khi mạng yếu. Bạn chỉ cần bật hoặc tắt từng tầng.",
            color = TextSecondary,
            style = DamType.Body13Line19_5
        )
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Địa chỉ Server nội bộ (WebSocket)", color = TextPrimary, style = DamType.Heading)
                Text("App tự kết nối máy chủ đám mây (dùng được cả Wi-Fi và 4G). Chỉ nhập khi muốn dùng server riêng (vd: ws://192.168.1.8:8765); để trống để tự động.", color = TextSecondary, style = DamType.Small)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = signalingUrl,
                        onValueChange = { signalingUrl = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GlassBg)
                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        singleLine = true,
                        textStyle = DamType.Body16SemiBold.copy(color = TextPrimary),
                        cursorBrush = SolidColor(AccentCyan)
                    )
                    PrimaryButton(
                        text = "Lưu",
                        onClick = {
                            // Nhập địa chỉ = dùng riêng server đó; để trống = tự động (máy chủ đám mây/LAN)
                            com.example.damthoai.service.DamThoaiCallManager.getInstance().setServerUrl(signalingUrl)
                            signalingUrl = AppStartPrefs.getManualServerUrl()
                            AppToast.show("Đã lưu địa chỉ Server!")
                        },
                        height = 44.dp
                    )
                }
            }
        }
        ConnectionLayerCard(
            iconRes = Res.drawable.ic_radio,
            title = "Internet",
            status = if (internetEnabled) "ĐANG DÙNG" else "TẮT",
            statusColor = if (internetEnabled) AccentCyan else TextSecondary,
            statusBackground = if (internetEnabled) null else TagNeutralBg,
            description = "4G/5G, Wi-Fi qua máy chủ SFU · 42 ms",
            checked = internetEnabled,
            onCheckedChange = onInternetChange
        )
        ConnectionLayerCard(
            iconRes = Res.drawable.ic_wifi,
            title = "Mạng nội bộ",
            status = if (localNetworkEnabled) "SẴN SÀNG" else "TẮT",
            statusColor = TextSecondary,
            statusBackground = TagNeutralBg,
            description = "Wi-Fi hoặc hotspot, tự tìm máy (mDNS) · thấy 12 máy",
            checked = localNetworkEnabled,
            onCheckedChange = onLocalNetworkChange
        )
        ConnectionLayerCard(
            iconRes = Res.drawable.ic_bt,
            title = "Bluetooth LE / Wi-Fi Direct",
            description = "Ngang hàng 30–100 m khi không có mạng",
            checked = bluetoothDirectEnabled,
            onCheckedChange = onBluetoothDirectChange
        )
    }
}

@Composable
private fun ConnectionLayerCard(
    iconRes: DrawableResource,
    title: String,
    description: String,
    status: String? = null,
    statusColor: androidx.compose.ui.graphics.Color = TextSecondary,
    statusBackground: androidx.compose.ui.graphics.Color? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
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
private fun ConnectionLayerIcon(iconRes: DrawableResource) {
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
    event: Event = Event(id = "default", name = "Sự kiện", pinCode = "240826", channels = emptyList(), members = emptyList()),
    channels: List<Channel> = emptyList(),
    onChannelQrCode: (String) -> Unit = { onQrCode() },
    onNavigateToActiveCall: () -> Unit = {},
    onAddChannel: (String) -> Unit = {},
    onRenameChannel: (String, String) -> Unit = { _, _ -> },
    onDeleteChannel: (String) -> Unit = {}
) {
    ManageEventContent(
        onBack = onBack,
        onQrCode = onQrCode,
        onChannelQrCode = onChannelQrCode,
        onMembers = onMembers,
        onLiveCue = onLiveCue,
        onEndEvent = onEndEvent,
        event = event,
        channels = channels.ifEmpty { event.channels },
        modifier = modifier,
        onNavigateToActiveCall = onNavigateToActiveCall,
        onAddChannel = onAddChannel,
        onRenameChannel = onRenameChannel,
        onDeleteChannel = onDeleteChannel
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
    onNavigateToActiveCall: () -> Unit = {},
    onAddChannel: (String) -> Unit = {},
    onRenameChannel: (String, String) -> Unit = { _, _ -> },
    onDeleteChannel: (String) -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var showAdminCode by rememberSaveable { mutableStateOf(false) }
    var showEndEventDialog by rememberSaveable { mutableStateOf(false) }
    var channelMenuId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddChannelDialog by rememberSaveable { mutableStateOf(false) }
    var renameChannelTarget by rememberSaveable { mutableStateOf<Channel?>(null) }

    val selectedMenuChannel = channels.firstOrNull { it.id == channelMenuId }
    val channelToDelete = channels.firstOrNull { it.id == deleteChannelId }
    // Chỉ hiện "Bạn là Cấp 1" khi người đang xem đúng là ban tổ chức
    val eventOwner = event.members.firstOrNull { it.id == event.ownerId }?.takeIf { event.isOwner }

    ScreenFrame("Quản lý sự kiện", onBack, modifier, bottomPadding = 26.dp) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(event.name, color = TextPrimary, style = DamType.Heading)
                Text(
                    eventOwner?.let { "Bạn là Cấp ${it.level.code} · quyền cao nhất" } ?: "Quản lý sự kiện",
                    color = TextSecondary,
                    style = DamType.Small
                )
                val code = event.adminCode
                if (!code.isNullOrBlank()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(GlassBorder.copy(alpha = 0.5f)))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Mã quản trị: ", color = TextSecondary, style = DamType.Small)
                            Text(
                                text = if (showAdminCode) code else "••••••••",
                                color = AccentCyan,
                                style = DamType.Mono.copy(fontSize = 13.sp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).clickable { showAdminCode = !showAdminCode },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(if (showAdminCode) Res.drawable.ic_x else Res.drawable.ic_eye),
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Box(
                                modifier = Modifier.size(36.dp).clickable { clipboardManager.setText(AnnotatedString(code)) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_copy),
                                    contentDescription = null,
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
                    painter = painterResource(if (channel.isDefault || channel.isPriority) Res.drawable.ic_crown else Res.drawable.ic_radio),
                    contentDescription = null,
                    tint = if (channel.isDefault || channel.isPriority) AccentAmber else AccentCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(channel.name, color = TextPrimary, style = DamType.Body)
                }
                IconBox(
                    iconRes = Res.drawable.ic_dots,
                    contentDescription = null,
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
                .border(1.dp, GlassBorder, RoundedCornerShape(22.dp))
                .clickable { showAddChannelDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "+ Thêm phòng ban / kênh mới",
                color = TextSecondary,
                style = DamType.Body13Bold
            )
        }
        ActionRow(Res.drawable.ic_qr, "Mã QR của ban", "Chia sẻ quyền vào nhanh", onQrCode)
        ActionRow(Res.drawable.ic_users, "Thành viên · ${event.members.size} người", "Quản lý cấp và chức vụ", onMembers)
        ActionRow(Res.drawable.ic_zap, "Live Cue", "Quản lý nội dung nhắc việc", onLiveCue, "ƯU TIÊN")
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
            primaryBrush = DangerBrush
        )
    }

    if (deleteChannelId != null) {
        KitDialog(
            title = "Xoá phòng ban?",
            description = "Bạn có chắc chắn muốn xoá phòng ban này?",
            primaryButtonText = "Xoá",
            secondaryButtonText = "Hủy",
            onPrimaryClick = {
                deleteChannelId?.let { onDeleteChannel(it) }
                deleteChannelId = null
            },
            onDismissRequest = { deleteChannelId = null },
            primaryBrush = DangerBrush
        )
    }

    if (showAddChannelDialog) {
        ChannelInputDialog(
            title = "Thêm phòng ban / kênh mới",
            onDismiss = { showAddChannelDialog = false },
            onSubmit = { name ->
                onAddChannel(name)
                showAddChannelDialog = false
            }
        )
    }

    renameChannelTarget?.let { channel ->
        ChannelInputDialog(
            title = "Đổi tên phòng ban",
            initialValue = channel.name,
            onDismiss = { renameChannelTarget = null },
            onSubmit = { newName ->
                onRenameChannel(channel.id, newName)
                renameChannelTarget = null
            }
        )
    }

    selectedMenuChannel?.let { channel ->
        KitBottomSheet(onDismissRequest = { channelMenuId = null }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Tùy chọn kênh", color = TextPrimary, style = DamType.Heading)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            renameChannelTarget = channel
                            channelMenuId = null
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("Đổi tên phòng ban", color = TextPrimary, style = DamType.Body)
                }
                if (!channel.isDefault) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                deleteChannelId = channel.id
                                channelMenuId = null
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Xoá phòng ban", color = DangerText, style = DamType.Body)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelInputDialog(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(com.example.damthoai.ui.theme.DialogBg)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, color = TextPrimary, style = DamType.Heading16)
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassBg)
                    .border(1.dp, AccentCyan, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                singleLine = true,
                textStyle = DamType.Body16SemiBold.copy(color = TextPrimary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentCyan)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecondaryButton(
                    text = "Hủy",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    height = 46.dp
                )
                PrimaryButton(
                    text = "Lưu",
                    onClick = {
                        if (text.isNotBlank()) {
                            onSubmit(text)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = text.isNotBlank(),
                    height = 46.dp
                )
            }
        }
    }
}

@Composable
fun MyQrCodeScreen(
    onBack: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    event: Event = Event(id = "default", name = "Sự kiện", pinCode = "240826", channels = emptyList(), members = emptyList()),
    channel: Channel? = null
) {
    val platformUi = LocalPlatformUi.current
    val clipboardManager = LocalClipboardManager.current
    val currentMember = event.members.firstOrNull { it.id == event.ownerId } ?: event.members.firstOrNull()
    val userLevel = currentMember?.level ?: MemberLevel.LEVEL_1

    if (userLevel == MemberLevel.LEVEL_3) {
        LaunchedEffect(Unit) {
            AppToast.show("Cấp 3 không được phép truy cập màn hình này.")
            onBack()
        }
        return
    }

    val channels = event.channels.ifEmpty { listOf(Channel(id = "ch_01", name = "Toàn sự kiện")) }
    var selectedChannelId by rememberSaveable { mutableStateOf(channel?.id ?: channels.first().id) }
    val selectedChannel = channels.firstOrNull { it.id == selectedChannelId } ?: channels.first()

    ScreenFrame("Mã QR của ban", onBack, modifier) {
        if (channels.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                    textAlign = TextAlign.Center
                )

                val currentPin = AppStartPrefs.getEventPin().ifBlank { event.pinCode }
                val encodedName = event.name.encodeURLParameter(spaceToPlus = true)
                val qrContent = selectedChannel.qrPayload.ifBlank {
                    "damthoai://join?channel=${selectedChannel.id}&pin=$currentPin&name=$encodedName"
                }
                QrCodeImage(
                    content = qrContent,
                    size = 200.dp,
                    contentDescription = null
                )

                Text("MÃ PIN", color = TextSecondary, style = DamType.Small.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Text(
                    text = selectedChannel.pin.ifBlank { currentPin },
                    color = AccentCyan,
                    style = DamType.PinBig
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(painter = painterResource(Res.drawable.ic_copy), contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sao chép", color = AccentCyan, style = DamType.Button.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
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
                        platformUi.shareText(link, "Chia sẻ mã QR kênh")
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(painter = painterResource(Res.drawable.ic_share), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Chia sẻ", color = TextPrimary, style = DamType.Button.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
fun EventMembersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventMembersViewModel = viewModel { EventMembersViewModel() }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                    Text(text = "Thành viên", color = TextPrimary, style = DamType.Heading18)
                    Text(text = "${uiState.members.size} người trong sự kiện", color = TextSecondary, style = DamType.Small)
                }
            }
            Spacer(Modifier.height(10.dp))
            com.example.damthoai.ui.components.ServerStatusBanner()
            Spacer(Modifier.height(10.dp))
            MemberSearchField(value = uiState.searchQuery, onValueChange = onSearchQueryChange)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemberFilterChip(text = "Tất cả", selected = uiState.selectedLevel == null, onClick = { onLevelSelected(null) })
                MemberLevel.entries.forEach { level ->
                    MemberFilterChip(text = "Cấp ${level.code}", selected = uiState.selectedLevel == level, onClick = { onLevelSelected(level) })
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
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
                onDismissRequest = onDismissSheet,
                onSave = onSaveMember,
                onRemove = onRemoveMember
            )
        }
    }
}

@Composable
private fun MemberSearchField(value: String, onValueChange: (String) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape).background(GlassBg).border(1.dp, GlassBorder, shape).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(Res.drawable.ic_search), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
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
                        Text("Tìm theo tên hoặc chức vụ", color = TextPlaceholder, style = DamType.Body)
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun MemberFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) TextPrimary else GlassBg
    val foreground = if (selected) ChipInk else TextPrimary
    Box(modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.clip(CircleShape).background(background).border(1.dp, if (selected) TextPrimary else GlassBorder, CircleShape).padding(horizontal = 13.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = foreground, style = DamType.Chip)
        }
    }
}

@Composable
private fun MemberEditSheet(
    member: Member,
    onDismissRequest: () -> Unit,
    onSave: (Member) -> Unit,
    onRemove: (Member) -> Unit
) {
    var selectedLevel by remember(member.id) { mutableStateOf(member.level) }
    var roleTitle by remember(member.id) { mutableStateOf(member.roleTitle) }

    KitBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(member.name, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(member.name, color = TextPrimary, style = DamType.Heading17)
                    Text("Cấp hiện tại: Cấp ${member.level.code}", color = TextSecondary, style = DamType.Small)
                }
            }
            Text("Cấp", color = TextSecondary, style = DamType.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MemberLevel.entries.forEach { level ->
                    val isSelected = selectedLevel == level
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).clickable { selectedLevel = level },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) TextPrimary else GlassBg)
                                .border(1.dp, if (isSelected) TextPrimary else GlassBorder, RoundedCornerShape(14.dp))
                                .padding(horizontal = 10.dp, vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Cấp ${level.code}", color = if (isSelected) ChipInk else TextPrimary, style = DamType.Chip)
                        }
                    }
                }
            }
            GlassTextField(value = roleTitle, onValueChange = { roleTitle = it }, label = "Chức vụ", placeholder = "Nhập chức vụ")
            PrimaryButton(text = "Lưu thay đổi", onClick = { onSave(member.copy(level = selectedLevel, roleTitle = roleTitle)) }, modifier = Modifier.fillMaxWidth())
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onRemove(member) },
                contentAlignment = Alignment.Center
            ) {
                Text("Xóa khỏi sự kiện", color = DangerText, style = DamType.Body13Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun EventEndedScreen(onReturn: () -> Unit, modifier: Modifier = Modifier) {
    ScreenFrame("Sự kiện đã kết thúc", onBack = null, modifier = modifier) {
        Spacer(Modifier.height(28.dp))
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ScreenIcon(Res.drawable.ic_check, tint = AccentAmber, boxSize = 72.dp, iconSize = 34.dp, shape = CircleShape)
                Text("Sự kiện đã kết thúc", color = TextPrimary, style = DamType.Title, textAlign = TextAlign.Center)
            }
        }
        PrimaryButton(text = "Về màn Vào kênh", onClick = onReturn, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun RemovedFromEventScreen(onReturn: () -> Unit, modifier: Modifier = Modifier) {
    ScreenFrame("Bạn đã bị xóa khỏi sự kiện", onBack = null, modifier = modifier) {
        Spacer(Modifier.height(28.dp))
        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ScreenIcon(Res.drawable.ic_del, tint = AccentRed, boxSize = 72.dp, iconSize = 34.dp, shape = CircleShape)
                Text("Bạn đã bị xóa khỏi sự kiện", color = TextPrimary, style = DamType.Title22, textAlign = TextAlign.Center)
                Text("Tổng đạo diễn đã xóa bạn khỏi sự kiện này.", color = TextSecondary, style = DamType.Body, textAlign = TextAlign.Center)
            }
        }
        PrimaryButton(text = "Về màn Vào kênh", onClick = onReturn, modifier = Modifier.fillMaxWidth())
    }
}

@Preview
@Composable
private fun ChannelMenuRouteScreenPreview() {
    DamThoaiTheme { ChannelMenuRouteScreen(false, {}, {}, {}) }
}

@Preview
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

@Preview
@Composable
private fun EventEndedScreenPreview() {
    DamThoaiTheme { EventEndedScreen({}) }
}

@Preview
@Composable
private fun RemovedFromEventScreenPreview() {
    DamThoaiTheme { RemovedFromEventScreen({}) }
}