package com.example.damthoai.hardware.audio

/**
 * Bọc lời gọi audio có thể lỗi do quyền bị thu hồi hoặc thiết bị đổi trạng thái.
 */
inline fun <T> runSafely(
    onError: (Throwable) -> T,
    block: () -> T
): T = try {
    block()
} catch (exception: SecurityException) {
    AudioIncidentBus.report(
        AudioIncident.AudioPermissionRevoked(
            exception.message ?: "RECORD_AUDIO/BLUETOOTH_CONNECT"
        )
    )
    onError(exception)
} catch (exception: IllegalStateException) {
    AudioIncidentBus.report(
        AudioIncident.AudioRouteFailed(
            exception.message ?: "Audio API đang ở trạng thái không hợp lệ"
        )
    )
    onError(exception)
} catch (exception: RuntimeException) {
    AudioIncidentBus.report(AudioIncident.Unknown(exception))
    onError(exception)
}
