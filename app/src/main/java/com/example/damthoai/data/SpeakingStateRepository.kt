package com.example.damthoai.data

import com.example.damthoai.hardware.PttController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SpeakerInfo(
    val name: String,
    val levelLabel: String = "",
    val isLocal: Boolean = false
)

interface SpeakingStateSource {
    val speakingByChannel: StateFlow<Map<String, SpeakerInfo>>
    val speakingChannelIds: StateFlow<Set<String>>
}

object SpeakingStateProvider {
    var source: SpeakingStateSource = SpeakingStateRepository
}

/**
 * ĐIỂM NỐI TV5/TV2: Hàm nhận sự kiện phát biểu từ xa (Floor Control WebSocket), cập nhật thông tin người đang phát biểu theo channelId.
 */
object SpeakingStateRepository : SpeakingStateSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _speakingByChannel = MutableStateFlow<Map<String, SpeakerInfo>>(emptyMap())
    override val speakingByChannel: StateFlow<Map<String, SpeakerInfo>> = _speakingByChannel.asStateFlow()

    override val speakingChannelIds: StateFlow<Set<String>> = _speakingByChannel
        .map { it.keys }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptySet())

    init {
        scope.launch {
            PttController.isPttActive.collect { isLocalActive ->
                val current = _speakingByChannel.value.toMutableMap()
                val localChannelId = "ch_03" // Default active local mic channel
                if (isLocalActive) {
                    current[localChannelId] = SpeakerInfo(name = "Bạn", levelLabel = "Cấp 1", isLocal = true)
                } else {
                    if (current[localChannelId]?.isLocal == true) {
                        current.remove(localChannelId)
                    }
                }
                _speakingByChannel.value = current
            }
        }
    }

    /**
     * ĐIỂM NỐI TV5/TV2: Cập nhật người dùng từ xa đang phát biểu từ tín hiệu tin nhắn WebSocket Signaling / Floor Control.
     */
    fun onRemoteSpeaker(channelId: String, name: String, levelLabel: String, speaking: Boolean) {
        val current = _speakingByChannel.value.toMutableMap()
        if (speaking) {
            current[channelId] = SpeakerInfo(name = name, levelLabel = levelLabel, isLocal = false)
        } else {
            if (current[channelId]?.isLocal == false) {
                current.remove(channelId)
            }
        }
        _speakingByChannel.value = current
    }
}
