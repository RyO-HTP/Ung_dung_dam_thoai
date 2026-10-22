/**
 * Path: app/src/main/java/com/example/damthoai/ui/channels/ChannelListUiState.kt
 *
 * Khối KDoc tiếng Việt:
 * Định nghĩa Trạng thái UI (UiState) cho màn hình Danh sách Kênh.
 */
package com.example.damthoai.ui.channels

import com.example.damthoai.data.fake.Channel

data class ChannelItemUiState(
    val channel: Channel,
    val volume: Float = 0.7f,
    val isMuted: Boolean = false,
    val isTransmittingMic: Boolean = false,
    val isDucked80: Boolean = false
)

data class ChannelListUiState(
    val eventName: String = "Đêm nhạc Chào Tân sinh viên",
    val networkStatus: String = "Internet · 42 ms",
    val liveCueHeader: String = "LIVE CUE · ĐANG DIỄN",
    val liveCueTitle: String = "Tiết mục 4 – Ban nhạc khách mời",
    val liveCueSubtitle: String = "Hậu cần: chuẩn bị đạo cụ chuyển cảnh",
    val channels: List<ChannelItemUiState> = emptyList(),
    val activeMicChannelId: String = "ch_03",
    val selectedMenuChannel: ChannelItemUiState? = null,
    val roleChangedNotification: RoleChangedUiState? = null
)

data class RoleChangedUiState(
    val title: String = "Lan Phương đã đổi chức vụ của bạn",
    val subtitle: String = "Trưởng ban Hậu cần · Cấp 2"
)
