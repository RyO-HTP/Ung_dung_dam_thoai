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
    const val USER_LIST = "user_list"
    const val EVENT_INFO = "event_info"
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
    val userId: String,
    /** Mã PIN sự kiện: server gom các thiết bị cùng PIN vào một nhóm đàm thoại. */
    @SerializedName("event_pin")
    val eventPin: String? = null,
    /** Tên hiển thị trong danh sách thành viên. */
    @SerializedName("display_name")
    val displayName: String? = null,
    @SerializedName("role_title")
    val roleTitle: String? = null,
    /** true nếu thiết bị là ban tổ chức (người tạo sự kiện). */
    @SerializedName("is_organizer")
    val isOrganizer: Boolean = false,
    /** Tên sự kiện do ban tổ chức đặt (chỉ ban tổ chức gửi). */
    @SerializedName("event_name")
    val eventName: String? = null
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
 * Gói tin nhận danh sách các thiết bị/người dùng online từ server
 */
data class UserListMessage(
    @SerializedName("type")
    val type: String = SignalingType.USER_LIST,
    @SerializedName("users")
    val users: List<String>? = null,
    /** Danh sách chi tiết (tên, vai trò). Server bản cũ không gửi trường này. */
    @SerializedName("members")
    val members: List<OnlineMember>? = null
)

/**
 * Một thành viên đang online trong sự kiện.
 */
data class OnlineMember(
    @SerializedName("user_id")
    val userId: String = "",
    @SerializedName("display_name")
    val displayName: String? = null,
    @SerializedName("role_title")
    val roleTitle: String? = null,
    @SerializedName("is_organizer")
    val isOrganizer: Boolean = false
)

/**
 * Thông tin sự kiện server trả về sau khi đăng ký bằng mã PIN.
 */
data class EventInfoMessage(
    @SerializedName("type")
    val type: String = SignalingType.EVENT_INFO,
    @SerializedName("event_pin")
    val eventPin: String? = null,
    @SerializedName("event_name")
    val eventName: String? = null,
    @SerializedName("found")
    val found: Boolean = false
)
