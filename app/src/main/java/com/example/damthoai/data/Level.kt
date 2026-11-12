/**
 * Path: app/src/main/java/com/example/damthoai/data/fake/Level.kt
 *
 * Khối KDoc tiếng Việt:
 * Enum định nghĩa Cấp bậc quyền hạn thành viên trong ứng dụng Đàm Thoại.
 * - CẤP 1: Tổng đạo diễn (người tạo sự kiện, quyền cao nhất)
 * - CẤP 2: Trưởng ban
 * - CẤP 3: Cộng tác viên (CTV)
 */
package com.example.damthoai.data.fake

enum class MemberLevel(
    val code: Int,
    val title: String,
    val shortLabel: String
) {
    LEVEL_1(1, "CẤP 1 - Tổng đạo diễn", "CẤP 1"),
    LEVEL_2(2, "CẤP 2 - Trưởng ban", "CẤP 2"),
    LEVEL_3(3, "CẤP 3 - Cộng tác viên", "CẤP 3");

    companion object {
        fun fromCode(code: Int): MemberLevel {
            return entries.find { it.code == code } ?: LEVEL_3
        }
    }
}
