package com.example.damthoai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.damthoai.R
import com.example.damthoai.data.fake.FakeCallData
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.PttSource
import com.example.damthoai.ui.components.BackButton
import com.example.damthoai.ui.components.BgVariant
import com.example.damthoai.ui.components.CallBottomActions
import com.example.damthoai.ui.components.CallStatusBanner
import com.example.damthoai.ui.components.DamThoaiBackground
import com.example.damthoai.ui.components.GlassCard
import com.example.damthoai.ui.components.PttButton
import com.example.damthoai.ui.components.SectionLabel
import com.example.damthoai.ui.components.StatusPill
import com.example.damthoai.ui.components.VolumeRow
import com.example.damthoai.ui.theme.AccentAmber
import com.example.damthoai.ui.theme.AccentCyan
import com.example.damthoai.ui.theme.CallStatusOffline
import com.example.damthoai.ui.theme.DamThoaiTheme
import com.example.damthoai.ui.theme.DamType
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
    onNavigateToEmergency: () -> Unit = {}
) {
    var isScreenPttPressed by remember(state) { mutableStateOf(false) }
    val displayState = if (state == CallUiState.CallIdle && isScreenPttPressed) {
        CallUiState.CallTalk
    } else {
        state
    }
    val content = FakeCallData.contentFor(displayState)
    var queuedCount by remember { mutableStateOf(1) }
    var volumes by remember(state) {
        mutableStateOf(content.channels.map { channel -> channel.volume })
    }

    DamThoaiBackground(modifier = modifier, variant = BgVariant.Default) {
        // Tuần 3: sửa - giữ nút thao tác cố định, chỉ cuộn phần thông tin khi thiếu chiều cao.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tuần 3: sửa - cuộn phần thông tin độc lập, không cuộn hàng nút đáy.
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CallHeader(onBack = onBack, state = displayState)
                LiveCueCard()
                CallStatusBanner(
                    state = displayState,
                    title = content.statusTitle,
                    description = content.statusDescription,
                    iconRes = content.statusIconRes,
                    color = content.statusColor,
                    backgroundColor = content.statusBackground,
                    queuedCount = queuedCount
                )
                SectionLabel(text = "Âm lượng từng kênh")
                // Tuần 3: sửa - khoảng cách giữa các hàng âm lượng theo thiết kế.
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content.channels.forEachIndexed { index, channel ->
                        VolumeRow(channel = channel.copy(volume = volumes[index])) { newValue ->
                            volumes = volumes.toMutableList().also { it[index] = newValue }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Tuần 3: sửa - PTT chiếm phần chiều cao còn lại ngoài vùng cuộn.
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                PttButton(
                    size = 140.dp,
                    state = displayState,
                    // Tuần 3: sửa - giữ vùng nút PTT tối thiểu 120dp.
                    modifier = Modifier.heightIn(min = 120.dp),
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
            // Tuần 3: sửa - hàng bốn nút nằm ngoài vùng cuộn để luôn hiện.
            CallBottomActions(
                onNavigateToPocket = onNavigateToPocketMode,
                onNavigateToEmergency = onNavigateToEmergency
            )
        }
    }
}

@Composable
private fun CallHeader(onBack: () -> Unit, state: CallUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BackButton(onClick = onBack)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Hậu cần – An ninh", color = TextPrimary, style = DamType.Heading)
            Text(
                "Bạn đang nói ở kênh này",
                color = TextSecondary,
                style = DamType.Small
            )
        }
        if (state == CallUiState.CallOffline) {
            StatusPill(text = "Offline", statusColor = CallStatusOffline)
        } else {
            StatusPill(text = "42 ms", statusColor = AccentCyan)
        }
    }
}

@Composable
private fun LiveCueCard() {
    // Tuần 3: sửa - dùng nguyên các cỡ chữ định nghĩa trong DamType.
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp,
            vertical = 9.dp
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AccentAmber.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stage),
                    contentDescription = null,
                    tint = AccentAmber,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Live Cue · đang diễn",
                    color = AccentAmber,
                    style = DamType.Label
                )
                Text(
                    "Tiết mục 4 – Ban nhạc khách mời",
                    color = TextPrimary,
                    style = DamType.Heading
                )
                Text(
                    "Hậu cần: chuẩn bị đạo cụ chuyển cảnh",
                    color = TextSecondary,
                    style = DamType.Small
                )
            }
        }
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
    DamThoaiTheme { ActiveCallScreen(onNavigateToPocketMode = {}, state = CallUiState.CallOffline) }
}
