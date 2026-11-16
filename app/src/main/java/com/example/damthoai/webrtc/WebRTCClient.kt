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

        // Giữ module âm thanh để chọn micro khi đổi tuyến (phần Java vẫn dùng được sau release())
        @Volatile
        private var audioDeviceModule: JavaAudioDeviceModule? = null

        /**
         * Chỉ định micro cho luồng thu âm WebRTC (null = để hệ thống tự chọn).
         * Cần thiết với tai nghe USB-C: micro USB nằm ở module âm thanh riêng, luồng thu đang mở
         * trên micro máy KHÔNG tự chuyển theo setCommunicationDevice → đối phương nghe im lặng.
         */
        fun setPreferredInputDevice(device: android.media.AudioDeviceInfo?) {
            try {
                audioDeviceModule?.setPreferredInputDevice(device)
                Log.d(TAG, "Micro ưu tiên: type=${device?.type} ${device?.productName}")
            } catch (e: Throwable) {
                Log.e(TAG, "Không chọn được micro: ${e.message}")
            }
        }

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
            this.audioDeviceModule = audioDeviceModule
            factory = created
            return created
        }
    }

    /**
     * Một kết nối P2P tới một thành viên (mesh: mỗi thành viên khác trong kênh một [Peer]).
     * ICE candidate của đối tác có thể đến trước khi setRemoteDescription hoàn tất;
     * thêm vào lúc đó sẽ bị WebRTC từ chối, nên phải xếp hàng chờ trong [pendingCandidates].
     */
    private class Peer(val pc: PeerConnection, val localTrack: AudioTrack) {
        val remoteTracks = mutableListOf<AudioTrack>()
        val pendingCandidates = mutableListOf<IceCandidate>()
        var remoteDescriptionSet = false
        // Có gửi tiếng mình tới người này không (chỉ người cùng kênh đang phát mới được gửi)
        var sending = false
        // Âm lượng nghe người này (0..1), theo âm lượng của kênh chung
        var volume = 1.0f
    }

    private val peers = mutableMapOf<String, Peer>()
    private val rtcFactory = getOrCreateFactory(context)
    private var iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )
    // Nguồn thu micro DÙNG CHUNG; mỗi peer có track riêng từ nguồn này để bật/tắt gửi theo từng người
    private var audioSource: AudioSource? = null
    private var trackCounter = 0
    private var isOutputMuted: Boolean = false
    private var isMicEnabled: Boolean = false

    private fun allRemoteTracks(): List<AudioTrack> = peers.values.flatMap { it.remoteTracks }

    private fun applySending(peer: Peer) {
        try {
            peer.localTrack.setEnabled(isMicEnabled && peer.sending)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Audio track đã bị giải phóng: ${e.message}")
        }
    }

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

    /** true nếu đang có PeerConnection tới [peerId]. */
    @Synchronized
    override fun hasPeerConnection(peerId: String): Boolean = peers.containsKey(peerId)

    /**
     * Khởi tạo PeerConnection tới [peerId] và gắn track micro dùng chung.
     * Chỉ đóng PeerConnection cũ của ĐÚNG peer này; kết nối tới các thành viên khác giữ nguyên.
     */
    @Synchronized
    override fun createPeerConnection(peerId: String, listener: RtcPeerListener) {
        createPeerConnection(peerId, object : CustomPeerConnectionObserver(this, peerId) {
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
    fun createPeerConnection(peerId: String, observer: PeerConnection.Observer) {
        closePeerInternal(peerId)

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // Tiếp tục dò đường truyền khi đổi Wi-Fi <-> 4G (Seamless Handover)
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val pc = rtcFactory.createPeerConnection(rtcConfig, observer)
        if (pc == null) {
            Log.e(TAG, "Không tạo được PeerConnection tới $peerId")
            return
        }
        // Track micro riêng cho peer này (cùng nguồn thu), mặc định chưa gửi
        val track = rtcFactory.createAudioTrack("${LOCAL_AUDIO_TRACK_ID}_${trackCounter++}", ensureAudioSource())
        val peer = Peer(pc, track)
        peers[peerId] = peer
        applySending(peer)
        pc.addTrack(track, listOf(LOCAL_STREAM_ID))
    }

    /** Tạo (một lần) nguồn thu micro dùng chung cho mọi PeerConnection. */
    private fun ensureAudioSource(): AudioSource {
        audioSource?.let { return it }
        val audioConstraints = MediaConstraints().apply {
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        return rtcFactory.createAudioSource(audioConstraints).also { audioSource = it }
    }

    /** Bật/tắt gửi tiếng mình tới [peerId] (chỉ có tác dụng khi đang giữ PTT). */
    @Synchronized
    override fun setPeerSending(peerId: String, enabled: Boolean) {
        val peer = peers[peerId] ?: return
        peer.sending = enabled
        applySending(peer)
    }

    /**
     * Bật/tắt microphone khi người dùng bấm hoặc nhả phím PTT
     * Tự động ghi log thực địa cho TV4 (FieldTestLogger)
     */
    @Synchronized
    override fun setMicrophoneEnabled(enabled: Boolean) {
        isMicEnabled = enabled
        peers.values.forEach { applySending(it) }
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
        allRemoteTracks().forEach { track ->
            try {
                track.setEnabled(!muted)
            } catch (ignored: Throwable) {}
        }
    }

    /**
     * Đặt âm lượng nghe tiếng của [peerId] (0 = im lặng, 1 = mức gốc). Áp ngay cho track hiện có
     * và cho cả track nhận được sau này.
     */
    @Synchronized
    override fun setPeerVolume(peerId: String, volume: Float) {
        val peer = peers[peerId] ?: return
        peer.volume = volume.coerceIn(0f, 1f)
        peer.remoteTracks.forEach { applyVolume(it, peer.volume) }
    }

    private fun applyVolume(track: AudioTrack, volume: Float) {
        try {
            track.setVolume(volume.toDouble())
        } catch (ignored: Throwable) {}
    }

    /** Ghi nhận track âm thanh từ đối tác [peerId] (Unified Plan: onAddTrack). */
    @Synchronized
    fun handleRemoteTrack(peerId: String, track: MediaStreamTrack?) {
        val audio = track as? AudioTrack ?: return
        val peer = peers[peerId] ?: return
        if (!peer.remoteTracks.contains(audio)) {
            peer.remoteTracks.add(audio)
        }
        try {
            audio.setEnabled(!isOutputMuted)
        } catch (ignored: Throwable) {}
        applyVolume(audio, peer.volume)
    }

    @Synchronized
    fun handleRemoteStreamAdded(peerId: String, stream: MediaStream?) {
        stream?.audioTracks?.forEach { handleRemoteTrack(peerId, it) }
    }

    @Synchronized
    fun handleRemoteStreamRemoved(peerId: String, stream: MediaStream?) {
        val peer = peers[peerId] ?: return
        stream?.audioTracks?.forEach { track ->
            if (track != null) {
                peer.remoteTracks.remove(track)
            }
        }
    }

    @Synchronized
    private fun pcOf(peerId: String): PeerConnection? = peers[peerId]?.pc

    /**
     * Tạo Offer SDP gửi tới [peerId]
     */
    override fun createOffer(peerId: String, onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) {
        createOfferInternal(peerId, MediaConstraints(), { onSuccess(it.description) }, onError)
    }

    private fun createOfferInternal(
        peerId: String,
        constraints: MediaConstraints,
        onSuccess: (SessionDescription) -> Unit,
        onError: (String) -> Unit
    ) {
        val pc = pcOf(peerId) ?: return onError("Chưa có PeerConnection tới $peerId")
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
     * Tạo Answer SDP phản hồi Offer của [peerId]
     */
    override fun createAnswer(peerId: String, onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) {
        val pc = pcOf(peerId) ?: return onError("Chưa có PeerConnection tới $peerId")
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
     * Thiết lập Remote Description từ [peerId].
     * Sau khi thành công, thêm các ICE candidate đã xếp hàng.
     */
    override fun setRemoteDescription(
        peerId: String,
        type: SdpType,
        sdp: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val sdpType = if (type == SdpType.OFFER) SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
        val peer = synchronized(this) { peers[peerId] } ?: return onError("Chưa có PeerConnection tới $peerId")
        val pc = peer.pc
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                val queued = synchronized(this@WebRTCClient) {
                    if (peers[peerId] !== peer) return
                    peer.remoteDescriptionSet = true
                    peer.pendingCandidates.toList().also { peer.pendingCandidates.clear() }
                }
                queued.forEach { pc.addIceCandidate(it) }
                onSuccess()
            }
            override fun onSetFailure(error: String?) {
                onError(error ?: "Lỗi đặt remote description")
            }
        }, SessionDescription(sdpType, sdp))
    }

    /**
     * Thêm ICE Candidate của [peerId] (xếp hàng nếu chưa có remote description)
     */
    @Synchronized
    override fun addIceCandidate(peerId: String, sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        val peer = peers[peerId] ?: return
        val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        if (!peer.remoteDescriptionSet) {
            peer.pendingCandidates.add(ice)
            return
        }
        peer.pc.addIceCandidate(ice)
    }

    /**
     * Khởi động lại ICE (ICE Restart) với [peerId] khi chuyển từ Wi-Fi sang 4G (Seamless Handover).
     * Bắt buộc WebRTC dò tìm lại đường truyền mạng mà không cần ngắt cuộc gọi.
     */
    override fun restartIce(peerId: String, onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            // Cờ bắt buộc khởi động lại dò tìm ICE
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        createOfferInternal(peerId, constraints, { onSuccess(it.description) }, onError)
    }

    /** Đóng kết nối với một thành viên; khi không còn ai thì giải phóng luôn micro. */
    @Synchronized
    override fun closePeer(peerId: String) {
        closePeerInternal(peerId)
        if (peers.isEmpty()) releaseLocalTrack()
    }

    /**
     * Đóng và giải phóng toàn bộ tài nguyên WebRTC
     */
    @Synchronized
    override fun close() {
        peers.keys.toList().forEach { closePeerInternal(it) }
        releaseLocalTrack()
    }

    private fun closePeerInternal(peerId: String) {
        val peer = peers.remove(peerId) ?: return
        // pc.dispose() tự giải phóng sender (chỉ nhả tham chiếu của nó tới track micro dùng chung)
        try {
            peer.pc.dispose()
        } catch (e: Throwable) {
            Log.w(TAG, "Lỗi giải phóng PeerConnection tới $peerId: ${e.message}")
        }
    }

    /**
     * Giải phóng micro dùng chung (gọi sau khi mọi PeerConnection đã đóng).
     * Giống mẫu AppRTC: không dispose audioTrack riêng (sender của PeerConnection đã nhả nó),
     * chỉ giải phóng AudioSource.
     */
    private fun releaseLocalTrack() {
        audioSource?.let { source ->
            try {
                source.dispose()
            } catch (e: Throwable) {
                Log.w(TAG, "Lỗi giải phóng AudioSource: ${e.message}")
            }
        }
        audioSource = null
    }

    fun endCall() { close() }
}

// Lớp phụ trợ cho SdpObserver
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

// Lớp phụ trợ cho PeerConnection.Observer (gắn với một thành viên [peerId])
open class CustomPeerConnectionObserver(
    private val webRTCClient: WebRTCClient? = null,
    private val peerId: String = ""
) : PeerConnection.Observer {
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(p0: Boolean) {}
    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(p0: org.webrtc.IceCandidate?) {}
    override fun onIceCandidatesRemoved(p0: Array<out org.webrtc.IceCandidate>?) {}
    override fun onAddStream(p0: org.webrtc.MediaStream?) {
        webRTCClient?.handleRemoteStreamAdded(peerId, p0)
    }
    override fun onRemoveStream(p0: org.webrtc.MediaStream?) {
        webRTCClient?.handleRemoteStreamRemoved(peerId, p0)
    }
    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        webRTCClient?.handleRemoteTrack(peerId, receiver?.track())
    }
    override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
    override fun onRenegotiationNeeded() {}
}
