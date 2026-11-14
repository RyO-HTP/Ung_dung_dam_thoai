package com.example.damthoai.service

import com.example.damthoai.webrtc.CustomPeerConnectionObserver
import com.example.damthoai.network.SignalingEvent
import com.example.damthoai.network.SignalingClient
import com.example.damthoai.network.SignalingConnectionState
import com.example.damthoai.network.ServerLocator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import com.example.damthoai.network.MdnsDiscoveryManager
import com.example.damthoai.network.model.OnlineMember
import com.example.damthoai.network.model.RegisterMessage
import com.example.damthoai.webrtc.WebRTCClient
import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.hardware.PttEvent
import com.example.damthoai.hardware.audio.AudioRoute
import com.example.damthoai.hardware.audio.AudioRouteGuard
import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
 * Trạng thái kết nối tới máy chủ Signaling, hiển thị cho người dùng.
 */
sealed class ServerStatus {
    object Idle : ServerStatus()
    data class Connected(val url: String) : ServerStatus()
    object Reconnecting : ServerStatus()
    object Searching : ServerStatus()
    data class NotFound(val lastUrl: String) : ServerStatus()
}

/**
 * Lớp điều phối trung tâm giữa Tầng Mạng (Signaling WebSocket, mDNS) và WebRTC Audio cho App.
 * Cung cấp API trực tiếp cho UI Jetpack Compose (PTT, Trạng thái đàm thoại, Quét máy chủ, Cảnh báo khẩn cấp).
 *
 * Mọi thao tác trạng thái chạy trên Main thread (scope Dispatchers.Main); callback WebRTC
 * được chuyển về Main qua [scope] trước khi đụng tới trạng thái.
 */
class DamThoaiCallManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DamThoaiCallManager"
        // Mất kết nối: chờ SignalingClient tự nối lại cùng địa chỉ chừng này trước khi tìm máy chủ lại
        private const val RECONNECT_GRACE_MS = 8000L
        private const val CONNECT_TIMEOUT_MS = 15_000L
        // Gọi /healthz của máy chủ đám mây mỗi 10 phút (Render free ngủ sau 15 phút không có lưu lượng)
        private const val CLOUD_KEEPALIVE_MS = 10 * 60_000L
        private const val SEARCH_RETRY_BASE_MS = 5000L
        private const val SEARCH_RETRY_MAX_MS = 30_000L
        private const val RECALL_DELAY_MS = 2000L

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

    // Danh sách ID người dùng online trong cùng sự kiện (giữ để tương thích)
    private val _onlineUsers = MutableStateFlow<List<String>>(emptyList())
    val onlineUsers: StateFlow<List<String>> = _onlineUsers.asStateFlow()

    // Danh sách thành viên online kèm tên, vai trò
    private val _onlineMembers = MutableStateFlow<List<OnlineMember>>(emptyList())
    val onlineMembers: StateFlow<List<OnlineMember>> = _onlineMembers.asStateFlow()

    // Tên sự kiện hiện tại (do ban tổ chức đặt, server gửi về khi vào bằng PIN)
    private val _eventName = MutableStateFlow(AppStartPrefs.getEventName(context))
    val eventName: StateFlow<String> = _eventName.asStateFlow()

    // ID đối tác đang đàm thoại
    private var activePeerUserId: String? = null

    // Đã chọn tuyến âm thanh cho phiên hiện tại chưa (không ghi đè lựa chọn của người dùng)
    private var audioSessionStarted = false

    // Bộ giám sát kết nối máy chủ (tự tìm lại máy chủ khi không kết nối được)
    private var supervisorJob: Job? = null
    private val serverLocator = ServerLocator(mdnsDiscovery)

    // Trạng thái kết nối máy chủ hiển thị cho người dùng
    private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Idle)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()
    private var recallJob: Job? = null

    // Quản lý lắng nghe chuyển mạng (Seamless Handover)
    private var networkChangeReceiver: com.example.damthoai.network.NetworkChangeReceiver? = null

    init {
        observeSignalingEvents()
        // Lắng nghe tín hiệu phím cứng / nút màn hình từ PttController
        scope.launch {
            PttController.events.collect { event ->
                when (event) {
                    is PttEvent.PttPressed -> setPushToTalkPressed(true)
                    is PttEvent.PttReleased -> setPushToTalkPressed(false)
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // THAM GIA SỰ KIỆN & HỒ SƠ
    // ------------------------------------------------------------------

    /**
     * Vào sự kiện bằng mã PIN.
     * @param eventNameHint tên sự kiện đã biết (ban tổ chức tự đặt, hoặc đọc từ mã QR). Nếu null,
     *        tên tạm thời là "Sự kiện Tần số <PIN>" cho tới khi server trả về tên thật.
     * @param asOrganizer true nếu thiết bị này là người tạo sự kiện.
     */
    fun joinEvent(pin: String, eventNameHint: String? = null, asOrganizer: Boolean = false) {
        val previousPin = AppStartPrefs.getEventPin(context)
        AppStartPrefs.setEventPin(context, pin)
        AppStartPrefs.setOrganizer(context, asOrganizer)
        AppStartPrefs.setHasJoinedEvent(context, true)
        when {
            !eventNameHint.isNullOrBlank() -> AppStartPrefs.setEventName(context, eventNameHint.trim())
            // Vào sự kiện khác: bỏ tên của sự kiện cũ, chờ server trả tên thật
            previousPin != pin || !AppStartPrefs.hasCustomEventName(context) ->
                AppStartPrefs.setEventName(context, "Sự kiện Tần số $pin")
        }
        _eventName.value = AppStartPrefs.getEventName(context)
        if (previousPin != pin) {
            resetPeer("Đổi sự kiện")
            _onlineMembers.value = emptyList()
            _onlineUsers.value = emptyList()
        }
        autoDiscoverAndConnect(AppStartPrefs.getUserId(context))
    }

    /**
     * Cập nhật tên hiển thị / vai trò; gửi ngay lên server để danh sách thành viên đổi theo.
     */
    fun updateProfile(displayName: String, roleTitle: String) {
        AppStartPrefs.setProfile(context, displayName, roleTitle)
        signalingClient.updateRegistration(buildRegistration(AppStartPrefs.getUserId(context)))
    }

    /** Ban tổ chức đổi tên sự kiện: lưu và báo server để mọi người cập nhật. */
    fun updateEventName(name: String) {
        if (name.isBlank()) return
        AppStartPrefs.setEventName(context, name.trim())
        _eventName.value = AppStartPrefs.getEventName(context)
        signalingClient.updateRegistration(buildRegistration(AppStartPrefs.getUserId(context)))
    }

    private fun buildRegistration(userId: String): RegisterMessage {
        val organizer = AppStartPrefs.isOrganizer(context)
        return RegisterMessage(
            userId = userId,
            eventPin = AppStartPrefs.getEventPin(context).ifBlank { null },
            displayName = AppStartPrefs.getDisplayName(context).ifBlank { null },
            roleTitle = AppStartPrefs.getRoleTitle(context).ifBlank { null },
            isOrganizer = organizer,
            eventName = if (organizer && AppStartPrefs.hasCustomEventName(context)) {
                AppStartPrefs.getEventName(context)
            } else {
                null
            }
        )
    }

    // ------------------------------------------------------------------
    // KẾT NỐI SIGNALING
    // ------------------------------------------------------------------

    /**
     * Vào/cập nhật sự kiện: gửi hồ sơ mới lên server (nếu đang kết nối) và bảo đảm
     * bộ giám sát kết nối đang chạy. Bộ giám sát tự tìm máy chủ và tự tìm lại khi mất kết nối.
     */
    fun autoDiscoverAndConnect(userId: String) {
        signalingClient.updateRegistration(buildRegistration(userId))
        startConnectionSupervisor()
    }

    /** Người dùng bấm "Thử lại" (hoặc vừa đổi mạng): bỏ lượt chờ hiện tại, tìm máy chủ ngay. */
    fun retryServerConnection() {
        if (!AppStartPrefs.hasJoinedEvent(context)) return
        startConnectionSupervisor(restart = true, searchNow = true)
    }

    /**
     * Người dùng lưu địa chỉ máy chủ trong cài đặt ("" = quay về tự động).
     * Nếu đang trong sự kiện thì chuyển sang địa chỉ mới ngay, không chờ vòng thử lại với địa chỉ cũ.
     */
    fun setServerUrl(url: String) {
        AppStartPrefs.setManualServerUrl(context, url)
        retryServerConnection()
    }

    /**
     * BỘ GIÁM SÁT KẾT NỐI — nơi DUY NHẤT quyết định kết nối tới máy chủ nào. Vòng lặp:
     *  1. Đang kết nối: chờ tới khi rớt. Rớt ngắn (≤ [RECONNECT_GRACE_MS]) thì để
     *     SignalingClient tự nối lại cùng địa chỉ.
     *  2. Không nối lại được: dùng [ServerLocator] tìm máy chủ theo [serverCandidates], rồi kết nối.
     *  3. Không tìm thấy: báo [ServerStatus.NotFound], chờ giãn dần (5s → 30s) rồi tìm lại.
     *
     * @param searchNow bỏ qua bước 1 (VD người dùng vừa đổi địa chỉ máy chủ, hoặc vừa đổi mạng).
     */
    private fun startConnectionSupervisor(restart: Boolean = false, searchNow: Boolean = false) {
        if (supervisorJob?.isActive == true) {
            if (!restart) return
            supervisorJob?.cancel()
        }
        supervisorJob = scope.launch {
            var failures = 0
            var skipWait = searchNow
            while (isActive) {
                if (!skipWait && isRegistered()) {
                    failures = 0
                    stayConnectedUntilDropped()
                    _serverStatus.value = ServerStatus.Reconnecting
                    if (awaitRegistered(RECONNECT_GRACE_MS)) continue
                }
                skipWait = false

                _serverStatus.value = ServerStatus.Searching
                val url = serverLocator.locate(serverCandidates())
                if (url != null && connectTo(url)) continue

                failures++
                _serverStatus.value = ServerStatus.NotFound(serverCandidates().firstOrNull().orEmpty())
                // Giãn dần nhịp tìm (5s, 10s, 20s, tối đa 30s) để đỡ tốn pin
                val wait = (SEARCH_RETRY_BASE_MS shl (failures - 1).coerceAtMost(3)).coerceAtMost(SEARCH_RETRY_MAX_MS)
                delay(wait)
            }
        }
    }

    /** Đang kết nối: báo trạng thái, giữ máy chủ đám mây không ngủ, và chờ tới khi kết nối rớt. */
    private suspend fun stayConnectedUntilDropped() = coroutineScope {
        val connectedUrl = signalingClient.currentServerUrl().orEmpty()
        _serverStatus.value = ServerStatus.Connected(connectedUrl)
        // Render free ngủ khi không có lưu lượng HTTP: gọi /healthz định kỳ trong lúc đang dùng
        val keepAlive = if (!ServerLocator.isLanUrl(connectedUrl)) launch(Dispatchers.IO) {
            while (true) {
                delay(CLOUD_KEEPALIVE_MS)
                ServerLocator.isServerAlive(connectedUrl)
            }
        } else null
        signalingClient.connectionState.first { it !is SignalingConnectionState.Registered }
        keepAlive?.cancel()
    }

    /** Kết nối tới [url] (nếu chưa ở đó) và chờ đăng ký xong. Thành công thì ghi nhớ địa chỉ. */
    private suspend fun connectTo(url: String): Boolean {
        val alreadyThere = signalingClient.isConnected() && signalingClient.currentServerUrl() == url
        if (!alreadyThere) {
            Log.d(TAG, "Kết nối tới máy chủ: $url")
            signalingClient.connect(url, buildRegistration(AppStartPrefs.getUserId(context)))
        }
        val ok = awaitRegistered(CONNECT_TIMEOUT_MS)
        if (ok) {
            AppStartPrefs.setLastServerUrl(context, url)
        } else {
            // Không để SignalingClient tự thử lại mãi một địa chỉ hỏng; bộ giám sát sẽ tìm lại
            signalingClient.disconnect()
        }
        return ok
    }

    private fun isRegistered() =
        signalingClient.connectionState.value is SignalingConnectionState.Registered

    /** Chờ tối đa [timeoutMs] tới khi đăng ký xong với máy chủ. */
    private suspend fun awaitRegistered(timeoutMs: Long): Boolean = withTimeoutOrNull(timeoutMs) {
        signalingClient.connectionState.first { it is SignalingConnectionState.Registered }
    } != null

    /**
     * Thứ tự ưu tiên máy chủ:
     *  1. Địa chỉ người dùng tự nhập.
     *  2. Máy chủ đám mây chung: mọi máy, dù Wi-Fi hay 4G, đều vào CÙNG một máy chủ nên luôn thấy nhau.
     *  3. Địa chỉ tìm được lần trước. Không được thì [ServerLocator] tự tìm máy chủ trong mạng LAN.
     */
    private fun serverCandidates(): List<String> = listOf(
        AppStartPrefs.getManualServerUrl(context),
        com.example.damthoai.BuildConfig.CLOUD_SIGNALING_URL,
        AppStartPrefs.getLastServerUrl(context)
    ).filter { it.isNotBlank() }.distinct()

    private fun stopConnectionSupervisor() {
        supervisorJob?.cancel()
        supervisorJob = null
        _serverStatus.value = ServerStatus.Idle
    }

    /**
     * Lắng nghe các sự kiện từ Signaling Server
     */
    private fun observeSignalingEvents() {
        scope.launch {
            signalingClient.events.collect { event ->
                try {
                    handleSignalingEvent(event)
                } catch (e: Exception) {
                    // Không để một gói tin lỗi làm sập vòng lặp nhận sự kiện (và cả ứng dụng)
                    Log.e(TAG, "Lỗi xử lý sự kiện signaling $event: ${e.message}", e)
                }
            }
        }
    }

    private fun handleSignalingEvent(event: SignalingEvent) {
        when (event) {
            is SignalingEvent.ConfigReceived -> {
                Log.d(TAG, "Nạp cấu hình ${event.iceServers.size} ICE Servers vào WebRTC Client")
                webRTCClient.updateIceServers(event.iceServers)
            }

            is SignalingEvent.EventInfoReceived -> {
                val myPin = AppStartPrefs.getEventPin(context)
                if (event.eventPin != myPin) return
                if (event.found && event.eventName.isNotBlank()) {
                    Log.d(TAG, "Tên sự kiện từ ban tổ chức: ${event.eventName}")
                    AppStartPrefs.setEventName(context, event.eventName)
                    _eventName.value = AppStartPrefs.getEventName(context)
                } else if (!AppStartPrefs.isOrganizer(context)) {
                    Log.w(TAG, "Server chưa có sự kiện với PIN $myPin (ban tổ chức chưa online)")
                }
            }

            is SignalingEvent.OfferReceived -> {
                val sender = event.senderId
                if (sender.isNullOrBlank()) {
                    Log.w(TAG, "Bỏ qua Offer không có sender_id")
                    return
                }
                Log.d(TAG, "Nhận Offer từ $sender. Chuẩn bị trả lời...")
                handleIncomingOffer(sender, event.sdp)
            }

            is SignalingEvent.AnswerReceived -> {
                if (event.senderId != null && event.senderId != activePeerUserId) {
                    Log.w(TAG, "Bỏ qua Answer từ ${event.senderId} (không phải đối tác hiện tại)")
                    return
                }
                Log.d(TAG, "Nhận Answer SDP từ đối tác")
                val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, event.sdp)
                webRTCClient.setRemoteDescription(answerSdp, onSuccess = {
                    Log.d(TAG, "Đã đặt Answer, chờ ICE kết nối")
                })
            }

            is SignalingEvent.IceCandidateReceived -> {
                if (event.senderId != null && event.senderId != activePeerUserId) return
                val candidate = IceCandidate(
                    event.sdpMid,
                    event.sdpMLineIndex,
                    event.candidate
                )
                webRTCClient.addIceCandidate(candidate)
            }

            is SignalingEvent.UserListReceived -> {
                Log.d(TAG, "Nhận danh sách online (${event.users.size} người): ${event.users}")
                _onlineUsers.value = event.users
                _onlineMembers.value = event.members
                evaluatePeers(event.users)
            }
        }
    }

    /**
     * Quyết định gọi ai dựa trên danh sách online:
     * - Đối tác hiện tại đã rời: đóng phiên.
     * - Chưa có đối tác: máy có ID nhỏ hơn chủ động gửi Offer (máy kia chờ), tránh 2 máy gọi nhau cùng lúc.
     */
    private fun evaluatePeers(users: List<String>) {
        val myUid = AppStartPrefs.getUserId(context)
        val current = activePeerUserId
        if (current != null && current !in users) {
            Log.d(TAG, "Đối tác $current đã rời sự kiện, đóng phiên WebRTC")
            resetPeer("Đối tác đã rời kênh")
        }
        if (activePeerUserId != null) return

        val targetPeer = users.filter { it != myUid }.sorted().firstOrNull { myUid < it }
        if (targetPeer != null) {
            Log.d(TAG, "Tự động kích hoạt gọi P2P tới đối tác online: $targetPeer")
            startCall(targetPeer)
        }
    }

    // ------------------------------------------------------------------
    // CUỘC GỌI WEBRTC
    // ------------------------------------------------------------------

    /**
     * Bắt đầu gọi cho một người dùng khác
     */
    fun startCall(targetUserId: String) {
        recallJob?.cancel()
        activePeerUserId = targetUserId
        _callState.value = CallState.Calling(targetUserId)

        prepareCallSession()
        setupPeerConnection(targetUserId)

        webRTCClient.createOffer(
            onSuccess = { sdp ->
                Log.d(TAG, "Gửi Offer SDP tới $targetUserId qua Signaling")
                signalingClient.sendOffer(targetUserId, sdp.description)
            },
            onError = { err ->
                Log.e(TAG, "Lỗi tạo Offer: $err")
                scope.launch { onPeerFailed(targetUserId, err) }
            }
        )
    }

    /**
     * Xử lý khi nhận được cuộc gọi đến (Offer).
     * Offer từ đúng đối tác hiện tại (ICE Restart khi đổi mạng) dùng lại PeerConnection đang có.
     */
    private fun handleIncomingOffer(senderUserId: String, offerSdpStr: String) {
        recallJob?.cancel()
        val isRenegotiation = senderUserId == activePeerUserId && webRTCClient.hasPeerConnection()
        if (!isRenegotiation) {
            activePeerUserId = senderUserId
            _callState.value = CallState.Calling(senderUserId)
            prepareCallSession()
            setupPeerConnection(senderUserId)
        }

        val offerSdp = SessionDescription(SessionDescription.Type.OFFER, offerSdpStr)
        webRTCClient.setRemoteDescription(offerSdp, onSuccess = {
            webRTCClient.createAnswer(
                onSuccess = { answerSdp ->
                    Log.d(TAG, "Gửi Answer SDP phản hồi $senderUserId")
                    signalingClient.sendAnswer(senderUserId, answerSdp.description)
                },
                onError = { err ->
                    Log.e(TAG, "Lỗi tạo Answer: $err")
                    scope.launch { onPeerFailed(senderUserId, err) }
                }
            )
        }, onError = { err ->
            Log.e(TAG, "Lỗi đặt Offer từ $senderUserId: $err")
            scope.launch { onPeerFailed(senderUserId, err) }
        })
    }

    /** Bật chặn phím PTT, theo dõi mạng và chọn tuyến âm thanh (chỉ lần đầu trong phiên). */
    private fun prepareCallSession() {
        PttController.setPttModeEnabled(true)
        PttController.acquireCapture("DamThoaiCallManager")
        startNetworkMonitoring()
        startAudioSession()
    }

    /**
     * Thiết lập PeerConnection và cấu hình chuyển tiếp ICE Candidates
     */
    private fun setupPeerConnection(targetUserId: String) {
        webRTCClient.createPeerConnection(object : CustomPeerConnectionObserver(webRTCClient) {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { ice ->
                    signalingClient.sendIceCandidate(
                        targetId = targetUserId,
                        sdpMid = ice.sdpMid,
                        sdpMLineIndex = ice.sdpMLineIndex,
                        candidate = ice.sdp
                    )
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "Trạng thái ICE Connection với $targetUserId: $state")
                scope.launch {
                    if (activePeerUserId != targetUserId) return@launch
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            _callState.value = CallState.InCall(targetUserId)
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            onPeerFailed(targetUserId, "Mất kết nối mạng P2P")
                        }
                        // DISCONNECTED thường tự hồi phục (đổi mạng); để handover/ICE xử lý
                        else -> {}
                    }
                }
            }
        })
    }

    /** Phiên P2P lỗi: đóng và thử gọi lại sau một lúc nếu đối tác vẫn online. */
    private fun onPeerFailed(peerId: String, reason: String) {
        if (activePeerUserId != peerId) return
        resetPeer(reason)
        recallJob?.cancel()
        recallJob = scope.launch {
            delay(RECALL_DELAY_MS)
            evaluatePeers(_onlineUsers.value)
        }
    }

    /** Đóng phiên P2P hiện tại nhưng vẫn ở trong sự kiện (vẫn kết nối signaling, PTT vẫn dùng được). */
    private fun resetPeer(reason: String?) {
        if (activePeerUserId == null && !webRTCClient.hasPeerConnection()) return
        webRTCClient.close()
        activePeerUserId = null
        _callState.value = if (reason == null) CallState.Idle else CallState.CallEnded(reason)
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

    /** Âm lượng nghe tiếng đàm thoại (0..1), do thanh âm lượng của kênh đang nghe điều khiển. */
    fun setListeningVolume(volume: Float) {
        webRTCClient.setRemoteVolume(volume)
    }

    /**
     * Kết thúc cuộc gọi
     */
    fun endCall() {
        recallJob?.cancel()
        resetPeer(null)
        _callState.value = CallState.Idle
        setPushToTalkPressed(false)

        // TẮT PTT, TRẢ LẠI PHÍM ÂM LƯỢNG CHO HỆ ĐIỀU HÀNH
        PttController.releaseCapture("DamThoaiCallManager")
        PttController.setPttModeEnabled(false)
        PttController.forceReleasePtt()

        // Dọn dẹp tài nguyên mạng và đưa âm thanh về mặc định.
        // Không dừng mDNS ở đây: bộ giám sát kết nối có thể đang dùng nó để tìm máy chủ.
        networkChangeReceiver?.stop()
        networkChangeReceiver = null
        releaseAudioRouting()
    }

    /**
     * Rời sự kiện: đóng cuộc gọi, ngắt signaling (danh sách thành viên của mọi người cập nhật ngay).
     */
    fun leaveEvent() {
        endCall()
        PttController.setPttModeEnabled(true) // Trả lại chế độ PTT mặc định cho lần vào sau
        stopConnectionSupervisor()
        signalingClient.disconnect()
        _onlineMembers.value = emptyList()
        _onlineUsers.value = emptyList()
        AppStartPrefs.setHasJoinedEvent(context, false)
    }

    /**
     * Ngắt toàn bộ dịch vụ khi đóng ứng dụng
     */
    fun destroy() {
        endCall()
        stopConnectionSupervisor()
        signalingClient.disconnect()
        mdnsDiscovery.stopDiscovery()
    }

    // --- CÁC HÀM XỬ LÝ NGOẠI VI (MẠNG & ÂM THANH) ---

    private fun startNetworkMonitoring() {
        if (networkChangeReceiver == null) {
            networkChangeReceiver = com.example.damthoai.network.NetworkChangeReceiver(context) {
                scope.launch { triggerSeamlessHandover() }
            }
            networkChangeReceiver?.start()
        }
    }

    private fun triggerSeamlessHandover() {
        val targetId = activePeerUserId ?: return
        Log.d(TAG, "Bắt đầu Seamless Handover tới $targetId...")

        // TUẦN 5: NẾU MẤT KẾT NỐI INTERNET CLOUD -> TỰ ĐỘNG BẬT mDNS FALLBACK QUÉT LAN
        if (!signalingClient.isConnected()) {
            Log.w(TAG, "Đổi mạng và mất kết nối máy chủ: tìm lại máy chủ ngay")
            retryServerConnection()
            return
        }

        // Chỉ bên chủ động gọi (ID nhỏ hơn) mới gửi ICE Restart, tránh 2 bên cùng gửi Offer
        if (AppStartPrefs.getUserId(context) > targetId) return

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
     * Mở phiên âm thanh đàm thoại một lần cho mỗi phiên:
     * Ưu tiên tai nghe Bluetooth / có dây nếu đang cắm, nếu không thì phát Loa ngoài (bộ đàm cần to rõ).
     * Mọi thay đổi tuyến đều đi qua [AudioRouteGuard] để nút "Loa ngoài" trên UI luôn đúng trạng thái.
     */
    private fun startAudioSession() {
        if (audioSessionStarted) return
        audioSessionStarted = true
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            Log.e(TAG, "Không đặt được chế độ âm thanh đàm thoại: ${e.message}")
        }
        val available = AudioRouteGuard.availableRoutes.value
        val preferred = when {
            AudioRoute.BLUETOOTH in available -> AudioRoute.BLUETOOTH
            AudioRoute.HEADSET in available -> AudioRoute.HEADSET
            else -> AudioRoute.SPEAKER
        }
        Log.d(TAG, "Định tuyến âm thanh mặc định sang: $preferred")
        AudioRouteGuard.userSelectRoute(preferred)
    }

    private fun releaseAudioRouting() {
        audioSessionStarted = false
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "Đã giải phóng tài nguyên Audio về mặc định")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi dọn dẹp audio: ${e.message}")
        }
    }
}
