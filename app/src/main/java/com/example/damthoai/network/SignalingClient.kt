package com.example.damthoai.network

import android.util.Log
import com.example.damthoai.network.model.IceCandidatePayload
import com.example.damthoai.network.model.RegisterMessage
import com.example.damthoai.network.model.SignalingType
import com.example.damthoai.network.model.SimpleSignalingMessage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.webrtc.PeerConnection
import java.util.concurrent.TimeUnit

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
 * Các sự kiện nhận được từ Signaling Server (hoặc từ thiết bị khác qua LAN)
 */
sealed class SignalingEvent {
    data class ConfigReceived(val iceServers: List<PeerConnection.IceServer>) : SignalingEvent()
    data class OfferReceived(val senderId: String?, val sdp: String) : SignalingEvent()
    data class AnswerReceived(val senderId: String?, val sdp: String) : SignalingEvent()
    data class IceCandidateReceived(
        val senderId: String?,
        val sdpMid: String?,
        val sdpMLineIndex: Int,
        val candidate: String
    ) : SignalingEvent()
    /** Tuần 3: đối tác thu hồi các candidate không còn hiệu lực (ICE Gathering liên tục) */
    data class IceCandidatesRemoved(
        val senderId: String?,
        val candidates: List<IceCandidatePayload>
    ) : SignalingEvent()
    data class HangupReceived(val senderId: String?) : SignalingEvent()
    /** Danh sách user_id đang online trên Signaling Server (kể cả chính mình) */
    data class PeersReceived(val peers: List<String>) : SignalingEvent()
    /** Tuần 5 (TV2): nhận tin nhắn thoại gửi bù từ đồng đội vừa có mạng trở lại */
    data class VoiceNoteReceived(
        val id: String,
        val senderId: String?,
        val channelId: String,
        val recordedAtMs: Long,
        val durationMs: Long,
        val sampleRateHz: Int,
        val audioBase64: String
    ) : SignalingEvent()
}

/**
 * Quản lý kết nối WebSocket và giao thức Signaling.
 *  - Tuần 1: Handshake đăng ký, nhận cấu hình STUN/TURN, định tuyến SDP/ICE P2P.
 *  - Tuần 4: Hàng đợi gói tin gửi đi khi WebSocket đang nối lại (đổi Wi-Fi <-> 4G) và
 *    [reconnectNow] để mở ngay kết nối trên mạng mới thay vì chờ socket cũ timeout.
 */
