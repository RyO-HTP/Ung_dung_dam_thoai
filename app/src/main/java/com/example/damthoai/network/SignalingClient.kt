package com.example.damthoai.network

import android.util.Log
import com.example.damthoai.network.model.BaseSignalingMessage
import com.example.damthoai.network.model.IceCandidateSignalingMessage
import com.example.damthoai.network.model.RegisterMessage
import com.example.damthoai.network.model.SdpSignalingMessage
import com.example.damthoai.network.model.SignalingType
import com.example.damthoai.network.model.WebRtcConfigMessage
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
 * Các sự kiện nhận được từ Signaling Server
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
}

/**
 * Quản lý kết nối WebSocket và giao thức Signaling cho Tuần 1.
 * Đảm bảo gửi Handshake đăng ký, nhận cấu hình STUN/TURN và định tuyến P2P.
 */
class SignalingClient(
    private val clientScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "SignalingClient"
        private const val NORMAL_CLOSURE_STATUS = 1000
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Giữ kết nối WebSocket liên tục
        .pingInterval(15, TimeUnit.SECONDS)     // Heartbeat chống rớt mạng
        .retryOnConnectionFailure(true)
        .build()

    private var webSocket: WebSocket? = null
    private var currentServerUrl: String? = null
    private var currentUserId: String? = null
    private var shouldReconnect: Boolean = true
    private var reconnectJob: Job? = null

    // State flow cho trạng thái kết nối
    private val _connectionState = MutableStateFlow<SignalingConnectionState>(SignalingConnectionState.Disconnected)
    val connectionState: StateFlow<SignalingConnectionState> = _connectionState.asStateFlow()

    // Shared flow phát các sự kiện nhận từ server
    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SignalingEvent> = _events.asSharedFlow()

    /**
     * Bắt đầu kết nối tới máy chủ Signaling và tự động gửi Handshake đăng ký
     * @param serverUrl URL dạng ws://[IP]:8765
     * @param userId Định danh người dùng (vd: "Tong_Dao_Dien")
     */
    fun connect(serverUrl: String, userId: String) {
        currentServerUrl = serverUrl
        currentUserId = userId
        shouldReconnect = true
        reconnectJob?.cancel()

        disconnectInternal()
        _connectionState.value = SignalingConnectionState.Connecting

        val request = try {
            Request.Builder().url(serverUrl).build()
        } catch (e: Exception) {
            _connectionState.value = SignalingConnectionState.Error("URL không hợp lệ: $serverUrl", e)
            return
        }

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    /**
     * Tạo listener xử lý các sự kiện WebSocket
     */
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket đã kết nối thành công tới $currentServerUrl")
                _connectionState.value = SignalingConnectionState.Connected

                // B. ĐĂNG KÝ (HANDSHAKE) THEO YÊU CẦU TUẦN 1
                currentUserId?.let { uid ->
                    val registerJson = gson.toJson(RegisterMessage(userId = uid))
                    Log.d(TAG, "Gửi gói tin đăng ký Handshake: $registerJson")
                    webSocket.send(registerJson)
                    _connectionState.value = SignalingConnectionState.Registered(uid)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Nhận tin nhắn: $text")
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket đang đóng: code=$code, reason=$reason")
                webSocket.close(NORMAL_CLOSURE_STATUS, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket đã đóng.")
                _connectionState.value = SignalingConnectionState.Disconnected
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Lỗi kết nối WebSocket: ${t.message}", t)
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
            val baseMsg = gson.fromJson(jsonStr, BaseSignalingMessage::class.java) ?: return

            when (baseMsg.type) {
                // C. LẤY CẤU HÌNH WEBRTC (STUN/TURN)
                SignalingType.WEBRTC_CONFIG -> {
                    val configMsg = gson.fromJson(jsonStr, WebRtcConfigMessage::class.java)
                    val rawConfigs = configMsg.config?.iceServers ?: emptyList()
                    val iceServers = rawConfigs.mapNotNull { it.toPeerConnectionIceServer() }
                    Log.d(TAG, "Nhận cấu hình WebRTC thành công: ${iceServers.size} ICE servers")
                    _events.tryEmit(SignalingEvent.ConfigReceived(iceServers))
                }

                // D. ĐỊNH TUYẾN P2P (OFFER / ANSWER / ICE_CANDIDATE)
                SignalingType.OFFER -> {
                    val sdpMsg = gson.fromJson(jsonStr, SdpSignalingMessage::class.java)
                    _events.tryEmit(SignalingEvent.OfferReceived(sdpMsg.senderId, sdpMsg.sdp))
                }

                SignalingType.ANSWER -> {
                    val sdpMsg = gson.fromJson(jsonStr, SdpSignalingMessage::class.java)
                    _events.tryEmit(SignalingEvent.AnswerReceived(sdpMsg.senderId, sdpMsg.sdp))
                }

                SignalingType.ICE_CANDIDATE -> {
                    val iceMsg = gson.fromJson(jsonStr, IceCandidateSignalingMessage::class.java)
                    _events.tryEmit(
                        SignalingEvent.IceCandidateReceived(
                            senderId = iceMsg.senderId,
                            sdpMid = iceMsg.sdpMid,
                            sdpMLineIndex = iceMsg.sdpMLineIndex,
                            candidate = iceMsg.candidate
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
     * Gửi Offer SDP cho người nhận
     */
    fun sendOffer(targetId: String, sdp: String): Boolean {
        val msg = SdpSignalingMessage(
            type = SignalingType.OFFER,
            targetId = targetId,
            senderId = currentUserId,
            sdp = sdp
        )
        return sendMessage(gson.toJson(msg))
    }

    /**
     * Gửi Answer SDP phản hồi Offer
     */
    fun sendAnswer(targetId: String, sdp: String): Boolean {
        val msg = SdpSignalingMessage(
            type = SignalingType.ANSWER,
            targetId = targetId,
            senderId = currentUserId,
            sdp = sdp
        )
        return sendMessage(gson.toJson(msg))
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
            senderId = currentUserId,
            sdpMid = sdpMid,
            sdpMLineIndex = sdpMLineIndex,
            candidate = candidate
        )
        return sendMessage(gson.toJson(msg))
    }

    /**
     * Gửi chuỗi tin nhắn JSON qua WebSocket
     */
    private fun sendMessage(json: String): Boolean {
        val ws = webSocket
        if (ws == null) {
            Log.w(TAG, "Không thể gửi, WebSocket chưa được khởi tạo!")
            return false
        }
        val success = ws.send(json)
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
        val uid = currentUserId ?: return

        reconnectJob?.cancel()
        reconnectJob = clientScope.launch {
            Log.d(TAG, "Sẽ thử kết nối lại sau ${RECONNECT_DELAY_MS / 1000}s...")
            delay(RECONNECT_DELAY_MS)
            if (shouldReconnect) {
                Log.d(TAG, "Đang thử kết nối lại tới $url...")
                connect(url, uid)
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
        try {
            webSocket?.close(NORMAL_CLOSURE_STATUS, "Chủ động ngắt kết nối")
        } catch (ignored: Exception) {}
        webSocket = null
    }
}
