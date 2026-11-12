package com.example.damthoai.ui.channels

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ChannelViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ChannelListUiState())
    val uiState: StateFlow<ChannelListUiState> = _uiState.asStateFlow()

    init {
        // Khởi tạo trạng thái mặc định cho các kênh nếu cần
    }

    // 1. Chọn kênh để phát Mic
    fun selectMicChannel(channelId: String) {
        _uiState.update { it.copy(activeMicChannelId = channelId) }
    }

    // 2. Mở menu tùy chọn của kênh
    fun openChannelMenu(channelItem: ChannelItemUiState) {
        _uiState.update { it.copy(selectedMenuChannel = channelItem) }
    }

    // 3. Đóng menu tùy chọn của kênh
    fun closeChannelMenu() {
        _uiState.update { it.copy(selectedMenuChannel = null) }
    }

    // 4. Cập nhật âm lượng kênh
    fun updateChannelVolume(channelId: String, newVolume: Float) {
        _uiState.update { state ->
            val updatedChannels = state.channels.map { item ->
                if (item.channel.id == channelId) {
                    item.copy(volume = newVolume)
                } else {
                    item
                }
            }
            state.copy(channels = updatedChannels)
        }
    }

    // 5. Bật/Tắt chế độ tắt tiếng (mute) của kênh
    fun toggleMuteChannel(channelId: String, isMuted: Boolean) {
        _uiState.update { state ->
            val updatedChannels = state.channels.map { item ->
                if (item.channel.id == channelId) {
                    item.copy(isMuted = isMuted)
                } else {
                    item
                }
            }
            state.copy(channels = updatedChannels)
        }
    }

    // 6. Rời khỏi kênh
    fun leaveChannel(channelId: String) {
        _uiState.update { state ->
            val updatedChannels = state.channels.filterNot { it.channel.id == channelId }
            state.copy(
                channels = updatedChannels,
                selectedMenuChannel = null
            )
        }
    }

    /**
     * Phương thức thực tế dùng để cập nhật thông báo khi cấp trên/quản trị viên thay đổi chức vụ của bạn.
     */
    fun showRoleChangedNotification(adminName: String, newRole: String, level: Int) {
        _uiState.update { currentState ->
            currentState.copy(
                roleChangedNotification = RoleChangedUiState(
                    changedByName = adminName,   // Truyền tên thực tế của người thao tác
                    newRoleTitle = newRole,      // Truyền chức vụ mới
                    newLevelCode = level         // Truyền cấp bậc mới
                )
            )
        }
    }

    /**
     * Đóng/ẩn thông báo khi người dùng đã xem xong.
     */
    fun dismissRoleChangedBanner() {
        _uiState.update { currentState ->
            currentState.copy(roleChangedNotification = null)
        }
    }
}