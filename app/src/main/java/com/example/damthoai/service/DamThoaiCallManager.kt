package com.example.damthoai.service

import android.content.Context
import java.util.UUID
import android.util.Log
import com.example.damthoai.audio.offline.OfflineVoiceQueueManager
import com.example.damthoai.audio.offline.QueuedVoiceMessage
import com.example.damthoai.audio.offline.VoiceQueueTransport
import com.example.damthoai.data.SpeakingStateRepository
import com.example.damthoai.network.NetworkStatus
import com.example.damthoai.hardware.HandsFreeSettings
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.audio.AudioFocusGuard
import com.example.damthoai.hardware.emergency.EmergencyAlertManager
import com.example.damthoai.network.LocalLanSignaling
import com.example.damthoai.network.MdnsDiscoveryManager
import com.example.damthoai.network.NetworkChangeReceiver
import com.example.damthoai.network.NetworkQualityMonitor
import com.example.damthoai.network.SignalingChannel
import com.example.damthoai.network.SignalingClient
import com.example.damthoai.network.SignalingConnectionState
import com.example.damthoai.network.SignalingEvent
import com.example.damthoai.network.model.IceCandidatePayload
import com.example.damthoai.webrtc.CallStats
import com.example.damthoai.webrtc.CustomPeerConnectionObserver
import com.example.damthoai.webrtc.WebRTCClient
import com.example.damthoai.webrtc.audio.AudioDuckingController
import com.example.damthoai.webrtc.audio.AudioProcessingManager
import com.example.damthoai.webrtc.audio.JitterBufferOptimizer
import com.example.damthoai.webrtc.audio.JitterProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
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
    /** Tuần 4: đường truyền đang được làm mới (đổi Wi-Fi <-> 4G), cuộc gọi KHÔNG bị ngắt */
    data class Reconnecting(val peerUserId: String, val attempt: Int) : CallState()
    data class CallEnded(val reason: String? = null) : CallState()
}

/**
 * Đường truyền tín hiệu đang dùng
 */
enum class ConnectionMode {
    /** WebSocket tới Signaling Server (Internet hoặc máy chủ LAN tìm qua mDNS) */
    SERVER,
    /** Tuần 5: mất Internet -> các điện thoại tự tìm nhau qua mDNS, nói chuyện trực tiếp qua Wi-Fi */
    LAN_P2P
}

/**
 * Lớp điều phối trung tâm giữa Tầng Mạng (Signaling WebSocket, mDNS) và WebRTC Audio cho App.
 * Cung cấp API trực tiếp cho UI Jetpack Compose (PTT, Trạng thái đàm thoại, Quét máy chủ, Cảnh báo khẩn cấp).
 *
 * Phần mạng của TV1:
 *  - Tuần 1/2: cấu hình STUN/TURN, luồng âm thanh P2P (offer/answer/ICE).
 *  - Tuần 3: ICE Gathering liên tục, chuyển tiếp candidate mới và candidate bị thu hồi.
 *  - Tuần 4: Seamless Handover - ICE restart khi đổi mạng / khi ICE rớt, không ngắt cuộc gọi.
 *  - Tuần 5: Local LAN Fallback - tự chuyển sang signaling P2P qua mDNS khi mất Internet.
 *  - Tuần 6: Thu thập getStats (RTT, mất gói, jitter, concealment) để đo khả năng phục hồi.
 */
class DamThoaiCallManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DamThoaiCallManager"

        /** Chờ ICE tự phục hồi trước khi chủ động ICE restart */
        private const val ICE_DISCONNECTED_GRACE_MS = 2500L
        private const val MAX_ICE_RESTART_ATTEMPTS = 5
        /** Signaling Server không liên lạc được quá lâu -> bật LAN Fallback */
        private const val LAN_FALLBACK_DELAY_MS = 6000L
        private const val STATS_INTERVAL_MS = 2000L

        @Volatile
        private var instance: DamThoaiCallManager? = null

        fun getInstance(context: Context): DamThoaiCallManager {
            return instance ?: synchronized(this) {
                instance ?: DamThoaiCallManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val signalingClient = SignalingClient(deviceKey = loadOrCreateDeviceKey(context))
    val lanSignaling = LocalLanSignaling(context)
    val audioProcessing = AudioProcessingManager()
    val webRTCClient = WebRTCClient(context, audioProcessing)
    val mdnsDiscovery = MdnsDiscoveryManager(context)
    val jitterOptimizer = JitterBufferOptimizer()
    val batteryMonitor = BatteryOptimizationMonitor()
    val offlineVoiceQueue = OfflineVoiceQueueManager.createDefault(context, audioProcessing)

    @Volatile
    var activeChannelId: String = AudioDuckingController.CHANNEL_SUPPORT

    val audioDucking = AudioDuckingController(
        onVolumeApplied = { channelId, effectiveVolume ->
            if (channelId == activeChannelId) {
                webRTCClient.setRemoteVolume(effectiveVolume.toDouble())
            }
        }
    )

    private var lastAppliedJitterProfile: JitterProfile? = null

    // Trạng thái cuộc gọi
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    // Trạng thái giữ mic Push-to-Talk (PTT)
    private val _isPttPressed = MutableStateFlow(false)
    val isPttPressed: StateFlow<Boolean> = _isPttPressed.asStateFlow()

    // Đường truyền signaling đang dùng (Server / LAN P2P)
    private val _connectionMode = MutableStateFlow(ConnectionMode.SERVER)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode.asStateFlow()

    /** Thành viên tìm thấy trong mạng LAN khi ở chế độ LAN Fallback */
    val lanPeers: StateFlow<Set<String>> get() = lanSignaling.peerIds

    /** Trạng thái WebSocket tới Signaling Server (để UI hiển thị) */
    val serverConnectionState: StateFlow<SignalingConnectionState> get() = signalingClient.connectionState

    // Thành viên đang online trên Signaling Server (không gồm chính mình)
    private val _onlinePeers = MutableStateFlow<List<String>>(emptyList())
    val onlinePeers: StateFlow<List<String>> = _onlinePeers.asStateFlow()

    // Số ICE server (STUN/TURN) đang dùng và có TURN hay không -> kiểm tra cấu hình Tuần 1
    private val _iceServerSummary = MutableStateFlow(describeIceServers(WebRTCClient.DEFAULT_ICE_SERVERS, fromServer = false))
    val iceServerSummary: StateFlow<String> = _iceServerSummary.asStateFlow()

    // Chỉ số chất lượng đường truyền thời gian thực (Tuần 6)
    private val _callStats = MutableStateFlow<CallStats?>(null)
    val callStats: StateFlow<CallStats?> = _callStats.asStateFlow()

    // ID người dùng hiện tại và đối tác
    private var localUserId: String? = null
    private var activePeerUserId: String? = null

    /** ICE servers lấy từ server gần nhất (để khôi phục khi thoát chế độ LAN) */
    private var serverIceServers: List<PeerConnection.IceServer> = WebRTCClient.DEFAULT_ICE_SERVERS

    // Quản lý lắng nghe chuyển mạng (Seamless Handover)
    private var networkChangeReceiver: NetworkChangeReceiver? = null
    private var iceRestartJob: Job? = null
    private var iceRestartAttempts = 0
    private var lanFallbackJob: Job? = null
    private var statsJob: Job? = null
    private var discoveryJob: Job? = null

    /** Kênh signaling đang hoạt động */
    private val channel: SignalingChannel
        get() = if (_connectionMode.value == ConnectionMode.LAN_P2P) lanSignaling else signalingClient

    init {
        attachOfflineQueueTransport()
        observeSignalingEvents()
        observeServerConnection()
        observeTv2AudioSettingsAndStates()
        // Lắng nghe tín hiệu phím cứng từ PttController
        scope.launch {
            PttController.events.collect { event ->
                when (event.javaClass.simpleName) {
                    "PttPressed" -> setPushToTalkPressed(true)
                    "PttReleased" -> setPushToTalkPressed(false)
                }
            }
        }
    }

    private fun attachOfflineQueueTransport() {
        offlineVoiceQueue.attachTransport(object : VoiceQueueTransport {
            override fun isTransportReady(): Boolean = isSignalingTransportReady()

            override fun sendVoiceNote(message: QueuedVoiceMessage, audioBase64: String): Boolean {
                val target = message.targetId.ifBlank {
                    activePeerUserId ?: lanSignaling.peerIds.value.firstOrNull() ?: return false
                }
                return channel.sendVoiceNote(
                    targetId = target,
                    messageId = message.id,
                    channelId = message.channelId,
                    recordedAtMs = message.recordedAtMs,
                    durationMs = message.durationMs,
                    sampleRateHz = message.sampleRateHz,
                    audioBase64 = audioBase64
                )
            }
        })
    }

    private fun isSignalingTransportReady(): Boolean {
        return signalingClient.connectionState.value is SignalingConnectionState.Registered ||
            (_connectionMode.value == ConnectionMode.LAN_P2P && lanSignaling.peerIds.value.isNotEmpty())
    }

    /**
     * TV2 (Tuần 4, 5, 6): Đồng bộ live cài đặt xử lý âm thanh, trạng thái nói đa kênh (Audio Ducking),
     * cảnh báo khẩn cấp và phục hồi mạng (auto-flush Hàng đợi Ngoại tuyến).
     */
    private fun observeTv2AudioSettingsAndStates() {
        scope.launch {
            HandsFreeSettings.duckSecondaryEnabled.collect { enabled ->
                audioDucking.setDuckingEnabled(enabled)
            }
        }
        scope.launch {
            HandsFreeSettings.autoMicGainEnabled.collect { enabled ->
                audioProcessing.setAgcEnabled(enabled)
            }
        }
        scope.launch {
            HandsFreeSettings.noiseSuppressionEnabled.collect { enabled ->
                audioProcessing.setNoiseSuppressionEnabled(enabled)
            }
        }
        scope.launch {
            SpeakingStateRepository.speakingChannelIds.collect { speakingChannels ->
                audioDucking.updateSpeakingChannels(speakingChannels)
            }
        }
        scope.launch {
            EmergencyAlertManager.currentAlert.collect { alert ->
                audioDucking.setEmergencyOverrideActive(alert != null)
            }
        }
        scope.launch {
            NetworkQualityMonitor.status.collect { status ->
                if (status !is NetworkStatus.Offline) {
                    offlineVoiceQueue.onNetworkRestored()
                }
            }
        }
        scope.launch {
            lanSignaling.peerIds.collect { peers ->
                if (peers.isNotEmpty()) {
                    offlineVoiceQueue.onNetworkRestored()
                }
            }
        }
    }

    /**
     * Bắt đầu tự động dò tìm máy chủ qua mDNS (mdns_broadcaster.py) và tự động kết nối
     */
    fun autoDiscoverAndConnect(userId: String, token: String? = null) {
        localUserId = userId
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            mdnsDiscovery.serverFound.collect { server ->
                Log.d(TAG, "Tự động phát hiện server: ${server.wsUrl}, tiến hành kết nối...")
                signalingClient.connect(server.wsUrl, userId, token)
                mdnsDiscovery.stopDiscovery()
            }
        }
        mdnsDiscovery.startDiscovery()
    }

    /**
     * Kết nối thủ công tới máy chủ WebSocket
     */
    fun connect(serverUrl: String, userId: String, token: String? = null) {
        localUserId = userId
        signalingClient.connect(serverUrl, userId, token)
    }

    /**
     * Bật chế độ LAN thủ công (vd: người dùng chọn "Chỉ dùng Wi-Fi nội bộ")
     */
    fun enableLanFallback(userId: String? = localUserId) {
        val uid = userId ?: return
        localUserId = uid
        enterLanMode(uid)
    }

    // ------------------------------------------------------------------ Signaling

    /**
     * Lắng nghe các sự kiện từ Signaling Server và từ các máy trong mạng LAN
     */
    private fun observeSignalingEvents() {
        scope.launch {
            merge(signalingClient.events, lanSignaling.events).collect { event ->
                when (event) {
                    is SignalingEvent.ConfigReceived -> {
                        Log.d(TAG, "Nạp cấu hình ${event.iceServers.size} ICE Servers vào WebRTC Client")
                        if (event.iceServers.isNotEmpty()) serverIceServers = event.iceServers
                        _iceServerSummary.value = describeIceServers(serverIceServers, fromServer = event.iceServers.isNotEmpty())
                        if (_connectionMode.value == ConnectionMode.SERVER) {
                            webRTCClient.updateIceServers(serverIceServers)
                        }
                    }

                    is SignalingEvent.OfferReceived -> {
                        val sender = event.senderId ?: "Nguoi_Goi"
                        Log.d(TAG, "Nhận Offer từ $sender")
                        handleIncomingOffer(sender, event.sdp)
                    }

                    is SignalingEvent.AnswerReceived -> {
                        if (event.senderId != null && event.senderId != activePeerUserId) return@collect
                        Log.d(TAG, "Nhận Answer SDP từ đối tác")
                        val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, event.sdp)
                        webRTCClient.setRemoteDescription(answerSdp, onSuccess = {
                            scope.launch {
                                if (_callState.value is CallState.Calling) {
                                    _callState.value = CallState.InCall(activePeerUserId ?: "DoiTac")
                                }
                            }
                        })
                    }

                    is SignalingEvent.IceCandidateReceived -> {
                        if (event.senderId != null && event.senderId != activePeerUserId) return@collect
                        webRTCClient.addIceCandidate(
                            IceCandidate(event.sdpMid, event.sdpMLineIndex, event.candidate)
                        )
                    }

                    is SignalingEvent.IceCandidatesRemoved -> {
                        if (event.senderId != null && event.senderId != activePeerUserId) return@collect
                        webRTCClient.removeIceCandidates(
                            event.candidates.map { IceCandidate(it.sdpMid, it.sdpMLineIndex, it.candidate) }
                        )
                    }

                    is SignalingEvent.HangupReceived -> {
                        if (event.senderId == activePeerUserId) {
                            Log.d(TAG, "Đối tác đã kết thúc cuộc gọi")
                            endCallInternal(notifyPeer = false, reason = "Đối tác đã kết thúc")
                        }
                    }

                    is SignalingEvent.PeersReceived -> {
                        _onlinePeers.value = event.peers.filter { it != signalingClient.userId }
                    }

                    is SignalingEvent.VoiceNoteReceived -> {
                        Log.d(TAG, "Nhận tin nhắn thoại ngoại tuyến [${event.id}] từ ${event.senderId}")
                        markField("offline_voice_received", "id=${event.id};from=${event.senderId};ms=${event.durationMs}")
                        offlineVoiceQueue.playReceivedVoiceNote(
                            audioBase64 = event.audioBase64,
                            sampleRateHz = event.sampleRateHz,
                            senderId = event.senderId
                        )
                    }
                }
            }
        }
    }

    /**
     * Tuần 5: theo dõi kết nối Signaling Server.
     * Mất server quá [LAN_FALLBACK_DELAY_MS] -> tự chuyển sang LAN P2P. Server sống lại -> quay về.
     */
    private fun observeServerConnection() {
        scope.launch {
            signalingClient.connectionState.collect { state ->
                when (state) {
                    is SignalingConnectionState.Registered -> {
                        lanFallbackJob?.cancel()
                        lanFallbackJob = null
                        // TV2 (Tuần 5): Có mạng trở lại -> gửi vọt toàn bộ tin nhắn thoại trong hàng đợi ngoại tuyến
                        offlineVoiceQueue.onNetworkRestored()
                        // Đang gọi qua LAN thì giữ nguyên đến hết cuộc gọi, tránh rớt tiếng
                        if (_connectionMode.value == ConnectionMode.LAN_P2P && activePeerUserId == null) {
                            exitLanMode()
                        }
                    }
                    is SignalingConnectionState.Error,
                    is SignalingConnectionState.Disconnected -> {
                        val uid = localUserId ?: signalingClient.userId ?: return@collect
                        if (signalingClient.serverUrl == null) return@collect // chưa từng kết nối
                        if (signalingClient.isRejected) return@collect // bị từ chối (sai mã/trùng tên), không phải mất mạng
                        if (_connectionMode.value == ConnectionMode.LAN_P2P || lanFallbackJob?.isActive == true) {
                            return@collect
                        }
                        lanFallbackJob = scope.launch {
                            delay(LAN_FALLBACK_DELAY_MS)
                            if (signalingClient.connectionState.value !is SignalingConnectionState.Registered) {
                                Log.w(TAG, "Không liên lạc được Signaling Server -> bật Local LAN Fallback")
                                enterLanMode(uid)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun enterLanMode(userId: String) {
        if (_connectionMode.value == ConnectionMode.LAN_P2P) return
        _connectionMode.value = ConnectionMode.LAN_P2P
        lanSignaling.start(userId)
        // Không có Internet -> STUN/TURN vô dụng, chỉ dùng host candidate IP nội bộ cho nhanh
        webRTCClient.updateIceServers(emptyList())
        markField("lan_fallback_on", "user=$userId")
        // Đang gọi dở -> ICE restart qua kênh LAN để nối lại bằng IP nội bộ
        if (activePeerUserId != null) triggerIceRestart("lan_fallback")
    }

    private fun exitLanMode() {
        if (_connectionMode.value != ConnectionMode.LAN_P2P) return
        Log.d(TAG, "Signaling Server hoạt động trở lại -> tắt LAN Fallback")
        _connectionMode.value = ConnectionMode.SERVER
        lanSignaling.stop()
        webRTCClient.updateIceServers(serverIceServers)
        markField("lan_fallback_off")
    }

    // ------------------------------------------------------------------ Cuộc gọi

    /**
     * Bắt đầu gọi cho một người dùng khác
     */
    fun startCall(targetUserId: String) {
        activePeerUserId = targetUserId
        iceRestartAttempts = 0
        _callState.value = CallState.Calling(targetUserId)

        // Bật chặn phím cứng đúng chuẩn API của PttController
        PttController.setPttModeEnabled(true)
        PttController.acquireCapture("DamThoaiCallManager")

        setupPeerConnection(targetUserId)

        // Kích hoạt theo dõi chuyển mạng, đo chất lượng, Audio Focus và dịch vụ chạy nền (TV2 Tuần 3)
        AudioFocusGuard.start(context)
        DamThoaiAudioService.start(context, activeChannelId)
        startNetworkMonitoring()
        startStatsLoop()

        webRTCClient.createOffer(
            onSuccess = { sdp ->
                Log.d(TAG, "Gửi Offer SDP tới $targetUserId qua Signaling")
                channel.sendOffer(targetUserId, sdp.description)
            },
            onError = { err ->
                Log.e(TAG, "Lỗi tạo Offer: $err")
                scope.launch { _callState.value = CallState.CallEnded(err) }
            }
        )
    }

    /**
     * Xử lý khi nhận được Offer:
     *  - Offer từ đối tác hiện tại = renegotiation/ICE restart (đối tác vừa đổi mạng) -> giữ cuộc gọi.
     *  - Offer từ người mới khi đang rảnh = cuộc gọi đến.
     */
    private fun handleIncomingOffer(senderUserId: String, offerSdpStr: String) {
        val offerSdp = SessionDescription(SessionDescription.Type.OFFER, offerSdpStr)

        if (senderUserId == activePeerUserId && webRTCClient.hasPeerConnection) {
            handleRenegotiationOffer(senderUserId, offerSdp)
            return
        }
        if (activePeerUserId != null && senderUserId != activePeerUserId) {
            Log.w(TAG, "Đang bận đàm thoại với $activePeerUserId, từ chối $senderUserId")
            channel.sendHangup(senderUserId)
            return
        }

        activePeerUserId = senderUserId
        iceRestartAttempts = 0

        // BẬT CHẶN PHÍM CỨNG PTT KHI NHẬN CUỘC GỌI
        PttController.setPttModeEnabled(true)
        PttController.acquireCapture("DamThoaiCallManager")

        setupPeerConnection(senderUserId)

        // Kích hoạt theo dõi chuyển mạng, đo chất lượng, Audio Focus và dịch vụ chạy nền (TV2 Tuần 3)
        AudioFocusGuard.start(context)
        DamThoaiAudioService.start(context, activeChannelId)
        startNetworkMonitoring()
        startStatsLoop()

        answerOffer(senderUserId, offerSdp)
    }

    private fun answerOffer(senderUserId: String, offerSdp: SessionDescription) {
        webRTCClient.setRemoteDescription(offerSdp, onSuccess = {
            webRTCClient.createAnswer(
                onSuccess = { answerSdp ->
                    Log.d(TAG, "Gửi Answer SDP phản hồi $senderUserId")
                    channel.sendAnswer(senderUserId, answerSdp.description)
                    scope.launch {
                        if (_callState.value !is CallState.Reconnecting) {
                            _callState.value = CallState.InCall(senderUserId)
                        }
                    }
                },
                onError = { err -> Log.e(TAG, "Lỗi tạo Answer: $err") }
            )
        })
    }

    /**
     * Tuần 4: đối tác gửi Offer mới (ICE restart) giữa cuộc gọi.
     * Nếu 2 máy cùng restart một lúc (va chạm Offer), máy "lịch sự" (user_id lớn hơn) huỷ Offer
     * của mình để nhận Offer kia; máy còn lại bỏ qua Offer đến và chờ Answer.
     */
    private fun handleRenegotiationOffer(senderUserId: String, offerSdp: SessionDescription) {
        val glare = webRTCClient.signalingState == PeerConnection.SignalingState.HAVE_LOCAL_OFFER
        if (glare) {
            val polite = (localUserId ?: "") > senderUserId
            if (!polite) {
                Log.d(TAG, "Va chạm Offer: giữ Offer của mình, bỏ qua Offer từ $senderUserId")
                return
            }
            Log.d(TAG, "Va chạm Offer: rollback để nhận Offer từ $senderUserId")
            webRTCClient.rollbackLocalOffer { answerOffer(senderUserId, offerSdp) }
            return
        }
        Log.d(TAG, "Nhận ICE restart từ $senderUserId (đối tác đổi mạng), giữ nguyên cuộc gọi")
        answerOffer(senderUserId, offerSdp)
    }

    /**
     * Thiết lập PeerConnection và cấu hình chuyển tiếp ICE Candidates
     */
    private fun setupPeerConnection(targetUserId: String) {
        webRTCClient.createPeerConnection(object : CustomPeerConnectionObserver(webRTCClient) {
            // Tuần 3: GATHER_CONTINUALLY -> candidate mới xuất hiện bất cứ khi nào có mạng mới
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { ice ->
                    Log.d(TAG, "ICE candidate cục bộ mới, gửi tới $targetUserId")
                    channel.sendIceCandidate(
                        targetId = targetUserId,
                        sdpMid = ice.sdpMid,
                        sdpMLineIndex = ice.sdpMLineIndex,
                        candidate = ice.sdp
                    )
                }
            }

            // Tuần 3: mạng cũ biến mất -> báo đối tác bỏ các candidate đã chết
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                val list = candidates?.map {
                    IceCandidatePayload(sdpMid = it.sdpMid, sdpMLineIndex = it.sdpMLineIndex, candidate = it.sdp)
                } ?: return
                if (list.isNotEmpty()) channel.sendIceCandidatesRemoved(targetUserId, list)
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "ICE gathering: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "Trạng thái ICE Connection: $state")
                scope.launch { onIceStateChanged(targetUserId, state) }
            }
        })
    }

    private fun onIceStateChanged(peerId: String, state: PeerConnection.IceConnectionState?) {
        if (peerId != activePeerUserId) return
        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                iceRestartJob?.cancel()
                iceRestartAttempts = 0
                _callState.value = CallState.InCall(peerId)
                markField("ice_connected", "peer=$peerId")
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                // Tạm mất gói (vd: đang chuyển vùng phủ sóng) -> chờ ICE tự phục hồi, quá hạn thì restart
                _callState.value = CallState.Reconnecting(peerId, iceRestartAttempts)
                scheduleIceRestart(ICE_DISCONNECTED_GRACE_MS, "ice_disconnected")
            }
            PeerConnection.IceConnectionState.FAILED -> {
                _callState.value = CallState.Reconnecting(peerId, iceRestartAttempts)
                scheduleIceRestart(0, "ice_failed")
            }
            else -> {}
        }
    }

    private fun scheduleIceRestart(delayMs: Long, reason: String) {
        if (iceRestartJob?.isActive == true) return
        iceRestartJob = scope.launch {
            delay(delayMs)
            if (activePeerUserId == null) return@launch
            // Chỉ máy "chủ động" (user_id nhỏ hơn) tự restart khi ICE rớt, tránh 2 bên cùng gửi Offer
            val peer = activePeerUserId ?: return@launch
            val initiator = (localUserId ?: "") < peer || reason.startsWith("network")
            if (initiator) triggerIceRestart(reason)
        }
    }

    /**
     * Bấm giữ phím PTT (Push-to-Talk) để nói, nhả ra để chỉ nghe.
     * TV2 (Tuần 5): Nếu đang mất mạng (Offline), tự động ghi âm lưu vào Hàng đợi Ngoại tuyến
     * và gửi vọt đi ngay khi có mạng trở lại.
     */
    fun setPushToTalkPressed(isPressed: Boolean) {
        _isPttPressed.value = isPressed
        val isOffline = NetworkQualityMonitor.status.value is NetworkStatus.Offline ||
            (_callState.value is CallState.Reconnecting && !isSignalingTransportReady())

        if (isPressed) {
            if (isOffline) {
                webRTCClient.setMicrophoneEnabled(false)
                offlineVoiceQueue.startOfflineRecording(
                    channelId = activeChannelId,
                    targetId = activePeerUserId ?: "",
                    senderId = localUserId ?: signalingClient.userId ?: "local_user"
                )
            } else {
                webRTCClient.setMicrophoneEnabled(true)
            }
        } else {
            if (offlineVoiceQueue.state.value.isRecording) {
                offlineVoiceQueue.stopOfflineRecordingAndEnqueue()
            }
            webRTCClient.setMicrophoneEnabled(false)
        }
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
    fun endCall() = endCallInternal(notifyPeer = true, reason = null)

    private fun endCallInternal(notifyPeer: Boolean, reason: String?) {
        val peer = activePeerUserId
        if (notifyPeer && peer != null) channel.sendHangup(peer)

        iceRestartJob?.cancel()
        statsJob?.cancel()
        statsJob = null
        webRTCClient.close()
        activePeerUserId = null
        _callState.value = if (reason != null) CallState.CallEnded(reason) else CallState.Idle
        _isPttPressed.value = false
        _callStats.value = null

        // TẮT PTT, TRẢ LẠI PHÍM ÂM LƯỢNG CHO HỆ ĐIỀU HÀNH
        PttController.releaseCapture("DamThoaiCallManager")
        PttController.setPttModeEnabled(false)
        PttController.forceReleasePtt()

        // Dọn dẹp tài nguyên mạng, Audio Focus và đặt lại bộ tối ưu Jitter Buffer
        networkChangeReceiver?.stop()
        networkChangeReceiver = null
        jitterOptimizer.reset()
        lastAppliedJitterProfile = null
        AudioFocusGuard.stop()

        // Server đã sống lại trong lúc gọi qua LAN -> giờ mới quay về Server
        if (_connectionMode.value == ConnectionMode.LAN_P2P &&
            signalingClient.connectionState.value is SignalingConnectionState.Registered
        ) {
            exitLanMode()
        }
    }

    /**
     * Ngắt toàn bộ dịch vụ khi đóng ứng dụng
     */
    fun destroy() {
        endCall()
        lanFallbackJob?.cancel()
        discoveryJob?.cancel()
        signalingClient.disconnect()
        lanSignaling.stop()
        mdnsDiscovery.stopDiscovery()
    }

    // --- CÁC HÀM XỬ LÝ NGOẠI VI (MẠNG & BLUETOOTH) ---

    private fun startNetworkMonitoring() {
        if (networkChangeReceiver == null) {
            networkChangeReceiver = NetworkChangeReceiver(
                context,
                onNetworkLost = {
                    activePeerUserId?.let { _callState.value = CallState.Reconnecting(it, iceRestartAttempts) }
                },
                onNetworkChanged = { type -> triggerSeamlessHandover(type) }
            )
            networkChangeReceiver?.start()
        }
    }

    /**
     * Tuần 4 - Seamless Handover: Wi-Fi <-> 4G.
     *  1. Mở ngay WebSocket mới trên mạng mới (socket cũ gắn với IP cũ đã chết).
     *  2. ICE restart: tạo Offer mới với ufrag/pwd mới -> 2 bên dò lại cặp candidate trên mạng mới.
     *     Offer được xếp hàng trong SignalingClient và gửi ngay khi WebSocket mở lại.
     *  3. Track âm thanh, DTLS, SRTP giữ nguyên -> người dùng chỉ nghe hụt vài trăm ms, không rớt cuộc gọi.
     */
    private fun triggerSeamlessHandover(networkType: String) {
        markField("network_change", "type=$networkType")
        if (_connectionMode.value == ConnectionMode.SERVER) {
            signalingClient.reconnectNow()
            signalingClient.sendNetworkChange(networkType)
        }
        if (activePeerUserId == null) return
        iceRestartAttempts = 0
        iceRestartJob?.cancel()
        triggerIceRestart("network_$networkType")
    }

    private fun triggerIceRestart(reason: String) {
        val targetId = activePeerUserId ?: return
        if (iceRestartAttempts >= MAX_ICE_RESTART_ATTEMPTS) {
            Log.e(TAG, "ICE restart thất bại $iceRestartAttempts lần, kết thúc cuộc gọi")
            endCallInternal(notifyPeer = true, reason = "Mất kết nối mạng P2P")
            return
        }
        iceRestartAttempts++
        _callState.value = CallState.Reconnecting(targetId, iceRestartAttempts)
        Log.d(TAG, "ICE restart #$iceRestartAttempts tới $targetId (lý do: $reason)")
        markField("ice_restart", "reason=$reason attempt=$iceRestartAttempts")

        webRTCClient.restartIce(
            onSuccess = { sdp ->
                Log.d(TAG, "Gửi Offer ICE restart qua ${_connectionMode.value}")
                channel.sendOffer(targetId, sdp.description)
            },
            onError = { err -> Log.e(TAG, "Lỗi Handover: $err") }
        )

        // Nếu sau 8s vẫn chưa nối lại được thì thử lần nữa (backoff tăng dần)
        iceRestartJob?.cancel()
        iceRestartJob = scope.launch {
            delay(4000L * iceRestartAttempts)
            if (_callState.value is CallState.Reconnecting) triggerIceRestart("retry")
        }
    }

    /**
     * Tuần 6: đo chất lượng cuộc gọi định kỳ (RTT, mất gói, jitter, concealment, relay/direct)
     * và ghi vào FieldTestLogger để phân tích khả năng phục hồi khi rớt gói.
     */
    private fun startStatsLoop() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive) {
                // TV2 (Tuần 6): Chu kỳ thích ứng theo trạng thái chạy nền/pin để tối ưu ca 4 giờ
                val intervalMs = batteryMonitor.recommendedStatsIntervalMs(
                    isInBackground = PttController.backgroundSessionActive.value,
                    isPttActive = _isPttPressed.value
                )
                delay(intervalMs)
                webRTCClient.collectStats { stats ->
                    _callStats.value = stats
                    // TV2 (Tuần 6): Tối ưu Jitter Buffer & Opus bitrate chống méo tiếng theo thời gian thực
                    val rec = jitterOptimizer.evaluate(stats)
                    webRTCClient.applyJitterBufferConfig(rec.maxPackets, rec.fastAccelerate)
                    webRTCClient.applyOpusBitrate(rec.recommendedOpusBitrateBps, rec.enableInbandFec)
                    if (rec.profile != lastAppliedJitterProfile) {
                        lastAppliedJitterProfile = rec.profile
                        markField(
                            "jitter_buffer_adjusted",
                            "profile=${rec.profile};maxPackets=${rec.maxPackets};" +
                                "fastAccel=${rec.fastAccelerate};bitrate=${rec.recommendedOpusBitrateBps};reason=${rec.reason}"
                        )
                    }
                    markField(
                        "webrtc_stats",
                        "rtt=${stats.rttMs} lossRate=${"%.3f".format(stats.intervalLossRate)} " +
                            "lost=${stats.packetsLost} jitterMs=${"%.1f".format(stats.jitterMs)} " +
                            "conceal=${"%.4f".format(stats.concealmentRatio)} " +
                            "path=${stats.localCandidateType}/${stats.remoteCandidateType} net=${stats.localNetworkType}"
                    )
                }
            }
        }
    }

    /** Mã riêng của máy, lưu bền để mở lại app vẫn giữ được user_id trên máy chủ */
    private fun loadOrCreateDeviceKey(context: Context): String {
        val prefs = context.getSharedPreferences("tv1_signaling", Context.MODE_PRIVATE)
        return prefs.getString("device_key", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_key", it).apply() }
    }

    private fun describeIceServers(servers: List<PeerConnection.IceServer>, fromServer: Boolean): String {
        val urls = servers.flatMap { it.urls }
        val stun = urls.count { it.startsWith("stun:") }
        val turn = urls.count { it.startsWith("turn:") || it.startsWith("turns:") }
        val source = if (fromServer) "từ server" else "mặc định trong app"
        return "$stun STUN, $turn TURN ($source)"
    }

    private fun markField(event: String, extra: String = "") {
        try {
            com.example.damthoai.hardware.diagnostics.FieldTestLogger.mark(event, source = "TV1_NET", extra = extra)
        } catch (ignored: Throwable) {}
    }
}
