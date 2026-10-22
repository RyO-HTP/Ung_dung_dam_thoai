/**
 * Path: app/src/main/java/com/example/damthoai/data/fake/Event.kt
 *
 * Khối KDoc tiếng Việt:
 * Data class đại diện cho Sự kiện (Event) trong ứng dụng Đàm Thoại.
 */
package com.example.damthoai.data.fake

data class Event(
    val id: String,
    val name: String,
    val pinCode: String,
    val adminCode: String? = null,
    val memberCount: Int = 0,
    val isOwner: Boolean = false,
    val isActive: Boolean = true
)
