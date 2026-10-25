/**
 * Path: app/src/main/java/com/example/damthoai/data/fake/Channel.kt
 *
 * Khối KDoc tiếng Việt:
 * Data class đại diện cho Kênh giao tiếp (Channel) trong sự kiện.
 */
package com.example.damthoai.data.fake

data class Channel(
    val id: String,
    val name: String,
    val isEmergency: Boolean = false,
    val isPriority: Boolean = false,
    val isMuted: Boolean = false,
    val activeMembers: Int = 0,
    val currentSpeaker: String? = null,
    val liveCueMessage: String? = null
)
