package com.example.damthoai.webrtc

import android.content.Context
import org.webrtc.*

class WebRTCClient(private val context: Context) {

    // Singleton cho PeerConnectionFactory để chống lỗi Dispose
    companion object {
        private var factory: PeerConnectionFactory? = null

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
    private var iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )
    private var audioTrack: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var isOutputMuted: Boolean = false
    private val remoteAudioTracks = mutableListOf<AudioTrack>()

    /**
     * Cập nhật danh sách ICE Servers nhận từ Signaling Server
     */
    fun updateIceServers(newServers: List<PeerConnection.IceServer>) {
        iceServers = newServers
    }

    /**
     * Khởi tạo PeerConnection và chuẩn bị kênh Audio cho PTT
     */
    fun createPeerConnection(observer: PeerConnection.Observer) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = rtcFactory.createPeerConnection(rtcConfig, observer)

        // Tạo Audio Source và Track để điều khiển Push-to-Talk
        val audioConstraints = MediaConstraints()
        audioSource = rtcFactory.createAudioSource(audioConstraints)
        audioTrack = rtcFactory.createAudioTrack("ARDAMSA0", audioSource)
        audioTrack?.setEnabled(false) // Mặc định tắt mic khi chưa giữ phím PTT
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

    @Synchronized
    fun handleRemoteStreamAdded(stream: MediaStream?) {
        stream?.audioTracks?.forEach { track ->
            if (track != null && !remoteAudioTracks.contains(track)) {
                remoteAudioTracks.add(track)
                try {
                    track.setEnabled(!isOutputMuted)
                } catch (ignored: Throwable) {}
            }
        }
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
    fun createOffer(onSuccess: (SessionDescription) -> Unit, onError: (String) -> Unit) {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                    onSuccess(it)
                }
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
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                    onSuccess(it)
                }
            }
            override fun onCreateFailure(error: String?) {
                onError(error ?: "Lỗi tạo Answer không xác định")
            }
        }, constraints)
    }

    /**
     * Thiết lập Remote Description từ đối tác gửi tới
     */
    fun setRemoteDescription(sdp: SessionDescription, onSuccess: () -> Unit) {
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                onSuccess()
            }
        }, sdp)
    }

    /**
     * Thêm ICE Candidate nhận được từ mạng
     */
    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    /**
     * Khởi động lại ICE (ICE Restart) khi chuyển từ Wi-Fi sang 4G (Seamless Handover).
     * Bắt buộc WebRTC dò tìm lại đường truyền mạng mà không cần ngắt cuộc gọi.
     */
    fun restartIce(onSuccess: (SessionDescription) -> Unit, onError: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            // Cờ bắt buộc khởi động lại dò tìm ICE
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    // Lưu SDP mới làm cấu hình cục bộ và trả về cho CallManager
                    peerConnection?.setLocalDescription(SimpleSdpObserver(), it)
                    onSuccess(it)
                }
            }

            override fun onCreateFailure(error: String?) {
                onError(error ?: "Lỗi ICE Restart không xác định")
            }
        }, constraints)
    }

    /**
     * Đóng và giải phóng tài nguyên WebRTC
     */
    @Synchronized
    fun close() {
        remoteAudioTracks.clear()
        audioTrack?.dispose()
        audioSource?.dispose()
        audioTrack = null
        audioSource = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
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
    override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
    override fun onRenegotiationNeeded() {}
}
