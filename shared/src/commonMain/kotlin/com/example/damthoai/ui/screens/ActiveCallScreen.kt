package com.example.damthoai.ui.screens

import com.example.damthoai.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.damthoai.network.SignalingConnectionState
import com.example.damthoai.service.CallState
import com.example.damthoai.service.DamThoaiCallManager
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.damthoai.data.CallContent
import com.example.damthoai.data.LiveCue
import com.example.damthoai.data.NetworkStatus
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.PttSource
import com.example.damthoai.hardware.audio.AudioRoute
import com.example.damthoai.hardware.audio.AudioRouteGuard
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.BgVariant
import com.example.damthoai.ui.components.CallBottomActions
import com.example.damthoai.ui.components.CallStatusBanner
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.LiveCueCard
import com.example.damthoai.ui.components.PttButton
import com.example.damthoai.ui.components.StatusPill
import com.example.damthoai.ui.components.VolumeRow
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentMint
import com.example.damthoai.ui.theme.AccentRed
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
import com.example.damthoai.ui.theme.GlassBg
import com.example.damthoai.ui.theme.TagRedBg
import com.example.damthoai.ui.theme.TextPrimary
import com.example.damthoai.ui.theme.TextSecondary

sealed class CallUiState {
    object CallIdle : CallUiState()
    object CallTalk : CallUiState()
    object CallTake : CallUiState()
    object CallBusy : CallUiState()
    object CallCut : CallUiState()
    object CallOffline : CallUiState()
}

@Composable
fun ActiveCallScreen(
    onNavigateToPocketMode: () -> Unit,
    modifier: Modifier = Modifier,
    state: CallUiState = CallUiState.CallIdle,
    onBack: () -> Unit = {},
    onNavigateToEmergency: () -> Unit = {},
    networkStatusOverride: NetworkStatus? = null,
    // Kênh và âm lượng lấy từ ChannelViewModel dùng chung: kéo ở đây thì màn danh sách kênh cũng đổi theo
    channels: List<com.example.damthoai.ui.channels.ChannelItemUiState> = emptyList(),
    activeChannelId: String? = null,
    onChannelVolumeChange: (channelId: String, volume: Float) -> Unit = { _, _ -> }
) {
    val callManager = remember { DamThoaiCallManager.getInstance() }
    val connectionState by callManager.signalingClient.connectionState.collectAsState()
    val callState by callManager.callState.collectAsState()
    val eventName by callManager.eventName.collectAsState()
    val onlineMembers by callManager.onlineMembers.collectAsState()

    val observedNetworkStatus = when (connectionState) {
        is SignalingConnectionState.Connected,
        is SignalingConnectionState.Registered -> NetworkStatus.CONNECTED
        else -> NetworkStatus.Offline
    }
    val networkStatus = networkStatusOverride ?: observedNetworkStatus
    val peerStatusText = when (val current = callState) {
        is CallState.InCall -> {
            val peerName = onlineMembers.firstOrNull { it.userId == current.peerUserId }
                ?.displayName?.takeIf { it.isNotBlank() } ?: current.peerUserId
            "Đã kết nối với $peerName"
        }
        is CallState.Calling -> "Đang kết nối âm thanh..."
        else -> if (onlineMembers.size <= 1) "Chờ thành viên khác vào kênh" else "Đang chờ kết nối"
    }

    val liveCue: LiveCue? = null

    val isScreenPttPressed = remember { mutableStateOf(false) }
    val displayState = if (state == CallUiState.CallIdle && isScreenPttPressed.value) {
        CallUiState.CallTalk
    } else {
        state
    }

    val content = CallContent(
        title = "Kênh đàm thoại",
        channels = emptyList()
    )
    val currentAudioRoute by AudioRouteGuard.currentRoute.collectAsState()
    val availableAudioRoutes by AudioRouteGuard.availableRoutes.collectAsState()
    val headsetRoute = when {
        AudioRoute.HEADSET in availableAudioRoutes -> AudioRoute.HEADSET
        AudioRoute.BLUETOOTH in availableAudioRoutes -> AudioRoute.BLUETOOTH
        else -> null
    }
    var queuedCount by remember { mutableStateOf(1) }

    DamThoaiBackground(modifier = modifier, variant = BgVariant.Default) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )  {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = maxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CallHeader(
                            onBack = onBack,
                            networkStatus = networkStatus,
                            title = eventName,
                            subtitle = peerStatusText
                        )
                        liveCue?.let { LiveCueCard(cue = it) }
                        CallStatusBanner(
                            state = displayState,
                            title = content.statusTitle,
                            description = content.statusDescription,
                            iconRes = content.statusIconRes ?: Res.drawable.ic_vol,
                            color = content.statusColor,
                            backgroundColor = content.statusBackground,
                            queuedCount = queuedCount
                        )
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "ÂM LƯỢNG TỪNG KÊNH",
                                    color = TextSecondary,
                                    style = DamType.Label
                                )
                                if (channels.isEmpty()) {
                                    Text(
                                        text = "Chưa có kênh nào.",
                                        color = TextSecondary,
                                        style = DamType.Small
                                    )
                                }
                                channels.forEach { item ->
                                    VolumeRow(
                                        channel = item.channel,
                                        volume = item.volume,
                                        tag = when {
                                            item.isMuted -> "TẮT TIẾNG"
                                            item.channel.id == activeChannelId -> "ĐANG NGHE · ${(item.volume * 100).toInt()}%"
                                            else -> null
                                        },
                                        onVolumeChange = { newValue ->
                                            onChannelVolumeChange(item.channel.id, newValue)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PttButton(
                            size = 140.dp,
                            state = displayState,
                            modifier = Modifier.heightIn(min = 120.dp),
                            onPress = {
                                PttController.onKeyPressed(PttSource.SCREEN_BUTTON)
                                isScreenPttPressed.value = (PttController.activeSource.value == PttSource.SCREEN_BUTTON)
                            },
                            onRelease = { wasReleased ->
                                PttController.onKeyReleased(PttSource.SCREEN_BUTTON)
                                // SỬA TRIỆT ĐỂ: Dùng .value để gán thay vì gán trực tiếp biến val
                                isScreenPttPressed.value = false
                                if (wasReleased && state == CallUiState.CallOffline) queuedCount += 1
                            }
                        )
                    }
                }
            }
            CallBottomActions(
                onNavigateToPocket = onNavigateToPocketMode,
                onNavigateToEmergency = onNavigateToEmergency,
                speakerSelected = currentAudioRoute == AudioRoute.SPEAKER,
                headsetSelected = currentAudioRoute == AudioRoute.HEADSET ||
                        currentAudioRoute == AudioRoute.BLUETOOTH,
                headsetEnabled = headsetRoute != null,
                // Bấm để bật loa ngoài; bấm lần nữa để chuyển về tai nghe (nếu đang cắm) hoặc loa trong
                onSelectSpeaker = {
                    if (currentAudioRoute == AudioRoute.SPEAKER) {
                        AudioRouteGuard.userSelectRoute(headsetRoute ?: AudioRoute.EARPIECE)
                    } else {
                        AudioRouteGuard.userSelectRoute(AudioRoute.SPEAKER)
                    }
                },
                onSelectHeadset = {
                    headsetRoute?.let(AudioRouteGuard::userSelectRoute)
                }
            )
        }
    }
}

