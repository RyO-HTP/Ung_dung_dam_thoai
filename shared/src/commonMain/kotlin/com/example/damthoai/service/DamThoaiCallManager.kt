package com.example.damthoai.service

import com.example.damthoai.data.AppStartPrefs
import com.example.damthoai.data.Seat
import com.example.damthoai.data.TeamChannels
import com.example.damthoai.data.TeamRole
import com.example.damthoai.hardware.PttController
import com.example.damthoai.hardware.PttEvent
import com.example.damthoai.hardware.audio.AudioRoute
import com.example.damthoai.hardware.audio.AudioRouteGuard
import com.example.damthoai.hardware.emergency.EmergencyAlert
import com.example.damthoai.hardware.emergency.EmergencyAlertManager
import com.example.damthoai.network.ServerLocator
import com.example.damthoai.network.SignalingClient
import com.example.damthoai.network.SignalingConnectionState
import com.example.damthoai.network.SignalingEvent
import com.example.damthoai.network.model.OnlineMember
import com.example.damthoai.network.model.RegisterMessage
import com.example.damthoai.platform.NetworkMonitor
import com.example.damthoai.platform.PlatformServices
import com.example.damthoai.platform.RtcIceState
import com.example.damthoai.platform.RtcPeerListener
import com.example.damthoai.platform.SdpType
import com.example.damthoai.platform.currentTimeMillis
import com.example.damthoai.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Trạng thái cuộc gọi đàm thoại
 */
sealed class CallState {
    object Idle : CallState()
    data class Calling(val targetUserId: String) : CallState()
    /** Đã có âm thanh với ít nhất một thành viên; [peerUserIds] là những người đã kết nối. */
    data class InCall(val peerUserIds: List<String>) : CallState()
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
class DamThoaiCallManager private constructor(private val platform: PlatformServices) {

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

        private var instance: DamThoaiCallManager? = null

        /**
         * Lấy bộ điều phối dùng chung. Chỉ gọi trên Main thread, sau khi
         * [PlatformServices.install] đã chạy lúc khởi động app.
         */
        fun getInstance(): DamThoaiCallManager =
            instance ?: DamThoaiCallManager(PlatformServices.instance).also { instance = it }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val signalingClient = SignalingClient()
    /** Âm thanh P2P WebRTC của nền tảng (Android: WebRTCClient, iOS: Swift). */
    val rtc = platform.rtc

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
    private val _eventName = MutableStateFlow(AppStartPrefs.getEventName())
    val eventName: StateFlow<String> = _eventName.asStateFlow()

    // MESH: mỗi thành viên khác trong kênh một kết nối P2P riêng.
    // peerId -> mã phiên; mã phiên đổi mỗi lần tạo lại kết nối, để bỏ qua callback của kết nối cũ.
    private val peerSessions = mutableMapOf<String, Int>()
    private var sessionCounter = 0
    // Những thành viên đã thông âm thanh (ICE CONNECTED/COMPLETED)
    private val connectedPeers = mutableSetOf<String>()
    // peerId -> các kênh dùng chung với người đó (quyết định gửi tiếng và âm lượng nghe)
    private val peerChannels = mutableMapOf<String, Set<String>>()

    // Vai trò + nhóm của máy này
    private val _seat = MutableStateFlow(AppStartPrefs.getSeat())
    val seat: StateFlow<Seat> = _seat.asStateFlow()

    // Kênh đang phát mic (PTT chỉ tới người cùng kênh này)
    private val _talkChannelId = MutableStateFlow(defaultTalkChannel(AppStartPrefs.getSeat()))
    val talkChannelId: StateFlow<String> = _talkChannelId.asStateFlow()

    // Kênh -> những người đang nói trên kênh đó (theo gói "ptt")
    private val _speakers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val speakers: StateFlow<Map<String, Set<String>>> = _speakers.asStateFlow()

    // Kênh -> âm lượng nghe hiệu dụng (0 = tắt loa kênh đó). Kênh chưa đặt = 1.
    private val channelVolumes = mutableMapOf<String, Float>()

    // Giành lượt nói: server quyết định ai được nói trên mỗi kênh, trưởng ban được chen ngang
    private val floor = FloorController(
        scope = scope,
        myUserId = { AppStartPrefs.getUserId() },
        isOnline = { signalingClient.isConnected() },
        sendRequest = { signalingClient.requestFloor(it) },
        sendRelease = { signalingClient.releaseFloor(it) },
        setMicOpen = { rtc.setMicrophoneEnabled(it) }
    )
    val floorStatus: StateFlow<FloorStatus> = floor.status

