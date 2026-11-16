package com.example.damthoai.data.fake

import com.example.damthoai.data.SpeakerInfo
import com.example.damthoai.data.SpeakingStateSource
import com.example.damthoai.network.NetworkStatusSource
import com.example.damthoai.network.NetworkTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NetworkStatus {
    data class Good(val ms: Int) : NetworkStatus()
    data class Weak(val ms: Int) : NetworkStatus()
    object Offline : NetworkStatus()
}

object FakeNetworkStatus : NetworkStatusSource {
    private val _status = MutableStateFlow<NetworkStatus>(NetworkStatus.Good(42))
    override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    private val _transport = MutableStateFlow(NetworkTransport.WIFI)
    override val transport: StateFlow<NetworkTransport> = _transport.asStateFlow()

    private val demoScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var demoJob: Job? = null
    private var currentIndex = 0
    private val demoStatuses = listOf(
        NetworkStatus.Good(42),
        NetworkStatus.Weak(186),
        NetworkStatus.Offline
    )

    fun setStatus(status: NetworkStatus) {
        _status.value = status
    }

    fun startDemoCycle() {
        if (demoJob?.isActive == true) return
        demoJob = demoScope.launch {
            while (true) {
                delay(5000)
                currentIndex = (currentIndex + 1) % demoStatuses.size
                _status.value = demoStatuses[currentIndex]
            }
        }
    }

    fun stopDemoCycle() {
        demoJob?.cancel()
        demoJob = null
    }
}

object FakeSpeakingState : SpeakingStateSource {
    private val _speakingByChannel = MutableStateFlow<Map<String, SpeakerInfo>>(
        mapOf("ch_01" to SpeakerInfo("Minh Anh", "Cấp 3", false))
    )
    override val speakingByChannel: StateFlow<Map<String, SpeakerInfo>> = _speakingByChannel.asStateFlow()

    private val _speakingChannelIds = MutableStateFlow(setOf("ch_01"))
    override val speakingChannelIds: StateFlow<Set<String>> = _speakingChannelIds.asStateFlow()

    fun setSpeakingChannels(channelIds: Set<String>) {
        _speakingChannelIds.value = channelIds
        _speakingByChannel.value = channelIds.associateWith { SpeakerInfo("Minh Anh", "Cấp 3", false) }
    }

    fun setSpeaking(channelId: String, isSpeaking: Boolean) {
        if (isSpeaking) {
            _speakingChannelIds.value = _speakingChannelIds.value + channelId
            _speakingByChannel.value = _speakingByChannel.value + (channelId to SpeakerInfo("Minh Anh", "Cấp 3", false))
        } else {
            _speakingChannelIds.value = _speakingChannelIds.value - channelId
            _speakingByChannel.value = _speakingByChannel.value - channelId
        }
    }
}
