/**
 * Path: app/src/main/java/com/example/damthoai/ui/channels/ChannelViewModel.kt
 *
 * Khối KDoc tiếng Việt:
 * ViewModel quản lý StateFlow cho Màn hình Danh sách Kênh.
 * Xử lý kéo thanh trượt âm lượng, bật tắt nghe kênh, mở Menu Sheet kênh, và Banner đổi chức vụ.
 */
package com.example.damthoai.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.damthoai.data.model.Channel
import com.example.damthoai.data.remote.EventSession
import com.example.damthoai.data.remote.RemoteEventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChannelViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelListUiState())
    val uiState: StateFlow<ChannelListUiState> = _uiState.asStateFlow()

    init {
        observeEventChannels()
    }

    private fun observeEventChannels() {
        viewModelScope.launch {
            RemoteEventRepository.currentEvent.collect { event ->
                val currentState = _uiState.value
                val previousChannels = currentState.channels.associateBy { it.channel.id }
                val eventChannels = event?.channels.orEmpty()
                val channelStates = eventChannels.map { channel ->
                    previousChannels[channel.id]?.copy(channel = channel)
                        ?: initialChannelState(channel)
                }
                val sessionPrimary = EventSession.getSession()?.primaryChannelId.orEmpty()
                val activeMicChannelId = currentState.activeMicChannelId
                    .takeIf { id -> id.isNotBlank() && channelStates.any { it.channel.id == id } }
                    ?: sessionPrimary.takeIf { id -> channelStates.any { it.channel.id == id } }
                    ?: channelStates.firstOrNull()?.channel?.id.orEmpty()
                val selectedMenuChannel = currentState.selectedMenuChannel?.let { selected ->
                    channelStates.firstOrNull { it.channel.id == selected.channel.id }
                }

                _uiState.update {
                    it.copy(
                        eventName = event?.name.orEmpty(),
                        channels = channelStates,
                        activeMicChannelId = activeMicChannelId,
                        selectedMenuChannel = selectedMenuChannel
                    )
                }
            }
        }
    }

    private fun initialChannelState(channel: Channel): ChannelItemUiState =
        ChannelItemUiState(
            channel = channel,
            volume = if (channel.isDefault || channel.id == "ch_01") 1.0f else 0.7f,
            isMuted = false,
            isTransmittingMic = false,
            isDucked80 = false
        )

    fun updateChannelVolume(channelId: String, newVolume: Float) {
        _uiState.update { state ->
            val updated = state.channels.map { item ->
                if (item.channel.id == channelId) {
                    item.copy(volume = newVolume)
                } else {
                    item
                }
            }
            state.copy(channels = updated)
        }
    }

    fun toggleMuteChannel(channelId: String, isMuted: Boolean) {
        _uiState.update { state ->
            val updated = state.channels.map { item ->
                if (item.channel.id == channelId) {
                    item.copy(isMuted = isMuted)
                } else {
                    item
                }
            }
            state.copy(channels = updated)
        }
    }

    fun selectMicChannel(channelId: String) {
        _uiState.update { it.copy(activeMicChannelId = channelId) }
        val chNum = channelId.takeLast(1).toIntOrNull() ?: 1
        val listening = _uiState.value.channels.mapNotNull { it.channel.id.takeLast(1).toIntOrNull() }.take(3)
        // Cập nhật session ngay để header ActiveCall đổi tên kênh tức thì
        EventSession.updatePrimaryChannel(null, channelId, listening)
        viewModelScope.launch {
            RemoteEventRepository.switchChannel(chNum, listening)
        }
    }

    fun openChannelMenu(item: ChannelItemUiState) {
        _uiState.update { it.copy(selectedMenuChannel = item) }
    }

    fun closeChannelMenu() {
        _uiState.update { it.copy(selectedMenuChannel = null) }
    }

    fun leaveChannel(channelId: String) {
        _uiState.update { state ->
            val remainingChannels = state.channels.filterNot { it.channel.id == channelId }
            val activeMicChannelId = if (state.activeMicChannelId == channelId) {
                remainingChannels.firstOrNull()?.channel?.id.orEmpty()
            } else {
                state.activeMicChannelId
            }
            state.copy(
                channels = remainingChannels,
                activeMicChannelId = activeMicChannelId,
                selectedMenuChannel = null
            )
        }
        val chNum = channelId.takeLast(1).toIntOrNull() ?: 1
        viewModelScope.launch {
            RemoteEventRepository.leaveChannel(chNum)
        }
    }

    fun dismissRoleChangedBanner() {
        _uiState.update { it.copy(roleChangedNotification = null) }
    }
}
