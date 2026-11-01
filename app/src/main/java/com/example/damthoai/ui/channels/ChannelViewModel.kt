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
import com.example.damthoai.data.fake.Channel
import com.example.damthoai.data.fake.FakeEventRepository
import com.example.damthoai.data.fake.FakeRepositories
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChannelViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelListUiState())
    val uiState: StateFlow<ChannelListUiState> = _uiState.asStateFlow()

    init {
        observeEventChannels()
        triggerMockRoleChangedBanner()
    }

    private fun observeEventChannels() {
        viewModelScope.launch {
            FakeEventRepository.currentEvent.collect { event ->
                val currentState = _uiState.value
                val previousChannels = currentState.channels.associateBy { it.channel.id }
                val channelStates = (event?.channels ?: FakeRepositories.sampleChannels).map { channel ->
                    previousChannels[channel.id]?.copy(channel = channel)
                        ?: initialChannelState(channel)
                }
                val activeMicChannelId = currentState.activeMicChannelId
                    .takeIf { id -> channelStates.any { it.channel.id == id } }
                    ?: channelStates.firstOrNull()?.channel?.id.orEmpty()
                val selectedMenuChannel = currentState.selectedMenuChannel?.let { selected ->
                    channelStates.firstOrNull { it.channel.id == selected.channel.id }
                }

                _uiState.update {
                    it.copy(
                        eventName = event?.name ?: FakeRepositories.sampleEvent.name,
                        channels = channelStates,
                        activeMicChannelId = activeMicChannelId,
                        selectedMenuChannel = selectedMenuChannel
                    )
                }
            }
        }
    }

    private fun initialChannelState(channel: Channel): ChannelItemUiState =
        when (channel.id) {
            "ch_01" -> ChannelItemUiState(
                channel = channel,
                volume = 1.0f,
                isTransmittingMic = false
            )
            "ch_02" -> ChannelItemUiState(channel = channel, volume = 0.7f)
            "ch_03" -> ChannelItemUiState(
                channel = channel,
                volume = 0.2f,
                isTransmittingMic = true,
                isDucked80 = true
            )
            else -> ChannelItemUiState(channel = channel)
        }

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
    }

    fun dismissRoleChangedBanner() {
        _uiState.update { it.copy(roleChangedNotification = null) }
    }

    private fun triggerMockRoleChangedBanner() {
        viewModelScope.launch {
            delay(2000)
            _uiState.update {
                it.copy(roleChangedNotification = RoleChangedUiState())
            }
            delay(5000)
            _uiState.update {
                it.copy(roleChangedNotification = null)
            }
        }
    }
}
