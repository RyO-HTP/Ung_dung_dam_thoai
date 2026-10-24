/**
 * Path: app/src/main/java/com/example/damthoai/data/fake/FakeRepositories.kt
 *
 * Khối KDoc tiếng Việt:
 * Dữ liệu giả (Fake Repository / Mock Data) dùng tạm cho toàn bộ ứng dụng.
 * // TODO(TV3): Sau này sẽ thay thế bằng Repository thật kết nối với WebRTC & Backend Server.
 */
package com.example.damthoai.data.fake

object FakeRepositories {

    val sampleEvent = Event(
        id = "evt_001",
        name = "Lễ Trao Giải Âm Nhạc 2026",
        pinCode = "889900",
        adminCode = "ADM-7788",
        memberCount = 24,
        isOwner = true
    )

    val sampleChannels = listOf(
        Channel(
            id = "ch_01",
            name = "Toàn sự kiện",
            isPriority = true,
            activeMembers = 12,
            currentSpeaker = "Lan Phương",
            liveCueMessage = "LIVE CUE: Chuẩn bị trao giải Ca sĩ của năm sau 30 giây!"
        ),
        Channel(
            id = "ch_02",
            name = "Sân khấu",
            isEmergency = false,
            activeMembers = 8,
            currentSpeaker = null
        ),
        Channel(
            id = "ch_03",
            name = "Hậu cần – An ninh",
            activeMembers = 24,
            currentSpeaker = null
        )
    )

    val sampleMembers = listOf(
        Member(
            id = "mem_01",
            name = "Lâm Phong",
            roleTitle = "Tổng đạo diễn",
            initials = "LP",
            level = MemberLevel.LEVEL_1,
            isTalking = true
        ),
        Member(
            id = "mem_02",
            name = "Minh Hải",
            roleTitle = "Trưởng ban Âm thanh",
            initials = "MH",
            level = MemberLevel.LEVEL_2
        ),
        Member(
            id = "mem_03",
            name = "Quang Huy",
            roleTitle = "Kỹ thuật Sân khấu",
            initials = "QH",
            level = MemberLevel.LEVEL_3
        )
    )
}
