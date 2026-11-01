package com.example.damthoai.data.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveCue(
    val title: String,
    val message: String,
    val channelName: String,
    val isPriority: Boolean = false
)

object FakeRemainingUiData {
    var lastSentLiveCue: LiveCue? = null
        private set

    val event = FakeRepositories.sampleEvent.copy(
        name = "Đêm nhạc Chào Tân sinh viên",
        channels = FakeRepositories.sampleChannels,
        members = FakeRepositories.sampleMembers,
        ownerId = "mem_01"
    )

    val liveCue = LiveCue(
        title = "Tiết mục 4 – Ban nhạc khách mời",
        message = "Hậu cần: chuẩn bị đạo cụ chuyển cảnh",
        channelName = "Toàn sự kiện",
        isPriority = true
    )

    private val _liveCueState = MutableStateFlow<LiveCue?>(liveCue)
    val liveCueState: StateFlow<LiveCue?> = _liveCueState.asStateFlow()

    val eventMembers = listOf(
        Member("mem_lp", "Lan Phương", "Tổng đạo diễn", "LP", MemberLevel.LEVEL_1, isTalking = true),
        Member("mem_qh", "Quốc Hùng", "Trưởng ban Sân khấu", "QH", MemberLevel.LEVEL_2),
        Member("mem_mh", "Minh Hậu", "Trưởng ban Hậu cần", "MH", MemberLevel.LEVEL_2),
        Member("mem_dl", "Đức Long", "Trưởng ban An ninh", "ĐL", MemberLevel.LEVEL_2),
        Member("mem_th", "Thu Hà", "CTV Kỹ thuật", "TH", MemberLevel.LEVEL_3),
        Member("mem_ma", "Minh Anh", "CTV Hậu cần", "MA", MemberLevel.LEVEL_3),
        Member("mem_bt", "Bảo Trâm", "CTV Đón tiếp", "BT", MemberLevel.LEVEL_3)
    )

    fun sendLiveCue(cue: LiveCue) {
        lastSentLiveCue = cue
        _liveCueState.value = cue
    }
}
