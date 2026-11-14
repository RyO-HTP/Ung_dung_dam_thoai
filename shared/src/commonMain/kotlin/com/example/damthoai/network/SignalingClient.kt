package com.example.damthoai.network

import com.example.damthoai.network.model.*
import com.example.damthoai.platform.IceServerSpec
import com.example.damthoai.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

/**
 * Trạng thái kết nối của Signaling Client
 */
sealed class SignalingConnectionState {
    object Disconnected : SignalingConnectionState()
    object Connecting : SignalingConnectionState()
    object Connected : SignalingConnectionState()
    data class Registered(val userId: String) : SignalingConnectionState()
    data class Error(val message: String, val throwable: Throwable? = null) : SignalingConnectionState()
}

/**
 * Các sự kiện nhận được từ Signaling Server
 */
sealed class SignalingEvent {
    data class ConfigReceived(val iceServers: List<IceServerSpec>) : SignalingEvent()
    data class OfferReceived(val senderId: String?, val sdp: String) : SignalingEvent()
    data class AnswerReceived(val senderId: String?, val sdp: String) : SignalingEvent()
    data class IceCandidateReceived(
        val senderId: String?,
        val sdpMid: String?,
        val sdpMLineIndex: Int,
        val candidate: String
    ) : SignalingEvent()
    data class UserListReceived(
        val users: List<String>,
        val members: List<OnlineMember>
    ) : SignalingEvent()
    data class EventInfoReceived(
        val eventPin: String,
        val eventName: String,
        val found: Boolean
    ) : SignalingEvent()
}

/**
 * Quản lý kết nối WebSocket và giao thức Signaling cho Tuần 1.
 * Đảm bảo gửi Handshake đăng ký, nhận cấu hình STUN/TURN và định tuyến P2P.
 */
