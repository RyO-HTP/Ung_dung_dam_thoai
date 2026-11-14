package com.example.damthoai.hardware.audio

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phát sự cố âm thanh tới các điểm nối phần cứng và giao diện.
 *
 * ĐIỂM NỐI: TV2/TV4 gọi [report]; TV3 quan sát [incidents], [activeIncident] hoặc [recentIncidents].
 */
object AudioIncidentBus {

    private const val INCIDENT_BUFFER_CAPACITY = 16
    private const val RECENT_INCIDENT_LIMIT = 5
    private const val AUTO_DISMISS_DELAY_MS = 8_000L

    private val lock = SynchronizedObject()
    private val busScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var dismissJob: Job? = null

    private val _incidents = MutableSharedFlow<AudioIncident>(
        replay = 0,
        extraBufferCapacity = INCIDENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val incidents: SharedFlow<AudioIncident> = _incidents.asSharedFlow()

    private val _activeIncident = MutableStateFlow<AudioIncident?>(null)
    val activeIncident: StateFlow<AudioIncident?> = _activeIncident.asStateFlow()

    private val _recentIncidents = MutableStateFlow<List<AudioIncident>>(emptyList())
    val recentIncidents: StateFlow<List<AudioIncident>> = _recentIncidents.asStateFlow()

    /** Gửi sự cố thật hoặc sự cố do bộ phận khác ghi nhận. */
    fun report(incident: AudioIncident) {
        synchronized(lock) {
            _recentIncidents.value = (_recentIncidents.value + incident).takeLast(RECENT_INCIDENT_LIMIT)
            _activeIncident.value = incident

            dismissJob?.cancel()
            dismissJob = busScope.launch {
                delay(AUTO_DISMISS_DELAY_MS)
                synchronized(lock) {
                    if (_activeIncident.value == incident) {
                        _activeIncident.value = null
                    }
                }
            }
        }
        _incidents.tryEmit(incident)
    }

    /** Bỏ qua/xóa sự cố đang hiển thị trên giao diện. */
    fun dismiss() {
        synchronized(lock) {
            dismissJob?.cancel()
            dismissJob = null
            _activeIncident.value = null
        }
    }

    /** Gửi sự cố mô phỏng; không điều khiển thiết bị âm thanh. */
    fun simulate(incident: AudioIncident) = report(incident)
}
