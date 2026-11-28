package com.example.damthoai.data.remote

import com.example.damthoai.data.model.Channel
import com.example.damthoai.data.model.Event
import com.example.damthoai.data.model.Member
import com.example.damthoai.data.model.MemberLevel
import com.google.gson.JsonElement

data class ChannelDto(
    val id: String,
    val channelId: Int,
    val name: String,
    val departmentCode: String?,
    val defaultPriority: Int,
    val onlineCount: Int,
    val statusText: String?
) {
    fun toDomain(): Channel = Channel(
        id = id,
        name = name,
        isDefault = channelId == 1,
        isPriority = defaultPriority == 3,
        activeMembers = onlineCount,
        currentSpeaker = null
    )
}

data class EventInfo(
    val eventId: Long,
    val eventName: String,
    val description: String?,
    val pinCode: String,
    val createdAt: String?,
    val adminCode: String?,
    val memberCount: Int?,
    val channelList: List<ChannelDto>?
) {
    fun toDomain(): Event = Event(
        id = eventId.toString(),
        name = eventName,
        pinCode = pinCode,
        adminCode = adminCode,
        channels = channelList?.map { it.toDomain() } ?: emptyList(),
        members = emptyList(),
        isActive = true
    )
}

data class CreateEventResponseDto(
    val eventId: Long,
    val eventName: String,
    val description: String?,
    val pinCode: String,
    val createdAt: String?,
    val adminCode: String?,
    val channelList: List<ChannelDto>?,
    val session: UserSessionDto?
) {
    fun toDomain(): Event = Event(
        id = eventId.toString(),
        name = eventName,
        pinCode = pinCode,
        adminCode = adminCode,
        channels = channelList?.map { it.toDomain() } ?: emptyList(),
        members = session?.let { listOf(it.toMember()) } ?: emptyList(),
        isActive = true
    )
}

data class UserSessionDto(
    val userId: JsonElement?,
    val userCode: String?,
    val eventId: Long,
    val displayName: String?,
    val roleTitle: String?,
    val userLevel: Int,
    val rolePriority: Int,
    val primaryTxChannel: Int,
    val primaryChannelId: String?,
    val listeningChannels: JsonElement?,
    val signalingUrl: String?,
    val signalingToken: String?,
    val online: Boolean?
) {
    val resolvedUserId: String
        get() {
            // Ưu tiên userCode (luôn là string sạch từ server)
            if (!userCode.isNullOrBlank()) return userCode
            val element = userId ?: return "user_0"
            return try {
                when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isString ->
                        element.asString
                    element.isJsonPrimitive && element.asJsonPrimitive.isNumber ->
                        element.asJsonPrimitive.asNumber.toString()
                    else -> element.toString().trim('"')
                }
            } catch (_: Exception) {
                "user_0"
            }
        }

    /** Parse listeningChannels từ server (mảng số [1,2] hoặc fallback). */
    fun parsedListeningChannels(fallbackPrimary: Int = primaryTxChannel): List<Int> {
        val element = listeningChannels
        if (element == null || element.isJsonNull) {
            return defaultListening(fallbackPrimary)
        }
        return try {
            when {
                element.isJsonArray -> {
                    val list = element.asJsonArray.mapNotNull { item ->
                        if (item.isJsonPrimitive && item.asJsonPrimitive.isNumber) {
                            item.asInt
                        } else null
                    }.filter { it in 1..8 }.distinct()
                    if (list.isEmpty()) defaultListening(fallbackPrimary) else list.take(3)
                }
                // Tương thích ngược nếu server còn trả chuỗi base64 cũ
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    defaultListening(fallbackPrimary)
                }
                else -> defaultListening(fallbackPrimary)
            }
        } catch (_: Exception) {
            defaultListening(fallbackPrimary)
        }
    }

    private fun defaultListening(primary: Int): List<Int> {
        val p = if (primary in 1..8) primary else 1
        return if (p == 1) listOf(1, 2) else listOf(1, p)
    }

    fun toMember(): Member = Member(
        id = resolvedUserId,
        name = displayName ?: "Thành viên",
        roleTitle = roleTitle ?: "Cộng tác viên",
        initials = (displayName ?: "TV").take(2).uppercase(),
        level = when (userLevel) {
            1 -> MemberLevel.LEVEL_1
            2 -> MemberLevel.LEVEL_2
            3 -> MemberLevel.LEVEL_3
            // 0 / thiếu field: suy luận theo roleTitle (tránh creator bị thành Cấp 3)
            else -> when {
                roleTitle?.contains("Tổng đạo diễn", ignoreCase = true) == true ||
                    roleTitle?.contains("admin", ignoreCase = true) == true -> MemberLevel.LEVEL_1
                roleTitle?.contains("Trưởng", ignoreCase = true) == true -> MemberLevel.LEVEL_2
                else -> MemberLevel.LEVEL_3
            }
        },
        isOnline = online ?: true
    )
}

data class JoinResponseDto(
    val event: EventInfo?,
    val session: UserSessionDto?,
    val status: String?,
    val userLevel: Int?,
    val departmentCode: String?,
    val userId: String?,
    val signalingUrl: String?,
    val signalingToken: String?
)
