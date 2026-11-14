package com.example.damthoai.platform

/** Máy chủ STUN/TURN (độc lập thư viện WebRTC của từng nền tảng). */
data class IceServerSpec(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)

enum class SdpType { OFFER, ANSWER }

/** Trạng thái ICE rút gọn mà tầng điều phối cần biết. */
enum class RtcIceState { NEW, CHECKING, CONNECTED, COMPLETED, DISCONNECTED, FAILED, CLOSED }

/** Callback của một PeerConnection. Có thể được gọi trên luồng bất kỳ. */
interface RtcPeerListener {
    fun onIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String)
    fun onIceConnectionChange(state: RtcIceState)
}

/**
 * Lớp âm thanh P2P WebRTC cho PTT.
 * - Android: bọc WebRTCClient (io.getstream:stream-webrtc-android).
 * - iOS: cài đặt bằng Swift (gói WebRTC của Google), tiêm vào qua [PlatformServices].
 * Mọi callback onSuccess/onError có thể chạy trên luồng của WebRTC.
 */
interface RtcEngine {
    fun updateIceServers(servers: List<IceServerSpec>)
    fun hasPeerConnection(): Boolean
    fun createPeerConnection(listener: RtcPeerListener)
    fun createOffer(onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit)
    fun createAnswer(onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit)
    fun setRemoteDescription(type: SdpType, sdp: String, onSuccess: () -> Unit, onError: (String) -> Unit)
    fun addIceCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String)
    fun restartIce(onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit)
    fun setMicrophoneEnabled(enabled: Boolean)
    fun setOutputMuted(muted: Boolean)
    fun setRemoteVolume(volume: Float)
    fun close()
}
