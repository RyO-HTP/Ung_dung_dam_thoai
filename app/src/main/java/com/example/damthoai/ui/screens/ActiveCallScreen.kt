package com.example.damthoai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.damthoai.R
import com.example.damthoai.data.fake.FakeCallData
import com.example.damthoai.data.fake.FakeRemainingUiData
import com.example.damthoai.data.fake.NetworkStatus
import com.example.damthoai.network.NetworkStatusProvider
import com.example.damthoai.data.SpeakingStateProvider
import com.example.damthoai.data.SpeakerInfo
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
    networkStatusOverride: NetworkStatus? = null
) {
    val observedNetworkStatus by NetworkStatusProvider.source.status.collectAsState()
    val networkStatus = networkStatusOverride ?: observedNetworkStatus
    val speakingByChannel by SpeakingStateProvider.source.speakingByChannel.collectAsState()
    val liveCue by FakeRemainingUiData.liveCueState.collectAsState()
    var isScreenPttPressed by remember(state) { mutableStateOf(false) }
    val baseState = if (networkStatus is NetworkStatus.Offline && state == CallUiState.CallIdle) {
        CallUiState.CallOffline
    } else {
        state
    }
    val displayState = if (baseState == CallUiState.CallIdle && isScreenPttPressed) {
        CallUiState.CallTalk
    } else {
        baseState
    }
    val content = FakeCallData.contentFor(displayState)
    val currentAudioRoute by AudioRouteGuard.currentRoute.collectAsState()
    val availableAudioRoutes by AudioRouteGuard.availableRoutes.collectAsState()
    val headsetRoute = when {
        AudioRoute.HEADSET in availableAudioRoutes -> AudioRoute.HEADSET
        AudioRoute.BLUETOOTH in availableAudioRoutes -> AudioRoute.BLUETOOTH
        else -> null
    }
    var queuedCount by remember { mutableStateOf(1) }
    var volumes by remember(state) {
        mutableStateOf(content.channels.map { channel -> channel.volume })
    }

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
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = maxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CallHeader(onBack = onBack, networkStatus = networkStatus)
                        liveCue?.let { LiveCueCard(cue = it) }
                        CallStatusBanner(
                            state = displayState,
                            title = content.statusTitle,
                            description = content.statusDescription,
                            iconRes = content.statusIconRes,
                            color = content.statusColor,
                            backgroundColor = content.statusBackground,
                            queuedCount = queuedCount
                        )
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                Text(
                                    text = "ÂM LƯỢNG TỪNG KÊNH",
                                    color = TextSecondary,
                                    style = DamType.Label
                                )
                                content.channels.forEachIndexed { index, channel ->
                                    val speaker = speakingByChannel[channel.name]
                                    VolumeRow(
                                        channel = channel.copy(volume = volumes[index]),
                                        speakerInfo = speaker,
                                        onVolumeChange = { newValue ->
                                            volumes = volumes.toMutableList().also {
                                                it[index] = newValue
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PttButton(
                            size = 140.dp,
                            state = displayState,
                            modifier = Modifier,
                            onPress = {
                                PttController.onKeyPressed(PttSource.SCREEN_BUTTON)
                                isScreenPttPressed =
                                    PttController.activeSource.value == PttSource.SCREEN_BUTTON
                            },
                            onRelease = { wasReleased ->
                                PttController.onKeyReleased(PttSource.SCREEN_BUTTON)
                                isScreenPttPressed = false
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
                onSelectSpeaker = { AudioRouteGuard.userSelectRoute(AudioRoute.SPEAKER) },
                onSelectHeadset = {
                    headsetRoute?.let(AudioRouteGuard::userSelectRoute)
                }
            )
        }
    }
}

@Composable
private fun CallHeader(onBack: () -> Unit, networkStatus: NetworkStatus) {
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
            Text("Hậu cần – An ninh", color = TextPrimary, style = DamType.Heading)
            Text(
                "Bạn đang nói ở kênh này",
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

// Tuần 3: sửa - preview kích thước màn hình thấp để kiểm tra hàng nút dưới.
@Preview(widthDp = 360, heightDp = 740, showBackground = true)
@Composable
private fun ActiveCallSmallScreenPreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallIdle) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CallIdlePreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallIdle) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CallTalkPreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallTalk) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CallTakePreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallTake) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CallBusyPreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallBusy) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CallCutPreview() {
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallCut) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
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
