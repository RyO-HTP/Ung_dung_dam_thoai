package com.example.damthoai.hardware.audio

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phát sự cố âm thanh tới các điểm nối phần cứng và giao diện.
 *
 * ĐIỂM NỐI: TV2/TV4 gọi [report]; TV3 quan sát [incidents] hoặc [recentIncidents].
 */
object AudioIncidentBus {

    private const val INCIDENT_BUFFER_CAPACITY = 16
    private const val RECENT_INCIDENT_LIMIT = 5
    private val lock = Any()

    private val _incidents = MutableSharedFlow<AudioIncident>(
        replay = 0,
        extraBufferCapacity = INCIDENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incidents: SharedFlow<AudioIncident> = _incidents.asSharedFlow()

    private val _recentIncidents = MutableStateFlow<List<AudioIncident>>(emptyList())
    val recentIncidents: StateFlow<List<AudioIncident>> = _recentIncidents.asStateFlow()

    /** Gửi sự cố thật hoặc sự cố do bộ phận khác ghi nhận. */
    fun report(incident: AudioIncident) {
        synchronized(lock) {
            _recentIncidents.value = (_recentIncidents.value + incident).takeLast(RECENT_INCIDENT_LIMIT)
        }
        _incidents.tryEmit(incident)
    }

    /** Gửi sự cố mô phỏng; không điều khiển thiết bị âm thanh. */
    fun simulate(incident: AudioIncident) = report(incident)
}
