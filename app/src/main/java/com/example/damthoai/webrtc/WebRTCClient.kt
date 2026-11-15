package com.example.damthoai.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*

/**
 * Chỉ số chất lượng cuộc gọi đọc từ WebRTC getStats() (TV1 - Tuần 6: đo khả năng phục hồi khi rớt gói).
 */
data class CallStats(
    val rttMs: Int? = null,
    val packetsReceived: Long = 0,
    val packetsLost: Long = 0,
    /** Tỉ lệ mất gói tính trên cửa sổ giữa 2 lần đo (0.0 - 1.0) */
    val intervalLossRate: Double = 0.0,
    val jitterMs: Double = 0.0,
    /** Số mẫu âm thanh bị NetEQ "vá" (concealment) do mất gói -> đo độ phục hồi âm thanh */
    val concealedSamples: Long = 0,
    val totalSamplesReceived: Long = 0,
    val localCandidateType: String? = null,
    val remoteCandidateType: String? = null,
    val localNetworkType: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /** Tỉ lệ âm thanh phải che lấp (0.0 - 1.0) */
    val concealmentRatio: Double
        get() = if (totalSamplesReceived > 0) concealedSamples.toDouble() / totalSamplesReceived else 0.0

    /** Đang đi qua TURN relay hay kết nối trực tiếp */
    val isRelayed: Boolean get() = localCandidateType == "relay" || remoteCandidateType == "relay"
}

class WebRTCClient(private val context: Context) {

