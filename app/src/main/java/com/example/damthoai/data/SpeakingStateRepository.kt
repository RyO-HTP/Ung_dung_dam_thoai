package com.example.damthoai.data

import com.example.damthoai.data.remote.ApiClient
import com.example.damthoai.data.remote.EventSession
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _speakingByChannel = MutableStateFlow<Map<String, SpeakerInfo>>(emptyMap())
    override val speakingByChannel: StateFlow<Map<String, SpeakerInfo>> = _speakingByChannel.asStateFlow()

    override val speakingChannelIds: StateFlow<Set<String>> = _speakingByChannel
        .map { it.keys }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptySet())

    init {
        scope.launch {
            PttController.isPttActive.collect { isLocalActive ->
                val session = EventSession.getSession()
                val primaryCh = session?.primaryChannelId ?: "ch_01"
                val chIdNum = primaryCh.takeLast(1).toIntOrNull() ?: 1
                val current = _speakingByChannel.value.toMutableMap()

                if (isLocalActive) {
                    current[primaryCh] = SpeakerInfo(name = session?.displayName ?: "Bạn", levelLabel = "Cấp ${session?.level ?: 1}", isLocal = true)
                    _speakingByChannel.value = current

                    val reqBody = mapOf(
                        "channelId" to chIdNum,
                        "channelCode" to primaryCh,
                        "userId" to (session?.userId ?: "user_1"),
                        "displayName" to (session?.displayName ?: "Thành viên"),
                        "roleTitle" to (session?.roleTitle ?: "Cộng tác viên"),
                        "userLevel" to (session?.level ?: 3),
                        "rolePriority" to (session?.priority ?: 1)
                    )
                    ApiClient.post<Any>("/api/v1/floor/request", reqBody)
                } else {
                    if (current[primaryCh]?.isLocal == true) {
                        current.remove(primaryCh)
                        _speakingByChannel.value = current
                    }

                    val reqBody = mapOf(
                        "channelId" to chIdNum,
                        "userId" to (session?.userId ?: "user_1")
                    )
                    ApiClient.post<Any>("/api/v1/floor/release", reqBody)
                }
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
