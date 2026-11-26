package com.example.damthoai.ui.preview

import com.example.damthoai.R
import com.example.damthoai.data.model.Channel
import com.example.damthoai.data.model.Event
import com.example.damthoai.data.model.Member
import com.example.damthoai.data.model.MemberLevel
import com.example.damthoai.data.model.LiveCue
import com.example.damthoai.data.model.EmergencyCallParticipant
import com.example.damthoai.data.model.CallChannel
import com.example.damthoai.data.model.CallContent
import com.example.damthoai.ui.screens.CallUiState
import com.example.damthoai.ui.theme.CallStatusMint
import com.example.damthoai.ui.theme.CallStatusMintBg
import com.example.damthoai.ui.theme.CallStatusBlue
import com.example.damthoai.ui.theme.CallStatusBlueBg
import com.example.damthoai.ui.theme.CallStatusAmber
import com.example.damthoai.ui.theme.CallStatusAmberBg
import com.example.damthoai.ui.theme.CallStatusRed
import com.example.damthoai.ui.theme.CallStatusRedBg
import com.example.damthoai.ui.theme.CallStatusOffline
import com.example.damthoai.ui.theme.CallStatusOfflineBg

object PreviewSamples {
    val sampleEvent = Event(
        id = "evt_001",
        name = "Lễ Trao Giải Âm Nhạc 2026",
        pinCode = "889900",
        adminCode = "ADM-7788",
        isOwner = true,
        channels = listOf(
            Channel(id = "ch_01", name = "Toàn sự kiện", isPriority = true, activeMembers = 12),
            Channel(id = "ch_02", name = "Sân khấu", activeMembers = 8),
            Channel(id = "ch_03", name = "Hậu cần – An ninh", activeMembers = 24)
        ),
        members = listOf(
            Member("mem_01", "Lâm Phong", "Tổng đạo diễn", "LP", MemberLevel.LEVEL_1, isTalking = true),
            Member("mem_02", "Minh Hải", "Trưởng ban Âm thanh", "MH", MemberLevel.LEVEL_2),
            Member("mem_03", "Quang Huy", "Kỹ thuật Sân khấu", "QH", MemberLevel.LEVEL_3)
        )
    )

    val sampleChannels = sampleEvent.channels
    val sampleMembers = sampleEvent.members
    val eventMembers = sampleMembers

    val recipients = listOf(
        Member("emergency_lp", "Lan Phương", "Tổng đạo diễn", "LP", MemberLevel.LEVEL_1),
        Member("emergency_qh", "Quốc Hùng", "Trưởng ban Sân khấu", "QH", MemberLevel.LEVEL_2),
        Member("emergency_dl", "Đức Long", "Trưởng ban An ninh", "ĐL", MemberLevel.LEVEL_2),
        Member("emergency_ma", "Minh Anh", "CTV Hậu cần", "MA", MemberLevel.LEVEL_3)
    )

    val callParticipants = listOf(
        EmergencyCallParticipant(
            member = Member("emergency_call_lp", "Lan Phương", "Tổng đạo diễn", "LP", MemberLevel.LEVEL_1, isTalking = true),
            status = "Tổng đạo diễn · đang nói",
            isSpeaking = true
        ),
        EmergencyCallParticipant(
            member = Member("emergency_call_mh", "Minh Hậu", "Trưởng ban Hậu cần", "MH", MemberLevel.LEVEL_2),
            status = "Đã vào cuộc gọi"
        )
    )

    val liveCue = LiveCue(
        title = "Tiết mục 4 – Ban nhạc khách mời",
        message = "Hậu cần: chuẩn bị đạo cụ chuyển cảnh",
        channelName = "Toàn sự kiện",
        isPriority = true
    )

    private val standardChannels = listOf(
        CallChannel(R.drawable.ic_radio, "Toàn sự kiện", "100%", 1f, "Lan Phương đang nói", CallStatusMint),
        CallChannel(R.drawable.ic_stage, "Sân khấu", "70%", 0.7f, "Không ai đang nói", CallStatusMint),
        CallChannel(R.drawable.ic_mic, "Hậu cần – An ninh", "20%", 0.2f, "Yên lặng", CallStatusMint, "HẠ ÂM 80%")
    )

    fun contentFor(state: CallUiState): CallContent = when (state) {
        CallUiState.CallIdle -> CallContent("Nghe kép đang bật", "Lan Phương đang nói", R.drawable.ic_radio, CallStatusMint, CallStatusMintBg, standardChannels)
        CallUiState.CallTalk -> CallContent("Bạn đang phát", "Mic mở", R.drawable.ic_mic, CallStatusBlue, CallStatusBlueBg, standardChannels)
        CallUiState.CallTake -> CallContent("Chiếm quyền", "Mic độc quyền", R.drawable.ic_crown, CallStatusAmber, CallStatusAmberBg, standardChannels)
        CallUiState.CallBusy -> CallContent("Kênh bận", "Chờ nhả nút", R.drawable.ic_users, CallStatusAmber, CallStatusAmberBg, standardChannels)
        CallUiState.CallCut -> CallContent("Bị ngắt lời", "Đạo diễn cắt", R.drawable.ic_alert, CallStatusRed, CallStatusRedBg, standardChannels)
        CallUiState.CallOffline -> CallContent("Mất kết nối", "Lưu offline", R.drawable.ic_wifi, CallStatusOffline, CallStatusOfflineBg, standardChannels)
    }
}
