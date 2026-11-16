package com.example.damthoai.data

/**
 * Vai trò phân tầng trong sự kiện.
 * - Trưởng ban: chỉ đàm thoại với trưởng nhóm (qua kênh "Ban chỉ huy").
 * - Trưởng nhóm: đàm thoại với trưởng ban (kênh "Ban chỉ huy") và thành viên nhóm mình (kênh nhóm).
 * - Thành viên: chỉ đàm thoại trong kênh nhóm của mình (với thành viên và trưởng nhóm của nhóm đó).
 *
 * [wire] là mã gửi qua máy chủ signaling và nhúng trong mã QR.
 */
enum class TeamRole(val wire: String, val title: String) {
    TRUONG_BAN("ban", "Trưởng ban"),
    TRUONG_NHOM("nhom", "Trưởng nhóm"),
    THANH_VIEN("tv", "Thành viên");

    companion object {
        /** Mã lạ hoặc thiếu (app/máy chủ bản cũ) được coi là thành viên. */
        fun fromWire(wire: String?): TeamRole = entries.firstOrNull { it.wire == wire } ?: THANH_VIEN
    }
}

/** Chỗ ngồi của một người trong sự kiện: vai trò + kênh nhóm (trưởng ban không thuộc nhóm nào). */
data class Seat(
    val role: TeamRole,
    val groupId: String,
    val groupName: String = ""
)

/**
 * Cấu trúc kênh theo vai trò và luật ai được nói chuyện với ai.
 * Mọi máy dùng chung các hàm thuần này nên luôn tính ra cùng một kết quả.
 */
object TeamChannels {
    /** Kênh chỉ huy: trưởng ban + các trưởng nhóm. Giữ id "ch_01" (kênh ưu tiên cũ). */
    const val COMMAND_ID = "ch_01"
    const val COMMAND_NAME = "Ban chỉ huy"

    /** Nhóm mặc định khi vào bằng PIN (không có QR vai trò) hoặc máy bản cũ. */
    const val GENERAL_ID = "ch_general"
    const val GENERAL_NAME = "Kênh chung"

    /** Các kênh mà một người thuộc về (nghe được và có thể chọn để phát). */
    fun channelsOf(seat: Seat): List<String> = when (seat.role) {
        TeamRole.TRUONG_BAN -> listOf(COMMAND_ID)
        TeamRole.TRUONG_NHOM -> listOf(COMMAND_ID, seat.groupId).distinct()
        TeamRole.THANH_VIEN -> listOf(seat.groupId)
    }

    /**
     * Các kênh hai người dùng để nói chuyện với nhau; rỗng = KHÔNG được kết nối.
     * - Trưởng ban ↔ trưởng nhóm: kênh Ban chỉ huy.
     * - Trưởng nhóm ↔ thành viên cùng nhóm: kênh nhóm.
     * - Thành viên ↔ thành viên cùng nhóm: kênh nhóm.
     * Mọi cặp khác (trưởng ban ↔ thành viên, trưởng ban ↔ trưởng ban, trưởng nhóm ↔ trưởng nhóm,
     * khác nhóm) không kết nối.
     */
    fun sharedChannels(a: Seat, b: Seat): Set<String> {
        val roles = setOf(a.role, b.role)
        val sameGroup = a.groupId == b.groupId
        return when {
            roles == setOf(TeamRole.TRUONG_BAN, TeamRole.TRUONG_NHOM) -> setOf(COMMAND_ID)
            roles == setOf(TeamRole.TRUONG_NHOM, TeamRole.THANH_VIEN) && sameGroup -> setOf(a.groupId)
            a.role == TeamRole.THANH_VIEN && b.role == TeamRole.THANH_VIEN && sameGroup -> setOf(a.groupId)
            else -> emptySet()
        }
    }

    fun channelName(channelId: String, groupName: String = ""): String = when (channelId) {
        COMMAND_ID -> COMMAND_NAME
        GENERAL_ID -> GENERAL_NAME
        else -> groupName.ifBlank { channelId }
    }
}