class SignalingClient(
    private val clientScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val json: Json = SignalingJson
) {
    companion object {
        private const val TAG = "SignalingClient"
        private const val PING_INTERVAL_MS = 15_000L // Heartbeat chống rớt mạng
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private val httpClient: HttpClient by lazy { createHttpClient(webSocketPingMs = PING_INTERVAL_MS) }

    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    @Volatile
    private var socketJob: Job? = null
    // Tăng mỗi lần connect(); callback của socket thuộc thế hệ cũ sẽ bị bỏ qua
    @Volatile
    private var connectionGeneration = 0
    private var currentServerUrl: String? = null
    // Gói đăng ký đầy đủ (PIN sự kiện, tên, vai trò) — gửi lại mỗi lần kết nối lại
    private var currentRegistration: RegisterMessage? = null
    private var shouldReconnect: Boolean = true
    private var reconnectJob: Job? = null

    // State flow cho trạng thái kết nối
    private val _connectionState = MutableStateFlow<SignalingConnectionState>(SignalingConnectionState.Disconnected)
    val connectionState: StateFlow<SignalingConnectionState> = _connectionState.asStateFlow()

    // Shared flow phát các sự kiện nhận từ server
    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SignalingEvent> = _events.asSharedFlow()

    /**
     * Kiểm tra xem WebSocket đã thực sự kết nối và sẵn sàng truyền dữ liệu chưa
     */
    fun isConnected(): Boolean {
        return when (_connectionState.value) {
            is SignalingConnectionState.Connected,
            is SignalingConnectionState.Registered -> true
            else -> false
        }
    }

    /**
     * Bắt đầu kết nối tới máy chủ Signaling và tự động gửi Handshake đăng ký
     * @param serverUrl URL dạng ws://[IP]:8765
     * @param userId Định danh người dùng (vd: "Tong_Dao_Dien")
     */
    fun connect(serverUrl: String, userId: String) {
        connect(serverUrl, RegisterMessage(userId = userId))
    }

    /**
     * Kết nối và đăng ký kèm hồ sơ (mã PIN sự kiện, tên hiển thị, vai trò).
     */
    fun connect(serverUrl: String, registration: RegisterMessage) {
        currentServerUrl = serverUrl
        currentRegistration = registration
        shouldReconnect = true
        reconnectJob?.cancel()

        disconnectInternal()
        _connectionState.value = SignalingConnectionState.Connecting

        val generation = ++connectionGeneration
        socketJob = clientScope.launch {
            fun isStale() = generation != connectionGeneration
            try {
                httpClient.webSocket(urlString = serverUrl) {
                    if (isStale()) return@webSocket
                    session = this
                    Log.d(TAG, "WebSocket đã kết nối thành công tới $currentServerUrl")
                    _connectionState.value = SignalingConnectionState.Connected

                    // B. ĐĂNG KÝ (HANDSHAKE) THEO YÊU CẦU TUẦN 1
                    currentRegistration?.let { registration ->
                        val registerJson = json.encodeToString(RegisterMessage.serializer(), registration)
                        Log.d(TAG, "Gửi gói tin đăng ký Handshake: $registerJson")
                        send(Frame.Text(registerJson))
                        _connectionState.value = SignalingConnectionState.Registered(registration.userId)
                    }

                    for (frame in incoming) {
                        if (isStale()) break
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            Log.d(TAG, "Nhận tin nhắn: $text")
                            handleIncomingMessage(text)
                        }
                    }
                }
                // Socket cũ (đã bị thay bằng kết nối mới) đóng: không được đổi trạng thái hay kết nối lại,
                // nếu không sẽ đóng nhầm kết nối đang hoạt động và tạo vòng lặp ngắt/nối mỗi 3 giây.
                if (isStale()) return@launch
                Log.d(TAG, "WebSocket đã đóng.")
                session = null
                _connectionState.value = SignalingConnectionState.Disconnected
                scheduleReconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (isStale()) return@launch
                Log.e(TAG, "Lỗi kết nối WebSocket: ${t.message}", t)
                session = null
                _connectionState.value = SignalingConnectionState.Error(t.message ?: "Mất kết nối", t)
                scheduleReconnect()
            }
        }
    }

    /**
     * Xử lý gói tin nhận được từ Server theo tài liệu Tuần 1
     */
    private fun handleIncomingMessage(jsonStr: String) {
        try {
            val baseMsg = json.decodeFromString(BaseSignalingMessage.serializer(), jsonStr)

            when (baseMsg.type) {
                // C. LẤY CẤU HÌNH WEBRTC (STUN/TURN)
                SignalingType.WEBRTC_CONFIG -> {
                    val configMsg = json.decodeFromString(WebRtcConfigMessage.serializer(), jsonStr)
                    val rawConfigs = configMsg.config?.iceServers ?: emptyList()
                    val iceServers = rawConfigs.mapNotNull { it.toIceServerSpec() }
                    Log.d(TAG, "Nhận cấu hình WebRTC thành công: ${iceServers.size} ICE servers")
                    _events.tryEmit(SignalingEvent.ConfigReceived(iceServers))
                }

                // D. ĐỊNH TUYẾN P2P (OFFER / ANSWER / ICE_CANDIDATE)
                SignalingType.OFFER -> {
                    val sdpMsg = json.decodeFromString(SdpSignalingMessage.serializer(), jsonStr)
                    _events.tryEmit(SignalingEvent.OfferReceived(sdpMsg.senderId, sdpMsg.sdp))
                }

                SignalingType.ANSWER -> {
                    val sdpMsg = json.decodeFromString(SdpSignalingMessage.serializer(), jsonStr)
                    _events.tryEmit(SignalingEvent.AnswerReceived(sdpMsg.senderId, sdpMsg.sdp))
                }

                SignalingType.ICE_CANDIDATE -> {
                    val iceMsg = json.decodeFromString(IceCandidateSignalingMessage.serializer(), jsonStr)
                    _events.tryEmit(
                        SignalingEvent.IceCandidateReceived(
                            senderId = iceMsg.senderId,
                            sdpMid = iceMsg.sdpMid,
                            sdpMLineIndex = iceMsg.sdpMLineIndex,
                            candidate = iceMsg.candidate
                        )
                    )
                }

                SignalingType.USER_LIST -> {
                    val userListMsg = json.decodeFromString(UserListMessage.serializer(), jsonStr)
                    // Server mới gửi "members" (có tên, vai trò); server cũ chỉ gửi "users" (ID)
                    val members = userListMsg?.members?.filter { it.userId.isNotBlank() }
                        ?: userListMsg?.users.orEmpty().map { OnlineMember(userId = it) }
                    val userList = members.map { it.userId }
                    Log.d(TAG, "Nhận danh sách người dùng online: $userList")
                    _events.tryEmit(SignalingEvent.UserListReceived(userList, members))
                }

                SignalingType.EVENT_INFO -> {
                    val info = json.decodeFromString(EventInfoMessage.serializer(), jsonStr)
                    _events.tryEmit(
                        SignalingEvent.EventInfoReceived(
                            eventPin = info.eventPin.orEmpty(),
                            eventName = info.eventName.orEmpty(),
                            found = info.found
                        )
                    )
                }

                else -> {
                    Log.w(TAG, "Loại gói tin không xác định: ${baseMsg.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi giải mã gói tin signaling: ${e.message}", e)
        }
    }

    /**
     * Cập nhật hồ sơ đăng ký (đổi tên, vai trò, sự kiện) và gửi lại ngay nếu đang kết nối.
     * Nếu đang kết nối tới máy chủ khác hoặc chưa kết nối thì chỉ lưu lại để dùng ở lần kết nối sau.
     */
    fun updateRegistration(registration: RegisterMessage) {
        currentRegistration = registration
        if (isConnected()) {
            sendMessage(json.encodeToString(RegisterMessage.serializer(), registration))
        }
    }

    /** URL máy chủ đang dùng (null nếu chưa từng kết nối). */
    fun currentServerUrl(): String? = currentServerUrl

    /**
     * Gửi Offer SDP cho người nhận
     */
    fun sendOffer(targetId: String, sdp: String): Boolean {
        val msg = SdpSignalingMessage(
            type = SignalingType.OFFER,
            targetId = targetId,
            senderId = currentRegistration?.userId,
            sdp = sdp
        )
        return sendMessage(json.encodeToString(SdpSignalingMessage.serializer(), msg))
    }

    /**
     * Gửi Answer SDP phản hồi Offer
     */
    fun sendAnswer(targetId: String, sdp: String): Boolean {
        val msg = SdpSignalingMessage(
            type = SignalingType.ANSWER,
            targetId = targetId,
            senderId = currentRegistration?.userId,
            sdp = sdp
        )
        return sendMessage(json.encodeToString(SdpSignalingMessage.serializer(), msg))
    }

    /**
     * Gửi ICE Candidate cho thiết bị đích
     */
    fun sendIceCandidate(
        targetId: String,
        sdpMid: String?,
        sdpMLineIndex: Int,
        candidate: String
    ): Boolean {
        val msg = IceCandidateSignalingMessage(
            targetId = targetId,
            senderId = currentRegistration?.userId,
            sdpMid = sdpMid,
            sdpMLineIndex = sdpMLineIndex,
            candidate = candidate
        )
        return sendMessage(json.encodeToString(IceCandidateSignalingMessage.serializer(), msg))
    }

    /**
     * Gửi chuỗi tin nhắn JSON qua WebSocket
     */
    private fun sendMessage(json: String): Boolean {
        val ws = session
        if (ws == null) {
            Log.w(TAG, "Không thể gửi, WebSocket chưa được khởi tạo!")
            return false
        }
        val success = ws.outgoing.trySend(Frame.Text(json)).isSuccess
        if (!success) {
            Log.w(TAG, "Gửi tin nhắn thất bại: $json")
        }
        return success
    }

    /**
     * Tự động lên lịch kết nối lại khi rớt mạng
     */
    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        val url = currentServerUrl ?: return
        val registration = currentRegistration ?: return

        reconnectJob?.cancel()
        reconnectJob = clientScope.launch {
            Log.d(TAG, "Sẽ thử kết nối lại sau ${RECONNECT_DELAY_MS / 1000}s...")
            delay(RECONNECT_DELAY_MS)
            if (shouldReconnect) {
                Log.d(TAG, "Đang thử kết nối lại tới $url...")
                // Dùng lại hồ sơ đầy đủ (PIN, tên, vai trò). Đăng ký chỉ bằng ID sẽ mất PIN
                // → máy rơi khỏi nhóm sự kiện và biến mất khỏi danh sách thành viên.
                connect(url, currentRegistration ?: registration)
            }
        }
    }

    /**
     * Ngắt kết nối và giải phóng tài nguyên
     */
    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        disconnectInternal()
        _connectionState.value = SignalingConnectionState.Disconnected
    }

    private fun disconnectInternal() {
        connectionGeneration++ // Vô hiệu hóa callback của socket sắp đóng
        val oldSession = session
        val oldJob = socketJob
        session = null
        socketJob = null
        if (oldSession != null) {
            clientScope.launch {
                runCatching { oldSession.close(CloseReason(CloseReason.Codes.NORMAL, "Chủ động ngắt kết nối")) }
                oldJob?.cancel()
            }
        } else {
            oldJob?.cancel()
        }
    }
}