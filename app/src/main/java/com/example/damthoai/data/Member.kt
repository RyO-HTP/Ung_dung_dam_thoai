/**
 * Path: app/src/main/java/com/example/damthoai/data/fake/Member.kt
 *
 * Khối KDoc tiếng Việt:
 * Data class đại diện cho Thành viên trong ekip sự kiện.
 */
package com.example.damthoai.data.fake

data class Member(
    val id: String,
    val name: String,
    val roleTitle: String,
    val initials: String,
    val level: MemberLevel,
    val isOnline: Boolean = true,
    val isTalking: Boolean = false
)