    // Câu báo cho người dùng (kênh bận, bị chen ngang, hết giờ nói) — UI hiện dạng toast
    private val _floorNotices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val floorNotices: SharedFlow<String> = _floorNotices.asSharedFlow()

    // Đã chọn tuyến âm thanh cho phiên hiện tại chưa (không ghi đè lựa chọn của người dùng)
    private var audioSessionStarted = false

    // Bộ giám sát kết nối máy chủ (tự tìm lại máy chủ khi không kết nối được)
    private var supervisorJob: Job? = null
    private val serverLocator = ServerLocator(platform.discovery, platform.lanProbe)

    // Trạng thái kết nối máy chủ hiển thị cho người dùng
    private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Idle)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()
    private var recallJob: Job? = null

    // Quản lý lắng nghe chuyển mạng (Seamless Handover)
    private var networkMonitor: NetworkMonitor? = null

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
        // Đổi sự kiện giành lượt nói thành câu báo có tên người
        scope.launch {
            floor.notices.collect { notice ->
                val text = when (notice) {
                    is FloorNotice.Busy -> "Kênh đang bận: ${memberLabel(notice.holderId)} đang nói"
                    is FloorNotice.Preempted -> "${memberLabel(notice.byId, withRole = true)} đã giành lượt nói"
                    FloorNotice.Timeout -> "Đã nói quá 60 giây. Nhả PTT rồi bấm lại để nói tiếp"
                }
                _floorNotices.tryEmit(text)
            }
        }
    }

    /** Tên hiển thị của một thành viên (kèm vai trò nếu cần). */
    private fun memberLabel(userId: String?, withRole: Boolean = false): String {
        val member = _onlineMembers.value.firstOrNull { it.userId == userId } ?: return "Người khác"
        val name = member.displayName?.takeIf { it.isNotBlank() } ?: member.userId
        return if (withRole) "$name (${member.seat().role.title})" else name
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
    fun joinEvent(
        pin: String,
        eventNameHint: String? = null,
        asOrganizer: Boolean = false,
        seat: Seat? = null
    ) {
        val previousPin = AppStartPrefs.getEventPin()
        AppStartPrefs.setEventPin(pin)
        AppStartPrefs.setOrganizer(asOrganizer)
        AppStartPrefs.setHasJoinedEvent(true)
        // Vai trò: theo mã QR; người tạo sự kiện là trưởng ban; vào bằng PIN sự kiện khác thì
        // là thành viên kênh chung; vào lại cùng sự kiện bằng PIN thì giữ vai trò cũ.
        val newSeat = when {
            seat != null -> seat
            asOrganizer -> Seat(TeamRole.TRUONG_BAN, TeamChannels.COMMAND_ID, TeamChannels.COMMAND_NAME)
            previousPin != pin -> Seat(TeamRole.THANH_VIEN, TeamChannels.GENERAL_ID, TeamChannels.GENERAL_NAME)
            else -> AppStartPrefs.getSeat()
        }
        when {
            !eventNameHint.isNullOrBlank() -> AppStartPrefs.setEventName(eventNameHint.trim())
            // Vào sự kiện khác: bỏ tên của sự kiện cũ, chờ server trả tên thật
            previousPin != pin || !AppStartPrefs.hasCustomEventName() ->
                AppStartPrefs.setEventName("Sự kiện Tần số $pin")
        }
        _eventName.value = AppStartPrefs.getEventName()
        if (previousPin != pin) {
            resetAllPeers("Đổi sự kiện")
            _onlineMembers.value = emptyList()
            _onlineUsers.value = emptyList()
        }
        applySeat(newSeat)
        // Gửi lại hồ sơ (kèm vai trò mới): server phát danh sách mới cho cả sự kiện
        autoDiscoverAndConnect(AppStartPrefs.getUserId())
    }

    /**
     * Cập nhật tên hiển thị / vai trò; gửi ngay lên server để danh sách thành viên đổi theo.
     */
    fun updateProfile(displayName: String, roleTitle: String) {
        AppStartPrefs.setProfile(displayName, roleTitle)
        signalingClient.updateRegistration(buildRegistration(AppStartPrefs.getUserId()))
    }

    /** Ban tổ chức đổi tên sự kiện: lưu và báo server để mọi người cập nhật. */
    fun updateEventName(name: String) {
        if (name.isBlank()) return
        AppStartPrefs.setEventName(name.trim())
        _eventName.value = AppStartPrefs.getEventName()
        signalingClient.updateRegistration(buildRegistration(AppStartPrefs.getUserId()))
    }

    /** Đổi vai trò/nhóm: lưu lại, chọn lại kênh phát và kết nối lại đúng những người được phép. */
    private fun applySeat(newSeat: Seat) {
        AppStartPrefs.setSeat(newSeat)
        if (_seat.value == newSeat) return
        _seat.value = newSeat
        _talkChannelId.value = defaultTalkChannel(newSeat)
        _speakers.value = emptyMap()
        evaluatePeers(_onlineMembers.value)
    }

    /** Kênh phát mặc định: kênh nhóm (trưởng nhóm, thành viên) hoặc Ban chỉ huy (trưởng ban). */
    private fun defaultTalkChannel(seat: Seat): String = TeamChannels.channelsOf(seat).last()

    private fun buildRegistration(userId: String): RegisterMessage {
        val organizer = AppStartPrefs.isOrganizer()
        val seat = AppStartPrefs.getSeat()
        return RegisterMessage(
            teamRole = seat.role.wire,
            channelId = seat.groupId,
            channelName = TeamChannels.channelName(seat.groupId, seat.groupName),
            userId = userId,
            eventPin = AppStartPrefs.getEventPin().ifBlank { null },
            displayName = AppStartPrefs.getDisplayName().ifBlank { null },
            roleTitle = AppStartPrefs.getRoleTitle().ifBlank { null },
            isOrganizer = organizer,
            eventName = if (organizer && AppStartPrefs.hasCustomEventName()) {
                AppStartPrefs.getEventName()
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
        if (!AppStartPrefs.hasJoinedEvent()) return
        startConnectionSupervisor(restart = true, searchNow = true)
    }

    /**
     * Người dùng lưu địa chỉ máy chủ trong cài đặt ("" = quay về tự động).
     * Nếu đang trong sự kiện thì chuyển sang địa chỉ mới ngay, không chờ vòng thử lại với địa chỉ cũ.
     */
    fun setServerUrl(url: String) {
        AppStartPrefs.setManualServerUrl(url)
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
                ServerLocator.isServerAlive(connectedUrl, platform.lanProbe)
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
            signalingClient.connect(url, buildRegistration(AppStartPrefs.getUserId()))
        }
        val ok = awaitRegistered(CONNECT_TIMEOUT_MS)
        if (ok) {
            AppStartPrefs.setLastServerUrl(url)
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
        AppStartPrefs.getManualServerUrl(),
        platform.cloudSignalingUrl,
        AppStartPrefs.getLastServerUrl()
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
                rtc.updateIceServers(event.iceServers)
            }

            is SignalingEvent.EventInfoReceived -> {
                val myPin = AppStartPrefs.getEventPin()
                if (event.eventPin != myPin) return
                if (event.found && event.eventName.isNotBlank()) {
                    Log.d(TAG, "Tên sự kiện từ ban tổ chức: ${event.eventName}")
                    AppStartPrefs.setEventName(event.eventName)
                    _eventName.value = AppStartPrefs.getEventName()
                } else if (!AppStartPrefs.isOrganizer()) {
                    Log.w(TAG, "Server chưa có sự kiện với PIN $myPin (ban tổ chức chưa online)")
                }
            }

            is SignalingEvent.OfferReceived -> {
                val sender = event.senderId
                if (sender.isNullOrBlank()) {
                    Log.w(TAG, "Bỏ qua Offer không có sender_id")
                    return
                }
                // Phân tầng vai trò: chỉ nhận cuộc gọi từ người được phép đàm thoại với mình
                if (allowedChannelsWith(sender).isEmpty()) {
                    Log.w(TAG, "Từ chối Offer từ $sender: vai trò không được đàm thoại với nhau")
                    return
                }
                Log.d(TAG, "Nhận Offer từ $sender. Chuẩn bị trả lời...")
                handleIncomingOffer(sender, event.sdp)
            }

            is SignalingEvent.AnswerReceived -> {
                val sender = event.senderId
                if (sender.isNullOrBlank() || !peerSessions.containsKey(sender)) {
                    Log.w(TAG, "Bỏ qua Answer từ $sender (chưa mở kết nối tới người này)")
                    return
                }
                Log.d(TAG, "Nhận Answer SDP từ $sender")
                rtc.setRemoteDescription(sender, SdpType.ANSWER, event.sdp, onSuccess = {
                    Log.d(TAG, "Đã đặt Answer của $sender, chờ ICE kết nối")
                }, onError = { err ->
                    Log.e(TAG, "Lỗi đặt Answer của $sender: $err")
                })
            }

            is SignalingEvent.IceCandidateReceived -> {
                val sender = event.senderId
                if (sender.isNullOrBlank() || !peerSessions.containsKey(sender)) return
                rtc.addIceCandidate(sender, event.sdpMid, event.sdpMLineIndex, event.candidate)
            }

            is SignalingEvent.UserListReceived -> {
                Log.d(TAG, "Nhận danh sách online (${event.users.size} người): ${event.users}")
                _onlineUsers.value = event.users
                _onlineMembers.value = event.members
                // Người đã rời thì không còn "đang nói"
                val online = event.users.toSet()
                _speakers.value = _speakers.value
                    .mapValues { (_, ids) -> ids.filter { it in online }.toSet() }
                    .filterValues { it.isNotEmpty() }
                evaluatePeers(event.members)
            }

            is SignalingEvent.FloorGranted -> floor.onGranted(event.channelId)
            is SignalingEvent.FloorDenied -> floor.onDenied(event.channelId, event.holderId)
            is SignalingEvent.FloorState -> handleFloorState(event)
            // Gói "ptt" của app bản cũ: đã thay bằng floor_state, bỏ qua
            is SignalingEvent.PttReceived -> {}
        }
    }

    /** Các kênh mình dùng chung với [userId] theo luật phân tầng (rỗng = không được kết nối). */
    private fun allowedChannelsWith(userId: String): Set<String> {
        val member = _onlineMembers.value.firstOrNull { it.userId == userId } ?: return emptySet()
        return TeamChannels.sharedChannels(_seat.value, member.seat())
    }

    /**
     * Người giữ lượt nói trên một kênh đổi (server phát cho cả sự kiện).
     * - Cập nhật "ai đang nói" cho các kênh mình thuộc về.
     * - Mình đang nói mà lượt sang người khác (bị chen ngang / hết giờ): tắt mic.
     * - (a) Trả lời nhanh: có người bắt đầu nói trên kênh khác kênh đang phát (và mình không giữ PTT)
     *   → tự chuyển kênh phát sang kênh đó, bấm PTT là trả lời đúng người vừa gọi.
     */
    private fun handleFloorState(event: SignalingEvent.FloorState) {
        floor.onState(event.channelId, event.holderId, event.preemptedId, event.reason)

        if (event.channelId !in TeamChannels.channelsOf(_seat.value)) return
        val holder = event.holderId
        _speakers.value = if (holder == null) {
            _speakers.value - event.channelId
        } else {
            _speakers.value + (event.channelId to setOf(holder))
        }

        val me = AppStartPrefs.getUserId()
        if (holder != null && holder != me && !_isPttPressed.value && _talkChannelId.value != event.channelId) {
            Log.d(TAG, "$holder đang nói trên ${event.channelId}: tự chuyển kênh phát để trả lời")
            setTalkChannel(event.channelId)
        }
    }

    /**
     * Chọn kênh phát mic. Chỉ chọn được kênh mình thuộc về.
     * Đổi kênh khi đang giữ PTT: trả lượt kênh cũ, xin lượt kênh mới.
     */
    fun setTalkChannel(channelId: String) {
        if (channelId !in TeamChannels.channelsOf(_seat.value)) return
        if (_talkChannelId.value == channelId) return
        _talkChannelId.value = channelId
        applyPeerAudio()
        if (_isPttPressed.value) floor.switchChannel(channelId)
    }

    /**
     * Âm lượng nghe theo kênh (0 = tắt loa kênh đó). Người dùng chung nhiều kênh với mình
     * nghe theo kênh to nhất.
     */
    fun setChannelVolume(channelId: String, volume: Float) {
        channelVolumes[channelId] = volume.coerceIn(0f, 1f)
        applyPeerAudio()
    }

    /** Áp lại cho mọi kết nối: gửi tiếng (đúng kênh phát) và âm lượng nghe (theo kênh). */
    private fun applyPeerAudio() {
        val talk = _talkChannelId.value
        peerSessions.keys.forEach { peerId ->
            val channels = peerChannels[peerId].orEmpty()
            rtc.setPeerSending(peerId, talk in channels)
            val volume = channels.maxOfOrNull { channelVolumes[it] ?: 1f } ?: 0f
            rtc.setPeerVolume(peerId, volume)
        }
    }

    /**
     * MESH theo phân tầng vai trò: giữ một kết nối P2P tới MỖI thành viên online mà mình được phép
     * đàm thoại (xem [TeamChannels.sharedChannels]).
     * - Đã rời, hoặc đổi vai trò/nhóm nên không còn được phép: đóng kết nối của riêng người đó.
     * - Được phép mà chưa có kết nối: máy có ID nhỏ hơn chủ động gửi Offer (máy kia chờ),
     *   tránh 2 máy gọi nhau cùng lúc. Mỗi cặp máy vì vậy có đúng một bên gọi.
     */
    private fun evaluatePeers(members: List<OnlineMember>) {
        val myUid = AppStartPrefs.getUserId()
        val mySeat = _seat.value
        val allowed = members
            .filter { it.userId != myUid }
            .associate { it.userId to TeamChannels.sharedChannels(mySeat, it.seat()) }
            .filterValues { it.isNotEmpty() }

        peerSessions.keys.filter { it !in allowed }.forEach { gone ->
            Log.d(TAG, "Đóng kết nối với $gone (đã rời hoặc không còn cùng kênh)")
            closePeer(gone, "Đối tác đã rời kênh")
        }
        // Kênh dùng chung có thể đổi (VD đối tác đổi nhóm): cập nhật rồi áp lại âm thanh
        allowed.forEach { (id, channels) -> if (peerSessions.containsKey(id)) peerChannels[id] = channels }
        applyPeerAudio()

        allowed.keys.filter { myUid < it && !peerSessions.containsKey(it) }
            .sorted()
            .forEach { peer ->
                Log.d(TAG, "Tự động kích hoạt gọi P2P tới $peer (kênh ${allowed[peer]})")
                startCall(peer)
            }
    }

    // ------------------------------------------------------------------
    // CUỘC GỌI WEBRTC
    // ------------------------------------------------------------------

    /**
     * Mở kết nối tới một thành viên (các kết nối khác giữ nguyên).
     */
    fun startCall(targetUserId: String) {
        prepareCallSession()
        val session = setupPeerConnection(targetUserId)
        publishCallState()

        rtc.createOffer(
            targetUserId,
            onSuccess = { sdp ->
                scope.launch {
                    if (peerSessions[targetUserId] != session) return@launch
                    Log.d(TAG, "Gửi Offer SDP tới $targetUserId qua Signaling")
                    signalingClient.sendOffer(targetUserId, sdp)
                }
            },
            onError = { err ->
                Log.e(TAG, "Lỗi tạo Offer tới $targetUserId: $err")
                scope.launch { onPeerFailed(targetUserId, session, err) }
            }
        )
    }

    /**
     * Xử lý Offer từ một thành viên.
     * Offer từ người đã có kết nối (ICE Restart khi đổi mạng) dùng lại PeerConnection đang có;
     * nếu Offer đó không áp được (VD máy kia vừa mở lại app) thì tạo kết nối mới và thử lại một lần.
     */
    private fun handleIncomingOffer(senderUserId: String, offerSdpStr: String) {
        val existing = peerSessions[senderUserId]
        val isRenegotiation = existing != null && rtc.hasPeerConnection(senderUserId)
        val session = if (isRenegotiation) {
            existing!!
        } else {
            prepareCallSession()
            setupPeerConnection(senderUserId).also { publishCallState() }
        }
        applyRemoteOffer(senderUserId, session, offerSdpStr, retryWithFreshConnection = isRenegotiation)
    }

    private fun applyRemoteOffer(
        senderUserId: String,
        session: Int,
        offerSdpStr: String,
        retryWithFreshConnection: Boolean
    ) {
        rtc.setRemoteDescription(senderUserId, SdpType.OFFER, offerSdpStr, onSuccess = {
            rtc.createAnswer(
                senderUserId,
                onSuccess = { answerSdp ->
                    scope.launch {
                        if (peerSessions[senderUserId] != session) return@launch
                        Log.d(TAG, "Gửi Answer SDP phản hồi $senderUserId")
                        signalingClient.sendAnswer(senderUserId, answerSdp)
                    }
                },
                onError = { err ->
                    Log.e(TAG, "Lỗi tạo Answer cho $senderUserId: $err")
                    scope.launch { onPeerFailed(senderUserId, session, err) }
                }
            )
        }, onError = { err ->
            Log.e(TAG, "Lỗi đặt Offer từ $senderUserId: $err")
            scope.launch {
                if (peerSessions[senderUserId] != session) return@launch
                if (retryWithFreshConnection) {
                    Log.d(TAG, "Tạo lại kết nối mới với $senderUserId rồi áp lại Offer")
                    val fresh = setupPeerConnection(senderUserId)
                    publishCallState()
                    applyRemoteOffer(senderUserId, fresh, offerSdpStr, retryWithFreshConnection = false)
                } else {
                    onPeerFailed(senderUserId, session, err)
                }
            }
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
     * Tạo PeerConnection tới [targetUserId] và chuyển tiếp ICE Candidates.
     * @return mã phiên của kết nối vừa tạo.
     */
    private fun setupPeerConnection(targetUserId: String): Int {
        val session = ++sessionCounter
        peerSessions[targetUserId] = session
        connectedPeers.remove(targetUserId)
        rtc.createPeerConnection(targetUserId, object : RtcPeerListener {
            override fun onIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
                signalingClient.sendIceCandidate(
                    targetId = targetUserId,
                    sdpMid = sdpMid,
                    sdpMLineIndex = sdpMLineIndex,
                    candidate = candidate
                )
            }

            override fun onIceConnectionChange(state: RtcIceState) {
                Log.d(TAG, "Trạng thái ICE Connection với $targetUserId: $state")
                scope.launch {
                    if (peerSessions[targetUserId] != session) return@launch
                    when (state) {
                        RtcIceState.CONNECTED,
                        RtcIceState.COMPLETED -> {
                            connectedPeers.add(targetUserId)
                            publishCallState()
                        }
                        RtcIceState.FAILED -> {
                            onPeerFailed(targetUserId, session, "Mất kết nối mạng P2P")
                        }
                        // DISCONNECTED thường tự hồi phục (đổi mạng); để handover/ICE xử lý
                        else -> {}
                    }
                }
            }
        })
        // Kênh dùng chung quyết định có gửi tiếng tới người này và nghe họ to bao nhiêu
        peerChannels[targetUserId] = allowedChannelsWith(targetUserId)
        applyPeerAudio()
        return session
    }

    /** Kết nối tới một thành viên lỗi: đóng riêng kết nối đó và thử gọi lại sau một lúc nếu họ vẫn online. */
    private fun onPeerFailed(peerId: String, session: Int, reason: String) {
        if (peerSessions[peerId] != session) return
        closePeer(peerId, reason)
        recallJob?.cancel()
        recallJob = scope.launch {
            delay(RECALL_DELAY_MS)
            evaluatePeers(_onlineMembers.value)
        }
    }

    /** Đóng kết nối với một thành viên, giữ nguyên kết nối với những người khác. */
    private fun closePeer(peerId: String, reason: String?) {
        if (peerSessions.remove(peerId) == null) return
        connectedPeers.remove(peerId)
        peerChannels.remove(peerId)
        rtc.closePeer(peerId)
        if (peerSessions.isEmpty() && reason != null) {
            _callState.value = CallState.CallEnded(reason)
        } else {
            publishCallState()
        }
    }

    /** Đóng mọi kết nối P2P nhưng vẫn ở trong sự kiện (vẫn kết nối signaling, PTT vẫn dùng được). */
    private fun resetAllPeers(reason: String?) {
        val hadPeers = peerSessions.isNotEmpty()
        peerSessions.clear()
        connectedPeers.clear()
        peerChannels.clear()
        rtc.close()
        if (hadPeers || reason == null) {
            _callState.value = if (reason == null) CallState.Idle else CallState.CallEnded(reason)
        }
    }

    /** Cập nhật [callState] theo các kết nối hiện có. */
    private fun publishCallState() {
        _callState.value = when {
            connectedPeers.isNotEmpty() -> CallState.InCall(connectedPeers.sorted())
            peerSessions.isNotEmpty() -> CallState.Calling(peerSessions.keys.sorted().first())
            else -> when (val current = _callState.value) {
                is CallState.InCall, is CallState.Calling -> CallState.Idle
                else -> current
            }
        }
    }

    /**
     * Bấm giữ phím PTT (Push-to-Talk) để nói, nhả ra để chỉ nghe
     */
    fun setPushToTalkPressed(isPressed: Boolean) {
        val wasPressed = _isPttPressed.value
        _isPttPressed.value = isPressed
        when {
            // Bấm: xin lượt nói; mic chỉ mở khi server cho (hoặc không có server)
            isPressed && !wasPressed -> floor.press(_talkChannelId.value)
            // Nhả (kể cả gọi lại khi kết thúc phiên): luôn tắt mic, trả lượt
            !isPressed -> floor.release()
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
        val now = currentTimeMillis()
        val alert = EmergencyAlert(
            id = "alert_$now",
            fromName = fromName,
            fromRole = fromRole,
            startedAtMs = now,
            isDirectCall = isDirectCall
        )
        Log.d(TAG, "Kích hoạt Cảnh báo Khẩn cấp: $fromName ($fromRole)")
        EmergencyAlertManager.trigger(alert)
    }

    /** Âm lượng nghe tiếng đàm thoại (0..1), do thanh âm lượng của kênh đang nghe điều khiển. */
    fun setListeningVolume(volume: Float) {
        TeamChannels.channelsOf(_seat.value).forEach { channelVolumes[it] = volume.coerceIn(0f, 1f) }
        applyPeerAudio()
    }

    /**
     * Kết thúc cuộc gọi
     */
    fun endCall() {
        recallJob?.cancel()
        resetAllPeers(null)
        setPushToTalkPressed(false)

        // TẮT PTT, TRẢ LẠI PHÍM ÂM LƯỢNG CHO HỆ ĐIỀU HÀNH
        PttController.releaseCapture("DamThoaiCallManager")
        PttController.setPttModeEnabled(false)
        PttController.forceReleasePtt()

        // Dọn dẹp tài nguyên mạng và đưa âm thanh về mặc định.
        // Không dừng mDNS ở đây: bộ giám sát kết nối có thể đang dùng nó để tìm máy chủ.
        networkMonitor?.stop()
        networkMonitor = null
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
        AppStartPrefs.setHasJoinedEvent(false)
    }

    /**
     * Ngắt toàn bộ dịch vụ khi đóng ứng dụng
     */
    fun destroy() {
        endCall()
        stopConnectionSupervisor()
        signalingClient.disconnect()
        platform.discovery.stopDiscovery()
    }

    // --- CÁC HÀM XỬ LÝ NGOẠI VI (MẠNG & ÂM THANH) ---

    private fun startNetworkMonitoring() {
        if (networkMonitor == null) {
            networkMonitor = platform.networkMonitorFactory().also { monitor ->
                monitor.start { scope.launch { triggerSeamlessHandover() } }
            }
        }
    }

    private fun triggerSeamlessHandover() {
        if (peerSessions.isEmpty()) return
        Log.d(TAG, "Bắt đầu Seamless Handover tới ${peerSessions.keys}...")

        // TUẦN 5: NẾU MẤT KẾT NỐI INTERNET CLOUD -> TỰ ĐỘNG BẬT mDNS FALLBACK QUÉT LAN
        if (!signalingClient.isConnected()) {
            Log.w(TAG, "Đổi mạng và mất kết nối máy chủ: tìm lại máy chủ ngay")
            retryServerConnection()
            return
        }

        // Với mỗi kết nối, chỉ bên chủ động gọi (ID nhỏ hơn) mới gửi ICE Restart, tránh 2 bên cùng gửi Offer
        val myUid = AppStartPrefs.getUserId()
        peerSessions.forEach { (targetId, session) ->
            if (myUid > targetId) return@forEach
            rtc.restartIce(
                targetId,
                onSuccess = { sdp ->
                    scope.launch {
                        if (peerSessions[targetId] != session) return@launch
                        Log.d(TAG, "Gửi Offer mới tới $targetId để nối lại cuộc gọi")
                        signalingClient.sendOffer(targetId, sdp)
                    }
                },
                onError = { err ->
                    Log.e(TAG, "Lỗi Handover với $targetId: $err")
                }
            )
        }
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
            platform.callAudio.start()
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
            platform.callAudio.release()
            Log.d(TAG, "Đã giải phóng tài nguyên Audio về mặc định")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi dọn dẹp audio: ${e.message}")
        }
    }
}
