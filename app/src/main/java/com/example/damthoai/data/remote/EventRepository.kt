package com.example.damthoai.data.remote

import android.content.Context
import com.example.damthoai.data.model.Channel
import com.example.damthoai.data.model.Event
import com.example.damthoai.data.model.Member
import com.example.damthoai.data.model.MemberLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface EventRepository {
    val currentEvent: StateFlow<Event?>
    val currentMember: StateFlow<Member?>

    suspend fun createEvent(eventName: String, description: String = "", pinCode: String = "", adminCode: String = ""): Result<Event>
    suspend fun restoreEvent(adminCode: String, displayName: String): Result<Event>
    suspend fun joinByPin(context: Context, pinCode: String, displayName: String, roleTitle: String, userLevel: Int, preferredChannel: Int): Result<Event>
    suspend fun joinByQr(context: Context, qrToken: String, displayName: String): Result<Event>
    suspend fun switchChannel(primaryTxChannel: Int, listeningChannels: List<Int>): Result<Unit>
    suspend fun leaveChannel(channelId: Int): Result<Unit>
    suspend fun addChannel(name: String): Result<Channel>
    suspend fun renameChannel(channelId: String, name: String): Result<Channel>
    suspend fun removeChannel(channelId: String): Result<Unit>
    suspend fun listMembers(): Result<List<Member>>
    suspend fun updateMember(memberId: String, userLevel: Int, roleTitle: String): Result<Unit>
    suspend fun removeMember(memberId: String): Result<Unit>
    fun logout(context: Context)
}

object RemoteEventRepository : EventRepository {
    private var appContext: Context? = null

    private val _currentEvent = MutableStateFlow<Event?>(null)
    override val currentEvent: StateFlow<Event?> = _currentEvent.asStateFlow()

