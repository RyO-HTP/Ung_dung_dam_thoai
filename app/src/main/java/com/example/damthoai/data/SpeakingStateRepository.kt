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
import kotlinx.coroutines.flow.drop
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
            PttController.isPttActive.drop(1).collect { isLocalActive ->
                val session = EventSession.getSession() ?: return@collect
                val primaryCh = session.primaryChannelId
                val chIdNum = primaryCh.takeLast(1).toIntOrNull() ?: 1
                val current = _speakingByChannel.value.toMutableMap()

                if (isLocalActive) {
                    current[primaryCh] = SpeakerInfo(name = session.displayName, levelLabel = "Cấp ${session.level}", isLocal = true)
                    _speakingByChannel.value = current

                    val reqBody = mapOf(
                        "channelId" to chIdNum,
                        "channelCode" to primaryCh,
                        "userId" to session.userId,
                        "displayName" to session.displayName,
                        "roleTitle" to session.roleTitle,
                        "userLevel" to session.level,
                        "rolePriority" to session.priority
                    )
                    ApiClient.post<Any>("/api/v1/floor/request", reqBody)
                } else {
                    if (current[primaryCh]?.isLocal == true) {
                        current.remove(primaryCh)
                        _speakingByChannel.value = current
                    }

                    val reqBody = mapOf(
                        "channelId" to chIdNum,
                        "userId" to session.userId
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
