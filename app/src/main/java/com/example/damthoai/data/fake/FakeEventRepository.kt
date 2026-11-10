package com.example.damthoai.data.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.random.Random

enum class ChannelValidationError {
    EMPTY_NAME,
    DUPLICATE_NAME,
    CHANNEL_LIMIT_REACHED,
    EVENT_NOT_FOUND,
    CHANNEL_NOT_FOUND
}

sealed interface ChannelMutationResult {
    data class Success(val channel: Channel) : ChannelMutationResult
    data class Rejected(val reason: ChannelValidationError) : ChannelMutationResult
}

object FakeEventRepository {
    const val MAX_CHANNELS = 8

    private val seedEvent = FakeRepositories.sampleEvent.let { event ->
        val owner = FakeRemainingUiData.eventMembers.first { it.level == MemberLevel.LEVEL_1 }
        event.copy(
            channels = FakeRepositories.sampleChannels.mapIndexed { index, channel ->
                val pin = if (index == 0) event.pinCode else generatePin()
                channel.copy(
                    isDefault = index == 0,
                    pin = pin,
                    qrPayload = createQrPayload(event.id, channel.id, pin),
                    memberIds = if (index == 0) setOf(owner.id) else emptySet()
                )
            },
            members = FakeRemainingUiData.eventMembers,
            ownerId = owner.id
        )
    }

    val sampleEvent: Event = seedEvent
    private val _currentEvent = MutableStateFlow<Event?>(seedEvent)
    val currentEvent: StateFlow<Event?> = _currentEvent.asStateFlow()

    /**
     * Tạo sự kiện mới.
     * Mã chỉ lưu trong bộ nhớ giả, ĐIỂM NỐI TV5: lưu và xác thực mã ở máy chủ để khôi phục được trên máy khác.
     */
    fun createEvent(name: String, adminCode: String? = null): Event {
        require(name.isNotBlank()) { "Tên sự kiện không được để trống." }

        val owner = FakeRemainingUiData.eventMembers.first { it.level == MemberLevel.LEVEL_1 }
        val eventId = UUID.randomUUID().toString()
        val defaultChannelNames = listOf("Toàn sự kiện", "Sân khấu", "Kỹ thuật", "Hậu cần – An ninh", "Đón tiếp")
        val channels = defaultChannelNames.mapIndexed { index, chName ->
            val channelId = UUID.randomUUID().toString()
            val pin = generatePin()
            Channel(
                id = channelId,
                name = chName,
                isDefault = index == 0,
                pin = pin,
                qrPayload = createQrPayload(eventId, channelId, pin),
                memberIds = setOf(owner.id)
            )
        }
        val codeToSave = if (!adminCode.isNullOrBlank()) {
            adminCode.trim().uppercase()
        } else {
            "ADM-" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        }
        return Event(
            id = eventId,
            name = name.trim(),
            pinCode = channels.first().pin,
            adminCode = codeToSave,
            channels = channels,
            members = listOf(owner),
            ownerId = owner.id,
            isOwner = true
        ).also { _currentEvent.value = it }
    }

    fun validateChannelName(
        name: String,
        excludingChannelId: String? = null
    ): ChannelValidationError? {
        val event = _currentEvent.value ?: return ChannelValidationError.EVENT_NOT_FOUND
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return ChannelValidationError.EMPTY_NAME
        if (event.channels.any {
                it.id != excludingChannelId && it.name.equals(normalizedName, ignoreCase = true)
            }
        ) {
            return ChannelValidationError.DUPLICATE_NAME
        }
        if (excludingChannelId == null && event.channels.size >= MAX_CHANNELS) {
            return ChannelValidationError.CHANNEL_LIMIT_REACHED
        }
        return null
    }

    fun addChannel(name: String): ChannelMutationResult {
        val event = _currentEvent.value
            ?: return ChannelMutationResult.Rejected(ChannelValidationError.EVENT_NOT_FOUND)
        val error = validateChannelName(name)
        if (error != null) return ChannelMutationResult.Rejected(error)

        val channelId = UUID.randomUUID().toString()
        val pin = generatePin()
        val channel = Channel(
            id = channelId,
            name = name.trim(),
            pin = pin,
            qrPayload = createQrPayload(event.id, channelId, pin)
        )
        _currentEvent.value = event.copy(channels = event.channels + channel)
        return ChannelMutationResult.Success(channel)
    }

    fun renameChannel(id: String, name: String): ChannelMutationResult {
        val event = _currentEvent.value
            ?: return ChannelMutationResult.Rejected(ChannelValidationError.EVENT_NOT_FOUND)
        val channel = event.channels.firstOrNull { it.id == id }
            ?: return ChannelMutationResult.Rejected(ChannelValidationError.CHANNEL_NOT_FOUND)
        val error = validateChannelName(name, excludingChannelId = id)
        if (error != null) return ChannelMutationResult.Rejected(error)

        val renamedChannel = channel.copy(name = name.trim())
        _currentEvent.value = event.copy(
            channels = event.channels.map { if (it.id == id) renamedChannel else it }
        )
        return ChannelMutationResult.Success(renamedChannel)
    }

    fun removeChannel(id: String): Boolean {
        val event = _currentEvent.value ?: return false
        val channel = event.channels.firstOrNull { it.id == id } ?: return false
        if (channel.isDefault) return false

        _currentEvent.value = event.copy(
            channels = event.channels.filterNot { it.id == id }
        )
        return true
    }

    fun findChannel(id: String?): Channel? =
        id?.let { channelId -> _currentEvent.value?.channels?.firstOrNull { it.id == channelId } }

    fun restoreEvent(adminCode: String): Event? {
        val normalizedCode = adminCode.trim().uppercase()
        return _currentEvent.value?.takeIf { it.adminCode == normalizedCode }
    }

    fun updateMember(member: Member) {
        val event = _currentEvent.value ?: return
        _currentEvent.value = event.copy(
            members = event.members.map { if (it.id == member.id) member else it }
        )
    }

    fun removeMember(memberId: String) {
        val event = _currentEvent.value ?: return
        _currentEvent.value = event.copy(
            members = event.members.filterNot { it.id == memberId },
            channels = event.channels.map { channel ->
                channel.copy(memberIds = channel.memberIds - memberId)
            }
        )
    }

    private fun generatePin(): String =
        Random.nextInt(0, 1_000_000).toString().padStart(6, '0')

    private fun createQrPayload(eventId: String, channelId: String, pin: String): String =
        "damthoai://event/$eventId/channel/$channelId?pin=$pin"
}
