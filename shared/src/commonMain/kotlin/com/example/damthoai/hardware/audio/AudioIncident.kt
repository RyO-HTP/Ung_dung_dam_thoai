package com.example.damthoai.hardware.audio

/**
 * Sự cố hoặc thay đổi có liên quan đến tuyến âm thanh và quyền audio.
 * Cung cấp [userMessage] là câu tiếng Việt hiển thị trực tiếp trên UI (TV3).
 */
sealed class AudioIncident(open val userMessage: String) {
    data class HeadsetUnplugged(
        val deviceType: Int,
        override val userMessage: String = "Tai nghe đã bị rút. Âm thanh đang tắt để tránh phát ra loa ngoài."
    ) : AudioIncident(userMessage)

    data class HeadsetReconnected(
        val deviceType: Int = 0,
        override val userMessage: String = "Tai nghe đã kết nối lại."
    ) : AudioIncident(userMessage)

    data class BluetoothDisconnected(
        val deviceName: String = "",
        override val userMessage: String = "Thiết bị Bluetooth đã ngắt kết nối."
    ) : AudioIncident(userMessage)

    data class AudioFocusLost(
        val reason: String,
        override val userMessage: String = "Có cuộc gọi đến hoặc ứng dụng khác chiếm âm thanh. Mic đã tạm ngắt."
    ) : AudioIncident(userMessage)

    data class AudioFocusRegained(
        override val userMessage: String = "Đã lấy lại tiêu điểm âm thanh."
    ) : AudioIncident(userMessage)

    data class MicUnavailable(
        val reason: String,
        override val userMessage: String = "Không mở được micro. Có thể ứng dụng khác đang dùng."
    ) : AudioIncident(userMessage)

    data class AudioPermissionRevoked(
        val permission: String,
        override val userMessage: String = "Mất quyền âm thanh. Hãy cấp lại trong Cài đặt."
    ) : AudioIncident(userMessage)

    data class AudioRouteFailed(
        val reason: String,
        override val userMessage: String = "Chuyển tuyến âm thanh thất bại."
    ) : AudioIncident(userMessage)

    data class Unknown(
        val throwable: Throwable,
        override val userMessage: String = "Sự cố âm thanh không xác định."
    ) : AudioIncident(userMessage)
}