class SignalingClient(
    private val clientScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private val gson: Gson = Gson()
) : SignalingChannel {
    companion object {
        private const val TAG = "SignalingClient"
        private const val NORMAL_CLOSURE_STATUS = 1000
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 10_000L
        private const val MAX_PENDING_MESSAGES = 200
        private const val PENDING_MESSAGE_TTL_MS = 30_000L
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Giữ kết nối WebSocket liên tục
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)     // Heartbeat phát hiện nhanh socket chết khi đổi mạng
        .retryOnConnectionFailure(true)
        .build()

    private val lock = Any()
    private var webSocket: WebSocket? = null
    private var isOpen: Boolean = false
    private var currentServerUrl: String? = null
    val serverUrl: String? get() = currentServerUrl
    private var currentUserId: String? = null
    val userId: String? get() = currentUserId
    private var shouldReconnect: Boolean = true
    private var reconnectJob: Job? = null
    private var reconnectAttempt: Int = 0

    /** Gói tin chờ gửi khi socket chưa mở: (thời điểm, json) */
    private val pendingMessages = ArrayDeque<Pair<Long, String>>()

    // State flow cho trạng thái kết nối
    private val _connectionState = MutableStateFlow<SignalingConnectionState>(SignalingConnectionState.Disconnected)
    val connectionState: StateFlow<SignalingConnectionState> = _connectionState.asStateFlow()

    // Shared flow phát các sự kiện nhận từ server
    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SignalingEvent> = _events.asSharedFlow()

    /**
     * Bắt đầu kết nối tới máy chủ Signaling và tự động gửi Handshake đăng ký
     * @param serverUrl URL dạng ws://[IP]:8765
     * @param userId Định danh người dùng (vd: "Tong_Dao_Dien")
     */
    fun connect(serverUrl: String, userId: String) {
        synchronized(lock) {
            currentServerUrl = serverUrl
            currentUserId = userId
            shouldReconnect = true
            reconnectJob?.cancel()
            reconnectAttempt = 0
            openSocketLocked()
        }
    }

    /**
     * Tuần 4 - Seamless Handover: đóng socket cũ (gắn với mạng vừa mất) và mở ngay socket mới
     * trên mạng hiện tại, không chờ socket cũ timeout.
     */
    fun reconnectNow() {
        synchronized(lock) {
            if (currentServerUrl == null || currentUserId == null) return
            shouldReconnect = true
            reconnectJob?.cancel()
            reconnectAttempt = 0
            Log.d(TAG, "Đổi mạng -> mở lại WebSocket ngay lập tức")
            openSocketLocked()
        }
    }

    private fun openSocketLocked() {
        val url = currentServerUrl ?: return
        disconnectInternalLocked()
        _connectionState.value = SignalingConnectionState.Connecting

        val request = try {
            Request.Builder().url(url).build()
        } catch (e: Exception) {
            _connectionState.value = SignalingConnectionState.Error("URL không hợp lệ: $url", e)
            return
        }
        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    /**
     * Tạo listener xử lý các sự kiện WebSocket.
     * Callback của socket cũ (đã bị thay thế khi đổi mạng) đều bị bỏ qua.
     */
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) {
                    if (webSocket !== this@SignalingClient.webSocket) return
                    Log.d(TAG, "WebSocket đã kết nối thành công tới $currentServerUrl")
                    isOpen = true
                    reconnectAttempt = 0
                    _connectionState.value = SignalingConnectionState.Connected

                    // ĐĂNG KÝ (HANDSHAKE) THEO YÊU CẦU TUẦN 1
                    currentUserId?.let { uid ->
                        val registerJson = gson.toJson(RegisterMessage(userId = uid))
                        Log.d(TAG, "Gửi gói tin đăng ký Handshake: $registerJson")
                        webSocket.send(registerJson)
                        _connectionState.value = SignalingConnectionState.Registered(uid)
                    }
                    flushPendingLocked(webSocket)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== this@SignalingClient.webSocket) return
                Log.d(TAG, "Nhận tin nhắn: $text")
                SignalingMessageParser.parse(gson, text)?.let { _events.tryEmit(it) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket đang đóng: code=$code, reason=$reason")
                webSocket.close(NORMAL_CLOSURE_STATUS, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                synchronized(lock) {
                    if (webSocket !== this@SignalingClient.webSocket) return
                    Log.d(TAG, "WebSocket đã đóng.")
                    isOpen = false
                    _connectionState.value = SignalingConnectionState.Disconnected
                    scheduleReconnectLocked()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                synchronized(lock) {
                    if (webSocket !== this@SignalingClient.webSocket) return
                    Log.e(TAG, "Lỗi kết nối WebSocket: ${t.message}", t)
                    isOpen = false
                    _connectionState.value = SignalingConnectionState.Error(t.message ?: "Mất kết nối", t)
                    scheduleReconnectLocked()
                }
            }
        }
    }

    /**
     * Gửi Offer SDP cho người nhận
     */
    override fun sendOffer(targetId: String, sdp: String): Boolean =
        sendMessage(SignalingMessageParser.offer(gson, currentUserId, targetId, sdp))

    /**
     * Gửi Answer SDP phản hồi Offer
     */
    override fun sendAnswer(targetId: String, sdp: String): Boolean =
        sendMessage(SignalingMessageParser.answer(gson, currentUserId, targetId, sdp))

    /**
     * Gửi ICE Candidate cho thiết bị đích
     */
    override fun sendIceCandidate(
        targetId: String,
        sdpMid: String?,
        sdpMLineIndex: Int,
        candidate: String
    ): Boolean = sendMessage(
        SignalingMessageParser.iceCandidate(gson, currentUserId, targetId, sdpMid, sdpMLineIndex, candidate)
    )

    override fun sendIceCandidatesRemoved(targetId: String, candidates: List<IceCandidatePayload>): Boolean =
        sendMessage(SignalingMessageParser.iceCandidatesRemoved(gson, currentUserId, targetId, candidates))

    override fun sendHangup(targetId: String): Boolean =
        sendMessage(SignalingMessageParser.hangup(gson, currentUserId, targetId))

    override fun sendVoiceNote(
        targetId: String,
        messageId: String,
        channelId: String,
        recordedAtMs: Long,
        durationMs: Long,
        sampleRateHz: Int,
        audioBase64: String
    ): Boolean = sendMessage(
        SignalingMessageParser.voiceNote(
            gson = gson,
            senderId = currentUserId,
            targetId = targetId,
            messageId = messageId,
            channelId = channelId,
            recordedAtMs = recordedAtMs,
            durationMs = durationMs,
            sampleRateHz = sampleRateHz,
            audioBase64 = audioBase64
        )
    )

    /** Báo server biết thiết bị vừa đổi mạng (phục vụ log thực địa) */
    fun sendNetworkChange(networkType: String): Boolean = sendMessage(
        gson.toJson(
            SimpleSignalingMessage(
                type = SignalingType.NETWORK_CHANGE,
                userId = currentUserId,
                networkType = networkType
            )
        )
    )

    /**
     * Gửi chuỗi JSON qua WebSocket. Nếu socket chưa mở (đang nối lại sau khi đổi mạng)
     * thì xếp vào hàng đợi và gửi ngay khi kết nối lại thành công.
     * @return true nếu đã gửi hoặc đã xếp hàng chờ gửi.
     */
    private fun sendMessage(json: String): Boolean = synchronized(lock) {
        val ws = webSocket
        if (ws != null && isOpen && ws.send(json)) return@synchronized true
        if (currentServerUrl == null) {
            Log.w(TAG, "Không thể gửi, WebSocket chưa được khởi tạo!")
            return@synchronized false
        }
        pendingMessages.addLast(System.currentTimeMillis() to json)
        while (pendingMessages.size > MAX_PENDING_MESSAGES) pendingMessages.removeFirst()
        Log.d(TAG, "WebSocket chưa sẵn sàng, xếp hàng gói tin (${pendingMessages.size} đang chờ)")
        true
    }

    private fun flushPendingLocked(ws: WebSocket) {
        val now = System.currentTimeMillis()
        var sent = 0
        while (pendingMessages.isNotEmpty()) {
            val (ts, json) = pendingMessages.removeFirst()
            if (now - ts > PENDING_MESSAGE_TTL_MS) continue // SDP/ICE quá cũ thì bỏ
            if (ws.send(json)) sent++
        }
        if (sent > 0) Log.d(TAG, "Đã gửi bù $sent gói tin sau khi kết nối lại")
    }

    /**
     * Tự động lên lịch kết nối lại khi rớt mạng (exponential backoff 1s -> 10s)
     */
    private fun scheduleReconnectLocked() {
        if (!shouldReconnect) return
        if (currentServerUrl == null || currentUserId == null) return

        reconnectJob?.cancel()
        val delayMs = (RECONNECT_BASE_DELAY_MS shl reconnectAttempt.coerceAtMost(4))
            .coerceAtMost(RECONNECT_MAX_DELAY_MS)
        reconnectAttempt++
        reconnectJob = clientScope.launch {
            Log.d(TAG, "Sẽ thử kết nối lại sau ${delayMs}ms...")
            delay(delayMs)
            synchronized(lock) {
                if (shouldReconnect) {
                    Log.d(TAG, "Đang thử kết nối lại tới $currentServerUrl...")
                    openSocketLocked()
                }
            }
        }
    }

    /**
     * Ngắt kết nối và giải phóng tài nguyên
     */
    fun disconnect() {
        synchronized(lock) {
            shouldReconnect = false
            reconnectJob?.cancel()
            disconnectInternalLocked()
            pendingMessages.clear()
            _connectionState.value = SignalingConnectionState.Disconnected
        }
    }

    private fun disconnectInternalLocked() {
        val old = webSocket
        webSocket = null
        isOpen = false
        try {
            old?.close(NORMAL_CLOSURE_STATUS, "Chủ động ngắt kết nối")
        } catch (ignored: Exception) {}
    }
}
