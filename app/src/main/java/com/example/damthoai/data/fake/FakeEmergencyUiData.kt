package com.example.damthoai.data.fake

import com.example.damthoai.data.model.Member
import com.example.damthoai.data.model.MemberLevel

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
