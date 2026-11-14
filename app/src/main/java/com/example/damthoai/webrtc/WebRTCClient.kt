package com.example.damthoai.webrtc

import android.content.Context
import android.util.Log
import com.example.damthoai.platform.IceServerSpec
import com.example.damthoai.platform.RtcEngine
import com.example.damthoai.platform.RtcIceState
import com.example.damthoai.platform.RtcPeerListener
import com.example.damthoai.platform.SdpType
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

/** Android: âm thanh P2P bằng stream-webrtc-android, cài đặt [RtcEngine] dùng chung. */
class WebRTCClient(private val context: Context) : RtcEngine {

    // Singleton cho PeerConnectionFactory để chống lỗi Dispose
    companion object {
        private const val TAG = "WebRTCClient"
        private const val LOCAL_AUDIO_TRACK_ID = "ARDAMSa0"
        private const val LOCAL_STREAM_ID = "ARDAMS"

        @Volatile
        private var factory: PeerConnectionFactory? = null

        @Synchronized
        fun getOrCreateFactory(context: Context): PeerConnectionFactory {
            factory?.let { return it }
            val appContext = context.applicationContext
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
            // Module âm thanh Java: thu micro + phát loa qua luồng VOICE_COMMUNICATION,
            // bật khử tiếng vọng / khử ồn phần cứng để 2 máy đặt gần nhau không bị hú.
            val audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                    override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                        Log.e(TAG, "Lỗi khởi tạo thu âm: $errorMessage")
                    }
                    override fun onWebRtcAudioRecordStartError(
                        errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                        errorMessage: String?
                    ) {
                        Log.e(TAG, "Lỗi bắt đầu thu âm ($errorCode): $errorMessage")
                    }
                    override fun onWebRtcAudioRecordError(errorMessage: String?) {
                        Log.e(TAG, "Lỗi thu âm: $errorMessage")
                    }
                })
                .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                    override fun onWebRtcAudioTrackInitError(errorMessage: String?) {
                        Log.e(TAG, "Lỗi khởi tạo phát âm: $errorMessage")
                    }
                    override fun onWebRtcAudioTrackStartError(
                        errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?,
                        errorMessage: String?
                    ) {
                        Log.e(TAG, "Lỗi bắt đầu phát âm ($errorCode): $errorMessage")
                    }
                    override fun onWebRtcAudioTrackError(errorMessage: String?) {
                        Log.e(TAG, "Lỗi phát âm: $errorMessage")
                    }
                })
                .createAudioDeviceModule()
            val created = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()
            // Factory đã giữ tham chiếu riêng tới module âm thanh
            audioDeviceModule.release()
            factory = created
            return created
        }
    }

    private var peerConnection: PeerConnection? = null
    private val rtcFactory = getOrCreateFactory(context)
    private var iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )
    private var audioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var isOutputMuted: Boolean = false
    // Âm lượng nghe (0..1, 1 = mức gốc) áp vào mọi AudioTrack nhận từ đối tác
    private var remoteVolume: Float = 1.0f
    private var isMicEnabled: Boolean = false
    private val remoteAudioTracks = mutableListOf<AudioTrack>()

    // ICE candidate của đối tác có thể đến trước khi setRemoteDescription hoàn tất;
    // thêm vào lúc đó sẽ bị WebRTC từ chối, nên phải xếp hàng chờ.
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    /**
     * Cập nhật danh sách ICE Servers nhận từ Signaling Server
     */
    @Synchronized
    override fun updateIceServers(servers: List<IceServerSpec>) {
        val newServers = servers.map { spec ->
            PeerConnection.IceServer.builder(spec.urls).apply {
                spec.username?.let { setUsername(it) }
                spec.credential?.let { setPassword(it) }
            }.createIceServer()
        }
        if (newServers.isNotEmpty()) {
            iceServers = newServers
        }
    }

    /** true nếu đang có PeerConnection hoạt động. */
    @Synchronized
    override fun hasPeerConnection(): Boolean = peerConnection != null

    /**
     * Khởi tạo PeerConnection và chuẩn bị kênh Audio cho PTT.
     * Luôn đóng PeerConnection cũ trước (tránh rò rỉ và 2 phiên chạy song song).
     */
    @Synchronized
    override fun createPeerConnection(listener: RtcPeerListener) {
        createPeerConnection(object : CustomPeerConnectionObserver(this) {
            override fun onIceCandidate(p0: IceCandidate?) {
                p0?.let { listener.onIceCandidate(it.sdpMid, it.sdpMLineIndex, it.sdp) }
            }

            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                val state = when (p0) {
                    PeerConnection.IceConnectionState.NEW -> RtcIceState.NEW
                    PeerConnection.IceConnectionState.CHECKING -> RtcIceState.CHECKING
                    PeerConnection.IceConnectionState.CONNECTED -> RtcIceState.CONNECTED
                    PeerConnection.IceConnectionState.COMPLETED -> RtcIceState.COMPLETED
                    PeerConnection.IceConnectionState.DISCONNECTED -> RtcIceState.DISCONNECTED
                    PeerConnection.IceConnectionState.FAILED -> RtcIceState.FAILED
                    PeerConnection.IceConnectionState.CLOSED, null -> RtcIceState.CLOSED
                }
                listener.onIceConnectionChange(state)
            }
        })
    }

    @Synchronized
    fun createPeerConnection(observer: PeerConnection.Observer) {
        closeInternal()

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // Tiếp tục dò đường truyền khi đổi Wi-Fi <-> 4G (Seamless Handover)
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = rtcFactory.createPeerConnection(rtcConfig, observer)
        if (pc == null) {
            Log.e(TAG, "Không tạo được PeerConnection")
            return
        }
        peerConnection = pc

        // Tạo Audio Source và Track, GẮN VÀO PeerConnection để âm thanh thực sự được gửi đi
        val audioConstraints = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        val source = rtcFactory.createAudioSource(audioConstraints)
        val track = rtcFactory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, source)
        track.setEnabled(isMicEnabled) // Mặc định tắt mic khi chưa giữ phím PTT
        pc.addTrack(track, listOf(LOCAL_STREAM_ID))
        audioSource = source
        audioTrack = track
    }

    /**
     * Bật/tắt microphone khi người dùng bấm hoặc nhả phím PTT
     * Tự động ghi log thực địa cho TV4 (FieldTestLogger)
     */
    @Synchronized
    override fun setMicrophoneEnabled(enabled: Boolean) {
        isMicEnabled = enabled
        try {
            audioTrack?.setEnabled(enabled)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Audio track đã bị giải phóng: ${e.message}")
        }
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
    override fun setOutputMuted(muted: Boolean) {
        isOutputMuted = muted
        remoteAudioTracks.forEach { track ->
            try {
                track.setEnabled(!muted)
            } catch (ignored: Throwable) {}
        }
    }

    /**
     * Đặt âm lượng nghe tiếng đối tác (0 = im lặng, 1 = mức gốc). Áp ngay cho track hiện có
     * và cho cả track nhận được sau này (khi cuộc gọi được nối lại).
     */
    @Synchronized
    override fun setRemoteVolume(volume: Float) {
        remoteVolume = volume.coerceIn(0f, 1f)
        remoteAudioTracks.forEach { applyVolume(it) }
    }

    private fun applyVolume(track: AudioTrack) {
        try {
            track.setVolume(remoteVolume.toDouble())
        } catch (ignored: Throwable) {}
    }

    /** Ghi nhận track âm thanh từ đối tác (Unified Plan: onAddTrack). */
    @Synchronized
    fun handleRemoteTrack(track: MediaStreamTrack?) {
        val audio = track as? AudioTrack ?: return
        if (!remoteAudioTracks.contains(audio)) {
            remoteAudioTracks.add(audio)
        }
        try {
            audio.setEnabled(!isOutputMuted)
        } catch (ignored: Throwable) {}
        applyVolume(audio)
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
     * Tạo Offer SDP để gửi đi
     */
    override fun createOffer(onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) {
        createOfferInternal(MediaConstraints(), { onSuccess(it.description) }, onError)
    }

    private fun createOfferInternal(
        constraints: MediaConstraints,
        onSuccess: (SessionDescription) -> Unit,
        onError: (String) -> Unit
    ) {
        val pc = synchronized(this) { peerConnection } ?: return onError("Chưa có PeerConnection")
        constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return onError("Offer rỗng")
                // Chỉ gửi Offer sau khi đặt local description thành công
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() = onSuccess(desc)
                    override fun onSetFailure(error: String?) =
                        onError(error ?: "Lỗi đặt local description (offer)")
                }, desc)
            }
            override fun onCreateFailure(error: String?) {
                onError(error ?: "Lỗi tạo Offer không xác định")
            }
        }, constraints)
    }

    /**
     * Tạo Answer SDP phản hồi Offer
     */
    override fun createAnswer(onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) {
        val pc = synchronized(this) { peerConnection } ?: return onError("Chưa có PeerConnection")
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        pc.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return onError("Answer rỗng")
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() = onSuccess(desc.description)
                    override fun onSetFailure(error: String?) =
                        onError(error ?: "Lỗi đặt local description (answer)")
                }, desc)
            }
            override fun onCreateFailure(error: String?) {
                onError(error ?: "Lỗi tạo Answer không xác định")
            }
        }, constraints)
    }

    /**
     * Thiết lập Remote Description từ đối tác gửi tới.
     * Sau khi thành công, thêm các ICE candidate đã xếp hàng.
     */
    override fun setRemoteDescription(type: SdpType, sdp: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val sdpType = if (type == SdpType.OFFER) SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
        setRemoteDescription(SessionDescription(sdpType, sdp), onSuccess, onError)
    }

    fun setRemoteDescription(
        sdp: SessionDescription,
        onSuccess: () -> Unit,
        onError: (String) -> Unit = { Log.e(TAG, "Lỗi đặt remote description: $it") }
    ) {
        val pc = synchronized(this) { peerConnection } ?: return onError("Chưa có PeerConnection")
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                val queued = synchronized(this@WebRTCClient) {
                    if (peerConnection !== pc) return
                    remoteDescriptionSet = true
                    pendingRemoteCandidates.toList().also { pendingRemoteCandidates.clear() }
                }
                queued.forEach { pc.addIceCandidate(it) }
                onSuccess()
            }
            override fun onSetFailure(error: String?) {
                onError(error ?: "Lỗi đặt remote description")
            }
        }, sdp)
    }

    /**
     * Thêm ICE Candidate nhận được từ mạng (xếp hàng nếu chưa có remote description)
     */
    override fun addIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
    }

    @Synchronized
    fun addIceCandidate(candidate: IceCandidate) {
        val pc = peerConnection
        if (pc == null || !remoteDescriptionSet) {
            pendingRemoteCandidates.add(candidate)
            return
        }
        pc.addIceCandidate(candidate)
    }

    /**
     * Khởi động lại ICE (ICE Restart) khi chuyển từ Wi-Fi sang 4G (Seamless Handover).
     * Bắt buộc WebRTC dò tìm lại đường truyền mạng mà không cần ngắt cuộc gọi.
     */
    override fun restartIce(onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            // Cờ bắt buộc khởi động lại dò tìm ICE
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        createOfferInternal(constraints, { onSuccess(it.description) }, onError)
    }

    /**
     * Đóng và giải phóng tài nguyên WebRTC
     */
    @Synchronized
    override fun close() {
        closeInternal()
    }

    private fun closeInternal() {
        remoteAudioTracks.clear()
        pendingRemoteCandidates.clear()
        remoteDescriptionSet = false
        // Thứ tự giống mẫu AppRTC: giải phóng PeerConnection trước (nó tự giải phóng sender/track),
        // sau đó mới giải phóng AudioSource. Không dispose audioTrack riêng để tránh giải phóng 2 lần.
        peerConnection?.let { pc ->
            try {
                pc.dispose()
            } catch (e: Throwable) {
                Log.w(TAG, "Lỗi giải phóng PeerConnection: ${e.message}")
            }
        }
        peerConnection = null
        audioTrack = null
        audioSource?.let { source ->
            try {
                source.dispose()
            } catch (e: Throwable) {
                Log.w(TAG, "Lỗi giải phóng AudioSource: ${e.message}")
            }
        }
        audioSource = null
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
    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        webRTCClient?.handleRemoteTrack(receiver?.track())
    }
    override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
    override fun onRenegotiationNeeded() {}
}