@Composable
private fun CallHeader(
    onBack: () -> Unit,
    networkStatus: NetworkStatus,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        BackButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                color = TextPrimary,
                style = DamType.Heading,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = TextSecondary,
                style = DamType.Small
            )
        }
        val (text, color, background) = when (networkStatus) {
            is NetworkStatus.Good -> Triple("${networkStatus.ms} ms", AccentMint, GlassBg)
            is NetworkStatus.Weak -> Triple("${networkStatus.ms} ms", AccentAmber, GlassBg)
            NetworkStatus.Offline -> Triple("Offline", AccentRed, TagRedBg)
        }
        StatusPill(
            text = text,
            modifier = Modifier.align(Alignment.CenterEnd),
            statusColor = color,
            backgroundColor = background,
            textColor = color,
            horizontalPadding = 9.dp,
            verticalPadding = 5.dp,
            textStyle = DamType.Small11Bold
        )
    }
}

@Preview
@Composable
private fun ActiveCallSmallScreenPreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallIdle) }
}

@Preview
@Composable
private fun CallIdlePreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallIdle) }
}

@Preview
@Composable
private fun CallTalkPreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallTalk) }
}

@Preview
@Composable
private fun CallTakePreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallTake) }
}

@Preview
@Composable
private fun CallBusyPreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallBusy) }
}

@Preview
@Composable
private fun CallCutPreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallCut) }
}

@Preview
@Composable
private fun CallOfflinePreview() {
    DamThoaiTheme {
        ActiveCallScreen(
            onNavigateToPocketMode = {},
            state = CallUiState.CallOffline,
            networkStatusOverride = NetworkStatus.Offline
        )
    }
}