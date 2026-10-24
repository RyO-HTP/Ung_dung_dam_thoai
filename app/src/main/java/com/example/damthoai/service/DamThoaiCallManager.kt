package com.example.damthoai.service

import android.content.Context
import android.util.Log
import com.example.damthoai.network.DiscoveredSignalingServer
import com.example.damthoai.network.MdnsDiscoveryManager
import com.example.damthoai.network.SignalingClient
import com.example.damthoai.network.SignalingConnectionState
import com.example.damthoai.network.SignalingEvent
import com.example.damthoai.webrtc.SimplePeerConnectionObserver
import com.example.damthoai.webrtc.WebRTCClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

/**
 * Trạng thái cuộc gọi đàm thoại
 */
sealed class CallState {
    object Idle : CallState()
    data class Calling(val targetUserId: String) : CallState()
    data class InCall(val peerUserId: String) : CallState()
    data class CallEnded(val reason: String? = null) : CallState()
}

/**
 * Lớp điều phối trung tâm giữa Tầng Mạng (Signaling WebSocket, mDNS) và WebRTC Audio cho App.
 * Cung cấp API trực tiếp cho UI Jetpack Compose (PTT, Trạng thái đàm thoại, Quét máy chủ).
 */
class DamThoaiCallManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DamThoaiCallManager"

        @Volatile
        private var instance: DamThoaiCallManager? = null

        fun getInstance(context: Context): DamThoaiCallManager {
            return instance ?: synchronized(this) {
                instance ?: DamThoaiCallManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val signalingClient = SignalingClient()
    val webRTCClient = WebRTCClient(context)
    val mdnsDiscovery = MdnsDiscoveryManager(context)

    // Trạng thái cuộc gọi
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    // Trạng thái giữ mic Push-to-Talk (PTT)
    private val _isPttPressed = MutableStateFlow(false)
    val isPttPressed: StateFlow<Boolean> = _isPttPressed.asStateFlow()

    // ID đối tác đang đàm thoại
    private var activePeerUserId: String? = null

    init {
        observeSignalingEvents()
    }

    /**
     * Bắt đầu tự động dò tìm máy chủ qua mDNS và tự động kết nối
     */
    fun autoDiscoverAndConnect(userId: String) {
        scope.launch {
            mdnsDiscovery.serverFound.collect { server ->
                Log.d(TAG, "Tự động phát hiện server: ${server.wsUrl}, tiến hành kết nối...")
                signalingClient.connect(server.wsUrl, userId)
                mdnsDiscovery.stopDiscovery()
            }
        }
        mdnsDiscovery.startDiscovery()
    }

    /**
     * Kết nối thủ công tới máy chủ WebSocket
     */
    fun connect(serverUrl: String, userId: String) {
        signalingClient.connect(serverUrl, userId)
    }

    /**
     * Lắng nghe các sự kiện từ Signaling Server
     */
    private fun observeSignalingEvents() {
        scope.launch {
            signalingClient.events.collect { event ->
                when (event) {
                    is SignalingEvent.ConfigReceived -> {
                        Log.d(TAG, "Nạp cấu hình ${event.iceServers.size} ICE Servers vào WebRTC Client")
                        webRTCClient.updateIceServers(event.iceServers)
                    }

                    is SignalingEvent.OfferReceived -> {
                        val sender = event.senderId ?: "Nguoi_Goi"
                        Log.d(TAG, "Nhận Offer từ $sender. Chuẩn bị trả lời...")
                        handleIncomingOffer(sender, event.sdp)
                    }

                    is SignalingEvent.AnswerReceived -> {
                        Log.d(TAG, "Nhận Answer SDP từ đối tác")
                        val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, event.sdp)
                        webRTCClient.setRemoteDescription(answerSdp, onSuccess = {
                            _callState.value = CallState.InCall(activePeerUserId ?: "DoiTac")
                        })
                    }

                    is SignalingEvent.IceCandidateReceived -> {
                        Log.d(TAG, "Nhận ICE Candidate từ đối tác")
                        val candidate = IceCandidate(
                            event.sdpMid,
                            event.sdpMLineIndex,
                            event.candidate
                        )
                        webRTCClient.addIceCandidate(candidate)
                    }
                }
            }
        }
    }

    /**
     * Bắt đầu gọi cho một người dùng khác
     */
    fun startCall(targetUserId: String) {
        activePeerUserId = targetUserId
        _callState.value = CallState.Calling(targetUserId)

        setupPeerConnection(targetUserId)

        webRTCClient.createOffer(
            onSuccess = { sdp ->
                Log.d(TAG, "Gửi Offer SDP tới $targetUserId qua Signaling")
                signalingClient.sendOffer(targetUserId, sdp.description)
            },
            onError = { err ->
                Log.e(TAG, "Lỗi tạo Offer: $err")
                _callState.value = CallState.CallEnded(err)
            }
        )
    }

    /**
     * Xử lý khi nhận được cuộc gọi đến (Offer)
     */
    private fun handleIncomingOffer(senderUserId: String, offerSdpStr: String) {
        activePeerUserId = senderUserId
        setupPeerConnection(senderUserId)

        val offerSdp = SessionDescription(SessionDescription.Type.OFFER, offerSdpStr)
        webRTCClient.setRemoteDescription(offerSdp, onSuccess = {
            webRTCClient.createAnswer(
                onSuccess = { answerSdp ->
                    Log.d(TAG, "Gửi Answer SDP phản hồi $senderUserId")
                    signalingClient.sendAnswer(senderUserId, answerSdp.description)
                    _callState.value = CallState.InCall(senderUserId)
                },
                onError = { err ->
                    Log.e(TAG, "Lỗi tạo Answer: $err")
                }
            )
        })
    }

    /**
     * Thiết lập PeerConnection và cấu hình chuyển tiếp ICE Candidates
     */
    private fun setupPeerConnection(targetUserId: String) {
        webRTCClient.createPeerConnection(object : SimplePeerConnectionObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { ice ->
                    Log.d(TAG, "Phát hiện ICE candidate cục bộ, gửi tới $targetUserId")
                    signalingClient.sendIceCandidate(
                        targetId = targetUserId,
                        sdpMid = ice.sdpMid,
                        sdpMLineIndex = ice.sdpMLineIndex,
                        candidate = ice.sdp
                    )
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "Trạng thái ICE Connection: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        _callState.value = CallState.InCall(targetUserId)
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> {
                        _callState.value = CallState.CallEnded("Mất kết nối mạng P2P")
                    }
                    else -> {}
                }
            }
        })
    }

    /**
     * Bấm giữ phím PTT (Push-to-Talk) để nói, nhả ra để chỉ nghe
     */
    fun setPushToTalkPressed(isPressed: Boolean) {
        _isPttPressed.value = isPressed
        webRTCClient.setMicrophoneEnabled(isPressed)
    }

    /**
     * Kết thúc cuộc gọi
     */
    fun endCall() {
        webRTCClient.close()
        activePeerUserId = null
        _callState.value = CallState.Idle
        _isPttPressed.value = false
    }

    /**
     * Ngắt toàn bộ dịch vụ khi đóng ứng dụng
     */
    fun destroy() {
        endCall()
        signalingClient.disconnect()
        mdnsDiscovery.stopDiscovery()
    }
}
