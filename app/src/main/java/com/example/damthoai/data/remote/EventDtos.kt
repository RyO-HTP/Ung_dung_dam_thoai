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
        get() = when {
            userId == null -> userCode ?: "user_0"
            userId.isJsonPrimitive -> userId.asString
            else -> userId.toString()
        }

    fun toMember(): Member = Member(
        id = resolvedUserId,
        name = displayName ?: "Thành viên",
        roleTitle = roleTitle ?: "Cộng tác viên",
        initials = (displayName ?: "TV").take(2).uppercase(),
        level = when (userLevel) {
            1 -> MemberLevel.LEVEL_1
            2 -> MemberLevel.LEVEL_2
            else -> MemberLevel.LEVEL_3
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
