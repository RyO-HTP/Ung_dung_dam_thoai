package com.example.damthoai.ui.channels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.example.damthoai.data.Channel
import com.example.damthoai.data.AppStartPrefs

class ChannelViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        ChannelListUiState(
            eventName = AppStartPrefs.getEventName(application).ifBlank { "Sự kiện Đàm Thoại" }
        )
    )
    val uiState: StateFlow<ChannelListUiState> = _uiState.asStateFlow()

    fun refreshEventName() {
        val currentName = AppStartPrefs.getEventName(getApplication()).ifBlank { "Sự kiện Đàm Thoại" }
        _uiState.update { it.copy(eventName = currentName) }
    }

    init {
        // Tên sự kiện cập nhật trực tiếp khi server gửi tên do ban tổ chức đặt
        viewModelScope.launch {
            com.example.damthoai.service.DamThoaiCallManager.getInstance(application).eventName.collect { name ->
                _uiState.update { it.copy(eventName = name.ifBlank { AppStartPrefs.DEFAULT_EVENT_NAME }) }
            }
        }
        // Hiện tại cả sự kiện dùng chung một luồng âm thanh P2P: âm lượng nghe = âm lượng của kênh
        // đang chọn (tắt tiếng kênh đó = im lặng). Cập nhật ngay khi người dùng kéo thanh trượt.
        viewModelScope.launch {
            val callManager = com.example.damthoai.service.DamThoaiCallManager.getInstance(application)
            _uiState
                .map { state ->
                    val active = state.channels.firstOrNull { it.channel.id == state.activeMicChannelId }
                    when {
                        active == null -> 1.0f
                        active.isMuted -> 0f
                        else -> active.volume
                    }
                }
                .distinctUntilChanged()
                .collect { callManager.setListeningVolume(it) }
        }
        // Khởi tạo 5 kênh chuẩn theo Yêu cầu Đề tài Đàm Thoại
        _uiState.update { state ->
            if (state.channels.isEmpty()) {
                state.copy(
                    channels = listOf(
                        ChannelItemUiState(
                            channel = Channel(
                                id = "ch_01",
                                name = "Toàn sự kiện",
                                isDefault = true,
                                isPriority = true,
                                activeMembers = 1,
                                volume = 1.0f
                            ),
                            volume = 1.0f
                        ),
                        ChannelItemUiState(
                            channel = Channel(
                                id = "ch_02",
                                name = "Sân khấu",
                                activeMembers = 1,
                                volume = 0.9f
                            ),
                            volume = 0.9f
                        ),
                        ChannelItemUiState(
                            channel = Channel(
                                id = "ch_03",
                                name = "Kỹ thuật",
                                activeMembers = 1,
                                volume = 0.85f
                            ),
                            volume = 0.85f
                        ),
                        ChannelItemUiState(
                            channel = Channel(
                                id = "ch_04",
                                name = "Hậu cần – An ninh",
                                activeMembers = 1,
                                volume = 0.80f
                            ),
                            volume = 0.80f
                        ),
                        ChannelItemUiState(
                            channel = Channel(
                                id = "ch_05",
                                name = "Đón tiếp",
                                activeMembers = 1,
                                volume = 0.75f
                            ),
                            volume = 0.75f
                        )
                    ),
                    activeMicChannelId = "ch_01"
                )
            } else {
                state
            }
        }
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
            val updatedChannels = state.channels.filterNot { item -> item.channel.id == channelId }
            state.copy(
                channels = updatedChannels,
                selectedMenuChannel = null
            )
        }
    }

    // 7. Thêm phòng ban / kênh mới (Admin)
    fun addChannel(name: String) {
        if (name.isBlank()) return
        val newId = "ch_${java.util.UUID.randomUUID().toString().take(8)}"
        val newChannel = Channel(id = newId, name = name.trim(), activeMembers = 1)
        _uiState.update { state ->
            state.copy(channels = state.channels + ChannelItemUiState(channel = newChannel, volume = 0.8f))
        }
    }

    // 8. Đổi tên phòng ban / kênh (Admin)
    fun renameChannel(channelId: String, newName: String) {
        if (newName.isBlank()) return
        _uiState.update { state ->
            val updated = state.channels.map { item ->
                if (item.channel.id == channelId) {
                    item.copy(channel = item.channel.copy(name = newName.trim()))
                } else {
                    item
                }
            }
            state.copy(channels = updated, selectedMenuChannel = null)
        }
    }

    // 9. Xóa phòng ban / kênh (Admin)
    fun deleteChannel(channelId: String) {
        _uiState.update { state ->
            val updated = state.channels.filterNot { item -> item.channel.id == channelId }
            state.copy(channels = updated, selectedMenuChannel = null)
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