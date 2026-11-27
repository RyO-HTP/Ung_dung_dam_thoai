package com.example.damthoai.data.remote

import android.content.Context
import com.example.damthoai.data.model.Channel
import com.example.damthoai.data.model.Event
import com.example.damthoai.data.model.Member
import com.example.damthoai.data.model.MemberLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface EventRepository {
    val currentEvent: StateFlow<Event?>
    val currentMember: StateFlow<Member?>
    val removedFromEventFlow: SharedFlow<Unit>

    suspend fun createEvent(eventName: String, description: String = "", pinCode: String = "", adminCode: String = "", displayName: String = "Tổng Đạo Diễn", roleTitle: String = "Tổng đạo diễn"): Result<Event>
    suspend fun restoreEvent(adminCode: String, displayName: String, roleTitle: String = "Tổng đạo diễn"): Result<Event>
    suspend fun joinByPin(context: Context, pinCode: String, displayName: String, roleTitle: String, preferredChannel: Int): Result<Event>
    suspend fun joinByQr(context: Context, qrToken: String, displayName: String): Result<Event>
    suspend fun refreshEvent(): Result<Event>
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

    private val _removedFromEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val removedFromEventFlow: SharedFlow<Unit> = _removedFromEventFlow.asSharedFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        EventSession.init(context)
        val session = EventSession.getSession()
        if (session != null) {
            val event = Event(
                id = session.eventId.toString(),
                name = session.eventName,
                pinCode = session.pinCode,
                adminCode = session.adminCode,
                channels = emptyList(),
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
                        },
                        isOnline = true
                    )
                ),
                isActive = true
            )
            _currentEvent.value = event
            _currentMember.value = event.members.firstOrNull()
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                refreshEvent()
            }
        }
    }

    private fun checkApiError(result: Result<*>, context: Context?): Boolean {
        if (result.isFailure) {
            val err = result.exceptionOrNull()
            // Chỉ coi là "bị xóa khỏi sự kiện" khi server trả đúng 403 USER_NOT_IN_EVENT.
            // 404 chung (backend down / URL sai / chưa tạo event) KHÔNG được đẩy sang màn xóa.
            if (err is ApiException && err.httpCode == 403 && err.errorCode == "USER_NOT_IN_EVENT") {
                context?.let { logout(it) }
                _removedFromEventFlow.tryEmit(Unit)
                return true
            }
        }
        return false
    }

    override suspend fun createEvent(
        eventName: String,
        description: String,
        pinCode: String,
        adminCode: String,
        displayName: String,
        roleTitle: String
    ): Result<Event> = withContext(Dispatchers.IO) {
        val body = mapOf(
            "eventName" to eventName,
            "description" to description,
            "pinCode" to pinCode,
            "adminCode" to adminCode,
            "displayName" to displayName,
            "roleTitle" to roleTitle
        )
        val result = ApiClient.post<CreateEventResponseDto>("/api/v1/events", body)
        if (result.isFailure) {
            checkApiError(result, appContext)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi tạo sự kiện"))
        }
        val dto = result.getOrNull() ?: return@withContext Result.failure(Exception("Phản hồi rỗng từ máy chủ"))
        val sessionDto = dto.session ?: return@withContext Result.failure(Exception("Phản hồi tạo sự kiện thiếu session"))

        val member = sessionDto.toMember()
        val sessionData = EventSession.SessionData(
            eventId = dto.eventId,
            eventName = dto.eventName,
            pinCode = dto.pinCode,
            userId = sessionDto.resolvedUserId,
            userCode = sessionDto.userCode ?: sessionDto.resolvedUserId,
            displayName = member.name,
            roleTitle = member.roleTitle,
            level = member.level.code,
            priority = sessionDto.rolePriority,
            primaryChannelId = sessionDto.primaryChannelId ?: "ch_01",
            listeningChannels = listOf(1),
            signalingUrl = sessionDto.signalingUrl,
            signalingToken = sessionDto.signalingToken,
            adminCode = dto.adminCode
        )
        appContext?.let { EventSession.saveSession(it, sessionData) }

        val event = Event(
            id = dto.eventId.toString(),
            name = dto.eventName,
            pinCode = dto.pinCode,
            adminCode = dto.adminCode,
            channels = dto.channelList?.map { it.toDomain() } ?: emptyList(),
            members = listOf(member),
            isActive = true
        )
        _currentEvent.value = event
        _currentMember.value = member
        Result.success(event)
    }

    override suspend fun restoreEvent(adminCode: String, displayName: String, roleTitle: String): Result<Event> = withContext(Dispatchers.IO) {
        val body = mapOf(
            "adminCode" to adminCode,
            "displayName" to displayName,
            "roleTitle" to roleTitle
        )
        val result = ApiClient.post<JoinResponseDto>("/api/v1/events/restore", body)
        if (result.isFailure) {
            checkApiError(result, appContext)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi khôi phục sự kiện"))
        }
        val dto = result.getOrNull() ?: return@withContext Result.failure(Exception("Phản hồi rỗng"))
        val eventInfo = dto.event ?: return@withContext Result.failure(Exception("Thiếu thông tin sự kiện"))
        val sessionDto = dto.session ?: return@withContext Result.failure(Exception("Thiếu session"))

        val member = sessionDto.toMember()
        val sessionData = EventSession.SessionData(
            eventId = eventInfo.eventId,
            eventName = eventInfo.eventName,
            pinCode = eventInfo.pinCode,
            userId = sessionDto.resolvedUserId,
            userCode = sessionDto.userCode ?: sessionDto.resolvedUserId,
            displayName = member.name,
            roleTitle = member.roleTitle,
            level = member.level.code,
            priority = sessionDto.rolePriority,
            primaryChannelId = sessionDto.primaryChannelId ?: "ch_01",
            listeningChannels = listOf(1),
            signalingUrl = sessionDto.signalingUrl,
            signalingToken = sessionDto.signalingToken,
            adminCode = adminCode
        )
        appContext?.let { EventSession.saveSession(it, sessionData) }

        val event = eventInfo.toDomain().copy(members = listOf(member))
        _currentEvent.value = event
        _currentMember.value = member
        Result.success(event)
    }

    override suspend fun joinByPin(
        context: Context,
        pinCode: String,
        displayName: String,
        roleTitle: String,
        preferredChannel: Int
    ): Result<Event> = withContext(Dispatchers.IO) {
        val body = mapOf(
            "pinCode" to pinCode,
            "displayName" to displayName,
            "roleTitle" to roleTitle,
            "preferredChannel" to preferredChannel
        )
        val result = ApiClient.post<JoinResponseDto>("/api/v1/auth/join-pin", body)
        if (result.isFailure) {
            checkApiError(result, context)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi tham gia bằng PIN"))
        }
        val dto = result.getOrNull() ?: return@withContext Result.failure(Exception("Phản hồi rỗng"))
        val eventInfo = dto.event ?: return@withContext Result.failure(Exception("Thiếu thông tin sự kiện"))
        val sessionDto = dto.session ?: return@withContext Result.failure(Exception("Thiếu session"))

        val member = sessionDto.toMember()
        val sessionData = EventSession.SessionData(
            eventId = eventInfo.eventId,
            eventName = eventInfo.eventName,
            pinCode = eventInfo.pinCode,
            userId = sessionDto.resolvedUserId,
            userCode = sessionDto.userCode ?: sessionDto.resolvedUserId,
            displayName = member.name,
            roleTitle = member.roleTitle,
            level = member.level.code,
            priority = sessionDto.rolePriority,
            primaryChannelId = sessionDto.primaryChannelId ?: "ch_0$preferredChannel",
            listeningChannels = listOf(1, preferredChannel),
            signalingUrl = sessionDto.signalingUrl ?: dto.signalingUrl,
            signalingToken = sessionDto.signalingToken ?: dto.signalingToken
        )
        EventSession.saveSession(context, sessionData)
        appContext = context.applicationContext

        val event = eventInfo.toDomain().copy(members = listOf(member))
        _currentEvent.value = event
        _currentMember.value = member
        Result.success(event)
    }

    override suspend fun joinByQr(context: Context, qrToken: String, displayName: String): Result<Event> = withContext(Dispatchers.IO) {
        val body = mapOf(
            "qrToken" to qrToken,
            "displayName" to displayName
        )
        val result = ApiClient.post<JoinResponseDto>("/api/v1/auth/join-by-qr", body)
        if (result.isFailure) {
            checkApiError(result, context)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi tham gia bằng QR"))
        }
        val dto = result.getOrNull() ?: return@withContext Result.failure(Exception("Phản hồi rỗng"))
        val eventInfo = dto.event ?: return@withContext Result.failure(Exception("Thiếu thông tin sự kiện"))
        val sessionDto = dto.session ?: return@withContext Result.failure(Exception("Thiếu session"))

        val member = sessionDto.toMember()
        val sessionData = EventSession.SessionData(
            eventId = eventInfo.eventId,
            eventName = eventInfo.eventName,
            pinCode = eventInfo.pinCode,
            userId = sessionDto.resolvedUserId,
            userCode = sessionDto.userCode ?: sessionDto.resolvedUserId,
            displayName = member.name,
            roleTitle = member.roleTitle,
            level = member.level.code,
            priority = sessionDto.rolePriority,
            primaryChannelId = sessionDto.primaryChannelId ?: "ch_01",
            listeningChannels = listOf(1),
            signalingUrl = sessionDto.signalingUrl ?: dto.signalingUrl,
            signalingToken = sessionDto.signalingToken ?: dto.signalingToken
        )
        EventSession.saveSession(context, sessionData)
        appContext = context.applicationContext

        val event = eventInfo.toDomain().copy(members = listOf(member))
        _currentEvent.value = event
        _currentMember.value = member
        Result.success(event)
    }

    override suspend fun refreshEvent(): Result<Event> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập sự kiện"))
        val eventId = _currentEvent.value?.id ?: session.eventId.toString()
        val result = ApiClient.get<EventInfo>("/api/v1/events/$eventId?actorUserId=${session.userId}")
        if (result.isFailure) {
            checkApiError(result, appContext)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi làm mới sự kiện"))
        }
        val eventInfo = result.getOrNull() ?: return@withContext Result.failure(Exception("Phản hồi rỗng"))
        val currentMembers = _currentEvent.value?.members.orEmpty().ifEmpty {
            listOf(
                Member(
                    id = session.userId,
                    name = session.displayName,
                    roleTitle = session.roleTitle,
                    initials = session.displayName.take(2).uppercase(),
                    level = when (session.level) {
                        1 -> MemberLevel.LEVEL_1
                        2 -> MemberLevel.LEVEL_2
                        else -> MemberLevel.LEVEL_3
                    },
                    isOnline = true
                )
            )
        }
        val updatedEvent = eventInfo.toDomain().copy(members = currentMembers)
        _currentEvent.value = updatedEvent
        Result.success(updatedEvent)
    }

    override suspend fun switchChannel(primaryTxChannel: Int, listeningChannels: List<Int>): Result<Unit> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập sự kiện"))
        val body = mapOf(
            "userId" to session.userId,
            "primaryTxChannel" to primaryTxChannel,
            "listeningChannels" to listeningChannels.take(3)
        )
        val result = ApiClient.post<Any>("/api/v1/users/switch-channel", body)
        if (result.isFailure) {
            checkApiError(result, appContext)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi chuyển kênh"))
        }
        // Cập nhật kênh primary local → header ActiveCall đổi đúng tên
        val channelId = "ch_0$primaryTxChannel"
        EventSession.updatePrimaryChannel(
            context = appContext,
            channelId = channelId,
            listeningChannels = listeningChannels.take(3)
        )
        Result.success(Unit)
    }

    override suspend fun leaveChannel(channelId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập sự kiện"))
        val body = mapOf(
            "userId" to session.userId,
            "channelId" to channelId
        )
        val result = ApiClient.post<Any>("/api/v1/users/leave-channel", body)
        if (result.isFailure) {
            checkApiError(result, appContext)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi rời kênh"))
        }
        Result.success(Unit)
    }

    override suspend fun addChannel(name: String): Result<Channel> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập"))
        val event = _currentEvent.value ?: return@withContext Result.failure(Exception("Không có sự kiện"))
        val body = mapOf("name" to name)
        val result = ApiClient.post<ChannelDto>("/api/v1/events/${event.id}/channels?actorUserId=${session.userId}", body)
        if (result.isFailure) {
            checkApiError(result, appContext)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi thêm kênh"))
        }
        val chDto = result.getOrNull() ?: return@withContext Result.failure(Exception("Phản hồi rỗng"))
        refreshEvent()
        Result.success(chDto.toDomain())
    }

    override suspend fun renameChannel(channelId: String, name: String): Result<Channel> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập"))
        val event = _currentEvent.value ?: return@withContext Result.failure(Exception("Không có sự kiện"))
        val body = mapOf("name" to name)
        val result = ApiClient.patch<ChannelDto>("/api/v1/events/${event.id}/channels/$channelId?actorUserId=${session.userId}", body)
        if (result.isFailure) {
            checkApiError(result, appContext)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi đổi tên kênh"))
        }
        val chDto = result.getOrNull() ?: return@withContext Result.failure(Exception("Phản hồi rỗng"))
        refreshEvent()
        Result.success(chDto.toDomain())
    }

    override suspend fun removeChannel(channelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập"))
        val event = _currentEvent.value ?: return@withContext Result.failure(Exception("Không có sự kiện"))
        val result = ApiClient.delete<Any>("/api/v1/events/${event.id}/channels/$channelId?actorUserId=${session.userId}")
        if (result.isFailure) {
            checkApiError(result, appContext)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi xóa kênh"))
        }
        refreshEvent()
        Result.success(Unit)
    }

    override suspend fun listMembers(): Result<List<Member>> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.success(emptyList())
        val event = _currentEvent.value ?: return@withContext Result.success(emptyList())
        val result = ApiClient.get<List<UserSessionDto>>("/api/v1/events/${event.id}/members?actorUserId=${session.userId}")
        if (result.isFailure) {
            checkApiError(result, appContext)
            return@withContext Result.success(emptyList())
        }
        val list = result.getOrNull().orEmpty()
        Result.success(list.map { it.toMember() })
    }

    override suspend fun updateMember(memberId: String, userLevel: Int, roleTitle: String): Result<Unit> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập"))
        val event = _currentEvent.value ?: return@withContext Result.failure(Exception("Không có sự kiện"))
        val body = mapOf("userLevel" to userLevel, "roleTitle" to roleTitle)
        val result = ApiClient.patch<Any>("/api/v1/events/${event.id}/members/$memberId?actorUserId=${session.userId}", body)
        if (result.isFailure) {
            checkApiError(result, appContext)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi cập nhật thành viên"))
        }
        refreshEvent()
        Result.success(Unit)
    }

    override suspend fun removeMember(memberId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val session = EventSession.getSession() ?: return@withContext Result.failure(Exception("Chưa đăng nhập"))
        val event = _currentEvent.value ?: return@withContext Result.failure(Exception("Không có sự kiện"))
        val result = ApiClient.delete<Any>("/api/v1/events/${event.id}/members/$memberId?actorUserId=${session.userId}")
        if (result.isFailure) {
            checkApiError(result, appContext)
            return@withContext Result.failure(result.exceptionOrNull() ?: Exception("Lỗi xóa thành viên"))
        }
        refreshEvent()
        Result.success(Unit)
    }

    override fun logout(context: Context) {
        EventSession.clearSession(context)
        appContext = null
        _currentEvent.value = null
        _currentMember.value = null
    }
}
