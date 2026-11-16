package com.example.damthoai.data

/**
 * Vai trò phân tầng trong sự kiện.
 * - Trưởng ban: đàm thoại với trưởng nhóm và các trưởng ban khác (kênh "Ban chỉ huy").
 * - Trưởng nhóm: đàm thoại với trưởng ban, các trưởng nhóm khác (kênh "Ban chỉ huy")
 *   và thành viên nhóm mình (kênh nhóm).
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
     * Các kênh hai người dùng để nói chuyện với nhau (các kênh cả hai cùng thuộc về);
     * rỗng = KHÔNG được kết nối.
     * - Trưởng ban ↔ trưởng ban, trưởng ban ↔ trưởng nhóm, trưởng nhóm ↔ trưởng nhóm: Ban chỉ huy.
     * - Trưởng nhóm ↔ thành viên cùng nhóm, thành viên ↔ thành viên cùng nhóm: kênh nhóm.
     * - Trưởng ban ↔ thành viên, hoặc thành viên khác nhóm: không có kênh chung → không kết nối.
     */
    fun sharedChannels(a: Seat, b: Seat): Set<String> =
        channelsOf(a).toSet() intersect channelsOf(b).toSet()

    fun channelName(channelId: String, groupName: String = ""): String = when (channelId) {
        COMMAND_ID -> COMMAND_NAME
        GENERAL_ID -> GENERAL_NAME
        else -> groupName.ifBlank { channelId }
    }
}
