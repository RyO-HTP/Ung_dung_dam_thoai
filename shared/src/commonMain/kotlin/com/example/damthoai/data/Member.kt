/**
 * Path: app/src/main/java/com/example/damthoai/data/fake/Member.kt
 *
 * Khối KDoc tiếng Việt:
 * Data class đại diện cho Thành viên trong ekip sự kiện.
 */
package com.example.damthoai.data

data class Member(
    val id: String,
    val name: String,
    val roleTitle: String,
    val initials: String,
    val level: MemberLevel,
    val isOnline: Boolean = true,
    val isTalking: Boolean = false,
    /** Vai trò + phòng ban (quyết định cấp và các kênh của người này). */
    val seat: Seat = Seat(TeamRole.THANH_VIEN, TeamChannels.GENERAL_ID, TeamChannels.GENERAL_NAME),
    /** Chủ sự kiện (người tạo sự kiện): không ai đổi được vai trò của người này. */
    val isOrganizer: Boolean = false
)
