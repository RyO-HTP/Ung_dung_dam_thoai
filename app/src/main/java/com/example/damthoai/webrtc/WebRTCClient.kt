package com.example.damthoai.webrtc

import android.content.Context
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory

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

    // Hàm gọi kết nối
    fun startCall() {
        val rtcFactory = getOrCreateFactory(context)
        
        // Cấu hình máy chủ STUN/TURN (Bạn có thể bổ sung lại server của bạn sau)
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        )

        // Tạo cuộc gọi mới
        peerConnection = rtcFactory.createPeerConnection(rtcConfig, object : CustomPeerConnectionObserver() {
            // Nơi xử lý các sự kiện ICE, AddStream...
        })
    }

    // Hàm ngắt kết nối an toàn (Tuyệt đối không dispose Factory)
    fun endCall() {
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
    }
}

// Lớp phụ trợ để code gọn hơn
open class CustomPeerConnectionObserver : PeerConnection.Observer {
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
    override fun onIceConnectionReceivingChange(p0: Boolean) {}
    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
    override fun onIceCandidate(p0: org.webrtc.IceCandidate?) {}
    override fun onIceCandidatesRemoved(p0: Array<out org.webrtc.IceCandidate>?) {}
    override fun onAddStream(p0: org.webrtc.MediaStream?) {}
    override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
    override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
    override fun onRenegotiationNeeded() {}
}