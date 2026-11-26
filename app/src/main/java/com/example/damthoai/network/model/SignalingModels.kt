package com.example.damthoai.network.model

import com.google.gson.annotations.SerializedName
import org.webrtc.PeerConnection

/**
 * Các loại gói tin Signaling chuẩn theo định nghĩa của Tuần 1 (Signaling Server)
 */
object SignalingType {
    const val REGISTER = "register"
    const val WEBRTC_CONFIG = "webrtc_config"
    const val OFFER = "offer"
    const val ANSWER = "answer"
    const val ICE_CANDIDATE = "ice_candidate"
    const val ICE_CANDIDATES_REMOVED = "ice_candidates_removed" // Tuần 3: ICE Gathering liên tục
    const val HANGUP = "hangup"
    const val NETWORK_CHANGE = "network_change" // Tuần 4: Seamless Handover
    const val VOICE_NOTE = "voice_note"         // Tuần 5 (TV2): Hàng đợi ngoại tuyến Store-and-Forward
    const val PEERS = "peers"                   // Danh sách thành viên đang online do server gửi
}

/**
 * Server gửi mỗi khi có người vào/ra: { "type": "peers", "peers": ["A", "B"] }
 */
data class PeersMessage(
    @SerializedName("type")
    val type: String = SignalingType.PEERS,
    @SerializedName("peers")
    val peers: List<String>? = null
)

/**
 * Gói tin cơ bản để xác định loại thông điệp (type)
 */
data class BaseSignalingMessage(
    @SerializedName("type")
    val type: String
)

/**
 * Gói tin đăng ký (Handshake) gửi từ thiết bị lên Server
 * Ví dụ: { "type": "register", "user_id": "Tong_Dao_Dien" }
 */
data class RegisterMessage(
    @SerializedName("type")
    val type: String = SignalingType.REGISTER,
    @SerializedName("user_id")
    val userId: String
)

/**
 * Cấu hình từng STUN / TURN server từ Metered hoặc Google
 */
data class IceServerConfig(
    @SerializedName("urls")
    val urls: Any?, // Có thể là String ("stun:...") hoặc List<String>
    @SerializedName("username")
    val username: String? = null,
    @SerializedName("credential")
    val credential: String? = null
) {
    /**
     * Chuyển đổi sang đối tượng PeerConnection.IceServer chuẩn của WebRTC Android
     */
    fun toPeerConnectionIceServer(): PeerConnection.IceServer? {
        val urlList = when (urls) {
            is String -> listOf(urls)
            is List<*> -> urls.filterIsInstance<String>()
            else -> emptyList()
        }
        if (urlList.isEmpty()) return null

        val builder = PeerConnection.IceServer.builder(urlList)
        if (!username.isNullOrEmpty()) {
            builder.setUsername(username)
        }
        if (!credential.isNullOrEmpty()) {
            builder.setPassword(credential)
        }
        return builder.createIceServer()
    }
}

/**
 * Cụm cấu hình ICE Servers
 */
data class WebRtcConfigData(
    @SerializedName("iceServers")
    val iceServers: List<IceServerConfig>? = null
)

/**
 * Gói tin nhận cấu hình WebRTC từ server sau khi đăng ký
 */
data class WebRtcConfigMessage(
    @SerializedName("type")
    val type: String = SignalingType.WEBRTC_CONFIG,
    @SerializedName("config")
    val config: WebRtcConfigData? = null
)

/**
 * Gói tin gửi / nhận Offer hoặc Answer SDP
 */
data class SdpSignalingMessage(
    @SerializedName("type")
    val type: String, // "offer" hoặc "answer"
    @SerializedName("target_id")
    val targetId: String? = null,
    @SerializedName("sender_id")
    val senderId: String? = null,
    @SerializedName("sdp")
    val sdp: String
)

/**
 * Gói tin ICE Candidate trao đổi giữa 2 thiết bị
 */
data class IceCandidateSignalingMessage(
    @SerializedName("type")
    val type: String = SignalingType.ICE_CANDIDATE,
    @SerializedName("target_id")
    val targetId: String? = null,
    @SerializedName("sender_id")
    val senderId: String? = null,
    @SerializedName("sdpMid")
    val sdpMid: String? = null,
    @SerializedName("sdpMLineIndex")
    val sdpMLineIndex: Int = 0,
    @SerializedName("candidate")
    val candidate: String
)

/**
 * Một ICE candidate dạng rút gọn (dùng trong danh sách candidate bị thu hồi)
 */
data class IceCandidatePayload(
    @SerializedName("sdpMid")
    val sdpMid: String? = null,
    @SerializedName("sdpMLineIndex")
    val sdpMLineIndex: Int = 0,
    @SerializedName("candidate")
    val candidate: String
)

/**
 * Tuần 3: Khi ICE Gathering liên tục phát hiện một đường truyền không còn dùng được
 * (vd: mất Wi-Fi), WebRTC thu hồi các candidate cũ -> báo cho đối tác bỏ đi.
 */
data class IceCandidatesRemovedMessage(
    @SerializedName("type")
    val type: String = SignalingType.ICE_CANDIDATES_REMOVED,
    @SerializedName("target_id")
    val targetId: String? = null,
    @SerializedName("sender_id")
    val senderId: String? = null,
    @SerializedName("candidates")
    val candidates: List<IceCandidatePayload> = emptyList()
)

/**
 * Gói tin kết thúc cuộc gọi / thông báo đổi mạng
 */
data class SimpleSignalingMessage(
    @SerializedName("type")
    val type: String,
    @SerializedName("target_id")
    val targetId: String? = null,
    @SerializedName("sender_id")
    val senderId: String? = null,
    @SerializedName("user_id")
    val userId: String? = null,
    @SerializedName("network_type")
    val networkType: String? = null
)

/**
 * Tuần 5 (TV2): Gói tin nhắn thoại gửi bù (Store-and-Forward) khi có mạng trở lại.
 */
data class VoiceNoteSignalingMessage(
    @SerializedName("type")
    val type: String = SignalingType.VOICE_NOTE,
    @SerializedName("id")
    val id: String,
    @SerializedName("target_id")
    val targetId: String? = null,
    @SerializedName("sender_id")
    val senderId: String? = null,
    @SerializedName("channel_id")
    val channelId: String = "",
    @SerializedName("recorded_at_ms")
    val recordedAtMs: Long = 0L,
    @SerializedName("duration_ms")
    val durationMs: Long = 0L,
    @SerializedName("sample_rate_hz")
    val sampleRateHz: Int = 16_000,
    @SerializedName("audio_base64")
    val audioBase64: String
)
