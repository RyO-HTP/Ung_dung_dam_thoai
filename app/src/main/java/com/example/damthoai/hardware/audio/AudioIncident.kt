package com.example.damthoai.hardware.audio

/**
 * Sự cố hoặc thay đổi có liên quan đến tuyến âm thanh và quyền audio.
 */
sealed class AudioIncident {
    data class HeadsetUnplugged(val deviceType: Int) : AudioIncident()
    object HeadsetReconnected : AudioIncident()
    object BluetoothDisconnected : AudioIncident()
    data class AudioFocusLost(val reason: String) : AudioIncident()
    object AudioFocusRegained : AudioIncident()
    data class MicUnavailable(val reason: String) : AudioIncident()
    data class AudioPermissionRevoked(val permission: String) : AudioIncident()
    data class AudioRouteFailed(val reason: String) : AudioIncident()
    data class Unknown(val throwable: Throwable) : AudioIncident()
}
