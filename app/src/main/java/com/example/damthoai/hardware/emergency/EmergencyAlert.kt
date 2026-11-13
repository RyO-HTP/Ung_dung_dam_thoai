package com.example.damthoai.hardware.emergency

/**
 * Dữ liệu một cảnh báo khẩn cấp.
 *
 * ĐIỂM NỐI: TV1/TV5 chuyển tín hiệu WebSocket thành dữ liệu này để gọi manager; TV3 quan sát
 * cảnh báo qua [EmergencyAlertManager.currentAlert].
 */
data class EmergencyAlert(
    val id: String,
    val fromName: String,
    val fromRole: String,
    val startedAtMs: Long,
    val isDirectCall: Boolean
)