    // Singleton cho PeerConnectionFactory để chống lỗi Dispose
    companion object {
        private const val TAG = "WebRTCClient"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"
        private const val STREAM_ID = "ARDAMS"
        private var factory: PeerConnectionFactory? = null

        /** STUN/TURN miễn phí mặc định khi chưa nhận được cấu hình từ Signaling Server */
        val DEFAULT_ICE_SERVERS: List<PeerConnection.IceServer> = listOf(
            PeerConnection.IceServer.builder(
                listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")
            ).createIceServer(),
            PeerConnection.IceServer.builder(
                listOf(
                    "turn:openrelay.metered.ca:80",
                    "turn:openrelay.metered.ca:443",
                    "turn:openrelay.metered.ca:443?transport=tcp"
                )
            ).setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
        )

        @Synchronized
        fun getOrCreateFactory(context: Context): PeerConnectionFactory {
            if (factory == null) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
                )
                factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
            }
            return factory!!
        }
    }

    private var peerConnection: PeerConnection? = null
    private val rtcFactory = getOrCreateFactory(context)
    private var iceServers: List<PeerConnection.IceServer> = DEFAULT_ICE_SERVERS
    private var audioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var isOutputMuted: Boolean = false
    private val remoteAudioTracks = mutableListOf<AudioTrack>()

    /** ICE candidate đến trước khi có Remote Description -> giữ lại, nạp sau */
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var hasRemoteDescription = false

    // Bộ đếm cho tính tỉ lệ mất gói theo cửa sổ
    private var lastPacketsReceived = 0L
    private var lastPacketsLost = 0L

    val hasPeerConnection: Boolean get() = peerConnection != null

    val signalingState: PeerConnection.SignalingState?
        get() = try { peerConnection?.signalingState() } catch (e: Throwable) { null }

    /**
     * Cập nhật danh sách ICE Servers nhận từ Signaling Server.
     * Nếu đang có cuộc gọi thì áp dụng ngay (lần ICE restart kế tiếp sẽ dùng TURN mới).
     * Truyền danh sách rỗng ở chế độ LAN Fallback (chỉ dùng host candidate nội bộ).
     */
    @Synchronized
    fun updateIceServers(newServers: List<PeerConnection.IceServer>) {
        iceServers = newServers
        peerConnection?.let { pc ->
            try {
                pc.setConfiguration(buildRtcConfig())
            } catch (e: Throwable) {
                Log.w(TAG, "Không áp dụng được ICE servers mới: ${e.message}")
            }
        }
    }

    /**
     * Cấu hình WebRTC (Tuần 1 + Tuần 3):
     *  - Unified Plan (chuẩn SDP hiện hành)
     *  - GATHER_CONTINUALLY: liên tục thu thập ICE candidate khi giao diện mạng thay đổi
     *    (bật Wi-Fi/4G) -> nền tảng cho Seamless Handover Tuần 4.
     *  - Pool candidate dựng sẵn để rút ngắn thời gian kết nối khi bấm PTT.
     */
    private fun buildRtcConfig(): PeerConnection.RTCConfiguration =
        PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceCandidatePoolSize = 2
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
            keyType = PeerConnection.KeyType.ECDSA
            // Phát hiện nhanh đường truyền chết để chuyển sang đường dự phòng
            iceConnectionReceivingTimeout = 3000
            iceBackupCandidatePairPingInterval = 2000
        }

    /**
     * Khởi tạo PeerConnection và chuẩn bị kênh Audio cho PTT
     */
    fun createPeerConnection(observer: PeerConnection.Observer) {
        if (peerConnection != null) {
            Log.w(TAG, "PeerConnection cũ vẫn còn, đóng trước khi tạo mới")
            close()
        }
        val pc = rtcFactory.createPeerConnection(buildRtcConfig(), observer)
        synchronized(this) { setupNewPeerConnection(pc) }
    }

    private fun setupNewPeerConnection(pc: PeerConnection?) {
        peerConnection = pc
        hasRemoteDescription = false
        pendingRemoteCandidates.clear()
        lastPacketsReceived = 0
        lastPacketsLost = 0

        // Tạo Audio Source và Track để điều khiển Push-to-Talk
        val audioConstraints = MediaConstraints()
        audioSource = rtcFactory.createAudioSource(audioConstraints)
        audioTrack = rtcFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource).also { track ->
            track.setEnabled(false) // Mặc định tắt mic khi chưa giữ phím PTT
            // Gắn track vào PeerConnection -> SDP có m=audio sendrecv, âm thanh thật sự được truyền đi
            peerConnection?.addTrack(track, listOf(STREAM_ID))
        }
    }

    /**
     * Bật/tắt microphone khi người dùng bấm hoặc nhả phím PTT
     * Tự động ghi log thực địa cho TV4 (FieldTestLogger)
     */
    fun setMicrophoneEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
        try {
            com.example.damthoai.hardware.diagnostics.FieldTestLogger.recordMicrophoneState(
                open = enabled,
                source = "TV2_PTT"
            )
        } catch (ignored: Throwable) {}
    }

    /**
     * Khóa/mở âm thanh đầu ra để bảo vệ chống lọt tiếng khi rút tai nghe (Dùng cho AudioRouteGuard - Tuần 5).
     * Bật/tắt trạng thái phát của tất cả AudioTrack nhận từ phía đối tác.
     */
    @Synchronized
    fun setOutputMuted(muted: Boolean) {
        isOutputMuted = muted
        remoteAudioTracks.forEach { track ->
            try {
                track.setEnabled(!muted)
            } catch (ignored: Throwable) {}
        }
    }

    /** Unified Plan: track âm thanh từ xa được báo qua onAddTrack/onTrack */
    @Synchronized
    fun handleRemoteTrack(track: MediaStreamTrack?) {
        val audio = track as? AudioTrack ?: return
        if (!remoteAudioTracks.contains(audio)) {
            remoteAudioTracks.add(audio)
            try {
                audio.setEnabled(!isOutputMuted)
            } catch (ignored: Throwable) {}
        }
    }

    @Synchronized
    fun handleRemoteStreamAdded(stream: MediaStream?) {
        stream?.audioTracks?.forEach { handleRemoteTrack(it) }
    }

    @Synchronized
    fun handleRemoteStreamRemoved(stream: MediaStream?) {
        stream?.audioTracks?.forEach { track ->
            if (track != null) {
                remoteAudioTracks.remove(track)
            }
        }
    }

    /**
     * Tạo Offer SDP để gửi đi. onSuccess chỉ được gọi khi Local Description đã được áp dụng.
     */
    fun createOffer(onSuccess: (SessionDescription) -> Unit, onError: (String) -> Unit) {
        createOfferInternal(MediaConstraints(), onSuccess, onError)
    }

    private fun createOfferInternal(
        constraints: MediaConstraints,
        onSuccess: (SessionDescription) -> Unit,
        onError: (String) -> Unit
    ) {
        val pc = peerConnection ?: return onError("Chưa có PeerConnection")
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return onError("Offer rỗng")
                setLocal(pc, desc, onSuccess, onError)
            }
            override fun onCreateFailure(error: String?) {
                onError(error ?: "Lỗi tạo Offer không xác định")
            }
        }, constraints)
    }

    /**
     * Tạo Answer SDP phản hồi Offer
     */
    fun createAnswer(onSuccess: (SessionDescription) -> Unit, onError: (String) -> Unit) {
        val pc = peerConnection ?: return onError("Chưa có PeerConnection")
        pc.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return onError("Answer rỗng")
                setLocal(pc, desc, onSuccess, onError)
            }
            override fun onCreateFailure(error: String?) {
                onError(error ?: "Lỗi tạo Answer không xác định")
            }
        }, MediaConstraints())
    }

    private fun setLocal(
        pc: PeerConnection,
        desc: SessionDescription,
        onSuccess: (SessionDescription) -> Unit,
        onError: (String) -> Unit
    ) {
        pc.setLocalDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() = onSuccess(desc)
            override fun onSetFailure(error: String?) = onError(error ?: "Lỗi setLocalDescription")
        }, desc)
    }

    /**
     * Thiết lập Remote Description từ đối tác gửi tới
     */
    fun setRemoteDescription(
        sdp: SessionDescription,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = { Log.e(TAG, "Lỗi setRemoteDescription: $it") }
    ) {
        val pc = peerConnection ?: return onError("Chưa có PeerConnection")
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                drainPendingCandidates()
                onSuccess()
            }
            override fun onSetFailure(error: String?) {
                onError(error ?: "Lỗi setRemoteDescription")
            }
        }, sdp)
    }

    /**
     * Huỷ Offer cục bộ đang chờ (xử lý "va chạm Offer" khi 2 máy cùng ICE restart một lúc)
     */
    fun rollbackLocalOffer(onDone: () -> Unit) {
        val pc = peerConnection ?: return
        pc.setLocalDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() = onDone()
            override fun onSetFailure(error: String?) {
                Log.w(TAG, "Rollback thất bại: $error")
                onDone()
            }
        }, SessionDescription(SessionDescription.Type.ROLLBACK, ""))
    }

    @Synchronized
    private fun drainPendingCandidates() {
        hasRemoteDescription = true
        val pc = peerConnection ?: return
        if (pendingRemoteCandidates.isNotEmpty()) {
            Log.d(TAG, "Nạp ${pendingRemoteCandidates.size} ICE candidate đang chờ")
            pendingRemoteCandidates.forEach { pc.addIceCandidate(it) }
            pendingRemoteCandidates.clear()
        }
    }

    /**
     * Thêm ICE Candidate nhận được từ mạng (giữ lại nếu Remote Description chưa sẵn sàng)
     */
    @Synchronized
    fun addIceCandidate(candidate: IceCandidate) {
        val pc = peerConnection ?: return
        if (!hasRemoteDescription) {
            pendingRemoteCandidates.add(candidate)
            return
        }
        pc.addIceCandidate(candidate)
    }

    /**
     * Tuần 3: Đối tác báo các candidate cũ đã chết (vd: mất Wi-Fi) -> bỏ để ICE không thử nữa
     */
    @Synchronized
    fun removeIceCandidates(candidates: List<IceCandidate>) {
        if (candidates.isEmpty()) return
        pendingRemoteCandidates.removeAll { c -> candidates.any { it.sdp == c.sdp } }
        peerConnection?.removeIceCandidates(candidates.toTypedArray())
    }

    /**
     * Khởi động lại ICE (ICE Restart) khi chuyển từ Wi-Fi sang 4G (Seamless Handover).
     * Bắt buộc WebRTC dò tìm lại đường truyền mạng mà không cần ngắt cuộc gọi:
     * DTLS/SRTP và audio track được giữ nguyên, chỉ đổi cặp candidate.
     */
    fun restartIce(onSuccess: (SessionDescription) -> Unit, onError: (String) -> Unit) {
        val pc = peerConnection ?: return onError("Chưa có PeerConnection")
        try {
            pc.restartIce()
        } catch (ignored: Throwable) {}
        val constraints = MediaConstraints().apply {
            // Cờ bắt buộc khởi động lại dò tìm ICE (tương thích bản WebRTC cũ)
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        createOfferInternal(constraints, onSuccess, onError)
    }

    /**
     * Đọc thống kê chất lượng đường truyền (RTT, mất gói, jitter, concealment, loại candidate).
     */
    fun collectStats(onResult: (CallStats) -> Unit) {
        val pc = peerConnection ?: return
        pc.getStats { report -> onResult(parseStats(report)) }
    }

    private fun parseStats(report: RTCStatsReport): CallStats {
        val all = report.statsMap.values
        fun Any?.asLong(): Long = (this as? Number)?.toLong() ?: 0L
        fun Any?.asDouble(): Double = (this as? Number)?.toDouble() ?: 0.0

        val selectedPair = all.firstOrNull { s ->
            s.type == "candidate-pair" && s.members["state"] == "succeeded" && s.members["nominated"] == true
        } ?: all.firstOrNull { it.type == "candidate-pair" && it.members["state"] == "succeeded" }

        val local = selectedPair?.members?.get("localCandidateId")?.let { report.statsMap[it as String] }
        val remote = selectedPair?.members?.get("remoteCandidateId")?.let { report.statsMap[it as String] }
        val inbound = all.firstOrNull { it.type == "inbound-rtp" && it.members["kind"] == "audio" }

        val received = inbound?.members?.get("packetsReceived").asLong()
        val lost = inbound?.members?.get("packetsLost").asLong()
        val dReceived = (received - lastPacketsReceived).coerceAtLeast(0)
        val dLost = (lost - lastPacketsLost).coerceAtLeast(0)
        lastPacketsReceived = received
        lastPacketsLost = lost
        val intervalLoss = if (dReceived + dLost > 0) dLost.toDouble() / (dReceived + dLost) else 0.0

        val rttSec = selectedPair?.members?.get("currentRoundTripTime") as? Number
        return CallStats(
            rttMs = rttSec?.let { (it.toDouble() * 1000).toInt() },
            packetsReceived = received,
            packetsLost = lost,
            intervalLossRate = intervalLoss,
            jitterMs = inbound?.members?.get("jitter").asDouble() * 1000,
            concealedSamples = inbound?.members?.get("concealedSamples").asLong(),
            totalSamplesReceived = inbound?.members?.get("totalSamplesReceived").asLong(),
            localCandidateType = local?.members?.get("candidateType") as? String,
            remoteCandidateType = remote?.members?.get("candidateType") as? String,
            localNetworkType = local?.members?.get("networkType") as? String
        )
    }

    /**
     * Đóng và giải phóng tài nguyên WebRTC
     */
    fun close() {
        // Lấy tham chiếu trong lock nhưng giải phóng NGOÀI lock: pc.close() chờ signaling thread,
        // mà callback trên signaling thread (onAddTrack, getStats...) cũng cần lock này -> tránh deadlock.
        val pc: PeerConnection?
        val track: AudioTrack?
        val source: AudioSource?
        synchronized(this) {
            remoteAudioTracks.clear()
            pendingRemoteCandidates.clear()
            hasRemoteDescription = false
            pc = peerConnection
            track = audioTrack
            source = audioSource
            peerConnection = null
            audioTrack = null
            audioSource = null
        }
        try { pc?.close() } catch (ignored: Throwable) {}
        try { pc?.dispose() } catch (ignored: Throwable) {}
        try { track?.dispose() } catch (ignored: Throwable) {}
        try { source?.dispose() } catch (ignored: Throwable) {}
    }

    fun startCall() {}
    fun endCall() { close() }
}

// Lớp phụ trợ cho SdpObserver
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

// Lớp phụ trợ cho PeerConnection.Observer
open class CustomPeerConnectionObserver(
    private val webRTCClient: WebRTCClient? = null
) : PeerConnection.Observer {
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(p0: Boolean) {}
    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(p0: org.webrtc.IceCandidate?) {}
    override fun onIceCandidatesRemoved(p0: Array<out org.webrtc.IceCandidate>?) {}
    override fun onAddStream(p0: org.webrtc.MediaStream?) {
        webRTCClient?.handleRemoteStreamAdded(p0)
    }
    override fun onRemoveStream(p0: org.webrtc.MediaStream?) {
        webRTCClient?.handleRemoteStreamRemoved(p0)
    }
    // Unified Plan không gọi onAddStream -> nhận track âm thanh từ xa ở đây
    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
        webRTCClient?.handleRemoteTrack(receiver?.track())
    }
    override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
    override fun onRenegotiationNeeded() {}
}
