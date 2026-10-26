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
}

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
