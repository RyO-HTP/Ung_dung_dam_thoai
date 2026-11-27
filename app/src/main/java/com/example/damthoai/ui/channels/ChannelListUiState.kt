/**
 * Path: app/src/main/java/com/example/damthoai/ui/channels/ChannelListUiState.kt
 *
 * Khối KDoc tiếng Việt:
 * Định nghĩa Trạng thái UI (UiState) cho màn hình Danh sách Kênh.
 */
package com.example.damthoai.ui.channels

import com.example.damthoai.data.model.Channel

data class ChannelItemUiState(
    val channel: Channel,
    val volume: Float = 0.7f,
    val isMuted: Boolean = false,
    val isTransmittingMic: Boolean = false,
    val isDucked80: Boolean = false
)

data class ChannelListUiState(
    val eventName: String = "",
    val liveCueHeader: String = "",
    val liveCueTitle: String = "",
    val liveCueSubtitle: String = "",
    val channels: List<ChannelItemUiState> = emptyList(),
    val activeMicChannelId: String = "",
    val selectedMenuChannel: ChannelItemUiState? = null,
    val roleChangedNotification: RoleChangedUiState? = null
)

data class RoleChangedUiState(
    val title: String = "Tổng đạo diễn đã đổi chức vụ của bạn",
    val subtitle: String = ""
)
