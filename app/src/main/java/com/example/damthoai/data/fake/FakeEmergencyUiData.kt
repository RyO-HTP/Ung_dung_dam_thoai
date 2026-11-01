package com.example.damthoai.data.fake

import com.example.damthoai.hardware.emergency.EmergencyAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmergencyCallParticipant(
    val member: Member,
    val status: String,
    val isSpeaking: Boolean = false
)

object FakeEmergencyUiData {
    val recipients = listOf(
        Member("emergency_lp", "Lan Phương", "Tổng đạo diễn", "LP", MemberLevel.LEVEL_1),
        Member("emergency_qh", "Quốc Hùng", "Trưởng ban Sân khấu", "QH", MemberLevel.LEVEL_2),
        Member("emergency_dl", "Đức Long", "Trưởng ban An ninh", "ĐL", MemberLevel.LEVEL_2),
        Member("emergency_ma", "Minh Anh", "CTV Hậu cần", "MA", MemberLevel.LEVEL_3)
    )

    val sampleAlert = EmergencyAlert(
        id = "preview-emergency",
        fromName = "Lan Phương",
        fromRole = "Tổng đạo diễn",
        startedAtMs = 0L,
        isDirectCall = true
    )

    private val _alert = MutableStateFlow<EmergencyAlert?>(sampleAlert)
    val alert: StateFlow<EmergencyAlert?> = _alert.asStateFlow()

    private val _isVibrating = MutableStateFlow(true)
    val isVibrating: StateFlow<Boolean> = _isVibrating.asStateFlow()

    private val _isFlashing = MutableStateFlow(true)
    val isFlashing: StateFlow<Boolean> = _isFlashing.asStateFlow()

    private val _inactiveSignal = MutableStateFlow(false)
    val inactiveSignal: StateFlow<Boolean> = _inactiveSignal.asStateFlow()

    val callParticipants = listOf(
        EmergencyCallParticipant(
            member = Member(
                "emergency_call_lp",
                "Lan Phương",
                "Tổng đạo diễn",
                "LP",
                MemberLevel.LEVEL_1,
                isTalking = true
            ),
            status = "Tổng đạo diễn · đang nói",
            isSpeaking = true
        ),
        EmergencyCallParticipant(
            member = Member(
                "emergency_call_mh",
                "Minh Hậu",
                "Trưởng ban Hậu cần",
                "MH",
                MemberLevel.LEVEL_2
            ),
            status = "Đã vào cuộc gọi"
        ),
        EmergencyCallParticipant(
            member = Member(
                "emergency_call_qh",
                "Quốc Hùng",
                "Trưởng ban Sân khấu",
                "QH",
                MemberLevel.LEVEL_2
            ),
            status = "Đang chờ trả lời..."
        )
    )
}
