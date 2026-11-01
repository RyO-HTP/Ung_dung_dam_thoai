package com.example.damthoai.data.fake

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

object FakeNetworkStatus {
    private val _status = MutableStateFlow<NetworkStatus>(NetworkStatus.Good(42))
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

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

object FakeSpeakingState {
    private val _speakingChannelIds = MutableStateFlow(setOf("ch_01"))
    val speakingChannelIds: StateFlow<Set<String>> = _speakingChannelIds.asStateFlow()

    fun setSpeakingChannels(channelIds: Set<String>) {
        _speakingChannelIds.value = channelIds
    }

    fun setSpeaking(channelId: String, isSpeaking: Boolean) {
        _speakingChannelIds.value = if (isSpeaking) {
            _speakingChannelIds.value + channelId
        } else {
            _speakingChannelIds.value - channelId
        }
    }
}
