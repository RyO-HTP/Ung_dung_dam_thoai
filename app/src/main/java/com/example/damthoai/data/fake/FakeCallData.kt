package com.example.damthoai.data.fake

import com.example.damthoai.R
import com.example.damthoai.ui.screens.CallUiState
import com.example.damthoai.ui.screens.CallUiState.CallBusy
import com.example.damthoai.ui.screens.CallUiState.CallCut
import com.example.damthoai.ui.screens.CallUiState.CallIdle
import com.example.damthoai.ui.screens.CallUiState.CallOffline
import com.example.damthoai.ui.screens.CallUiState.CallTalk
import com.example.damthoai.ui.screens.CallUiState.CallTake
import com.example.damthoai.ui.theme.CallStatusAmber
import com.example.damthoai.ui.theme.CallStatusAmberBg
import com.example.damthoai.ui.theme.CallStatusBlue
import com.example.damthoai.ui.theme.CallStatusBlueBg
import com.example.damthoai.ui.theme.CallStatusMint
import com.example.damthoai.ui.theme.CallStatusMintBg
import com.example.damthoai.ui.theme.CallStatusOffline
import com.example.damthoai.ui.theme.CallStatusOfflineBg
import com.example.damthoai.ui.theme.CallStatusRed
import com.example.damthoai.ui.theme.CallStatusRedBg
import androidx.compose.ui.graphics.Color

data class FakeVolumeChannel(
    val iconRes: Int,
    val name: String,
    val valueLabel: String,
    val volume: Float,
    val speaker: String,
    val speakerColor: Color,
    val tag: String? = null
)

data class FakeCallContent(
    val statusTitle: String,
    val statusDescription: String,
    val statusIconRes: Int,
    val statusColor: Color,
    val statusBackground: Color,
    val channels: List<FakeVolumeChannel>
)

private val quietEventChannel = FakeVolumeChannel(
    iconRes = R.drawable.ic_radio,
    name = "Toàn sự kiện",
    valueLabel = "100%",
    volume = 1f,
    speaker = "Không ai đang nói",
    speakerColor = CallStatusMint
)
private val quietStageChannel = FakeVolumeChannel(
    iconRes = R.drawable.ic_stage,
    name = "Sân khấu",
    valueLabel = "70%",
    volume = 0.7f,
    speaker = "Không ai đang nói",
    speakerColor = CallStatusMint
)
private val quietSupportChannel = FakeVolumeChannel(
    iconRes = R.drawable.ic_mic,
    name = "Hậu cần – An ninh",
    valueLabel = "80%",
    volume = 0.8f,
    speaker = "Yên lặng",
    speakerColor = CallStatusMint
)

private val standardChannels = listOf(
    quietEventChannel,
    quietStageChannel,
    quietSupportChannel
)

object FakeCallData {
    fun contentFor(state: CallUiState): FakeCallContent = when (state) {
        CallIdle -> FakeCallContent(
            statusTitle = "Nghe kép đang bật",
            statusDescription = "Lan Phương (Cấp 1) đang nói ở Toàn sự kiện, kênh Hậu cần tự hạ âm 80%",
            statusIconRes = R.drawable.ic_radio,
            statusColor = CallStatusMint,
            statusBackground = CallStatusMintBg,
            channels = standardChannels.mapIndexed { index, channel ->
                if (index == 0) channel.copy(
                    speaker = "Lan Phương đang nói",
                    speakerColor = CallStatusBlue
                ) else channel
            }
        )
        CallTalk -> FakeCallContent(
            statusTitle = "Bạn đang phát · 00:07",
            statusDescription = "Mic chỉ mở ở kênh Hậu cần – An ninh",
            statusIconRes = R.drawable.ic_mic,
            statusColor = CallStatusBlue,
            statusBackground = CallStatusBlueBg,
            channels = standardChannels.mapIndexed { index, channel ->
                if (index == 2) channel.copy(
                    speaker = "Bạn đang nói",
                    speakerColor = CallStatusBlue
                ) else channel.copy(speaker = "Không ai đang nói")
            }
        )
        CallTake -> FakeCallContent(
            statusTitle = "Bạn đang chiếm quyền phát",
            statusDescription = "Mic của Minh Anh (Cấp 3) đã bị ngắt vì cấp bạn cao hơn",
            statusIconRes = R.drawable.ic_crown,
            statusColor = CallStatusAmber,
            statusBackground = CallStatusAmberBg,
            channels = standardChannels.mapIndexed { index, channel ->
                if (index == 2) channel.copy(
                    speaker = "Bạn đang nói",
                    speakerColor = CallStatusBlue
                ) else channel.copy(speaker = "Không ai đang nói")
            }
        )
        CallBusy -> FakeCallContent(
            statusTitle = "Kênh đang bận",
            statusDescription = "Đức Long (Cấp 2) đang nói. Chờ họ nhả nút để nói.",
            statusIconRes = R.drawable.ic_users,
            statusColor = CallStatusAmber,
            statusBackground = CallStatusAmberBg,
            channels = standardChannels.mapIndexed { index, channel ->
                if (index == 2) channel.copy(
                    speaker = "Đức Long đang nói",
                    speakerColor = CallStatusAmber
                ) else channel.copy(speaker = "Không ai đang nói")
            }
        )
        CallCut -> FakeCallContent(
            statusTitle = "Mic của bạn đã bị ngắt",
            statusDescription = "Lan Phương (Tổng đạo diễn, Cấp 1) đã chiếm quyền phát",
            statusIconRes = R.drawable.ic_alert,
            statusColor = CallStatusRed,
            statusBackground = CallStatusRedBg,
            channels = standardChannels.mapIndexed { index, channel ->
                if (index == 2) channel.copy(
                    valueLabel = "CẤP 1",
                    tag = "CẤP 1",
                    speaker = "Lan Phương đang nói",
                    speakerColor = CallStatusRed
                ) else channel.copy(speaker = "Không ai đang nói")
            }
        )
        CallOffline -> FakeCallContent(
            statusTitle = "Mất kết nối",
            statusDescription = "Đang ghi âm và lưu lại, sẽ tự gửi khi có mạng",
            statusIconRes = R.drawable.ic_wifi,
            statusColor = CallStatusOffline,
            statusBackground = CallStatusOfflineBg,
            channels = standardChannels.map { channel ->
                channel.copy(speaker = "Không có tín hiệu", speakerColor = CallStatusOffline)
            }
        )
    }
}
