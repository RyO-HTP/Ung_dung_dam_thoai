package com.example.damthoai.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

/**
 * Trình quản lý kết nối WebRTC dành riêng cho đàm thoại âm thanh (Voice / PTT Walkie-Talkie).
 * Hỗ trợ nhận STUN/TURN động từ Signaling Server và điều khiển Micro đàm thoại.
 */
class WebRTCClient(private val context: Context) {

    companion object {
        private const val TAG = "WebRTCClient"
        private const val AUDIO_TRACK_ID = "damthoai_audio_track"

        @Volatile
        private var factory: PeerConnectionFactory? = null

        /**
         * Khởi tạo Singleton PeerConnectionFactory an toàn chống lỗi dispose lặp
         */
        fun getOrCreateFactory(context: Context): PeerConnectionFactory {
            return factory ?: synchronized(this) {
                factory ?: run {
                    val initOptions = PeerConnectionFactory.InitializationOptions
                        .builder(context.applicationContext)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions()
                    PeerConnectionFactory.initialize(initOptions)

                    val options = PeerConnectionFactory.Options()
                    PeerConnectionFactory.builder()
                        .setOptions(options)
                        .createPeerConnectionFactory().also { factory = it }
                }
            }
        }
    }

    private val rtcFactory: PeerConnectionFactory by lazy {
        getOrCreateFactory(context)
    }

    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    // Lưu danh sách ICE Servers hiện hành (mặc định có Google STUN làm fallback)
    private var currentIceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    /**
     * Cập nhật danh sách máy chủ STUN/TURN nhận được từ Signaling Server
     */
    fun updateIceServers(servers: List<PeerConnection.IceServer>) {
        if (servers.isNotEmpty()) {
            Log.d(TAG, "Đã cập nhật ${servers.size} ICE Servers động từ server.")
            currentIceServers = servers
        }
    }

    /**
     * Khởi tạo luồng âm thanh nội bộ (Microphone) cho bộ đàm
     */
    fun initAudioTrack(): AudioTrack {
        if (localAudioTrack == null) {
            val audioConstraints = MediaConstraints().apply {
                // Tối ưu cho đàm thoại giọng nói: lọc nhiễu, chống vang echo
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            }
            localAudioSource = rtcFactory.createAudioSource(audioConstraints)
            localAudioTrack = rtcFactory.createAudioTrack(AUDIO_TRACK_ID, localAudioSource)
            // Mặc định bật audio, khi dùng chế độ PTT (Push-to-Talk) có thể toggle
            localAudioTrack?.setEnabled(true)
        }
        return localAudioTrack!!
    }

    /**
     * Bật/tắt micro (hỗ trợ nút Push-to-Talk)
     */
    fun setMicrophoneEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        Log.d(TAG, "Trạng thái Micro PTT: ${if (enabled) "MỞ (Đang nói)" else "TẮT (Chế độ nghe)"}")
    }

    /**
     * Khởi tạo kết nối PeerConnection với ICE Servers động
     */
    fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(currentIceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = rtcFactory.createPeerConnection(rtcConfig, observer)

        // Gắn luồng Audio vào PeerConnection
        val audioTrack = initAudioTrack()
        peerConnection?.addTrack(audioTrack, listOf("damthoai_stream"))

        return peerConnection
    }

    /**
     * Tạo Offer SDP để gửi cho bên nhận
     */
    fun createOffer(
        onSuccess: (SessionDescription) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Tạo và gán Offer SDP cục bộ thành công")
                            onSuccess(sdp)
                        }

                        override fun onSetFailure(err: String?) {
                            onError(err ?: "Lỗi setLocalDescription Offer")
                        }
                    }, sdp)
                }
            }

            override fun onCreateFailure(err: String?) {
                onError(err ?: "Lỗi createOffer")
            }
        }, constraints)
    }

    /**
     * Tạo Answer SDP phản hồi Offer
     */
    fun createAnswer(
        onSuccess: (SessionDescription) -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Tạo và gán Answer SDP cục bộ thành công")
                            onSuccess(sdp)
                        }

                        override fun onSetFailure(err: String?) {
                            onError(err ?: "Lỗi setLocalDescription Answer")
                        }
                    }, sdp)
                }
            }

            override fun onCreateFailure(err: String?) {
                onError(err ?: "Lỗi createAnswer")
            }
        }, constraints)
    }

    /**
     * Thiết lập Remote Description (khi nhận Offer hoặc Answer từ máy đối tác)
     */
    fun setRemoteDescription(
        sdp: SessionDescription,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "Thiết lập Remote Description thành công (${sdp.type})")
                onSuccess()
            }

            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Lỗi khi gán Remote Description: $err")
                onError(err ?: "Lỗi setRemoteDescription")
            }
        }, sdp)
    }

    /**
     * Bổ sung ICE Candidate nhận được từ máy đối tác
     */
    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    /**
     * Ngắt kết nối cuộc gọi an toàn (giữ lại Factory)
     */
    fun close() {
        try {
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            localAudioTrack?.dispose()
            localAudioTrack = null

            localAudioSource?.dispose()
            localAudioSource = null
            Log.d(TAG, "Đã dọn dẹp tài nguyên cuộc gọi WebRTC.")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đóng kết nối WebRTC: ${e.message}", e)
        }
    }
}

/**
 * Lớp phụ trợ SdpObserver với cài đặt mặc định
 */
open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

/**
 * Lớp phụ trợ PeerConnection.Observer với cài đặt mặc định
 */
open class SimplePeerConnectionObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(candidate: IceCandidate?) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
    override fun onAddStream(stream: MediaStream?) {}
    override fun onRemoveStream(stream: MediaStream?) {}
    override fun onDataChannel(channel: DataChannel?) {}
    override fun onRenegotiationNeeded() {}
    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
}
