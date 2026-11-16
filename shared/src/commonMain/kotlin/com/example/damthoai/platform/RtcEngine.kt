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
 * Lớp âm thanh P2P WebRTC cho PTT, dạng MESH: mỗi thành viên khác trong kênh có một
 * PeerConnection riêng (khóa bằng [peerId]), cùng dùng chung một track micro.
 * - Android: bọc WebRTCClient (io.getstream:stream-webrtc-android).
 * - iOS: cài đặt bằng Swift (gói WebRTC của Google), tiêm vào qua [PlatformServices].
 * Mọi callback onSuccess/onError có thể chạy trên luồng của WebRTC.
 */
interface RtcEngine {
    fun updateIceServers(servers: List<IceServerSpec>)
    fun hasPeerConnection(peerId: String): Boolean
    /** Tạo PeerConnection cho [peerId] (đóng PeerConnection cũ của đúng peer đó nếu có). */
    fun createPeerConnection(peerId: String, listener: RtcPeerListener)
    fun createOffer(peerId: String, onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit)
    fun createAnswer(peerId: String, onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit)
    fun setRemoteDescription(peerId: String, type: SdpType, sdp: String, onSuccess: () -> Unit, onError: (String) -> Unit)
    fun addIceCandidate(peerId: String, sdpMid: String?, sdpMLineIndex: Int, candidate: String)
    fun restartIce(peerId: String, onSuccess: (sdp: String) -> Unit, onError: (String) -> Unit)
    /** Đóng kết nối với một thành viên, giữ nguyên các kết nối khác. */
    fun closePeer(peerId: String)
    /** Công tắc chính của micro (giữ PTT). Âm thanh chỉ đi tới peer có [setPeerSending] = true. */
    fun setMicrophoneEnabled(enabled: Boolean)
    /**
     * Có gửi tiếng mình tới [peerId] hay không (mỗi peer một track micro riêng từ cùng nguồn thu).
     * Dùng để chỉ nói vào kênh đang chọn: người không cùng kênh đó không nghe thấy.
     */
    fun setPeerSending(peerId: String, enabled: Boolean)
    fun setOutputMuted(muted: Boolean)
    /** Âm lượng nghe tiếng của [peerId] (0 = tắt, 1 = mức gốc), theo âm lượng kênh chung với người đó. */
    fun setPeerVolume(peerId: String, volume: Float)
    /** Đóng tất cả kết nối. */
    fun close()
}
