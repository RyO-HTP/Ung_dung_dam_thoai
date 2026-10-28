package com.example.damthoai.data.fake

data class LiveCue(
    val title: String,
    val message: String,
    val channelName: String,
    val isPriority: Boolean = false
)

object FakeRemainingUiData {
    val event = FakeRepositories.sampleEvent.copy(
        name = "Đêm nhạc Chào Tân sinh viên",
        memberCount = 48
    )

    val managementChannels = FakeRepositories.sampleChannels + listOf(
        Channel(id = "ch_04", name = "Kỹ thuật"),
        Channel(id = "ch_05", name = "Đón tiếp")
    )

    val liveCue = LiveCue(
        title = "Tiết mục 4 – Ban nhạc khách mời",
        message = "Hậu cần: chuẩn bị đạo cụ chuyển cảnh",
        channelName = "Toàn sự kiện",
        isPriority = true
    )

    val eventMembers = listOf(
        Member("mem_lp", "Lan Phương", "Tổng đạo diễn", "LP", MemberLevel.LEVEL_1, isTalking = true),
        Member("mem_qh", "Quốc Hùng", "Trưởng ban Âm thanh", "QH", MemberLevel.LEVEL_2),
        Member("mem_mh", "Minh Hậu", "Trưởng ban Hậu cần", "MH", MemberLevel.LEVEL_2),
        Member("mem_dl", "Đức Long", "Kỹ thuật sân khấu", "ĐL", MemberLevel.LEVEL_3),
        Member("mem_th", "Thu Hà", "Điều phối đón tiếp", "TH", MemberLevel.LEVEL_2),
        Member("mem_ma", "Minh Anh", "Cộng tác viên", "MA", MemberLevel.LEVEL_3),
        Member("mem_bt", "Bảo Trâm", "Cộng tác viên", "BT", MemberLevel.LEVEL_3)
    )
}
