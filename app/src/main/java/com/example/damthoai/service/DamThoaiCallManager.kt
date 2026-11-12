package com.example.damthoai.service

import com.example.damthoai.webrtc.CustomPeerConnectionObserver
import com.example.damthoai.network.SignalingEvent
import com.example.damthoai.network.SignalingClient
import com.example.damthoai.network.MdnsDiscoveryManager
import com.example.damthoai.webrtc.WebRTCClient
import android.content.Context
import android.media.AudioManager
import android.util.Log
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
import com.example.damthoai.hardware.PttController

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
 * Cung cấp API trực tiếp cho UI Jetpack Compose (PTT, Trạng thái đàm thoại, Quét máy chủ, Cảnh báo khẩn cấp).
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
    
    // Quản lý lắng nghe chuyển mạng (Seamless Handover)
    private var networkChangeReceiver: com.example.damthoai.network.NetworkChangeReceiver? = null

    init {
        observeSignalingEvents()
        // Lắng nghe tín hiệu phím cứng từ PttController
        scope.launch {
            com.example.damthoai.hardware.PttController.events.collect { event ->
                when (event.javaClass.simpleName) {
                    "PttPressed" -> setPushToTalkPressed(true)
                    "PttReleased" -> setPushToTalkPressed(false)
                }
            }
        }
    }

    /**
     * Bắt đầu tự động dò tìm máy chủ qua mDNS và tự động kết nối
     */
    fun autoDiscoverAndConnect(userId: String) {
        scope.launch {
            mdnsDiscovery.serverFound.collect { server ->
                Log.d(TAG, "Tự động phát hiện server qua mDNS: ${server.wsUrl}, tiến hành kết nối...")
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

        // Bật chặn phím cứng đúng chuẩn API của PttController
        com.example.damthoai.hardware.PttController.setPttModeEnabled(true)
        com.example.damthoai.hardware.PttController.acquireCapture("DamThoaiCallManager")

        setupPeerConnection(targetUserId)
        
        // Kích hoạt theo dõi chuyển mạng và định tuyến âm thanh thông minh
        startNetworkMonitoring()
        setupSmartAudioRouting()

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

        // BẬT CHẶN PHÍM CỨNG PTT KHI NHẬN CUỘC GỌI
        com.example.damthoai.hardware.PttController.setPttModeEnabled(true)
        com.example.damthoai.hardware.PttController.acquireCapture("DamThoaiCallManager")

        setupPeerConnection(senderUserId)
        
        // Kích hoạt theo dõi chuyển mạng và định tuyến âm thanh thông minh
        startNetworkMonitoring()
        setupSmartAudioRouting()

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
        webRTCClient.createPeerConnection(object : CustomPeerConnectionObserver() {
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
     * Kích hoạt phát cảnh báo khẩn cấp (Emergency Alert - Tuần 4)
     * Tích hợp với EmergencyAlertManager của TV4
     */
    fun triggerEmergency(
        fromName: String = "Tổng Đạo Diễn",
        fromRole: String = "Chỉ Huy",
        isDirectCall: Boolean = true
    ) {
        val alert = com.example.damthoai.hardware.emergency.EmergencyAlert(
            id = "alert_${System.currentTimeMillis()}",
            fromName = fromName,
            fromRole = fromRole,
            startedAtMs = System.currentTimeMillis(),
            isDirectCall = isDirectCall
        )
        Log.d(TAG, "Kích hoạt Cảnh báo Khẩn cấp: $fromName ($fromRole)")
        com.example.damthoai.hardware.emergency.EmergencyAlertManager.trigger(context, alert)
    }

    /**
     * Kết thúc cuộc gọi
     */
    fun endCall() {
        webRTCClient.close()
        activePeerUserId = null
        _callState.value = CallState.Idle
        _isPttPressed.value = false

        // TẮT PTT, TRẢ LẠI PHÍM ÂM LƯỢNG CHO HỆ ĐIỀU HÀNH
        com.example.damthoai.hardware.PttController.releaseCapture("DamThoaiCallManager")
        com.example.damthoai.hardware.PttController.setPttModeEnabled(false)
        PttController.forceReleasePtt()
        
        // Dọn dẹp tài nguyên mạng, mDNS và đưa âm thanh về mặc định
        networkChangeReceiver?.stop()
        networkChangeReceiver = null
        releaseAudioRouting()
        mdnsDiscovery.stopDiscovery()
    }

    /**
     * Ngắt toàn bộ dịch vụ khi đóng ứng dụng
     */
    fun destroy() {
        endCall()
        signalingClient.disconnect()
        mdnsDiscovery.stopDiscovery()
    }
    
    // --- CÁC HÀM XỬ LÝ NGOẠI VI (MẠNG & ÂM THANH) ---

    private fun startNetworkMonitoring() {
        if (networkChangeReceiver == null) {
            networkChangeReceiver = com.example.damthoai.network.NetworkChangeReceiver(context) {
                triggerSeamlessHandover()
            }
            networkChangeReceiver?.start()
        }
    }

    private fun triggerSeamlessHandover() {
        val targetId = activePeerUserId ?: return
        Log.d(TAG, "Bắt đầu Seamless Handover tới $targetId...")
        
        // TUẦN 5: NẾU MẤT KẾT NỐI INTERNET CLOUD -> TỰ ĐỘNG BẬT mDNS FALLBACK QUÉT LAN
        if (!signalingClient.isConnected()) {
            Log.w(TAG, "Mất kết nối Internet diện rộng! Đang kích hoạt mDNS Local LAN Fallback...")
            autoDiscoverAndConnect(targetId)
            return
        }

        // Nếu vẫn có mạng bình thường thì chạy ICE Restart của Tuần 4
        webRTCClient.restartIce(
            onSuccess = { sdp ->
                Log.d(TAG, "Gửi Offer mới qua Signaling để nối lại cuộc gọi")
                signalingClient.sendOffer(targetId, sdp.description)
            },
            onError = { err ->
                Log.e(TAG, "Lỗi Handover: $err")
            }
        )
    }

    /**
     * Định tuyến âm thanh thông minh: 
     * Ưu tiên Tai nghe Bluetooth, nếu không có/lỗi thì tự động bật Loa ngoài (Speakerphone).
     * Được bọc try-catch an toàn để chống crash SIGABRT trên máy ảo.
     */
    private fun setupSmartAudioRouting() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            // Thử kết nối Bluetooth SCO nếu thiết bị hỗ trợ
            if (audioManager.isBluetoothScoAvailableOffCall) {
                try {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    audioManager.isSpeakerphoneOn = false
                    Log.d(TAG, "Đã định tuyến âm thanh sang: Tai nghe Bluetooth")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi kích hoạt Bluetooth SCO (bỏ qua): ${e.message}")
                }
            }

            // Mặc định ép phát ra Loa ngoài to rõ cho ứng dụng bộ đàm
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = true
            Log.d(TAG, "Đã định tuyến âm thanh sang: Loa ngoài (Speakerphone)")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi thiết lập audio routing: ${e.message}")
        }
    }

    private fun releaseAudioRouting() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "Đã giải phóng tài nguyên Audio về mặc định")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi dọn dẹp audio: ${e.message}")
        }
    }
}