    private val _currentMember = MutableStateFlow<Member?>(null)
    override val currentMember: StateFlow<Member?> = _currentMember.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        EventSession.init(context)
        val session = EventSession.getSession()
        if (session != null) {
            val event = Event(
                id = session.eventId.toString(),
                name = session.eventName,
                pinCode = session.pinCode,
                members = listOf(
                    Member(
                        id = session.userId,
                        name = session.displayName,
                        roleTitle = session.roleTitle,
                        initials = session.displayName.take(2).uppercase(),
                        level = when (session.level) {
                            1 -> MemberLevel.LEVEL_1
                            2 -> MemberLevel.LEVEL_2
                            else -> MemberLevel.LEVEL_3
                        }
                    )
                ),
                isActive = true
            )
            _currentEvent.value = event
            _currentMember.value = event.members.firstOrNull()
        }
    }

    override suspend fun createEvent(eventName: String, description: String, pinCode: String, adminCode: String): Result<Event> = withContext(Dispatchers.IO) {
        val body = mapOf(
            "eventName" to eventName,
            "description" to description,
            "pinCode" to pinCode,
            "adminCode" to adminCode
        )
        val result = ApiClient.post<EventInfo>("/api/v1/events", body)
        result.map { eventInfo ->
            eventInfo.toDomain()
        }
    }

    override suspend fun restoreEvent(adminCode: String, displayName: String): Result<Event> = withContext(Dispatchers.IO) {
        val body = mapOf("adminCode" to adminCode, "displayName" to displayName)
        val result = ApiClient.post<JoinResponseDto>("/api/v1/events/restore", body)
        result.map { dto ->
            val event = dto.event.toDomain()
            val member = dto.session.toMember()
            val session = EventSession.SessionData(
                eventId = dto.event.eventId,
                eventName = dto.event.eventName,
                pinCode = dto.event.pinCode,
                userId = dto.session.resolvedUserId,
                userCode = dto.session.userCode ?: dto.session.resolvedUserId,
                displayName = member.name,
                roleTitle = member.roleTitle,
                level = member.level.code,
                priority = 4 - member.level.code,
                primaryChannelId = dto.session.primaryChannelId ?: "ch_01",
                listeningChannels = dto.session.listeningChannels ?: listOf(1),
                signalingUrl = dto.session.signalingUrl,
                signalingToken = dto.session.signalingToken
            )
            appContext?.let { EventSession.saveSession(it, session) }
            val updatedEvent = event.copy(members = listOf(member))
            _currentEvent.value = updatedEvent
            _currentMember.value = member
            updatedEvent
        }
    }

    override suspend fun joinByPin(context: Context, pinCode: String, displayName: String, roleTitle: String, userLevel: Int, preferredChannel: Int): Result<Event> = withContext(Dispatchers.IO) {
        val rolePriority = 4 - userLevel
        val body = mapOf(
            "pinCode" to pinCode,
            "displayName" to displayName,
            "roleTitle" to roleTitle,
            "userLevel" to userLevel,
            "rolePriority" to rolePriority,
            "preferredChannel" to preferredChannel
        )
        val result = ApiClient.post<JoinResponseDto>("/api/v1/auth/join-pin", body)
        result.map { dto ->
            val event = dto.event.toDomain()
            val member = dto.session.toMember()
            val sessionData = EventSession.SessionData(
                eventId = dto.event.eventId,
                eventName = dto.event.eventName,
                pinCode = dto.event.pinCode,
                userId = dto.session.resolvedUserId,
                userCode = dto.session.userCode ?: dto.session.resolvedUserId,
                displayName = member.name,
                roleTitle = member.roleTitle,
                level = userLevel,
                priority = rolePriority,
                primaryChannelId = dto.session.primaryChannelId ?: "ch_0$preferredChannel",
                listeningChannels = dto.session.listeningChannels ?: listOf(1, preferredChannel),
                signalingUrl = dto.session.signalingUrl,
                signalingToken = dto.session.signalingToken
            )
            EventSession.saveSession(context, sessionData)
            val updatedEvent = event.copy(members = listOf(member))
            _currentEvent.value = updatedEvent
            _currentMember.value = member
            updatedEvent
        }
    }

    override suspend fun joinByQr(context: Context, qrToken: String, displayName: String): Result<Event> = withContext(Dispatchers.IO) {
        val body = mapOf(
            "qrToken" to qrToken,
            "displayName" to displayName
        )
        val result = ApiClient.post<JoinResponseDto>("/api/v1/auth/join-by-qr", body)
        result.map { dto ->
            val event = dto.event.toDomain()
            val member = dto.session.toMember()
            val sessionData = EventSession.SessionData(
                eventId = dto.event.eventId,
                eventName = dto.event.eventName,
                pinCode = dto.event.pinCode,
                userId = dto.session.resolvedUserId,
                userCode = dto.session.userCode ?: dto.session.resolvedUserId,
                displayName = member.name,
                roleTitle = member.roleTitle,
                level = member.level.code,
                priority = 4 - member.level.code,
                primaryChannelId = dto.session.primaryChannelId ?: "ch_01",
                listeningChannels = dto.session.listeningChannels ?: listOf(1),
                signalingUrl = dto.session.signalingUrl,
                signalingToken = dto.session.signalingToken
            )
            EventSession.saveSession(context, sessionData)
            val updatedEvent = event.copy(members = listOf(member))
            _currentEvent.value = updatedEvent
            _currentMember.value = member
            updatedEvent
        }
    }

    override suspend fun switchChannel(primaryTxChannel: Int, listeningChannels: List<Int>): Result<Unit> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập sự kiện"))
        val body = mapOf(
            "userId" to session.userId,
            "primaryTxChannel" to primaryTxChannel,
            "listeningChannels" to listeningChannels
        )
        val result = ApiClient.post<Any>("/api/v1/users/switch-channel", body)
        result.map {}
    }

    override suspend fun leaveChannel(channelId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập sự kiện"))
        val body = mapOf(
            "userId" to session.userId,
            "channelId" to channelId
        )
        val result = ApiClient.post<Any>("/api/v1/users/leave-channel", body)
        result.map {}
    }

    override suspend fun addChannel(name: String): Result<Channel> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập"))
        val event = _currentEvent.value ?: return@withContext Result.failure(Exception("Không có sự kiện"))
        val body = mapOf("userId" to session.userId, "name" to name)
        val result = ApiClient.post<ChannelDto>("/api/v1/events/${event.id}/channels", body)
        result.map { it.toDomain() }.onSuccess { ch ->
            val cur = _currentEvent.value
            if (cur != null) {
                _currentEvent.value = cur.copy(channels = cur.channels + ch)
            }
        }
    }

    override suspend fun renameChannel(channelId: String, name: String): Result<Channel> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập"))
        val event = _currentEvent.value ?: return@withContext Result.failure(Exception("Không có sự kiện"))
        val body = mapOf("userId" to session.userId, "name" to name)
        val result = ApiClient.post<ChannelDto>("/api/v1/events/${event.id}/channels/$channelId", body)
        result.map { it.toDomain() }.onSuccess { ch ->
            val cur = _currentEvent.value
            if (cur != null) {
                _currentEvent.value = cur.copy(channels = cur.channels.map { if (it.id == channelId) ch else it })
            }
        }
    }

    override suspend fun removeChannel(channelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập"))
        val event = _currentEvent.value ?: return@withContext Result.failure(Exception("Không có sự kiện"))
        val body = mapOf("userId" to session.userId)
        val result = ApiClient.post<Any>("/api/v1/events/${event.id}/channels/$channelId", body)
        result.map {}.onSuccess {
            val cur = _currentEvent.value
            if (cur != null) {
                _currentEvent.value = cur.copy(channels = cur.channels.filterNot { it.id == channelId })
            }
        }
    }

    override suspend fun listMembers(): Result<List<Member>> = withContext(Dispatchers.IO) {
        val event = _currentEvent.value ?: return@withContext Result.success(emptyList())
        val result = ApiClient.get<List<UserSessionDto>>("/api/v1/events/${event.id}/members")
        result.map { list -> list.map { it.toMember() } }
    }

    override suspend fun updateMember(memberId: String, userLevel: Int, roleTitle: String): Result<Unit> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập"))
        val event = _currentEvent.value ?: return@withContext Result.failure(Exception("Không có sự kiện"))
        val body = mapOf("actorUserId" to session.userId, "userLevel" to userLevel, "roleTitle" to roleTitle)
        val result = ApiClient.post<Any>("/api/v1/events/${event.id}/members/$memberId", body)
        result.map {}
    }

    override suspend fun removeMember(memberId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập"))
        val event = _currentEvent.value ?: return@withContext Result.failure(Exception("Không có sự kiện"))
        val body = mapOf("actorUserId" to session.userId)
        val result = ApiClient.post<Any>("/api/v1/events/${event.id}/members/$memberId", body)
        result.map {}
    }

    override fun logout(context: Context) {
        EventSession.clearSession(context)
        appContext = null
        _currentEvent.value = null
        _currentMember.value = null
    }
}
