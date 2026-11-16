/**
 * Path: app/src/main/java/com/example/damthoai/data/model/Event.kt
 *
 * Khối KDoc tiếng Việt:
 * Data class đại diện cho Sự kiện (Event) trong ứng dụng Đàm Thoại.
 */
package com.example.damthoai.data.model

data class Event(
    val id: String,
    val name: String,
    val pinCode: String,
    val adminCode: String? = null,
    val channels: List<Channel> = emptyList(),
    val members: List<Member> = emptyList(),
    val ownerId: String = "",
    val isOwner: Boolean = false,
    val isActive: Boolean = true
) {
    val memberCount: Int
        get() = members.size
}
