/**
 * Path: app/src/main/java/com/example/damthoai/ui/channels/ChannelListUiState.kt
 * Định nghĩa Trạng thái UI (UiState) cho màn hình Danh sách Kênh.
 */
package com.example.damthoai.ui.channels

import com.example.damthoai.data.Channel

data class ChannelItemUiState(
    val channel: Channel,
    val volume: Float = 0.7f,
    val isMuted: Boolean = false,
    val isTransmittingMic: Boolean = false,
    val isDucked80: Boolean = false
)

data class ChannelListUiState(
    val eventName: String = "Đêm nhạc Chào Tân sinh viên",
    val liveCueHeader: String = "LIVE CUE · ĐANG DIỄN",
    val liveCueTitle: String = "Tiết mục 4 – Ban nhạc khách mời",
    val liveCueSubtitle: String = "Hậu cần: chuẩn bị đạo cụ chuyển cảnh",
    val channels: List<ChannelItemUiState> = emptyList(),
    val activeMicChannelId: String = "ch_03",
    val selectedMenuChannel: ChannelItemUiState? = null,
    val roleChangedNotification: RoleChangedUiState? = null
)

// Cấu trúc trạng thái thông báo động thực tế, tích hợp sẵn chuỗi hiển thị
data class RoleChangedUiState(
    val changedByName: String,   // Tên người thực hiện đổi (Ví dụ: Tổng đạo diễn)
    val newRoleTitle: String,    // Chức vụ mới (Ví dụ: Trưởng ban Hậu cần)
    val newLevelCode: Int        // Cấp bậc mới (1, 2, 3)
) {
    val title: String get() = "$changedByName đã đổi chức vụ của bạn"
    val subtitle: String get() = "$newRoleTitle · Cấp $newLevelCode"
}